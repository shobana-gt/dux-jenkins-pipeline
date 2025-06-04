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
                sh 'cp /opt/omnissa/dux/ts_manifest.yml ${WORKSPACE}/ts_manifest.yml'
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
/*         stage('Debug Host IPs') {
            steps {
                script {
                    def ips = getHostIPs()
                    echo "Available IPs:\n${ips}"
                }
            }
        } */
        stage('Run Dux Health Check') {
            steps {
                script {
                    echo "Running Dux Health Check..."
                    try {
                        // Determine the command to execute based on the selected IP
                        def command = params.HOST_IP == 'All' ? 'dux status' : "dux status -p ${params.HOST_IP}"
                        
                        // Execute the command
                        def statusOutput = sh(script: command, returnStdout: true).trim()
                        echo "Dux Status Output:\n${statusOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux status` command fails
                        error "Failed to execute '${params.HOST_IP == 'All' ? 'dux status' : "dux status -p ${params.HOST_IP}"}'. Error: ${e.message}"
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