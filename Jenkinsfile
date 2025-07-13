// Jenkinsfile for book-service
// This file should be placed at the root of the book-service repository.

// Define global environment variables
def dockerRegistry = "sumitrajneesh" // e.g., "myusername" - REPLACE WITH YOUR DOCKER HUB USERNAME
def dockerCredentialsId = "3bdc9f350d0642d19dec3a60aa1875b4" // Jenkins credential ID for Docker Hub/GitLab Registry
def sonarqubeServerId = "SonarQube" // Jenkins SonarQube server configuration ID - REPLACE WITH YOUR ACTUAL JENKINS SONARQUBE SERVER NAME
def sonarqubeCredentialsId = "sonarqube-server" // Jenkins credential ID for SonarQube access token
def kubernetesCredentialsId = "kubernetes-credentials" // Jenkins credential ID for Kubernetes access (e.g., Kubeconfig)
def kubernetesContext = "minikube" // Kubernetes context name for your staging cluster - REPLACE WITH YOUR ACTUAL K8S CONTEXT
def helmChartPath = "helm/book-service-chart" // Path to book-service's Helm chart within its repository
def dbPasswordCredentialId = "book-db-password" // Jenkins credential ID for book-service DB password (Secret Text)
def dbUserCredentialId = "book-db-user" // Jenkins credential ID for book-service DB user (Secret Text) - OPTIONAL, if user is also secret

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
            parallel {
                stage('Unit Tests') {
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
                stage('Code Quality (SonarQube)') {
                    steps {
                        echo "Running SonarQube analysis for Spring Boot book-service..."
                        // The withSonarQubeEnv wrapper injects SONAR_HOST_URL and SONAR_AUTH_TOKEN
                        withSonarQubeEnv(installationName: sonarqubeServerId, credentialsId: sonarqubeCredentialsId) {
                            // This 'sh' command MUST be inside the withSonarQubeEnv block
                            sh "mvn sonar:sonar -Dsonar.projectKey=book-service -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}"
                        }
                    }
                    post {
                        // This 'always' block ensures quality gate check runs even if analysis fails
                        always {
                            // Increased timeout to 10 minutes for SonarQube Quality Gate check
                            timeout(time: 10, unit: 'MINUTES') {
                                // This step waits for the SonarQube Quality Gate result
                                waitForQualityGate abortPipeline: true
                            }
                        }
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // Use env.JOB_NAME to get the pipeline name (e.g., "book-service-pipeline")
                    // and then strip "-pipeline" to get "book-service"
                    def serviceName = env.JOB_NAME.toLowerCase().replace('-pipeline', '')
                    // Create a unique image tag using branch name and build number
                    def imageTag = "${env.BRANCH_NAME == 'main' ? 'latest' : env.BRANCH_NAME}-${env.BUILD_NUMBER}".replaceAll('/', '-')
                    def dockerImageName = "${dockerRegistry}/${serviceName}:${imageTag}"

                    echo "Building Docker image: ${dockerImageName}"
                    sh "docker build -t ${dockerImageName} ."

                    // Use withCredentials to securely inject Docker Hub username and password
                    withCredentials([usernamePassword(credentialsId: dockerCredentialsId, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                        sh "echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin ${dockerRegistry.split('/')[0]}"
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
                    def serviceName = env.JOB_NAME.toLowerCase().replace('-pipeline', '')
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
                               "--set image.repository=${dockerRegistry}/${serviceName} " +
                               "--set image.tag=${env.DOCKER_IMAGE.split(':')[-1]} " +
                               "--set database.host=${dbHost} " +
                               "--set database.port=${dbPort} " +
                               "--set database.name=${dbName} " +
                               "--set database.user=${dbUser} " +
                               "--set database.password=${dbPassword} " + // Pass password directly if not using K8s secret for DB_PASSWORD env var
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