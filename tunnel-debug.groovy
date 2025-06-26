def DUX_MAJOR_VERSION = 0
pipeline {
    agent any
    stages {
        stage('Set Dux Major Version') {
            steps {
                script {
                    def duxVersion = sh(script: "dux version | tail -n 1", returnStdout: true).trim()
                    echo "Dux version detected: ${duxVersion}"

                    // Extract the first digit of the version
                    DUX_MAJOR_VERSION = duxVersion.tokenize('.')[0]
                    echo "Dux major version set to: ${DUX_MAJOR_VERSION}"
                }
            }
        }
        stage('Run Dux Logs') {
            steps {
                script {
                    echo "Fetching tunnel server logs for troubleshooting..."
                    def command = "" // Define the command variable outside the try block

                    try {
                   /* echo "Checking Dux version..."
                    def duxVersion = sh(script: "dux version | tail -n 1", returnStdout: true).trim()
                    echo "Dux version detected: ${duxVersion}"

                    // Extract the first digit of the version
                    def majorVersion = duxVersion.tokenize('.')[0] as int*/

                    if (DUX_MAJOR_VERSION >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux tunnel logs'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel logs' : "dux tunnel logs -p ${params.HOST_IP}"
                    } else {
                        echo "Dux version is less than 3. Running 'dux logs'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel logs' : "dux tunnel logs -p ${params.HOST_IP}"
                    }

                    // Execute the `dux logs` command
                    def logsOutput = sh(script: command, returnStdout: true).trim()
                    echo "Dux logs command output:\n${logsOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux logs` command fails
                        error "Failed to execute '${command}'. Error: ${e.message}"
                    }
                }
            }
        }
        stage('Run Dux Report') {
            steps {
                script {
                    echo "Generating output of vpnreport command..."
                    def command = "" 
                    if (DUX_MAJOR_VERSION >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux tunnel report'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel report' : "dux tunnel report -p ${params.HOST_IP}"
                    } else {
                        echo "Dux version is less than 3. Running 'dux report'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel report' : "dux tunnel report -p ${params.HOST_IP}"
                    }
                    try {
                        // Execute the `dux report` command
                        def reportOutput = sh(script: command, returnStdout: true).trim()
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