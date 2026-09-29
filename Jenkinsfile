pipeline {
    agent any

    tools {
        // These names MUST exactly match the names configured
        // under Manage Jenkins → Tools
        jdk 'JDK21'
        nodejs 'NodeJS-20'
        maven 'MAVEN'
    }

    stages {

        // =========================================================
        // 1. CHECKOUT
        // =========================================================
        stage('Checkout') {
            steps {
                // Checkout source code from the configured Git repository
                checkout scm
            }
        }


        // =========================================================
        // 2. FRONTEND TEST & BUILD
        // =========================================================
        stage('Frontend Test & Build') {
            steps {
                dir('frontend') {

                    // Install dependencies from package-lock.json
                    bat 'npm ci'

                    // Run frontend tests
                    bat 'npm test -- --run'

                    // Create production-ready files in dist/
                    bat 'npm run build'
                }
            }
        }


        // =========================================================
        // 3. BACKEND TEST & BUILD
        // =========================================================
        stage('Backend Test & Build') {
            steps {
                dir('backend') {

                    // Clean previous Maven build output
                    // Run unit tests
                    // Package Spring Boot application as JAR
                    bat 'mvnw.cmd clean test package'
                }
            }
        }


        // =========================================================
        // 4. SONARCLOUD ANALYSIS
        // =========================================================
        stage('SonarCloud Analysis') {
            steps {
                dir('backend') {

                    // Inject SonarCloud configuration and credentials
                    // configured in Jenkins
                    withSonarQubeEnv('SonarCloud') {

                        bat '''
                            mvnw.cmd sonar:sonar ^
                            -Dsonar.projectKey=YOUR_PROJECT_KEY ^
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

                // Build frontend Docker image
                // Dockerfile: frontend/Dockerfile
                bat '''
                    docker build ^
                    -t incident-management-frontend:%BUILD_NUMBER% ^
                    ./frontend
                '''
            }
        }


        // =========================================================
        // 6. DOCKER BUILD - BACKEND
        // =========================================================
        stage('Docker Build - Backend') {
            steps {

                // Build backend Docker image
                // Dockerfile: backend/Dockerfile
                bat '''
                    docker build ^
                    -t incident-management-backend:%BUILD_NUMBER% ^
                    ./backend
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

            Backend:
              - Tests passed
              - Maven package created
              - SonarCloud analysis completed
              - Docker image built

            ECR PUSH: NOT INCLUDED
            TRIVY SCAN: NOT INCLUDED

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


