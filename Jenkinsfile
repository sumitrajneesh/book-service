// Jenkinsfile for book-service
// This file should be placed at the root of the book-service repository.

// Define global environment variables
// IMPORTANT: dockerRegistry should be the full registry path including your username for image naming
def dockerRegistry = "docker.io/sumitrajneesh" // Correct format for Docker Hub: docker.io/your_username
def dockerCredentialsId = "3bdc9f350d0642d19dec3a60aa1875b4" // Jenkins credential ID for Docker Hub
def kubernetesCredentialsId = "kubernetes-credentials" // Jenkins credential ID for Kubernetes access (e.g., Kubeconfig)
def kubernetesContext = "minikube" // Kubernetes context name for your staging cluster - REPLACE WITH YOUR ACTUAL K8s CONTEXT
def helmChartPath = "helm/book-service-chart" // Path to book-service's Helm chart within its repository
def dbPasswordCredentialId = "book-db-password" // Jenkins credential ID for book-service DB password (Secret Text)
def dbUserCredentialId = "book-db-user" // Jenkins credential ID for book-service DB user (Secret Text)

// Pipeline definition
pipeline {
    agent any // Or a specific agent label if you have dedicated build agents

    options {
        buildDiscarder(logRotator(numToKeepStr: '10')) // Keep last 10 builds
        timestamps() // Add timestamps to console output
        skipDefaultCheckout(false) // Ensure SCM checkout happens
    }

    stages {
        stage('Checkout SCM') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test') {
            steps {
                echo "Running Maven unit tests for Spring Boot book-service..."
                sh 'mvn clean test'
            }
            post {
                always {
                    // Collect JUnit test results
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // Correctly extract the service name (e.g., "book-service") from the job name
                    // Assuming job name is like "book-service-pipeline" or "book-service"
                    // If your job name is "book-service-pipeline/main", you might need a more robust regex or split
                    // For simplicity, let's assume the job name is just "book-service-pipeline" or "book-service"
                    // If your job name is "book-service-pipeline/main", you'll need to adjust this.
                    // A safer way to get just the service name might be to define it explicitly or parse differently.
                    // Let's assume the job name is 'book-service-pipeline' or 'book-service'
                    def baseServiceName = env.JOB_NAME.toLowerCase().replace('-pipeline', '')

                    // If your job name is 'book-service-pipeline/main', then baseServiceName will be 'book-service/main'.
                    // We need to extract just 'book-service'. Let's refine this:
                    def serviceNameParts = env.JOB_NAME.toLowerCase().split('/')
                    def serviceName = serviceNameParts[0].replace('-pipeline', '') // Takes 'book-service-pipeline' and makes it 'book-service'

                    // Create a unique image tag using branch name and build number
                    // Ensure the branch name is sanitized for use in a Docker tag
                    def sanitizedBranchName = env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-') // Replace non-tag-safe chars
                    def imageTag = "${sanitizedBranchName == 'main' ? 'latest' : sanitizedBranchName}-${env.BUILD_NUMBER}".toLowerCase()

                    // Use the full dockerRegistry variable here
                    def dockerImageName = "${dockerRegistry}/${serviceName}:${imageTag}"

                    echo "Building Docker image: ${dockerImageName}"
                    sh "docker build -t ${dockerImageName} ."

                    // Use withCredentials to securely inject Docker Hub username and password
                    withCredentials([usernamePassword(credentialsId: dockerCredentialsId, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                        // The docker login command should target the actual Docker Hub registry (docker.io)
                        sh "echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin docker.io"
                        echo "Pushing Docker image: ${dockerImageName}"
                        sh "docker push ${dockerImageName}"
                    }

                    // Store the full image name for later stages
                    env.DOCKER_IMAGE = dockerImageName
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'main' // Only deploy to staging from the main branch
            }
            steps {
                script {
                    // Ensure serviceName here is also just "book-service"
                    def serviceNameParts = env.JOB_NAME.toLowerCase().split('/')
                    def serviceName = serviceNameParts[0].replace('-pipeline', '')

                    def namespace = "staging"

                    echo "Deploying ${serviceName} to Kubernetes staging cluster (${kubernetesContext})..."

                    // Use withKubeConfig to securely use Kubernetes credentials
                    withKubeConfig(credentialsId: kubernetesCredentialsId, contextName: kubernetesContext) {
                        // Create namespace if it doesn't exist (idempotent)
                        sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"

                        // Database connection details for book-service's PostgreSQL
                        // Fetch DB_USER and DB_PASSWORD from Jenkins Secret Text credentials
                        withCredentials([
                            string(credentialsId: dbUserCredentialId, variable: 'BOOK_DB_USER'),
                            string(credentialsId: dbPasswordCredentialId, variable: 'BOOK_DB_PASSWORD')
                        ]) {
                            def dbHost = "book-service-postgresql" // K8s service name of your PostgreSQL instance for book-service
                            def dbPort = "5432"
                            def dbName = "book_db"
                            // Use the fetched credentials
                            def dbUser = BOOK_DB_USER
                            def dbPassword = BOOK_DB_PASSWORD

                            echo "Upgrading/installing Helm chart for ${serviceName}..."
                            sh "helm upgrade --install ${serviceName} ${helmChartPath} --namespace ${namespace} " +
                               "--set image.repository=${dockerRegistry}/${serviceName} " + // Use the full dockerRegistry here
                               "--set image.tag=${env.DOCKER_IMAGE.split(':')[-1]} " +
                               "--set database.host=${dbHost} " +
                               "--set database.port=${dbPort} " +
                               "--set database.name=${dbName} " +
                               "--set database.user=${dbUser} " +
                               "--set database.password=${dbPassword} " +
                               "--wait --timeout 5m"

                            echo "Deployment of ${serviceName} to staging completed."
                            echo "Check the status using: kubectl get pods -n ${namespace} -l app.kubernetes.io/instance=${serviceName}"
                        }
                    }
                }
            }
        }
    } // End of stages block

    // Post-pipeline actions (runs after all stages complete or fail)
    post {
        always {
            cleanWs() // Clean the workspace always
        }
        failure {
            echo "Pipeline for ${env.JOB_NAME} on branch ${env.BRANCH_NAME} failed. Check logs for details."
        }
        success {
            echo "Pipeline for ${env.JOB_NAME} on branch ${env.BRANCH_NAME} succeeded!"
        }
    }
}