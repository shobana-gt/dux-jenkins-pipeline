def GIT_USER_EMAIL = env.GIT_USER_EMAIL ?: 'noreply@example.com'
def GIT_USER_NAME = env.GIT_USER_NAME ?: 'Jenkins CI'
pipeline {
    agent any
    environment {
        UEM_PASSWORD = credentials('uem-pword') // Fetch the UEM password from Jenkins credentials
    }
    stages {
        stage('Check ts_manifest.yml Changes') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/ts_manifest.yml'
                    def lastHashFile = '/var/lib/jenkins/last_ts_manifest_md5'
                    def repoPath = 'git@github.com:shobana-gt/dux-jenkins-pipeline.git'
                    def branchName = 'jenkins-dux-pipeline' // Use the branch configured in seed.groovy
                    def configDir = 'config'

                    // Calculate current hash of ts_manifest.yml
                    def currentHash = sh(script: "md5sum ${manifestPath} | awk '{print \$1}'", returnStdout: true).trim()

                    // Check if last hash file exists
                    def lastHashExists = fileExists(lastHashFile)

                    if (!lastHashExists) {
                        echo "First time run: Pushing ts_manifest.yml to GitHub with comment 'created ts_manifest.yml'"
                        sh """
                            git clone -b ${branchName} ${repoPath} repo
                            cp ${manifestPath} repo/${configDir}/
                            cd repo
                            git add ${configDir}/ts_manifest.yml
                            git commit -m 'created ts_manifest.yml'
                            git push origin ${branchName}
                        """
                        writeFile file: lastHashFile, text: currentHash
                    } else {
                        def lastHash = readFile(lastHashFile).trim()
                        if (currentHash != lastHash) {
                            echo "File has changed: Pushing ts_manifest.yml to GitHub with comment 'user edit'"
                            sh """
                                git clone -b ${branchName} ${repoPath} repo
                                cp ${manifestPath} repo/${configDir}/
                                cd repo
                                git add ${configDir}/ts_manifest.yml
                                git commit -m 'user edit'
                                git push origin ${branchName}
                            """
                            writeFile file: lastHashFile, text: currentHash
                        } else {
                            echo "No changes detected in ts_manifest.yml"
                        }
                    }
                }
            }
        }
        stage('Pre-check Hosts in ts_manifest.yml') {
            steps {
                script {
                    def manifestPath = '/opt/omnissa/dux/ts_manifest.yml'
                    def hosts = sh(script: "awk '/hosts:/ {flag=1; next} /:/ {flag=0} flag {print \$1}' ${manifestPath}", returnStdout: true).trim().split("\n")

                    for (host in hosts) {
                        echo "Checking host: ${host}"

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
                    if (!deployOutput.contains("deployment successful")) {
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