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
        AWS_ACCOUNT_ID = 'YOUR_ACCOUNT_ID'

        FRONTEND_IMAGE = 'incident-management-frontend'
        BACKEND_IMAGE  = 'incident-management-backend'

        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    }

    stages {

        // =========================================================
        // 1. CHECKOUT
        // =========================================================
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // =========================================================
        // 2. FRONTEND TEST & BUILD
        // =========================================================
        stage('Frontend Test & Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --run'
                    sh 'npm run build'
                }
            }
        }

        // =========================================================
        // 3. BACKEND TEST & BUILD
        // =========================================================
        stage('Backend Test & Build') {
            steps {
                dir('backend') {
                    sh 'chmod +x mvnw'
                    sh './mvnw clean test package'
                }
            }
        }

        // =========================================================
        // 4. SONARCLOUD ANALYSIS
        // =========================================================
        stage('SonarCloud Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarCloud') {
                        sh '''
                            ./mvnw sonar:sonar \
                            -Dsonar.projectKey=YOUR_PROJECT_KEY \
                            -Dsonar.organization=YOUR_ORGANIZATION_KEY
                        '''
                    }
                }
            }
        }

        // =========================================================
        // 5. DOCKER BUILD - FRONTEND
        // =========================================================
        stage('Docker Build - Frontend') {
            steps {
                sh '''
                    docker build \
                    -t ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                    ./frontend
                '''
            }
        }

        // =========================================================
        // 6. DOCKER BUILD - BACKEND
        // =========================================================
        stage('Docker Build - Backend') {
            steps {
                sh '''
                    docker build \
                    -t ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                    ./backend
                '''
            }
        }

        // =========================================================
        // 7. ECR LOGIN
        // =========================================================
        stage('ECR Login') {
            steps {
                sh '''
                    aws ecr get-login-password --region ${AWS_REGION} | \
                    docker login \
                    --username AWS \
                    --password-stdin ${ECR_REGISTRY}
                '''
            }
        }

        // =========================================================
        // 8. TAG & PUSH FRONTEND
        // =========================================================
        stage('Push Frontend to ECR') {
            steps {
                sh '''
                    docker tag \
                    ${FRONTEND_IMAGE}:${BUILD_NUMBER} \
                    ${ECR_REGISTRY}/${FRONTEND_IMAGE}:${BUILD_NUMBER}

                    docker push \
                    ${ECR_REGISTRY}/${FRONTEND_IMAGE}:${BUILD_NUMBER}
                '''
            }
        }

        // =========================================================
        // 9. TAG & PUSH BACKEND
        // =========================================================
        stage('Push Backend to ECR') {
            steps {
                sh '''
                    docker tag \
                    ${BACKEND_IMAGE}:${BUILD_NUMBER} \
                    ${ECR_REGISTRY}/${BACKEND_IMAGE}:${BUILD_NUMBER}

                    docker push \
                    ${ECR_REGISTRY}/${BACKEND_IMAGE}:${BUILD_NUMBER}
                '''
            }
        }
    }

    // =============================================================
    // POST ACTIONS
    // =============================================================
    post {

        success {
            echo '''
            ============================================
            CI PIPELINE COMPLETED SUCCESSFULLY
            ============================================

            Frontend:
              - Dependencies installed
              - Tests passed
              - Production build created
              - Docker image built
              - Image pushed to ECR

            Backend:
              - Tests passed
              - Maven package created
              - SonarCloud analysis completed
              - Docker image built
              - Image pushed to ECR

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
