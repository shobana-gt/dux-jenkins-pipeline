def GIT_USER_EMAIL = env.GIT_USER_EMAIL ?: 'noreply@example.com'
def GIT_USER_NAME = env.GIT_USER_NAME ?: 'Jenkins CI'
def CLUSTER_CREDS_REPO = env.CLUSTER_CREDS_REPO ?: 'git@github.com:shobana-gt/container-cluster-config.git'
def CLUSTER_CREDS_GIT_CRED_REF = env.CLUSTER_CREDS_GIT_CRED_REF ?: 'github-ssh-key'

pipeline {
    agent any
    parameters {
        string(
            name: 'CLUSTER_BRANCH',
            defaultValue: 'main',
            description: 'Branch to checkout from cluster secrets repository'
        )
        string(
            name: 'ARTIFACTORY_PATH',
            defaultValue: 'https://packages.omnissa.com/ws1-tunnel/dux/2.3.0.405/dux-2.3.0.405-1.x86_64.rpm',
            description: 'Path to the Dux RPM in the artifactory'
        )
    }
    stages {
        stage('Clone Cluster Repository') {
            steps {
                script {
                    echo "Cloning cluster repository from ${CLUSTER_CREDS_REPO} branch ${params.CLUSTER_BRANCH}..."
                    sh """
                        rm -rf repo
                        git clone -b ${params.CLUSTER_BRANCH} ${CLUSTER_CREDS_REPO} repo
                    """
                }
            }
        }
        stage('Prepare EIC Config Directory') {
            steps {
                script {
                    echo "Preparing EIC Config Directory..."
                    sh """
                        mkdir -p /opt/omnissa/dux/eic-config
                    """
                }
            }
        }
        stage('Copy Files to Target Directory') {
            steps {
                script {
                    echo "Copying files from repo/config/eic-config to /opt/omnissa/dux/eic-config..."
                    sh """
                        cp repo/config/eic-config/policies.json /opt/omnissa/dux/eic-config/policies.json
                        cp repo/config/eic-config/application.yaml /opt/omnissa/dux/eic-config/application.yaml
                        cp repo/config/eic-config/logback.xml /opt/omnissa/dux/eic-config/logback.xml
                    """
                }
            }
        }
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
        stage('Initialize EIC Manifest') {
            steps {
                script {
                    echo "Checking Dux version..."
                    def duxVersion = sh(script: "dux version | tail -n 1", returnStdout: true).trim()
                    echo "Dux version detected: ${duxVersion}"

                    // Extract the first digit of the version
                    def majorVersion = duxVersion.tokenize('.')[0] as int

                    // Check if the manifest exists
                    def manifestPath = "/opt/omnissa/dux/eic_manifest.yml"
                    def manifestExists = fileExists(manifestPath)

                    if (manifestExists) {
                        echo "Manifest already exists at ${manifestPath}."
                        error "Manifest already exists. Please check and update the manifest manually to avoid issues."
                    } else {
                        echo "Manifest does not exist at ${manifestPath}."
                        if (majorVersion >= 3) {
                            echo "Dux version is 3.0 or higher. Running 'dux eic init' to create the manifest..."
                            sh "dux eic init"
                        } else {
                            error "Dux version is less than 3.0. EIC Container is not supported. Exiting pipeline."
                        }
                    }
                }
            }
        }
        stage('Check eic_manifest.yml') {
            steps {
                script {
                    def manifestPath = "/opt/omnissa/dux/eic_manifest.yml"
                    if (fileExists(manifestPath)) {
                        echo "eic_manifest.yml already exists. Update the manifest if needed."
                    } else {
                        echo "eic_manifest.yml does not exist. Proceed with creating it if required."
                    }
                }
            }
        }
    }
    post {
        success {
            echo "EIC: initialization pipeline completed successfully."
        }
        failure {
            echo "EIC: initialization pipeline failed."
        }
    }
}