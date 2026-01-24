pipeline {
    agent any

    environment {
        IMAGE = "tanaka878/shiply-gateway"  // Docker Hub repo for your gateway image
        VERSION = "${env.BUILD_NUMBER}"           // Unique version tag using Jenkins build number
    }

    stages {
        stage('Checkout') {
            steps {
                // Pull latest code from your GitHub repo's main branch
                git branch: 'main', url: 'https://github.com/musungare-tanaka/shiply-gateway.git'
            }
        }

        stage('Docker Build & Push') {
            steps {
                withCredentials([string(credentialsId: 'docker-hub-token', variable: 'DOCKER_TOKEN')]) {
                    // Login to Docker Hub securely
                    sh "echo $DOCKER_TOKEN | docker login -u tanaka878 --password-stdin"

                    // Build Docker image tagged with the current build number (version)
                    sh "docker build -t $IMAGE:$VERSION ."

                    // Push the new version tag to Docker Hub
                    sh "docker push $IMAGE:$VERSION"

                    // Also tag and push 'latest' for convenience (optional)
                    sh "docker tag $IMAGE:$VERSION $IMAGE:latest"
                    sh "docker push $IMAGE:latest"
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'server_ssh_keys', keyFileVariable: 'SSH_KEY')]) {
                    // SSH into your server and update the container using docker-compose
                    sh """
                    ssh -i $SSH_KEY -o StrictHostKeyChecking=no root@144.91.75.79 << EOF
                    cd /opt/shiply
                    docker compose pull gateway
                    docker compose up -d gateway
                    EOF
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'Deployment successful! 🎉'
        }
        failure {
            echo 'Deployment failed. Please check the logs. ⚠️'
        }
        always {
            echo 'Pipeline finished.'
        }
    }
}
