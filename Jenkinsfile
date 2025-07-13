// Jenkinsfile for book-service
// This file should be placed at the root of the book-service repository.

// Define global environment variables
def dockerRegistry = "sumitrajneesh" // e.g., "myusername"
def dockerCredentialsId = "3bdc9f350d0642d19dec3a60aa1875b4" // Jenkins credential ID for Docker Hub/GitLab Registry
def sonarqubeServerId = "sonarqube-server" // Jenkins SonarQube server configuration ID
def kubernetesCredentialsId = "kubernetes-credentials" // Jenkins credential ID for Kubernetes access (e.g., Kubeconfig)
def kubernetesContext = "minikube" // Kubernetes context name for your staging cluster
def helmChartPath = "helm/book-service-chart" // Path to book-service's Helm chart within its repository

// Pipeline definition
pipeline {
    agent any // Or a specific agent label if you have dedicated build agents

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
        skipDefaultCheckout(false)
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        echo "Running Maven unit tests for Spring Boot book-service..."
                        sh "mvn clean test"
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/*.xml'
                        }
                    }
                }

                // ... other stages ...

        stage('Code Quality (SonarQube)') {
            steps {
                echo "Running SonarQube analysis for Spring Boot book-service..."
                // The withSonarQubeEnv wrapper injects the necessary environment variables
                withSonarQubeEnv(installationName: 'sonarqube-server', credentialsId: 'sonarqube-server') {
                // This 'sh' command MUST be inside the withSonarQubeEnv block
                // Ensure you have the correct projectKey and host/login parameters
                sh "mvn sonar:sonar -Dsonar.projectKey=book-service -Dsonar.host.url=${SONARQUBE_URL} -Dsonar.login=${SONARQUBE_TOKEN}"
            }
    }
    post {
        // This 'always' block ensures quality gate check runs even if analysis fails
        always {
            timeout(time: 5, unit: 'MINUTES') {
                // This step waits for the SonarQube Quality Gate result
                // It needs to be outside the withSonarQubeEnv block, but within the stage's post section
                waitForQualityGate abortPipeline: true
            }
        }
    }
}

// ... rest of your Jenkinsfile ...
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    def serviceName = env.JOB_NAME.toLowerCase().replaceAll('/', '-')
                    def imageTag = "${env.BRANCH_NAME == 'main' ? 'latest' : env.BRANCH_NAME}-${env.BUILD_NUMBER}".replaceAll('/', '-')
                    def dockerImageName = "${dockerRegistry}/${serviceName}:${imageTag}"

                    echo "Building Docker image: ${dockerImageName}"
                    sh "docker build -t ${dockerImageName} ."

                    withCredentials([usernamePassword(credentialsId: dockerCredentialsId, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                        sh "echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin ${dockerRegistry.split('/')[0]}"
                        echo "Pushing Docker image: ${dockerImageName}"
                        sh "docker push ${dockerImageName}"
                    }

                    env.DOCKER_IMAGE = dockerImageName
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                script {
                    def serviceName = env.JOB_NAME.toLowerCase().replaceAll('/', '-')
                    def namespace = "staging"

                    echo "Deploying ${serviceName} to Kubernetes staging cluster (${kubernetesContext})..."

                    withKubeConfig(credentialsId: kubernetesCredentialsId, contextName: kubernetesContext) {
                        sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"

                        // Database connection details for book-service's PostgreSQL
                        // IMPORTANT: In a real scenario, use Jenkins Secret Text credentials for DB_USER and DB_PASSWORD
                        // Example: withCredentials([string(credentialsId: 'book-db-user', variable: 'BOOK_DB_USER'), string(credentialsId: 'book-db-password', variable: 'BOOK_DB_PASSWORD')]) {
                        // Then use ${BOOK_DB_USER} and ${BOOK_DB_PASSWORD}
                        def dbHost = "book-service-postgresql" // This is the K8s service name of your PostgreSQL instance for book-service
                        def dbPort = "5432"
                        def dbName = "book_db"
                        def dbUser = "bookuser"
                        def dbPassword = "bookpassword" // Replace with Jenkins Secret Text variable

                        sh "helm upgrade --install ${serviceName} ${helmChartPath} --namespace ${namespace} " +
                           "--set image.repository=${dockerRegistry}/${serviceName} " +
                           "--set image.tag=${env.DOCKER_IMAGE.split(':')[-1]} " +
                           "--set db.host=${dbHost} " +
                           "--set db.port=${dbPort} " +
                           "--set db.name=${dbName} " +
                           "--set db.user=${dbUser} " +
                           "--set db.password=${dbPassword} " +
                           "--wait --timeout 5m"

                        echo "Deployment of ${serviceName} to staging completed."
                        echo "Check the status using: kubectl get pods -n ${namespace} -l app.kubernetes.io/instance=${serviceName}"
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        failure {
            echo "Pipeline for ${env.JOB_NAME} on branch ${env.BRANCH_NAME} failed. Check logs for details."
        }
        success {
            echo "Pipeline for ${env.JOB_NAME} on branch ${env.BRANCH_NAME} succeeded!"
        }
    }
}
