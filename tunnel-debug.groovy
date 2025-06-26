def DUX_MAJOR_VERSION = 0
pipeline {
    agent any
    parameters {
        choice(
            name: 'HOST_IP',
            choices: getHostIPs(),
            description: 'Select the IP address of the host (or "All" for all hosts)'
        )
    }
    stages {
        stage('Prepare Workspace') {
            steps {
                sh 'cp /opt/omnissa/dux/ts_manifest.yml ${env.WORKSPACE}/ts_manifest.yml'
            }
        }
        stage('Debug File Existence') {
            steps {
                script {
                    def fileExists = fileExists('ts_manifest.yml')
                    echo "Manifest file exists: ${fileExists}"
                }
            }
        }
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

def getHostIPs() {
    def ips = ['All'] // Add "All" as the first option

    node {
        try {
            def manifestPath = 'ts_manifest.yml' // Ensure the file is in the workspace

            // Read the manifest file from the workspace
            def manifestContent = readFile(manifestPath)
            echo "Manifest Content:\n${manifestContent}" // Debug log

            def yaml = new org.yaml.snakeyaml.Yaml()
            def manifest = yaml.load(manifestContent)

            // Navigate to the `hosts` section and extract the `address` field
            if (manifest.tunnel_server?.hosts) {
                manifest.tunnel_server.hosts.each { host ->
                    if (host.address) {
                        ips << host.address
                    }
                }
            } else {
                echo "No hosts found in the manifest file."
            }

            echo "Extracted IPs: ${ips}" // Debug log
        } catch (Exception e) {
            error "Failed to read or parse the manifest file: ${e.message}" // Fail the pipeline
        }
    }

    return ips.join('\n') // Return as a newline-separated string for the `choice` parameter
}