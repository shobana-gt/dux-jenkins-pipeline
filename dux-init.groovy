pipeline {
    agent any
    parameters {
        string(name: 'ARTIFACTORY_PATH', defaultValue: 'https://packages.omnissa.com/ws1-tunnel/dux/2.3.0.405/dux-2.3.0.405-1.x86_64.rpm', description: 'Path to the Dux RPM in the artifactory')
        string(name: 'IMAGE_PATH', defaultValue: '/path/to/image', description: 'Path to the image')
    }
    stages {
        stage('Download Dux RPM') {
            steps {
                echo "Downloading Dux RPM from ${params.ARTIFACTORY_PATH}"
                sh """
                    curl -O ${params.ARTIFACTORY_PATH}
                """
            }
        }
        stage('Convert RPM to DEB') {
            steps {
                echo 'Converting RPM to DEB'
                sh """
                    sudo apt update
                    sudo apt install -y alien
                    sudo alien -d dux-2.3.0.405-1.x86_64.rpm
                """
            }
        }
        stage('Install Dux') {
            steps {
                echo 'Installing Dux DEB package'
                sh """
                    sudo dpkg -i dux_2.3.0.405-2_amd64.deb
                    
                """
            }
        }
                stage('Update Permissions for /opt') {
            steps {
                echo 'Updating permissions for /opt directory'
                sh """
                    sudo chown -R jenkins:jenkins /opt
                    sudo chmod -R 775 /opt
                """
            }
        }
        stage('Verify Installation') {
            steps {
                echo 'Verifying Dux installation'
                sh """
                    whoami
                    ls -ltr /
                    which dux
                    dux version

                """
            }
        }
        stage('Initialize Dux Tunnel') {
            steps {
                echo 'Initializing Dux Tunnel'
                sh """
                    dux init
                """
            }
        }
        stage('Check ts_manifest.yml') {
            steps {
                echo 'Checking if ts_manifest.yml is created'
                script {
                    def manifestExists = sh(
                        script: 'test -f /opt/omnissa/dux/ts_manifest.yml',
                        returnStatus: true
                    )
                    if (manifestExists == 0) {
                        echo 'ts_manifest.yml file created successfully. Dux initialization succeeded.'
                    } else {
                        error 'dux-init failed: ts_manifest.yml file not found.'
                    }
                }
            }
        }
    }
    post {
        always {
            echo 'Pipeline execution completed.'
        }
    }
}