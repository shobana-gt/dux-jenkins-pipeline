pipeline {
    agent any
    parameters {
        activeChoice(
            name: 'HOST_IP',
            description: 'Select the IP address of the host (or "All" for all hosts)',
            choiceType: 'SINGLE_SELECT', // Dropdown list
            script: [
                classpath: [],
                sandbox: true,
                script: '''
                    // Read the manifest file and extract IP addresses
                    def manifestPath = '/opt/omnissa/dux/ts_manifest.yml'
                    def ips = ['All'] // Add "All" as the first option
                    def manifestFile = new File(manifestPath)
                    if (manifestFile.exists()) {
                        def manifestContent = manifestFile.text
                        def yaml = new org.yaml.snakeyaml.Yaml()
                        def manifest = yaml.load(manifestContent)
                        // Assuming the manifest has a structure like:
                        // hosts:
                        //   - address: 10.87.132.166
                        //   - address: 10.87.132.167
                        manifest.hosts.each { host ->
                            ips << host.address
                        }
                    } else {
                        ips << 'Manifest file not found'
                    }
                    return ips
                '''
            ]
        )
    }
    stages {
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