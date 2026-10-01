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

        TRIVY_IMAGE = 'aquasec/trivy:latest'
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
                    sh 'mvn clean test package'
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
                    docker build \
                    -t ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                    ./frontend
                '''
            }
        }

        stage('Docker Build - Backend') {
            steps {
                sh '''
                    docker build \
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
                    ${TRIVY_IMAGE} \
                    image \
                    --severity HIGH,CRITICAL \
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
                    ${TRIVY_IMAGE} \
                    image \
                    --severity HIGH,CRITICAL \
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
            echo 'Jenkins pipeline execution completed.'
        }
    }
}
