pipeline {
    agent any
    stages {
        stage('Stop Container') {
            steps {
                script {
                    echo "Stopping container..."
                    try {
                        // Execute the `dux stop` command
                        def stopOutput = sh(script: "dux stop -y", returnStdout: true).trim()
                        echo "dux stop command output:\n${stopOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux stop` command fails
                        error "Failed to execute 'dux stop'. Error: ${e.message}"
                    }
                }
            }
        }
        stage('Check Dux Status') {
            steps {
                script {
                    echo "Checking status of deployment..."
                    try {
                        // Execute the `dux status` command
                        def statusOutput = sh(script: "dux status", returnStdout: true).trim()
                        echo "dux status comamnd output:\n${statusOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux status` command fails
                        error "Failed to execute 'dux status'. Error: ${e.message}"
                    }
                }
            }
        }
    }
    post {
        success {
            echo "Dux Stop Container operation completed successfully."
        }
        failure {
            echo "Dux Stop Container operation failed."
        }
    }
}