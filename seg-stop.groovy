node {
    // Prepare the workspace and copy the manifest file
    sh "cp /opt/omnissa/dux/seg_manifest.yml ${env.WORKSPACE}/seg_manifest.yml"
}
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
        stage('Set Dux Major Version') {
            steps {
                script {
                    def duxVersion = sh(script: "dux version | tail -n 1", returnStdout: true).trim()
                    echo "Dux version detected: ${duxVersion}"

                    // Extract the first digit of the version
                    env.DUX_MAJOR_VERSION = duxVersion.tokenize('.')[0]
                    echo "Dux major version set to: ${env.DUX_MAJOR_VERSION}"
                }
            }
        }
        stage('Stop Container') {
            steps {
                script {
                    echo "Stopping container..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg stop'..."
                        command = params.HOST_IP == 'All' ? 'dux seg stop -y' : "dux seg stop -y -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0 . SEG container is not supported."
                    }
                    try {
                        // Execute the `dux stop` command
                        def stopOutput = sh(script: "${command} 2>&1", returnStdout: true).trim()
                        echo "dux stop command output:\n${stopOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux stop` command fails
                        error "Failed to execute '${command}'. Error: ${e.message}"
                    }
                }
            }
        }
        stage('Check Dux Status') {
            steps {
                script {
                    echo "Checking status of deployment..."
                    def command = "" // Define the command variable outside the try block

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg status'..."
                        command = params.HOST_IP == 'All' ? 'dux seg status' : "dux seg status -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0 . SEG container is not supported."
                    }

                    
                    try {
                        // Execute the `dux status` command
                        def statusOutput = sh(script: command, returnStdout: true).trim()
                        echo "dux status command output:\n${statusOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux status` command fails
                        error "Failed to execute '$command'. Error: ${e.message}"
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

def getHostIPs() {
    def ips = ['All']

    node {
        try {
            def manifestPath = "${env.WORKSPACE}/seg_manifest.yml"

            def manifestContent = readFile(manifestPath)
            echo "Manifest Content:\n${manifestContent}"

            def yaml = new org.yaml.snakeyaml.Yaml()
            def manifest = yaml.load(manifestContent)

            if (manifest.seg?.hosts) {
                manifest.seg.hosts.each { host ->
                    if (host.address) {
                        ips << host.address
                    }
                }
            } else {
                echo "No hosts found in the manifest file."
            }

            echo "Extracted IPs: ${ips}"
        } catch (Exception e) {
            echo "Failed to read or parse the manifest file: ${e.message}"
        }
    }

    return ips.join('\n')
}

