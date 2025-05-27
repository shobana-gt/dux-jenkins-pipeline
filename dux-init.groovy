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
                    dux tunnel init
                """
            }
        }
    }
    post {
        always {
            echo 'Pipeline execution completed.'
        }
    }
}