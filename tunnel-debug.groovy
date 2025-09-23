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
/*         stage('Prepare Workspace') {
            steps {
                sh "cp /opt/omnissa/dux/ts_manifest.yml ${env.WORKSPACE}/ts_manifest.yml"
            }
        }
        stage('Debug File Existence') {
            steps {
                script {
                    def fileExists = fileExists("${env.WORKSPACE}/ts_manifest.yml")
                    echo "Manifest file exists: ${fileExists}"
                }
            }
        } */
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
        stage('Run Dux Logs') {
            steps {
                script {
                    echo "Fetching tunnel server logs for troubleshooting..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux tunnel logs'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel logs' : "dux tunnel logs -p ${params.HOST_IP}"
                    } else {
                        echo "Dux version is less than 3. Running 'dux logs'..."
                        command = params.HOST_IP == 'All' ? 'dux logs' : "dux logs -p ${params.HOST_IP}"
                    }

                    def logsOutput = sh(script: "${command} 2>&1", returnStdout: true).trim()
                    echo "Dux logs command output:\n${logsOutput}"
                }
            }
        }
        stage('Run Dux Report') {
            steps {
                script {
                    echo "Generating output of vpnreport command..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux tunnel report'..."
                        command = params.HOST_IP == 'All' ? 'dux tunnel report' : "dux tunnel report -p ${params.HOST_IP}"
                    } else {
                        echo "Dux version is less than 3. Running 'dux report'..."
                        command = params.HOST_IP == 'All' ? 'dux report' : "dux report -p ${params.HOST_IP}"
                    }

                    def reportOutput = sh(script: "${command} 2>&1", returnStdout: true).trim()
                    echo "Dux Report Output:\n${reportOutput}"
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
    def ips = ['All']

    node {
        try {
            def manifestPath = "/opt/omnissa/dux/ts_manifest.yml"

            def manifestContent = readFile(manifestPath)
            echo "Manifest Content:\n${manifestContent}"

            def yaml = new org.yaml.snakeyaml.Yaml()
            def manifest = yaml.load(manifestContent)

            if (manifest.tunnel_server?.hosts) {
                manifest.tunnel_server.hosts.each { host ->
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