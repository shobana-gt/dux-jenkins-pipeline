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
        string(
            name: 'CLUSTER_BRANCH',
            defaultValue: null,
            description: 'Branch to checkout from cluster secrets repository'
        )
        string(
            name: 'manifestPath',
            defaultValue: '/opt/omnissa/dux/seg_manifest.yml',
            description: 'Path to the manifest file for SEG'
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

        stage('Check seg_manifest.yml Changes') {
            steps {
                script {
                    def manifestPath = params.manifestPath
                    def lastHashFile = '/var/lib/jenkins/last_seg_manifest_md5'
                    def repoPath = CLUSTER_CREDS_REPO
                    def branchName = params.CLUSTER_BRANCH
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
                            def repoUrl = CLUSTER_CREDS_REPO

                            // Check if the branch exists in the remote repository
                            def branchExists = sh(
                                script: """
                                    GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git ls-remote --heads ${repoUrl} ${branchName} | wc -l
                                """,
                                returnStdout: true
                            ).trim() == "1"

                            if (!branchExists) {
                                echo "Branch '${branchName}' does not exist in the remote repository. Creating it now..."

                                // Clone the repository and create the branch
                                sh """
                                    GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone ${repoUrl} repo
                                    cd repo
                                    git checkout -b ${branchName}
                                    git push origin ${branchName}
                                """
                            } else {
                                echo "Branch '${branchName}' already exists in the remote repository."
                            }

                            // Proceed with cloning the branch
                            sh """
                                GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${repoUrl} repo
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
                                    GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${repoPath} repo
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

        stage('Run Dux Deploy -d') {
            steps {
                script {
                    echo "Running Dux Deploy -d to deploy the container..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg deploy -d'..."
                        command = 'dux seg deploy -d'
                    } else {
                        error "Dux version is less than 3.0. SEG container is not supported."
                    }

                    try {
                        def dryRunOutput = sh(script: "${command} 2>&1", returnStdout: true).trim()
                        echo "dux deploy dryrun command output:\n${dryRunOutput}"
                    } catch (Exception e) {
                        error "Failed to execute '${command}'. Error: ${e.message}. Exiting pipeline."
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
                        command = params.HOST_IP == 'All' ? "dux seg deploy -u ${env.UEM_PASSWORD} -y" : "dux seg deploy -u ${env.UEM_PASSWORD} -y -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0. SEG container is not supported."
                    }

                    try {
                        def deployOutput = sh(script: command, returnStdout: true).trim()
                        echo "dux deploy command output:\n${deployOutput}"

                        if (!deployOutput.contains("Deployment is up")) {
                            error "Dux Deploy with UEM Password failed. Exiting pipeline."
                        }
                    } catch (Exception e) {
                        error "Failed to execute '${command}'. Error: ${e.message}"
                    }
                }
            }
        }

        stage('Check Dux Status') {
            steps {
                script {
                    echo "Checking Status of deployments..."
                    def command = ""

                    if (env.DUX_MAJOR_VERSION.toInteger() >= 3) {
                        echo "Dux version is 3 or higher. Running 'dux seg status'..."
                        command = params.HOST_IP == 'All' ? 'dux seg status' : "dux seg status -p ${params.HOST_IP}"
                    } else {
                        error "Dux version is less than 3.0. SEG container is not supported."
                    }

                    try {
                        def statusOutput = sh(script: command, returnStdout: true).trim()
                        echo "dux status command output:\n${statusOutput}"
                    } catch (Exception e) {
                        error "Failed to execute '${command}'. Error: ${e.message}"
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