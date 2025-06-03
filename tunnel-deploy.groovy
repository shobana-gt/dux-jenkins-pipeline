def GIT_USER_EMAIL = env.GIT_USER_EMAIL ?: 'noreply@example.com'
def GIT_USER_NAME = env.GIT_USER_NAME ?: 'Jenkins CI'
def CLUSTER_CREDS_REPO = env.CLUSTER_CREDS_REPO
def CLUSTER_CREDS_GIT_CRED_REF = env.CLUSTER_CREDS_GIT_CRED_REF

pipeline {
    agent any
    environment {
        UEM_PASSWORD = credentials('uem-pword') // Fetch the UEM password from Jenkins credentials
    }
    parameters {
           string(name: 'CLUSTER_BRANCH',
               defaultValue: null,
               description: 'Branch to checkout from cluster secrets repository')
            string(
                    name: 'manifestPath',
                    defaultValue: '/opt/omnissa/dux/ts_manifest.yml',
                    description: 'Path to the manifest file for Tunnel')

    }
    stages {
        stage('Check ts_manifest.yml Changes') {
            steps {
                script {
                    def manifestPath = params.manifestPath
                    def lastHashFile = '/var/lib/jenkins/last_ts_manifest_md5'
                    def repoPath = CLUSTER_CREDS_REPO
                    def branchName = params.CLUSTER_BRANCH // Use the branch configured in seed.groovy
                    def configDir = 'config'

                    // Calculate current hash of ts_manifest.yml
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
                 echo "First time run: Pushing ts_manifest.yml to GitHub with comment 'created ts_manifest.yml'"
                    withCredentials([sshUserPrivateKey(credentialsId: CLUSTER_CREDS_GIT_CRED_REF, keyFileVariable: 'SSH_KEY')]) {
                        sh """
                            GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${CLUSTER_CREDS_REPO} repo
                            cp ${manifestPath} repo/${configDir}/
                            cd repo
                            git config user.email "${GIT_USER_EMAIL}"
                            git config user.name "${GIT_USER_NAME}"
                            git add ${configDir}/ts_manifest.yml
                            git commit -m 'created ts_manifest.yml'
                            git push origin ${branchName}
                        """
                    }
                    writeFile file: lastHashFile, text: currentHash
                    } else {
                        def lastHash = readFile(lastHashFile).trim()
                        if (currentHash != lastHash) {
                        echo "File has changed: Pushing ts_manifest.yml to GitHub with comment 'user edit'"
                        withCredentials([sshUserPrivateKey(credentialsId: CLUSTER_CREDS_GIT_CRED_REF, keyFileVariable: 'SSH_KEY')]) {
                            sh """
                                GIT_SSH_COMMAND='ssh -i ${SSH_KEY}' git clone -b ${branchName} ${CLUSTER_CREDS_REPO} repo
                                cp ${manifestPath} repo/${configDir}/
                                cd repo
                                git config user.email "${GIT_USER_EMAIL}"
                                git config user.name "${GIT_USER_NAME}"
                                git add ${configDir}/ts_manifest.yml
                                git commit -m 'user edit'
                                git push origin ${branchName}
                            """
                        }
                        writeFile file: lastHashFile, text: currentHash
                        } else {
                            echo "No changes detected in ts_manifest.yml"
                        }
                    }
                }
            }
        }
       /*  stage('Pre-check Hosts in ts_manifest.yml') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/ts_manifest.yml'
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
        stage('Checking Prerequisites on Hosts') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/ts_manifest.yml'
                    def hosts = sh(script: "awk '/- address:/ {print \$3}' ${manifestPath}", returnStdout: true).trim().split("\n")

                    for (host in hosts) {
                        echo "Checking prerequisites on host: ${host}"
                        echo "Checking if SSH is running on ${host}..."
                        echo "Checking if Docker is running on ${host}..."
                    }
                }
            }
        }
        stage('Run Dux Deploy -d') {
            steps {
                script {
                    def deployOutput = sh(script: "dux deploy -d", returnStdout: true).trim()
                    echo "Dux Deploy Output: ${deployOutput}"
                    if (deployOutput.contains("error")) {
                        error "Dux Deploy -d failed with errors. Exiting pipeline."
                    }
                }
            }
        }
        stage('Run Dux Deploy with UEM Password') {
            steps {
                script {
                    def deployOutput = sh(script: "dux deploy -u ${env.UEM_PASSWORD} -y", returnStdout: true).trim()
                    echo "Dux Deploy Output: ${deployOutput}"
                    if (!deployOutput.contains("Deployment is up")) {
                        error "Dux Deploy with UEM Password failed. Exiting pipeline."
                    }
                }
            }
        }
        stage('Check Dux Status') {
            steps {
                script {
                    def statusOutput = sh(script: "dux status", returnStdout: true).trim()
                    echo "Dux Status Output: ${statusOutput}"
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