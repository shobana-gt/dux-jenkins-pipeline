def GIT_USER_EMAIL = env.GIT_USER_EMAIL ?: 'noreply@example.com'
def GIT_USER_NAME = env.GIT_USER_NAME ?: 'Jenkins CI'
def CLUSTER_CREDS_REPO = env.CLUSTER_CREDS_REPO
def CLUSTER_CREDS_GIT_CRED_REF = env.CLUSTER_CREDS_GIT_CRED_REF
def CLUSTER_BRANCH = env.CLUSTER_BRANCH 
node {
    // Prepare the workspace and copy the manifest file
    sh "cp /opt/omnissa/dux/seg_manifest.yml ${env.WORKSPACE}/seg_manifest.yml"
}
pipeline {
    agent any
    environment {
        UEM_PASSWORD = credentials('uem-pword') // Fetch the UEM password from Jenkins credentials
    }

    parameters {
        choice(
            name: 'HOST_IP',
            choices: getHostIPs(),
            description: 'Select the IP address of the host (or "All" for all hosts)'
        )
        string(name: 'CLUSTER_BRANCH',
               defaultValue: null,
               description: 'Branch to checkout from cluster secrets repository')
        string(
                    name: 'manifestPath',
                    defaultValue: '/opt/omnissa/dux/seg_manifest.yml',
                    description: 'Path to the manifest file for SEG')

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
        stage('Check seg_manifest.yml Changes') {
            steps {
                script {
                    def manifestPath = params.manifestPath
                    def lastHashFile = '/var/lib/jenkins/last_seg_manifest_md5'
                    def repoPath = CLUSTER_CREDS_REPO
                    def branchName = params.CLUSTER_BRANCH // Use the branch configured in seed.groovy
                    def configDir = 'config'

                    // Calculate current hash of seg_manifest.yml
                    def currentHash = sh(script: "md5sum ${manifestPath} | awk '{print \$1}'", returnStdout: true).trim()

                    // Check if last hash file exists
                    def lastHashExists = fileExists(lastHashFile)
                    // Ensure the repo directory is clean
                    sh """
                        if [ -d repo ]; then
                            rm -rf repo
                        fi
                    """
                    if (!lastHashExists) {
                    echo "First time run: Pushing seg_manifest.yml to GitHub with comment 'created seg_manifest.yml'"
                    withCredentials([sshUserPrivateKey(credentialsId: CLUSTER_CREDS_GIT_CRED_REF, keyFileVariable: 'SSH_KEY')]) {
                        sh """
                            GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${CLUSTER_CREDS_REPO} repo
                            cp ${manifestPath} repo/${configDir}/
                            cd repo
                            git config user.email "${GIT_USER_EMAIL}"
                            git config user.name "${GIT_USER_NAME}"
                            git add ${configDir}/seg_manifest.yml
                            git commit -m 'created seg_manifest.yml'
                            git push origin ${branchName}
                        """
                    }
                    writeFile file: lastHashFile, text: currentHash
                    } else {
                        def lastHash = readFile(lastHashFile).trim()
                        if (currentHash != lastHash) {
                        echo "File has changed: Pushing seg_manifest.yml to GitHub with comment 'user edit'"
                        withCredentials([sshUserPrivateKey(credentialsId: CLUSTER_CREDS_GIT_CRED_REF, keyFileVariable: 'SSH_KEY')]) {
                            sh """
                                GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${CLUSTER_CREDS_REPO} repo
                                cp ${manifestPath} repo/${configDir}/
                                cd repo
                                git config user.email "${GIT_USER_EMAIL}"
                                git config user.name "${GIT_USER_NAME}"
                                git add ${configDir}/seg_manifest.yml
                                git commit -m 'user edit'
                                git push origin ${branchName}
                            """
                        }
                        writeFile file: lastHashFile, text: currentHash
                        } else {
                            echo "No changes detected in seg_manifest.yml"
                        }
                    }
                }
            }
        }
       /*  stage('Pre-check Hosts in seg_manifest.yml') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/seg_manifest.yml'
                    def hosts = sh(script: "awk '/- address:/ {print \$3}' ${manifestPath}", returnStdout: true).trim().split("\n")

                    for (host in hosts) {
                        echo "Checking host: ${host}"
                        // Validate host address
                        if (!host || !host.matches('^([a-zA-Z0-9.-]+|[0-9]{1,3}(\\.[0-9]{1,3}){3})$')) {
                            error "Address of host is empty or invalid: ${host}. Exiting pipeline."
                        }
                        // Add host key to known_hosts
                        sh """
                            ssh-keyscan -H ${host} >> ~/.ssh/known_hosts
                        """
                        // Check if SSH is enabled
                        def sshCheck = sh(script: "ssh -o BatchMode=yes -o ConnectTimeout=5 ${host} 'echo SSH enabled'", returnStatus: true)
                        if (sshCheck != 0) {
                            error "SSH is not enabled on host: ${host}. Exiting pipeline."
                        }

                        // Check if Docker is installed and running
                        def dockerCheck = sh(script: "ssh ${host} 'docker info > /dev/null 2>&1'", returnStatus: true)
                        if (dockerCheck != 0) {
                            error "Docker is not installed or not running on host: ${host}. Exiting pipeline."
                        }

                        echo "Host ${host} passed pre-checks."
                    }
                }
            }
        } */
         /*stage('Checking Prerequisites on Hosts') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/seg_manifest.yml'
                    def hosts = sh(script: "awk '/- address:/ {print \$3}' ${manifestPath}", returnStdout: true).trim().split("\n")

                    for (host in hosts) {
                        echo "Checking prerequisites on host: ${host}"
                        echo "Checking if SSH is running on ${host}..."
                        echo "Checking if Docker is running on ${host}..."
                    }
                }
            }
        } */
        stage('Run Dux Deploy -d') {
            steps {
                script {
                    echo "Running Dux Deploy -d to deploy the container..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg deploy -d'..."
                        command = 'dux seg deploy -d' 
                    } else {
                        error "Dux version is less than 3.0 . SEG container is not supported."
                    }
                    try {
                        // Execute the `dux deploy -d` command
                        def dryRunOutput = sh(script: command, returnStdout: true).trim()
                        echo "dux deploy dryrun command output:\n${dryRunOutput}"
                    } catch (Exception e) {
                        // Handle errors if the `dux deploy -d` command fails
                        error "Failed to execute '${command}'. Error: ${e.message}. Exiting pipeline"
                    }

                }
            }
        }
        stage('Run Dux Deploy with UEM Password') {
            steps {
                script {
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg deploy'..."
                        command = params.HOST_IP == 'All' ? "dux seg deploy -u ${env.UEM_PASSWORD} -y" : "dux deploy -u ${env.UEM_PASSWORD} -y -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0 . SEG container is not supported."
                    }
                    try {
                        // Execute the `dux stop` command
                        def deployOutput = sh(script: command, returnStdout: true).trim()
                        echo "dux deploy command output:\n${deployOutput}"

                        if (!deployOutput.contains("Deployment is up")) {
                            error "Dux Deploy with UEM Password failed. Exiting pipeline."
                        }

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
                    echo "Checking Status of deployments..."
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
        failure {
            echo "Pipeline failed."
        }
        success {
            echo "Pipeline executed successfully."
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