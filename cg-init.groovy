def GIT_USER_EMAIL = env.GIT_USER_EMAIL ?: 'noreply@example.com'
def GIT_USER_NAME = env.GIT_USER_NAME ?: 'Jenkins CI'
def CLUSTER_CREDS_REPO = env.CLUSTER_CREDS_REPO
def CLUSTER_CREDS_GIT_CRED_REF = env.CLUSTER_CREDS_GIT_CRED_REF
pipeline {
    agent any
    parameters {
        string(name: 'ARTIFACTORY_PATH', defaultValue: 'https://packages.omnissa.com/ws1-tunnel/dux/3.1.2.1076/dux-3.1.2.1076-1.x86_64.rpm', description: 'Path to the Dux RPM in the artifactory')
        string(name: 'CLUSTER_BRANCH',
               defaultValue: null,
               description: 'Branch to checkout from cluster secrets repository')
    }
    stages {
        stage('Install Dux if Not Installed') {
            steps {
                script {
                    if (params.ARTIFACTORY_PATH?.trim()) {
                        echo "ARTIFACTORY_PATH provided: ${params.ARTIFACTORY_PATH}"

                        echo "Checking if Dux is already installed..."
                        def duxExists = sh(script: "command -v dux", returnStatus: true) == 0

                        if (duxExists) {
                            echo "Dux is already installed. Proceeding with uninstallation..."
                            def osType = sh(script: "cat /etc/os-release | grep '^ID=' | cut -d'=' -f2", returnStdout: true).trim()

                            if (osType == "ubuntu") {
                                echo "OS is Ubuntu. Uninstalling Dux using dpkg..."
                                sh "sudo dpkg --remove dux || true"
                            } else {
                                echo "OS is not Ubuntu. Uninstalling Dux using rpm..."
                                def duxInstalled = sh(script: "rpm -qa | grep dux", returnStdout: true).trim()
                                sh "sudo rpm -e ${duxInstalled}"
                            }
                        } else {
                            echo "Dux is not installed. Proceeding with installation..."
                        }

                        // Extract the file name from the ARTIFACTORY_PATH
                        def rpmFileName = params.ARTIFACTORY_PATH.tokenize('/').last()
                        echo "Extracted RPM file name: ${rpmFileName}"

                        echo "Downloading Dux RPM from ${params.ARTIFACTORY_PATH}"
                        sh """
                            curl -O ${params.ARTIFACTORY_PATH}
                        """

                        // Check the OS type again for installation
                        def osType = sh(script: "cat /etc/os-release | grep '^ID=' | cut -d'=' -f2", returnStdout: true).trim()

                        if (osType == "ubuntu") {
                            echo "Converting RPM to DEB for Ubuntu..."
                            sh """
                                sudo apt update
                                sudo apt install -y alien
                                sudo alien -d ${rpmFileName}
                            """

                            // Dynamically determine the generated .deb file name
                            def debFileName = sh(script: "ls *.deb | grep '^dux_'", returnStdout: true).trim()
                            echo "Generated DEB file name: ${debFileName}"

                            echo "Installing Dux DEB package"
                            sh """
                                sudo dpkg -i ${debFileName}
                            """

                            echo "Updating permissions for /opt directory"
                            sh """
                                sudo chown -R jenkins:jenkins /opt
                                sudo chmod -R 775 /opt
                            """
                        } else if (osType == "centos" || osType == "rhel" || osType == "fedora") {
                            echo "Installing RPM directly for non-Ubuntu systems..."
                            sh "sudo rpm -i ${rpmFileName}"
                        } else {
                            error "Unsupported OS type: ${osType}. Exiting pipeline."
                        }

                        // Verify directory structure after installation
                        def directories = [
                            "/opt/omnissa/dux/",
                            "/opt/omnissa/dux/images/",
                            "/opt/omnissa/dux/logs/"
                        ]
                        directories.each { dir ->
                            if (!fileExists(dir)) {
                                error "Directory ${dir} does not exist after installation. Exiting pipeline."
                            }
                        }
                        echo "Dux installation completed successfully."
                    } else {
                        echo "ARTIFACTORY_PATH not provided. Skipping installation."
                    }
                }
            }
        }
        stage('Cleanup Workspace') {
            steps {
                script {
                    echo "Cleaning up .deb files from the workspace..."
                    sh "rm -f *.deb"
                    echo "Cleanup completed."
                }
            }
        }
        stage('Verify Installation') {
            steps {
                script {
                    echo "Verifying Dux installation..."
                    def duxExists = sh(script: "command -v dux", returnStatus: true) == 0
                    if (!duxExists) {
                        error "Dux command not found after installation. Exiting pipeline."
                    }
                    echo "Dux is installed and verified."
                }
            }
        }
        stage('Initialize Content Gateway') {
            steps {
                script {
                    echo "Checking Dux version..."
                    def duxVersion = sh(script: "dux version | tail -n 1", returnStdout: true).trim()
                    echo "Dux version detected: ${duxVersion}"

                    // Extract the first digit of the version
                    def majorVersion = duxVersion.tokenize('.')[0] as int

                    // Check if the manifest exists
                    def manifestPath = "/opt/omnissa/dux/cg_manifest.yml"
                    def manifestExists = fileExists(manifestPath)

                    if (manifestExists) {
                        echo "Manifest already exists at ${manifestPath}."
                        error "Manifest already exists. Please check and update the manifest manually to avoid issues."
                    } else {
                        echo "Manifest does not exist at ${manifestPath}."
                        if (majorVersion >= 3) {
                            echo "Dux version is 3.0 or higher. Running 'dux cg init' to create the manifest..."
                            sh "dux cg init"
                        } else {
                            error "Dux version is less than 3.0. Content Gateway container is not supported. Exiting pipeline."
                        }
                    }
                }
            }
        }
        stage('Check cg_manifest.yml') {
            steps {
                script {
                    def manifestPath = "/opt/omnissa/dux/cg_manifest.yml"
                    if (fileExists(manifestPath)) {
                        echo "cg_manifest.yml already exists. Update the manifest if needed."
                    } else {
                        echo "cg_manifest.yml does not exist. Proceed with creating it if required."
                    }
                }
            }
        }
    }
    post {
        success {
            echo "CG: initialization pipeline completed successfully."
        }
        failure {
            echo "CG: initialization pipeline failed."
        }
    }
}
