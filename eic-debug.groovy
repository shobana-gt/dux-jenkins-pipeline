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
                sh "cp /opt/omnissa/dux/eic_manifest.yml ${env.WORKSPACE}/eic_manifest.yml"
            }
        }
        stage('Debug File Existence') {
            steps {
                script {
                    def fileExists = fileExists("${env.WORKSPACE}/eic_manifest.yml")
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
                    echo "Fetching EIC container logs for troubleshooting..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux eic logs'..."
                        command = params.HOST_IP == 'All' ? 'dux eic logs' : "dux eic logs -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0 . EIC container is not supported."
                    }

                    def logsOutput = sh(script: "${command} 2>&1", returnStdout: true).trim()
                    echo "Dux logs command output:\n${logsOutput}"
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
            def manifestPath = "${env.WORKSPACE}/eic_manifest.yml"

            def manifestContent = readFile(manifestPath)
            echo "Manifest Content:\n${manifestContent}"

            def yaml = new org.yaml.snakeyaml.Yaml()
            def manifest = yaml.load(manifestContent)

            if (manifest.eic_policy_engine?.hosts) {
                manifest.eic_policy_engine.hosts.each { host ->
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