# ResolveX CI/CD, explained simply

This guide walks through the whole pipeline: what happens from the moment code
reaches GitHub until users see the new version behind the load balancer. Every
step shows the **real command** from the [`Jenkinsfile`](../Jenkinsfile) or
[`deploy/deploy-app.sh`](../deploy/deploy-app.sh), followed by what it does in
plain words.

- **CI (Continuous Integration)** checks that the code builds, the tests pass,
  the code quality is acceptable and the Docker images have no serious
  vulnerabilities, then stores the images in ECR.
- **CD (Continuous Deployment)** takes those images and runs them on the two app
  servers in AWS, then checks that the site works.

---

## 1. The big picture

```
 Developer
    │  git push / merge PR to main
    ▼
 GitHub (dilliganesh-xd/ResolveX-ITSM)
    │  Jenkins checks out the code
    ▼
 Jenkins ───────────────────────── CI ──────────────────────────────┐
    1  Frontend Build            npm ci + npm run build              │
    2  Backend Test & Build      mvn clean verify (all tests)        │
    3  SonarCloud Analysis       code quality report                 │
    4  Docker Build - Frontend   Nginx + React image                 │
    5  Docker Build - Backend    Java 21 + Spring Boot image         │
    6  Trivy Scan - Frontend     stop if HIGH/CRITICAL fixable CVEs  │
    7  Trivy Scan - Backend      same check for the backend          │
    8  ECR Login                 docker login to AWS ECR             │
    9  Tag Images                give the images their ECR names     │
   10  Push Images to ECR        upload both images                  │
 ───────────────────────────────── CD ──────────────────────────────┤
   11  Deploy to App Servers     SSH to app-1, then app-2            │
   12  Verify via Load Balancer  GET http://<ALB>/api/health         │
 ───────────────────────────────────────────────────────────────────┘
    │
    ▼
 Users ──HTTP 80──► ALB (resolvex-alb) ──┬─► resolvex-app-1 (ap-south-1a)
                                         └─► resolvex-app-2 (ap-south-1b)

 Each app server runs 2 containers:
   resolvex-frontend  Nginx + React   (port 80 on the server)
   resolvex-backend   Spring Boot     (127.0.0.1:8081, hidden from outside)

 Both servers share:
   RDS PostgreSQL (data) · ElastiCache Valkey (cache) · S3 (attachments)
   EventBridge → Lambda → SES (SLA breach emails)
```

**One sentence version:** Jenkins builds and tests the code, packs it into two
Docker images, scans them, pushes them to ECR, then logs in to each EC2 server,
pulls the new images and restarts the containers one server at a time, and
finally checks the site through the load balancer.

---

## 2. Who built what

| Part | Owner |
| --- | --- |
| Application code, GitHub, app EC2 servers, ALB, CD stages | Dilli Ganesh |
| Jenkins server, RDS, S3, Redis (ElastiCache) | Raja |
| Docker / ECR, monitoring, EventBridge + Lambda + SES | Oviya |

---

## 3. The top of the Jenkinsfile (settings, not steps)

### 3.1 `agent any`

```groovy
pipeline {
    agent any
```

Run the pipeline on any available Jenkins machine. We have one Jenkins server,
so it runs there.

### 3.2 `options`

```groovy
options {
    buildDiscarder(logRotator(numToKeepStr: '2'))
    disableConcurrentBuilds()
}
```

| Option | Simple meaning |
| --- | --- |
| `buildDiscarder ... '2'` | Keep only the last 2 build logs, so the Jenkins disk does not fill up. |
| `disableConcurrentBuilds()` | Only one build at a time. Two deploys at once would fight over the same servers. |

### 3.3 `tools`

```groovy
tools {
    jdk 'JDK21'
    nodejs 'NodeJS-20'
    maven 'MAVEN'
}
```

These names are set up in **Manage Jenkins → Tools**. Jenkins puts Java 21,
Node.js 20 and Maven on the `PATH` before the stages run, so `mvn` and `npm`
just work.

### 3.4 `environment` (values every stage can read)

