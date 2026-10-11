# Deployment: two EC2 instances behind an Application Load Balancer

How ResolveX runs in AWS (`ap-south-1`, account `333968387080`), and how
Jenkins deploys it.

```
Users ──HTTP 80──► resolvex-alb ──► resolvex-tg ──┬─► resolvex-app-1 (ap-south-1a, 172.31.44.227)
                                                  └─► resolvex-app-2 (ap-south-1b, 172.31.0.225)
Each app instance runs two containers on the Docker network resolvex-net:
  resolvex-frontend  Nginx + React, host port 80 → container 8080, proxies /api/ to the backend
  resolvex-backend   Spring Boot, published on 127.0.0.1:8081 only
Both instances share: RDS PostgreSQL (resolvexdb), ElastiCache Valkey (resolvex-cache),
S3 (resolvex-itsm-attachments) and EventBridge (default bus).
```

## AWS resources

| Resource | Name / ID | Notes |
| --- | --- | --- |
| VPC | `vpc-00e39f1c49892158d` | The default VPC; RDS and the cache are here too |
| Load balancer | `resolvex-alb` | Internet-facing, zones 1a + 1b, listener HTTP 80 → `resolvex-tg` |
| App URL | `http://resolvex-alb-2061703933.ap-south-1.elb.amazonaws.com` | |
| Target group | `resolvex-tg` | Instances, HTTP 80, health check `GET /api/health`, healthy 2 / unhealthy 2, 15 s interval |
| App instances | `resolvex-app-1` `i-0860a1e99d6079cd0` · `resolvex-app-2` `i-06ed730645707a2cb` | Amazon Linux 2023 x86_64, t3.small, 15 GB gp3, key pair `resolvex-app-key` |
| IAM role | `ResolveX-App-EC2-Role` | `ResolveX-ITSM-S3-Attachments-Policy`, `AmazonEC2ContainerRegistryReadOnly`, `AmazonSSMManagedInstanceCore`, inline `events:PutEvents` on the default bus |
| Instance metadata | IMDSv2 required, **hop limit 2** | Hop limit 2 is what lets the backend *container* use the instance role |

Each instance was launched with user data that installs Docker and creates the
`resolvex-net` network.

## Security groups

| Group | Inbound | Purpose |
| --- | --- | --- |
| `resolvex-alb-sg` `sg-0943a105d4c58e5f2` | HTTP 80 from `0.0.0.0/0` | Public entry point |
| `resolvex-app-sg` `sg-0400876c866c41f8b` | HTTP 80 from `resolvex-alb-sg`; SSH 22 from the Jenkins group `sg-05f392635523a9fe3` and one admin IP | App instances accept traffic only from the ALB, and deploys only from Jenkins |
| `resolvex-rds-sg` `sg-0b418f5dbd3643408` | + PostgreSQL 5432 from `resolvex-app-sg` | Database |
| `default` `sg-056eb85cc1fe71af7` (cache) | + TCP 6379 from `resolvex-app-sg` | Valkey cache |

The backend port is never exposed: it is published on `127.0.0.1` only, and
the app group has no rule for 8080 or 8081.

## How Jenkins deploys

After CI (build, tests, SonarCloud, Trivy, push to ECR), the
**Deploy to App Servers** stage handles one instance at a time. The other
instance keeps serving through the ALB, so a deploy is rolling. For each instance:

1. It writes the backend's settings and secrets to `~/resolvex/backend.env` on
   the instance, over SSH, with file mode 600. Secrets never appear on a command
   line or in the build log.
2. It runs [`deploy/deploy-app.sh`](../deploy/deploy-app.sh) there. The script:
   - logs in to ECR with the instance role and pulls this build's two images
   - starts the backend and waits for `/api/health`
   - starts the frontend and checks the full path Nginx → Spring Boot
   - prunes old images

Finally, **Verify via Load Balancer** polls `http://<ALB>/api/health` until a
target is healthy.

Two settings differ per instance:

- `SLA_SCHEDULER_ENABLED` is `true` on **app-1 only**. A sweep on both instances
  would announce every SLA breach twice (two emails).
- Both instances get the **same** `JWT_SECRET`, so a login token issued by one
  works on the other.

### Jenkins credentials

Add these under Manage Jenkins → Credentials → Global.

| ID | Kind | Value |
| --- | --- | --- |
| `resolvex-db-password` | Secret text | RDS password (already present) |
| `resolvex-jwt-secret` | Secret text | Random string, at least 32 characters |
| `resolvex-admin-bootstrap` | Username with password | First administrator's email and password, created only while the database has no admin |
| `resolvex-app-ssh` | SSH Username with private key | Username `ec2-user`, private key from `resolvex-app-key.pem` |

## First deployment: creating the first admin

A new database has no users, and only an admin can create users. On startup,
`AdminBootstrap` creates the admin from `resolvex-admin-bootstrap`, but **only
if no admin exists yet**, so later deploys never change it. After signing in,
that admin creates the teams, SLA policies and other users from the admin
screens.

## Operations

- **Logs:** connect to an instance with Session Manager, then run
  `sudo docker logs --tail 200 resolvex-backend` (or `resolvex-frontend`).
- **Health:** check `/api/health` through the ALB. Each instance also serves
  `curl -s localhost:8081/actuator/health`, which includes the DB and Redis.
- **Cost:** the two t3.small instances and the ALB are billed hourly. Stop the
  instances (and delete the ALB) when the environment is not needed.
- **HTTPS:** needs a domain name and an ACM certificate on a 443 listener.
  Not set up yet.
