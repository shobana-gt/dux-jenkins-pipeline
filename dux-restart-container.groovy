pipeline {
    agent any
    stages {
        stage('Restart Container') {
            steps {
                script {
                    echo "Restarting container deployed..."
                    try {
                        // Execute the `dux restart -y` command
                        def restartOutput = sh(script: "dux restart -y", returnStdout: true).trim()
                        echo "dux restart command output:\n${restartOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux restart -y` command fails
                        error "Failed to execute 'dux restart -y'. Error: ${e.message}"
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
            echo "Restart Container operation completed successfully."
        }
        failure {
            echo "Restart Container operation failed."
        }
    }
}