| Variable | Value | Used for |
| --- | --- | --- |
| `AWS_REGION` | `ap-south-1` | Mumbai region, where everything lives |
| `AWS_ACCOUNT_ID` / `ECR_REGISTRY` | `333968387080...amazonaws.com` | Address of our private Docker registry (ECR) |
| `FRONTEND_IMAGE` / `BACKEND_IMAGE` | `incident-management-frontend/backend` | Local image names on the Jenkins server |
| `ECR_FRONTEND_REPO` / `ECR_BACKEND_REPO` | `resolvex-frontend/backend` | Repository names inside ECR |
| `TRIVY_IMAGE` / `TRIVY_CACHE` | `aquasec/trivy:0.75.0` / `trivy-cache` | Scanner version, and a Docker volume that stores its vulnerability database so it is not downloaded every time |
| `RDS_HOST` / `RDS_DB` / `RDS_USER` | `resolvexdb...rds.amazonaws.com` | PostgreSQL database |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_SSL_ENABLED` | `clustercfg.resolvex-cache...` / `6379` / `true` | ElastiCache Valkey. It requires encryption in transit, so SSL must be `true` |
| `S3_BUCKET_NAME` | `resolvex-itsm-attachments` | Where incident attachments are stored |
| `APP_HOSTS` | `172.31.44.227 172.31.0.225` | **Private** IPs of app-1 and app-2. Jenkins is in the same VPC, so it can reach them |
| `SLA_PRIMARY_HOST` | `172.31.44.227` | Only app-1 runs the SLA sweep (explained in step 11) |
| `ALB_DNS` | `resolvex-alb-2061703933...elb.amazonaws.com` | The public URL users open |

`BUILD_NUMBER` is not in this list: Jenkins provides it automatically (1, 2, 3,
...). We use it as the **image tag**, so every build has its own image version,
for example `resolvex-backend:20`.

**No passwords are in this file.** Secrets live in Jenkins Credentials and are
loaded only inside step 11.

---

## 4. CI stages, step by step

### Step 1: Frontend Build

```groovy
dir('frontend') {
    sh 'npm ci'
    sh 'npm run build'
}
```

| Command | What it does |
| --- | --- |
| `dir('frontend')` | Go into the `frontend` folder. |
| `npm ci` | Install the exact package versions in `package-lock.json` (a clean, repeatable install, unlike `npm install`). |
| `npm run build` | Type-check the TypeScript and build the React app into `dist/`. |

**Why:** a quick early check. If the React code has a type error, the pipeline
stops here in about a minute instead of after the slower steps.
**If it fails:** a TypeScript or build error in the frontend.

### Step 2: Backend Test & Build

```groovy
dir('backend') {
    sh 'mvn -B clean verify'
}
```

| Part | Meaning |
| --- | --- |
| `mvn` | Maven, the Java build tool. It reads `backend/pom.xml`. |
| `-B` | Batch mode: no colours or prompts, cleaner Jenkins logs. |
| `clean` | Delete the old `target/` folder so nothing stale is reused. |
| `verify` | Compile → run **all** unit and integration tests → package the JAR. |

The tests use the `test` profile: an in-memory H2 database and an in-memory
cache, so they need no RDS or Redis.
**If it fails:** a compile error or a failing test. The log shows which test.

### Step 3: SonarCloud Analysis

```groovy
dir('backend') {
    withSonarQubeEnv('SonarCloud') {
        sh '''
            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar \
            -Dsonar.projectKey=sonartokenkey_itsm-resolvex \
            -Dsonar.organization=sonartokenkey
        '''
    }
}
```

| Part | Meaning |
| --- | --- |
| `withSonarQubeEnv('SonarCloud')` | Loads the SonarCloud URL and token saved in **Manage Jenkins → System**. The token never appears in the Jenkinsfile. |
| `sonar-maven-plugin:...:sonar` | Sends the backend code to SonarCloud for analysis: bugs, code smells, duplicated code, security hotspots. |
| `projectKey` / `organization` | Which SonarCloud project receives the report. |

**Why:** a quality report for every build, viewable on sonarcloud.io.
This stage reports only; it does not stop the pipeline on a bad quality score.

### Step 4: Docker Build - Frontend

```bash
docker build --pull -t ${FRONTEND_IMAGE}:${BUILD_NUMBER} ./frontend
```

| Part | Meaning |
| --- | --- |
| `docker build ./frontend` | Build an image using [`frontend/Dockerfile`](../frontend/Dockerfile). |
| `--pull` | Always download the newest base images, so we get the latest security fixes. |
| `-t name:tag` | Name the image, e.g. `incident-management-frontend:20`. |

What the frontend Dockerfile does (a **multi-stage** build):

1. **Build stage** (`node:20-alpine`): `npm ci` and `npm run build` → React files in `dist/`.
2. **Runtime stage** (`nginxinc/nginx-unprivileged:alpine`): copy only `dist/`
   and `nginx.conf`. Node.js is not in the final image, so it is small.
   - `apk upgrade` installs the latest Alpine security fixes.
   - Runs as user `101` (not root), so Nginx listens on **8080**, not 80.

What [`nginx.conf`](../frontend/nginx.conf) does inside the container:

- `/` → serves the React app. Unknown paths such as `/incidents/5` return
  `index.html`, so React Router handles them.
- `/api/` → forwarded to `http://resolvex-backend:8080` (the backend container).
  `resolver 127.0.0.11` is Docker's internal DNS, re-checked every 10 seconds,
  so Nginx finds the new backend container after each deploy.
