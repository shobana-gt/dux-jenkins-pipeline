pipeline {
    agent any
    stages {
        stage('Remove Container') {
            steps {
                script {
                    echo "Removing container..."
                    try {
                        // Execute the `dux destroy -y` command
                        def destroyOutput = sh(script: "dux destroy -y", returnStdout: true).trim()
                        echo "dux destroy command output:\n${destroyOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux destroy -y` command fails
                        error "Failed to execute 'dux destroy -y'. Error: ${e.message}"
                    }
                }
            }
        }
        stage('Check Dux Status') {
            steps {
                script {
                    echo "Checking Dux Status..."
                    try {
                        // Execute the `dux status` command
                        def statusOutput = sh(script: "dux status", returnStdout: true).trim()
                        echo "dux status command output:\n${statusOutput}"
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
            echo "Remove Container operation completed successfully."
        }
        failure {
            echo "Remove Container operation failed."
        }
    }
}