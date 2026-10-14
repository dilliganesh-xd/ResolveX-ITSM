// ResolveX-ITSM CI/CD pipeline.
// Step-by-step explanation in simple words: docs/ci-cd-explained.md
//
// CI (stages 1-10): build, test, SonarCloud, Docker images, Trivy scan, push to ECR.
// CD (stages 11-12): deploy to both app EC2 servers one by one, check via the ALB.

pipeline {
    agent any

    // Keep only 2 old builds; never run two builds at the same time.
    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '2'
            )
        )
        disableConcurrentBuilds()
    }

    // Java 21, Node.js 20 and Maven, as configured in Manage Jenkins -> Tools.
    tools {
        jdk 'JDK21'
        nodejs 'NodeJS-20'
        maven 'MAVEN'
    }

    // Shared values for every stage. No secrets here: they come from
    // Jenkins Credentials in the deploy stage. BUILD_NUMBER (from Jenkins)
    // is used as the image tag, so every build is a new image version.
    environment {
        // AWS and ECR configuration
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '333968387080'
        ECR_REGISTRY = '333968387080.dkr.ecr.ap-south-1.amazonaws.com'

        FRONTEND_IMAGE = 'incident-management-frontend'
        BACKEND_IMAGE  = 'incident-management-backend'

        ECR_FRONTEND_REPO = 'resolvex-frontend'
        ECR_BACKEND_REPO  = 'resolvex-backend'

        // Trivy configuration
        TRIVY_IMAGE = 'aquasec/trivy:0.75.0'
        TRIVY_CACHE = 'trivy-cache'

        // PostgreSQL RDS configuration
        RDS_HOST = 'resolvexdb.cfmwqcs60l4v.ap-south-1.rds.amazonaws.com'
        RDS_DB   = 'resolvex'
        RDS_USER = 'resolvex_admin'

        // Amazon ElastiCache (Valkey) configuration. The cache requires
        // encryption in transit, so TLS must be on.
        REDIS_HOST = 'clustercfg.resolvex-cache.kkxqo3.aps1.cache.amazonaws.com'
        REDIS_PORT = '6379'
        REDIS_SSL_ENABLED = 'true'

        // Amazon S3 attachment storage
        S3_BUCKET_NAME = 'resolvex-itsm-attachments'

        // App EC2 instances behind the ALB (private IPs: Jenkins reaches them
        // inside the VPC). They are deployed one at a time, so the other keeps
        // serving through the load balancer.
        APP_HOSTS = '172.31.44.227 172.31.0.225'

        // The SLA sweep runs on this host only. Running it on both would send
        // every SLA breach email twice.
        SLA_PRIMARY_HOST = '172.31.44.227'

        // Public entry point of the application
        ALB_DNS = 'resolvex-alb-2061703933.ap-south-1.elb.amazonaws.com'
    }

    stages {

        // ============================================
        // CI: FRONTEND BUILD
        // ============================================
        // 1. Install exact npm packages and build React. Fails fast on TypeScript errors.

        stage('Frontend Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        // ============================================
        // CI: BACKEND TEST AND BUILD
        // ============================================
        // 2. Compile the backend, run all tests (H2 + in-memory cache), package the JAR.

        stage('Backend Test & Build') {
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
        }

        // ============================================
        // CI: SONARCLOUD ANALYSIS
        // ============================================
        // 3. Send the backend code to SonarCloud for a quality report (token from Jenkins).

        stage('SonarCloud Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarCloud') {
                        sh '''
                            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar \
                            -Dsonar.projectKey=sonartokenkey_itsm-resolvex \
                            -Dsonar.organization=sonartokenkey
                        '''
                    }
                }
            }
        }

        // ============================================
        // CI: BUILD FRONTEND DOCKER IMAGE
        // ============================================
        // 4. Build the Nginx + React image (frontend/Dockerfile), tagged with the build number.

        stage('Docker Build - Frontend') {
            steps {
                sh '''
                    docker build --pull \
                      -t ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                      ./frontend
                '''
            }
        }

        // ============================================
        // CI: BUILD BACKEND DOCKER IMAGE
        // ============================================
        // 5. Build the Java 21 + Spring Boot image (backend/Dockerfile). Tests ran in step 2.

        stage('Docker Build - Backend') {
            steps {
                sh '''
                    docker build --pull \
                      -t ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                      ./backend
                '''
            }
        }

        // ============================================
        // CI: TRIVY FRONTEND SCAN
        // ============================================
        // 6. Security gate: fail the build if the image has a fixable HIGH/CRITICAL CVE.

        stage('Trivy Scan - Frontend') {
            steps {
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v ${TRIVY_CACHE}:/root/.cache/ \
                      ${TRIVY_IMAGE} \
                      image \
                      --severity HIGH,CRITICAL \
                      --ignore-unfixed \
                      --exit-code 1 \
                      ${FRONTEND_IMAGE}:${BUILD_NUMBER}
                '''
            }
        }

        // ============================================
        // CI: TRIVY BACKEND SCAN
        // ============================================
        // 7. Same security gate for the backend image.

        stage('Trivy Scan - Backend') {
            steps {
                sh '''
                    docker run --rm \
                      -v /var/run/docker.sock:/var/run/docker.sock \
                      -v ${TRIVY_CACHE}:/root/.cache/ \
                      ${TRIVY_IMAGE} \
                      image \
                      --severity HIGH,CRITICAL \
                      --ignore-unfixed \
                      --exit-code 1 \
                      ${BACKEND_IMAGE}:${BUILD_NUMBER}
                '''
            }
        }

        // ============================================
        // CI: ECR LOGIN
        // ============================================
        // 8. Get a temporary ECR password from AWS and log Docker in with it.

        stage('ECR Login') {
            steps {
                sh '''
                    aws ecr get-login-password \
                      --region ${AWS_REGION} | \
                    docker login \
                      --username AWS \
                      --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        // ============================================
        // CI: TAG IMAGES
        // ============================================
        // 9. Give each image its full ECR name so Docker knows where to push it.

        stage('Tag Images') {
            steps {
                sh '''
                    docker tag \
                      ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                      ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}

                    docker tag \
                      ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                      ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}
                '''
            }
        }

        // ============================================
        // CI: PUSH IMAGES TO ECR
        // ============================================
        // 10. Upload both images to ECR. CI ends here.

        stage('Push Images to ECR') {
            steps {
                sh '''
                    docker push \
                      ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}

                    docker push \
                      ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}
                '''
            }
        }

        // ============================================
        // CD: DEPLOY TO THE APP EC2 INSTANCES
        // ============================================
        // 11. For app-1, then app-2: write backend.env over SSH, then run
        //     deploy/deploy-app.sh there (pull images, restart containers, health checks).
        //
        // For each app server: write the backend settings and secrets to
        // ~/resolvex/backend.env over SSH (never on a command line), then run
        // deploy/deploy-app.sh there, which pulls this build's images from ECR
        // and replaces the containers. AWS access on the servers comes from
        // their IAM role (ResolveX-App-EC2-Role); no AWS keys are passed.
        //
        // Jenkins credentials used:
        //   resolvex-db-password      Secret text        RDS password
        //   resolvex-jwt-secret       Secret text        JWT signing key (32+ chars), same on every server
        //   resolvex-admin-bootstrap  Username/password  first admin, created only while no admin exists
        //   resolvex-app-ssh          SSH key            ec2-user + resolvex-app-key.pem

        stage('Deploy to App Servers') {
            steps {
                withCredentials([
                    string(credentialsId: 'resolvex-db-password', variable: 'DB_PASSWORD'),
                    string(credentialsId: 'resolvex-jwt-secret', variable: 'JWT_SECRET'),
                    usernamePassword(credentialsId: 'resolvex-admin-bootstrap',
                        usernameVariable: 'ADMIN_EMAIL', passwordVariable: 'ADMIN_PASSWORD'),
                    sshUserPrivateKey(credentialsId: 'resolvex-app-ssh',
                        keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')
                ]) {
                    script {
                        for (String host : env.APP_HOSTS.trim().split('\\s+')) {
                            if (host.startsWith('REPLACE')) {
                                error("APP_HOSTS still has a placeholder: ${host}")
                            }
                            boolean primary = (host == env.SLA_PRIMARY_HOST)
                            withEnv(["APP_HOST=${host}", "SLA_SCHEDULER=${primary}"]) {
                                sh '''
                                    set +x
                                    umask 077
                                    ENV_FILE="$(mktemp)"
                                    trap 'rm -f "$ENV_FILE"' EXIT

                                    {
                                      echo "SPRING_PROFILES_ACTIVE=postgres"
                                      echo "DB_URL=jdbc:postgresql://${RDS_HOST}:5432/${RDS_DB}"
                                      echo "DB_USERNAME=${RDS_USER}"
                                      echo "DB_PASSWORD=${DB_PASSWORD}"
                                      echo "DB_DDL_AUTO=validate"
                                      echo "FLYWAY_ENABLED=true"
                                      echo "JWT_SECRET=${JWT_SECRET}"
                                      echo "CORS_ALLOWED_ORIGINS=http://${ALB_DNS}"
                                      echo "CACHE_TYPE=redis"
                                      echo "REDIS_HOST=${REDIS_HOST}"
                                      echo "REDIS_PORT=${REDIS_PORT}"
                                      echo "REDIS_SSL_ENABLED=${REDIS_SSL_ENABLED}"
                                      echo "AWS_REGION=${AWS_REGION}"
                                      echo "ATTACHMENT_STORAGE=s3"
                                      echo "S3_BUCKET_NAME=${S3_BUCKET_NAME}"
                                      echo "AWS_EVENTBRIDGE_ENABLED=true"
                                      echo "SLA_SCHEDULER_ENABLED=${SLA_SCHEDULER}"
                                      echo "ADMIN_BOOTSTRAP_EMAIL=${ADMIN_EMAIL}"
                                      echo "ADMIN_BOOTSTRAP_PASSWORD=${ADMIN_PASSWORD}"
                                    } > "$ENV_FILE"

                                    SSH_OPTS="-i $SSH_KEY -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15"

                                    echo "===== Deploying build ${BUILD_NUMBER} to ${APP_HOST} (SLA sweep: ${SLA_SCHEDULER}) ====="
                                    ssh $SSH_OPTS "${SSH_USER}@${APP_HOST}" \
                                      'mkdir -p ~/resolvex && umask 077 && cat > ~/resolvex/backend.env' < "$ENV_FILE"

                                    ssh $SSH_OPTS "${SSH_USER}@${APP_HOST}" \
                                      bash -s -- "${ECR_REGISTRY}" "${ECR_BACKEND_REPO}" "${ECR_FRONTEND_REPO}" \
                                        "${BUILD_NUMBER}" "${AWS_REGION}" < deploy/deploy-app.sh
                                '''
                            }
                        }
                    }
                }
            }
        }

        // ============================================
        // CD: VERIFY THROUGH THE LOAD BALANCER
        // ============================================
        // 12. Call /api/health through the ALB, the same path users take.

        stage('Verify via Load Balancer') {
            steps {
                sh '''
                    echo "===== Checking http://${ALB_DNS}/api/health ====="
                    for i in $(seq 1 24); do
                      if curl -fsS --max-time 5 "http://${ALB_DNS}/api/health"; then
                        echo
                        echo "Application is live at http://${ALB_DNS}"
                        exit 0
                      fi
                      sleep 5
                    done
                    echo "The load balancer did not report a healthy target within 2 minutes."
                    exit 1
                '''
            }
        }
    }

    // Runs after the stages: print a result, always clean local images.
    post {
        success {
            echo '''
            ============================================
            RESOLVEX-ITSM CI/CD COMPLETED SUCCESSFULLY
            ============================================

            Frontend build completed
            Backend tests and build completed
            SonarCloud analysis completed
            Trivy security scans passed
            Docker images pushed to ECR
            Deployed to both app EC2 instances
            Verified through the load balancer

            RDS configuration preserved
            Redis configuration preserved
            S3 configuration passed to backend

            ============================================
            '''
        }

        failure {
            echo '''
            ============================================
            RESOLVEX-ITSM CI/CD PIPELINE FAILED
            ============================================

            Check the failed stage and Jenkins console
            output to identify the root cause.

            ============================================
            '''
        }

        always {
            // Remove this build's temporary local image tags.
            // Running containers can continue using their images.
            sh '''
                docker rmi \
                  ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                  ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                  ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER} \
                  ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER} \
                  || true
            '''

            echo 'Jenkins pipeline execution completed.'
        }
    }
}