- `client_max_body_size 11m` → allows attachment uploads up to 10 MB.

### Step 5: Docker Build - Backend

```bash
docker build --pull -t ${BACKEND_IMAGE}:${BUILD_NUMBER} ./backend
```

What [`backend/Dockerfile`](../backend/Dockerfile) does:

1. **Build stage** (`maven:3.9.16-eclipse-temurin-21`):
   `mvn -B clean package -DskipTests` → `app.jar`.
   Tests are skipped here because they already passed in step 2.
2. **Runtime stage** (`eclipse-temurin:21-jre-alpine`): only the Java runtime
   and `app.jar`. Maven and the source code are not in the final image.
   - `apk upgrade` for security fixes.
   - Runs as user `10001` (not root).
   - `EXPOSE 8080`, started with `java -jar app.jar`.

### Steps 6 and 7: Trivy Scan - Frontend / Backend

```bash
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v ${TRIVY_CACHE}:/root/.cache/ \
  ${TRIVY_IMAGE} \
  image \
  --severity HIGH,CRITICAL \
  --ignore-unfixed \
  --exit-code 1 \
  ${FRONTEND_IMAGE}:${BUILD_NUMBER}
```

Trivy is a security scanner. It looks at every package inside the image and
compares it with a list of known vulnerabilities (CVEs).

| Part | Meaning |
| --- | --- |
| `docker run --rm aquasec/trivy:0.75.0` | Run Trivy itself as a container, deleted when done. Nothing to install on Jenkins. |
| `-v /var/run/docker.sock:...` | Lets Trivy see the images Jenkins just built. |
| `-v trivy-cache:/root/.cache/` | Keeps the vulnerability database between builds (faster). |
| `image <name>` | Scan this image. |
| `--severity HIGH,CRITICAL` | Only care about serious problems. |
| `--ignore-unfixed` | Skip problems that have no fix yet (we cannot do anything about them). |
| `--exit-code 1` | If anything is found, **fail the build**. |

**Why:** a vulnerable image never reaches ECR or the servers. This is the
security gate of the pipeline.
**If it fails:** the log lists the package and CVE. Usually fixed by updating a
dependency in `pom.xml` / `package.json`, or rebuilding to get a newer base image.

### Step 8: ECR Login

```bash
aws ecr get-login-password --region ${AWS_REGION} | \
docker login --username AWS --password-stdin ${ECR_REGISTRY}
```

1. `aws ecr get-login-password` asks AWS for a temporary password (valid 12 hours),
   using the AWS permissions of the Jenkins server.
2. `|` passes it straight to `docker login`. `--password-stdin` keeps the password
   off the command line and out of the logs.

