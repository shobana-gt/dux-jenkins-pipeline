pipeline {
    agent any
    stages {
        stage('Run Dux Logs') {
            steps {
                script {
                    echo "Fetching tunnel server logs for troubleshooting..."
                    try {
                        // Execute the `dux logs` command
                        def logsOutput = sh(script: "dux logs", returnStdout: true).trim()
                        echo "Dux Logs Output:\n${logsOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux logs` command fails
                        error "Failed to execute 'dux logs'. Error: ${e.message}"
                    }
                }
            }
        }
        stage('Run Dux Report') {
            steps {
                script {
                    echo "Generating output of vpnreport command..."
                    try {
                        // Execute the `dux report` command
                        def reportOutput = sh(script: "dux report", returnStdout: true).trim()
                        echo "Dux Report Output:\n${reportOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux report` command fails
                        error "Failed to execute 'dux report'. Error: ${e.message}"
                    }
                }
            }
        }
    }
    post {
        success {
            echo "Dux Debug job completed successfully."
        }
        failure {
            echo "Dux Debug job failed."
        }
    }
}