pipeline {
    agent any
    parameters {
        string(name: 'ARTIFACTORY_PATH', defaultValue: 'https://packages.omnissa.com/ws1-tunnel/dux/2.3.0.405/dux-2.3.0.405-1.x86_64.rpm', description: 'Path to the Dux RPM in the artifactory')
    }
    stages {
        stage('Install Dux if Not Installed') {
            steps {
                script {
                    echo "Checking if Dux is already installed..."
                    def duxExists = sh(script: "command -v dux", returnStatus: true) == 0

                    if (duxExists) {
                        echo "Dux is already installed. Moving to the next stage."
                    } else {
                        echo "Dux is not installed. Proceeding with installation..."

                        // Check the OS type
                        def osType = sh(script: "cat /etc/os-release | grep '^ID=' | cut -d'=' -f2", returnStdout: true).trim()

                        if (osType == "ubuntu") {
                            echo "OS is Ubuntu. Proceeding with Ubuntu-specific installation steps..."

                            // Ubuntu-specific stages
                            echo "Downloading Dux RPM from ${params.ARTIFACTORY_PATH}"
                            sh """
                                echo "Testing URL accessibility..."
                                curl -I ${params.ARTIFACTORY_PATH}
                                echo "Downloading RPM..."
                                curl -O ${params.ARTIFACTORY_PATH}
                            """

                            echo 'Converting RPM to DEB'
                            sh """
                                sudo apt update
                                sudo apt install -y alien
                                sudo alien -d dux-2.3.0.405-1.x86_64.rpm
                            """

                            echo 'Installing Dux DEB package'
                            sh """
                                sudo dpkg -i dux_2.3.0.405-2_amd64.deb
                            """

                            echo 'Updating permissions for /opt directory'
                            sh """
                                sudo chown -R jenkins:jenkins /opt
                                sudo chmod -R 775 /opt
                            """
                        } else if (osType == "centos" || osType == "rhel" || osType == "fedora") {
                            echo "OS supports yum/dnf. Proceeding with yum/dnf installation steps..."

                            // Remove existing dux.repo
                            sh "sudo rm -f /etc/yum.repos.d/dux.repo"

                            // Clean the package manager cache
                            sh """
                                if command -v yum > /dev/null; then
                                    sudo yum clean all
                                elif command -v dnf > /dev/null; then
                                    sudo dnf clean all
                                fi
                            """

                            // Create dux.repo and install Dux
                            sh """
                                cat << EOF | sudo tee /etc/yum.repos.d/dux.repo
                                [dux]
                                name=Workspace ONE Tunnel CLI
                                baseurl=https://packages.omnissa.com/ws1-tunnel/dux
                                enabled=1
                                gpgcheck=0
                                EOF

                                if command -v yum > /dev/null; then
                                    sudo yum install -y dux
                                elif command -v dnf > /dev/null; then
                                    sudo dnf install -y dux
                                fi
                            """
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
                    }
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
        stage('Initialize Dux Tunnel') {
            steps {
                script {
                    echo "Initializing Dux Tunnel..."
                    sh "dux init"
                }
            }
        }
        stage('Check ts_manifest.yml') {
            steps {
                script {
                    def manifestPath = "/opt/omnissa/dux/ts_manifest.yml"
                    if (fileExists(manifestPath)) {
                        echo "ts_manifest.yml already exists. Update the manifest if needed."
                    } else {
                        echo "ts_manifest.yml does not exist. Proceed with creating it if required."
                    }
                }
            }
        }
    }
    post {
        success {
            echo "Tunnel initialization pipeline completed successfully."
        }
        failure {
            echo "Tunnel initialization pipeline failed."
        }
    }
}