Now Docker on Jenkins is allowed to push to our private registry.

### Step 9: Tag Images

```bash
docker tag ${FRONTEND_IMAGE}:${BUILD_NUMBER} ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}
docker tag ${BACKEND_IMAGE}:${BUILD_NUMBER}  ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}
```

A tag is just a second name for the same image. Docker decides **where** to
push from the name, so the image needs the full ECR address:

```
incident-management-backend:20
        ↓ docker tag
333968387080.dkr.ecr.ap-south-1.amazonaws.com/resolvex-backend:20
```

### Step 10: Push Images to ECR

```bash
docker push ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}
docker push ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}
```

Uploads both images to ECR. **CI ends here.** Every build number is now kept in
ECR, so any older version can be deployed again if needed.

---

## 5. CD stages, step by step

### Step 11: Deploy to App Servers

This stage does three things:

1. Loads the secrets from Jenkins Credentials.
2. For each server (app-1 first, then app-2), writes a settings file there.
3. Runs `deploy/deploy-app.sh` on that server.

#### 11a. Load the secrets

```groovy
withCredentials([
    string(credentialsId: 'resolvex-db-password', variable: 'DB_PASSWORD'),
    string(credentialsId: 'resolvex-jwt-secret', variable: 'JWT_SECRET'),
    usernamePassword(credentialsId: 'resolvex-admin-bootstrap',
        usernameVariable: 'ADMIN_EMAIL', passwordVariable: 'ADMIN_PASSWORD'),
    sshUserPrivateKey(credentialsId: 'resolvex-app-ssh',
        keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')
]) {
```

| Credential ID | Type | Becomes | Purpose |
| --- | --- | --- | --- |
| `resolvex-db-password` | Secret text | `DB_PASSWORD` | RDS password |
| `resolvex-jwt-secret` | Secret text | `JWT_SECRET` | Signs login tokens. **Same on both servers**, so a user logged in through app-1 is still logged in if the ALB sends the next request to app-2 |
| `resolvex-admin-bootstrap` | Username + password | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Creates the first admin, **only if** the database has no admin yet |
| `resolvex-app-ssh` | SSH private key | `SSH_KEY` (a temp key file), `SSH_USER` (`ec2-user`) | Lets Jenkins log in to the EC2 servers |

These values exist only inside this block. Jenkins hides them as `****` if they
ever get printed.

#### 11b. Loop over the servers

```groovy
for (String host : env.APP_HOSTS.trim().split('\\s+')) {
    if (host.startsWith('REPLACE')) {
        error("APP_HOSTS still has a placeholder: ${host}")
    }
    boolean primary = (host == env.SLA_PRIMARY_HOST)
    withEnv(["APP_HOST=${host}", "SLA_SCHEDULER=${primary}"]) {
        ...
    }
}
```

| Line | Simple meaning |
| --- | --- |
| `split('\\s+')` | Turn `"172.31.44.227 172.31.0.225"` into a list of 2 IPs. |
| `startsWith('REPLACE')` | Safety check: stop if someone left a placeholder instead of a real IP. |
| `primary = (host == SLA_PRIMARY_HOST)` | `true` for app-1, `false` for app-2. |
| `withEnv([...])` | Make `APP_HOST` and `SLA_SCHEDULER` available to the shell script below. |

The loop is **one server at a time** = a **rolling deploy**. While app-1 is
restarting, the ALB sends all users to app-2, so the site stays up.

**Why the SLA sweep runs on app-1 only:** the sweep checks every minute for
incidents that missed their SLA and sends an email through EventBridge. If both
servers ran it, every breach email would be sent twice.

#### 11c. Write the settings file (`backend.env`)

```bash
set +x
umask 077
ENV_FILE="$(mktemp)"
trap 'rm -f "$ENV_FILE"' EXIT

{
  echo "SPRING_PROFILES_ACTIVE=postgres"
  echo "DB_URL=jdbc:postgresql://${RDS_HOST}:5432/${RDS_DB}"
  ...
} > "$ENV_FILE"
```

