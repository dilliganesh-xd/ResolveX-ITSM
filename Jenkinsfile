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

        FRONTEND_IMAGE = 'incident-management-frontend'
        BACKEND_IMAGE  = 'incident-management-backend'
    }

    stages {

        // =========================================================
        // 1. FRONTEND BUILD
        // =========================================================
        stage('Frontend Build') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }

        // =========================================================
        // 2. BACKEND TEST & BUILD
        // =========================================================
        stage('Backend Test & Build') {
            steps {
                dir('backend') {
                    sh 'mvn clean test package'
                }
            }
        }

        // =========================================================
        // 3. SONARCLOUD ANALYSIS
        // =========================================================
        stage('SonarCloud Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarCloud') {
                        sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar -Dsonar.projectKey=sonartokenkey_itsm-resolvex -Dsonar.organization=sonartokenkey'
                    }
                }
            }
        }

        // =========================================================
        // 4. DOCKER BUILD - FRONTEND
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
        // 5. DOCKER BUILD - BACKEND
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

            ECR PUSH: DISABLED FOR NOW
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
