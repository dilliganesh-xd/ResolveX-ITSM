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
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '333968387080'

        ECR_REGISTRY = '333968387080.dkr.ecr.ap-south-1.amazonaws.com'

        FRONTEND_IMAGE = 'incident-management-frontend'
        BACKEND_IMAGE  = 'incident-management-backend'

        ECR_FRONTEND_REPO = 'resolvex-frontend'
        ECR_BACKEND_REPO  = 'resolvex-backend'

        // Pinned so scan behaviour only changes when the version is bumped here.
        TRIVY_IMAGE = 'aquasec/trivy:0.75.0'
        // Named Docker volume that keeps Trivy's vulnerability DB between builds,
        // so each scan does not re-download it (and hit registry rate limits).
        TRIVY_CACHE = 'trivy-cache'
    }

    stages {

        stage('Frontend Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        stage('Backend Test & Build') {
            steps {
                dir('backend') {
                    // verify = compile, test once, package. `test package` ran the suite twice.
                    sh 'mvn -B clean verify'
                }
            }
        }

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

        stage('Docker Build - Frontend') {
            steps {
                sh '''
                    docker build --pull \
                    -t ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                    ./frontend
                '''
            }
        }

        stage('Docker Build - Backend') {
            steps {
                sh '''
                    docker build --pull \
                    -t ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                    ./backend
                '''
            }
        }

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
    }

    post {
        success {
            echo '''
            ============================================
            CI PIPELINE COMPLETED SUCCESSFULLY
            ============================================

            Frontend build completed
            Backend build and tests completed
            SonarCloud analysis completed
            Docker images built successfully
            Trivy security scans passed
            Images pushed to ECR successfully

            ============================================
            '''
        }

        failure {
            echo '''
            ============================================
            CI PIPELINE FAILED
            ============================================

            Check the failed stage and Jenkins console
            output for the root cause.

            ============================================
            '''
        }

        always {
            // Remove this build's images from the agent so its disk does not fill up.
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
