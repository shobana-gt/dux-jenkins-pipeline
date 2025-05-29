pipeline {
    agent any
    stages {
        stage('Run Dux Health Check') {
            steps {
                script {
                    echo "Running Dux Health Check..."
                    try {
                        // Execute the `dux status` command
                        def statusOutput = sh(script: "dux status", returnStdout: true).trim()
                        echo "Dux Status Output:\n${statusOutput}"
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
            echo "Dux Health Check completed successfully."
        }
        failure {
            echo "Dux Health Check failed."
        }
    }
}