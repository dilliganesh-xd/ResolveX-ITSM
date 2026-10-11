
pipeline {
    agent any

    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '2'
            )
        )
        disableConcurrentBuilds()
    }

    tools {
        jdk 'JDK21'
        nodejs 'NodeJS-20'
        maven 'MAVEN'
    }

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