| Command | Simple meaning |
| --- | --- |
| `set +x` | Do not print each command, so secrets are not echoed in the log. |
| `umask 077` | New files are readable only by their owner. |
| `mktemp` | Create a temporary file on Jenkins. |
| `trap 'rm -f ...' EXIT` | Always delete that temp file at the end, even if something fails. |
| `{ echo ...; } > "$ENV_FILE"` | Write all the backend settings into the temp file. |

What each setting tells the Spring Boot backend:

| Setting | Meaning |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=postgres` | Use PostgreSQL (`application-postgres.yml`), not the local H2 database |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | How to connect to RDS |
| `DB_DDL_AUTO=validate` | Hibernate must **not** change tables, only check they match the code |
| `FLYWAY_ENABLED=true` | Flyway creates and updates tables from `db/migration/V1__..., V2__...` on startup |
| `JWT_SECRET` | Login token signing key |
| `CORS_ALLOWED_ORIGINS=http://<ALB>` | Browsers may call the API only from the ALB address |
| `CACHE_TYPE=redis`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL_ENABLED` | Cache SLA policies in ElastiCache. If the cache is down, the app falls back to the database |
| `AWS_REGION` | `ap-south-1` for all AWS calls |
| `ATTACHMENT_STORAGE=s3`, `S3_BUCKET_NAME` | Save attachments in S3, not on the server disk |
| `AWS_EVENTBRIDGE_ENABLED=true` | Send SLA breach events to EventBridge |
| `SLA_SCHEDULER_ENABLED` | `true` on app-1, `false` on app-2 |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` | First admin, created only on an empty database |

There are **no AWS access keys** here. The servers use their IAM role
(`ResolveX-App-EC2-Role`) to reach ECR, S3 and EventBridge.

#### 11d. Copy the settings file to the server

```bash
SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"

ssh $SSH_OPTS "${SSH_USER}@${APP_HOST}" \
  'mkdir -p ~/resolvex && umask 077 && cat > ~/resolvex/backend.env' < "$ENV_FILE"
```

| Part | Meaning |
| --- | --- |
| `-i $SSH_KEY` | Log in with the `resolvex-app-key.pem` key from Jenkins Credentials. |
| `StrictHostKeyChecking=accept-new` | Trust a server the first time, but refuse if its identity later changes. |
| `ConnectTimeout=15` | Give up after 15 s instead of hanging if the server is down. |
| `mkdir -p ~/resolvex` | Make the folder on the server if it does not exist. |
| `cat > ~/resolvex/backend.env' < "$ENV_FILE"` | Send the temp file's contents through SSH and save it on the server (mode 600). |

The secrets travel inside the encrypted SSH connection, not as command-line
arguments, so they never appear in `ps` output or the Jenkins log.

#### 11e. Run the deploy script on the server

```bash
ssh $SSH_OPTS "${SSH_USER}@${APP_HOST}" \
  bash -s -- "${ECR_REGISTRY}" "${ECR_BACKEND_REPO}" "${ECR_FRONTEND_REPO}" \
    "${BUILD_NUMBER}" "${AWS_REGION}" < deploy/deploy-app.sh
```

`bash -s` on the server reads a script from standard input. `< deploy/deploy-app.sh`
sends our script from the Git checkout. The values after `--` become `$1` to `$5`
inside the script. **The script never needs to be copied to the server**; the
version in Git is always the one that runs.

---

## 6. What `deploy/deploy-app.sh` does on each server

```bash
set -euo pipefail
```

Stop on the first error (`-e`), on an unset variable (`-u`), or when any part of
a pipe fails (`pipefail`). A half-finished deploy is never reported as success.

