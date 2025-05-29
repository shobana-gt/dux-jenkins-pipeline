import javaposse.jobdsl.dsl.DslFactory
import org.jenkinsci.plugins.scriptsecurity.scripts.*

def gitUserEmail = "noreply@example.com"
def gitUserName = "Jenkins CI"



scriptApproval = ScriptApproval.get()
alreadyApproved = new HashSet<>(Arrays.asList(scriptApproval.getApprovedSignatures()))
void approveSignature(String signature) {
    if (!alreadyApproved.contains(signature)) {
        scriptApproval.approveSignature(signature)
    }
}

// Approve required signatures
approveSignature('method javaposse.jobdsl.dsl.DslFactory pipelineJob java.lang.String')
approveSignature('method javaposse.jobdsl.dsl.helpers.scm.GitContext remote')
approveSignature('method javaposse.jobdsl.dsl.helpers.scm.GitContext branch java.lang.String')
approveSignature('method javaposse.jobdsl.dsl.helpers.scm.GitContext credentials java.lang.String')
approveSignature('method javaposse.jobdsl.dsl.helpers.scm.GitContext url java.lang.String')
approveSignature('method javaposse.jobdsl.dsl.helpers.BuildParametersContext stringParam java.lang.String java.lang.String java.lang.String')
approveSignature('method javaposse.jobdsl.dsl.helpers.EnvironmentVariableContext environmentVariables java.util.Map')
approveSignature('method org.jenkinsci.plugins.workflow.steps.ShellStep sh java.lang.String')
approveSignature('method org.jenkinsci.plugins.workflow.steps.ShellStep sh java.util.Map')
approveSignature('method org.jenkinsci.plugins.workflow.steps.ErrorStep error java.lang.String')
approveSignature('method org.jenkinsci.plugins.workflow.cps.CpsScript readFile java.lang.String')
approveSignature('method org.jenkinsci.plugins.workflow.cps.CpsScript writeFile java.util.Map')
approveSignature('method java.lang.String trim')
approveSignature('method java.lang.String split java.lang.String')
approveSignature('method groovy.json.JsonSlurper parseText java.lang.String')
approveSignature('method groovy.json.JsonBuilder call java.util.Map')
approveSignature('method java.lang.String plus java.lang.String')
approveSignature('method java.lang.String contains java.lang.CharSequence')

// Save approvals
scriptApproval.save()

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
    environmentVariables (
    GIT_USER_EMAIL: "$gitUserEmail",
    GIT_USER_NAME: "$gitUserName",
    )
    parameters {
        stringParam('ARTIFACTORY_PATH', 'https://packages.omnissa.com/ws1-tunnel/dux/2.3.0.405/dux-2.3.0.405-1.x86_64.rpm', 'Path to the Dux RPM in the artifactory')
        stringParam('IMAGE_PATH', '/path/to/image', 'Path to the image')
    }
}
pipelineJob('dux-deploy') {
    description('Pipeline to deploy Dux with pre-checks and deployment stages')

    // Define the pipeline script location
    definition {
        cpsScm {
            scm {
                git {
                    remote {
                        url('git@github.com:shobana-gt/dux-jenkins-pipeline.git') // GitHub repository URL
                        credentials('github-ssh-key') // Use the SSH key stored in Jenkins
                    }
                    branch('jenkins-dux-pipeline') // Specify the branch to fetch the scripts from
                }
            }
            scriptPath('dux-deploy.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
    GIT_USER_EMAIL: "$gitUserEmail",
    GIT_USER_NAME: "$gitUserName",
    )
    // Define parameters for the pipeline
    parameters {
        stringParam('manifestPath', '/opt/omnissa/dux/ts_manifest.yml', 'Path to the ts_manifest.yml file')
    }
}


// Dux Healthcheck Pipeline
pipelineJob('dux-healthcheck') {
    description('Pipeline to run dux status command for health check')

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
            scriptPath('dux-healthcheck.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
        GIT_USER_EMAIL: "$gitUserEmail",
        GIT_USER_NAME: "$gitUserName",
    )
}

// Dux Debug Pipeline
pipelineJob('dux-debug') {
    description('Pipeline to run dux logs and dux report commands for debugging')

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
            scriptPath('dux-debug.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
        GIT_USER_EMAIL: "$gitUserEmail",
        GIT_USER_NAME: "$gitUserName",
    )
}

// Dux Stop Container Pipeline
pipelineJob('dux-stop-container') {
    description('Pipeline to stop the Dux container and check its status')

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
            scriptPath('dux-stop-container.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
        GIT_USER_EMAIL: "$gitUserEmail",
        GIT_USER_NAME: "$gitUserName",
    )
}

// Dux Restart Container Pipeline
pipelineJob('dux-restart-container') {
    description('Pipeline to restart the Dux container and check its status')

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
            scriptPath('dux-restart-container.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
        GIT_USER_EMAIL: "$gitUserEmail",
        GIT_USER_NAME: "$gitUserName",
    )
}

// Dux Remove Container Pipeline
pipelineJob('dux-remove-container') {
    description('Pipeline to remove the Dux container and check its status')

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
            scriptPath('dux-remove-container.groovy') // Path to the pipeline script in the repository
        }
    }
    environmentVariables (
        GIT_USER_EMAIL: "$gitUserEmail",
        GIT_USER_NAME: "$gitUserName",
    )
}