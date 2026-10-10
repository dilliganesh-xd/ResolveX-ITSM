
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

        // Amazon ElastiCache Redis configuration
        REDIS_HOST = 'clustercfg.resolvex-cache.kkxqo3.aps1.cache.amazonaws.com'
        REDIS_PORT = '6379'
        REDIS_SSL_ENABLED = 'false'

        // Amazon S3 attachment storage
        S3_BUCKET_NAME = 'resolvex-itsm-attachments'
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
        // CD: DEPLOY BACKEND
        // ============================================

        stage('Deploy Backend') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'resolvex-db-password',
                        variable: 'DB_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x

                        docker stop resolvex-backend || true
                        docker rm resolvex-backend || true

                        docker pull \
                          ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}

                        docker run -d \
                          --name resolvex-backend \
                          --restart unless-stopped \
                          -p 8081:8080 \
                          -e SPRING_PROFILES_ACTIVE=postgres \
                          -e DB_URL="jdbc:postgresql://${RDS_HOST}:5432/${RDS_DB}" \
                          -e DB_USERNAME="${RDS_USER}" \
                          -e DB_PASSWORD="$DB_PASSWORD" \
                          -e DB_DDL_AUTO=validate \
                          -e FLYWAY_ENABLED=true \
                          -e REDIS_HOST="${REDIS_HOST}" \
                          -e REDIS_PORT="${REDIS_PORT}" \
                          -e REDIS_SSL_ENABLED="${REDIS_SSL_ENABLED}" \
                          -e AWS_REGION="${AWS_REGION}" \
                          -e S3_BUCKET_NAME="${S3_BUCKET_NAME}" \
                          ${ECR_REGISTRY}/${ECR_BACKEND_REPO}:${BUILD_NUMBER}
                    '''
                }
            }
        }

        // ============================================
        // CD: VERIFY BACKEND
        // ============================================

        stage('Verify Backend') {
            steps {
                sh '''
                    sleep 15

                    echo "===== Backend Container ====="
                    docker ps --filter "name=resolvex-backend"

                    echo "===== Backend Logs ====="
                    docker logs --tail 100 resolvex-backend

                    echo "===== Backend Health ====="
                    curl -f --max-time 10 \
                      http://localhost:8081/actuator/health
                '''
            }
        }

        // ============================================
        // CD: DEPLOY FRONTEND
        // ============================================

        stage('Deploy Frontend') {
            steps {
                sh '''
                    docker stop resolvex-frontend || true
                    docker rm resolvex-frontend || true

                    docker pull \
                      ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}

                    docker run -d \
                      --name resolvex-frontend \
                      --restart unless-stopped \
                      -p 80:80 \
                      ${ECR_REGISTRY}/${ECR_FRONTEND_REPO}:${BUILD_NUMBER}
                '''
            }
        }

        // ============================================
        // CD: FINAL DEPLOYMENT VERIFICATION
        // ============================================

        stage('Deployment Verification') {
            steps {
                sh '''
                    sleep 5

                    echo "===== Running Containers ====="
                    docker ps

                    echo "===== Backend Health ====="
                    curl -f --max-time 10 \
                      http://localhost:8081/actuator/health

                    echo "===== Frontend HTTP Response ====="
                    curl -f -I --max-time 10 http://localhost/
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
            Backend deployment verified
            Frontend deployment verified

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