| # | Command | Simple meaning |
| --- | --- | --- |
| 1 | `REGISTRY="$1" ... REGION="$5"` | Read the 5 values Jenkins passed. |
| 2 | `aws ecr get-login-password ... \| docker login ...` | Log in to ECR using the server's IAM role. |
| 3 | `docker pull -q "${BACKEND_IMAGE}"` and `"${FRONTEND_IMAGE}"` | Download this build's two images. |
| 4 | `docker network inspect resolvex-net \|\| docker network create resolvex-net` | Make sure the shared Docker network exists, so Nginx can find the backend by the name `resolvex-backend`. |
| 5 | `docker rm -f resolvex-backend` | Stop and remove the old backend container. |
| 6 | `docker run -d --name resolvex-backend --network resolvex-net --restart unless-stopped --env-file ~/resolvex/backend.env -p 127.0.0.1:8081:8080 ...` | Start the new backend (details below). |
| 7 | loop: `curl -fsS http://127.0.0.1:8081/api/health` | Wait up to 3 minutes for Spring Boot to start (it connects to RDS and runs Flyway). If it never becomes healthy, print the last 150 log lines and **fail**. |
| 8 | `docker rm -f resolvex-frontend` | Remove the old frontend container. |
| 9 | `docker run -d --name resolvex-frontend --network resolvex-net --restart unless-stopped -p 80:8080 ...` | Start the new frontend: server port 80 → Nginx port 8080. The ALB talks to port 80. |
| 10 | loop: `curl -fsS http://127.0.0.1/api/health` | Check the full path **Nginx → backend** works. Fail if not. |
| 11 | `curl -s http://127.0.0.1:8081/actuator/health` | Print the detailed health (database + Redis). Information only, not a gate. |
| 12 | `docker image prune -af --filter "until=24h"` | Delete images older than 24 hours. The servers have only 15 GB of disk. |

The `docker run` flags for the backend:

| Flag | Meaning |
| --- | --- |
| `-d` | Run in the background. |
| `--name resolvex-backend` | Fixed name. Nginx uses it to find the backend. |
| `--network resolvex-net` | Same network as the frontend. |
| `--restart unless-stopped` | Start again automatically after a crash or a server reboot. |
| `--env-file ~/resolvex/backend.env` | Load all settings and secrets from the file written in step 11d. |
| `-p 127.0.0.1:8081:8080` | Reachable only from inside the server itself (for the health checks). Never from the internet. |
| `--log-opt max-size=10m --log-opt max-file=3` | Keep at most 30 MB of logs per container. |

---

## 7. Step 12: Verify via Load Balancer

```bash
for i in $(seq 1 24); do
  if curl -fsS --max-time 5 "http://${ALB_DNS}/api/health"; then
    echo "Application is live at http://${ALB_DNS}"
    exit 0
  fi
  sleep 5
done
exit 1
```

Try the public URL up to 24 times, 5 seconds apart (2 minutes total). This is the
same path a real user takes: **internet → ALB → app server → Nginx → Spring Boot**.
If it answers, the deploy is done. If not, the build fails.

---

## 8. `post` section (runs after all stages)

| Block | When | What |
| --- | --- | --- |
| `success` | Every stage passed | Prints a success summary. |
| `failure` | Any stage failed | Prints "check the failed stage". |
| `always` | Every time | `docker rmi ... \|\| true` deletes this build's local images on Jenkins to save disk. `\|\| true` means "do not fail if an image is already gone". The copies in ECR are kept. |

---

## 9. How each AWS service is connected

| AWS service | Who uses it | How it is connected |
| --- | --- | --- |
| **ECR** | Jenkins pushes, app servers pull | Jenkins: its AWS permissions. Servers: `AmazonEC2ContainerRegistryReadOnly` on the IAM role |
| **EC2 x2** | Run the containers | Amazon Linux 2023, t3.small, Docker installed by user data, in zones 1a and 1b |
| **ALB** (`resolvex-alb`) | Users' entry point | Listener HTTP 80 → target group `resolvex-tg` → both servers. Health check `GET /api/health` every 15 s |
| **RDS PostgreSQL** (`resolvexdb`) | Backend data | `DB_URL` in `backend.env`. Security group allows 5432 only from the app servers |
| **ElastiCache Valkey** | Backend cache | `REDIS_HOST`, TLS on. Security group allows 6379 only from the app servers |
| **S3** (`resolvex-itsm-attachments`) | Attachments | IAM role policy `ResolveX-ITSM-S3-Attachments-Policy`. Private bucket |
| **EventBridge → Lambda → SES** | SLA breach emails | Backend sends an event (source `resolvex.itsm`, detail type `SLA Breach`) with the IAM role's `events:PutEvents`. An EventBridge rule triggers Lambda, which sends the email with SES |
| **IAM role** `ResolveX-App-EC2-Role` | The app servers | Gives ECR, S3 and EventBridge access without any keys. IMDS hop limit 2 lets the **container** use the role |
| **SonarCloud** (not AWS) | Code quality | Token saved in Jenkins |

