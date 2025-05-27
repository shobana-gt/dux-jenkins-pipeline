import javaposse.jobdsl.dsl.DslFactory


pipelineJob('dux-init') {
    description('Pipeline to install and initialize Dux')

    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('git@github.com:shobana-gt/dux-jenkins-pipeline.git')
                        credentials('github-ssh-key') // Use the SSH key stored in Jenkins
                    }
                    branch('jenkins-dux-pipeline') // Specify the branch to fetch the scripts from
                }
            }
            scriptPath('dux-init.groovy') // Path to the pipeline script in the repository
        }
    }

    parameters {
        stringParam('ARTIFACTORY_PATH', 'https://packages.omnissa.com/ws1-tunnel/dux/2.3.0.405/dux-2.3.0.405-1.x86_64.rpm', 'Path to the Dux RPM in the artifactory')
        stringParam('IMAGE_PATH', '/path/to/image', 'Path to the image')
    }
}