### Security groups (who can talk to whom)

```
Internet ──80──► resolvex-alb-sg ──80──► resolvex-app-sg ──5432──► resolvex-rds-sg
                                                          └──6379──► cache SG
Jenkins SG ──22 (SSH)──► resolvex-app-sg
```

- Users can reach only the ALB, never the servers directly.
- Servers accept web traffic only from the ALB, and SSH only from Jenkins.
- The database and the cache accept connections only from the app servers.
- The backend port (8081) is bound to `127.0.0.1`, so it is never public.

---

## 10. One build's journey (example: build #20)

1. A pull request is merged to `main` on GitHub.
2. Jenkins starts build **#20** and checks out the code.
3. React builds, all backend tests (about 150) pass, SonarCloud receives the report.
4. Images `incident-management-frontend:20` and `incident-management-backend:20` are built.
5. Trivy finds no fixable HIGH/CRITICAL vulnerabilities.
6. Images are pushed to ECR as `resolvex-frontend:20` and `resolvex-backend:20`.
7. **app-1:** settings written, images pulled, backend healthy, frontend healthy. (app-2 served users meanwhile.)
8. **app-2:** same steps. (app-1 serves users now.)
9. `http://<ALB>/api/health` answers → **SUCCESS**.
10. Local images on Jenkins are cleaned up.

---

## 11. Useful commands for checking things yourself

On an app server (connect with Session Manager or SSH):

```bash
sudo docker ps
```

```bash
sudo docker logs --tail 200 resolvex-backend
```

```bash
sudo docker logs --tail 50 resolvex-frontend
```

```bash
curl -s http://127.0.0.1:8081/actuator/health
```

```bash
curl -s http://127.0.0.1/api/health
```

```bash
sudo cat ~ec2-user/resolvex/backend.env | cut -d= -f1
```

(The last one lists only the setting **names**, not the secret values.)

From anywhere:

```bash
curl -s http://resolvex-alb-2061703933.ap-south-1.elb.amazonaws.com/api/health
```

List the image versions stored in ECR:

```bash
aws ecr describe-images --repository-name resolvex-backend --region ap-south-1 --query 'sort_by(imageDetails,&imagePushedAt)[-5:].imageTags'
```

---

## 12. Quick answers (for reviews and interviews)

- **Why build the frontend in step 1 and again in Docker?** Step 1 fails fast in
  about a minute. The Docker build makes the real, clean image.
- **Why skip tests in the backend Dockerfile?** They already passed in step 2.
- **Why the build number as the tag, not `latest`?** Every version is unique and
  traceable, and any old version can be redeployed.
- **Why deploy one server at a time?** The other server keeps serving through
  the ALB, so there is no downtime.
- **Why are secrets in a file and not `-e` flags?** `-e` values show up in the
  command line and process list. The file is mode 600 and sent over SSH.
- **Why no AWS keys anywhere?** The EC2 IAM role gives temporary credentials
  automatically. Nothing to leak.
- **Why only app-1 runs the SLA sweep?** Otherwise every breach email goes out twice.
- **Why the same JWT secret on both servers?** A token from one server must be
  accepted by the other, because the ALB can send any request to either.
- **What if the new version is broken?** The health check in `deploy-app.sh`
  fails on app-1 and the pipeline stops before touching app-2. The ALB marks
  app-1 unhealthy and sends everyone to app-2, which still runs the previous
  version. Fix (or revert) the code and push again; the old images are still in
  ECR if a manual rollback is needed.
