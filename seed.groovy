import javaposse.jobdsl.dsl.DslFactory
import org.jenkinsci.plugins.scriptsecurity.scripts.*

// CHANGEME
//////////////// THE following are the settings to apply to individual pipelines //////////////////
def clusterCredsRepository = "git@github.com:shobana-gt/container-cluster-config.git"
def clusterCredsGitCredRef = "github-ssh-key"

// GIT USER CONFIG TO COMMIT CLUSTER CREDS
def gitUserEmail = "noreply@example.com"
def gitUserName = "Jenkins CI"

/* // Approve required signatures
def scriptApproval = ScriptApproval.get()
def alreadyApproved = new HashSet<>(
    Arrays.asList(scriptApproval.getApprovedSignatures() ?: [] as String[])
)
void approveSignature(String signature) {
    if (!alreadyApproved.contains(signature)) {
        scriptApproval.approveSignature(signature)
    }
} */
scriptApproval = ScriptApproval.get()
alreadyApproved = new HashSet<>(Arrays.asList(scriptApproval.getApprovedSignatures()))
void approveSignature(String signature) {
    if (!alreadyApproved.contains(signature)) {
        scriptApproval.approveSignature(signature)
    }
}

approveSignature('method javaposse.jobdsl.dsl.DslFactory folder java.lang.String')
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

scriptApproval.save()

// Define the Git repository details
def gitRepoUrl = 'git@github.com:shobana-gt/dux-jenkins-pipeline.git'
def gitBranch = 'jenkins-dux-pipeline'
def credentialsId = 'github-ssh-key'

// Define container types and their jobs
def containers = [
    'Tunnel': ['Init', 'Deploy', 'Healthcheck', 'Debug', 'Stop', 'Restart', 'Remove'],
    'EIC': ['Init', 'Deploy', 'Healthcheck', 'Debug', 'Stop', 'Restart', 'Remove'],
    'SEG': ['Init', 'Deploy', 'Healthcheck', 'Debug', 'Stop', 'Restart', 'Remove']
]
// Helper function to determine the manifest path based on the container name
def getManifestPath(container) {
    switch (container.toLowerCase()) {
        case 'tunnel':
            return "/opt/omnissa/dux/ts_manifest.yml"
        case 'eic':
            return "/opt/omnissa/dux/eic_manifest.yml"
        case 'seg':
            return "/opt/omnissa/dux/seg_manifest.yml"
        default:
            return "/opt/omnissa/dux/ts_manifest.yml" // Fallback for unknown containers
    }
}

// Create folders and jobs
containers.each { container, jobs ->
    folder(container) {
        description("Folder for ${container} container jobs")
    }

    jobs.each { job ->
        def jobName = "${container}/${job.toLowerCase()}" // Nested job under the folder
        def pipelineScriptPath = "${container.toLowerCase()}-${job.toLowerCase()}.groovy"

        pipelineJob(jobName) {
            description("Pipeline for ${job} in ${container} container")

            definition {
                cpsScm {
                    scm {
                        git {
                            remote {
                                url(gitRepoUrl)
                                credentials(credentialsId)
                            }
                            branch(gitBranch)
                        }
                    }
                    scriptPath(pipelineScriptPath) // Path to the pipeline script in the repository
                }
            }

            environmentVariables(
                GIT_USER_EMAIL: "$gitUserEmail",
                GIT_USER_NAME: "$gitUserName",
                CLUSTER_CREDS_REPO: "$clusterCredsRepository",
                CLUSTER_CREDS_GIT_CRED_REF: "$clusterCredsGitCredRef"
            )

            // Add parameters for specific jobs if needed
/*             if (job == 'Deploy') {
                parameters {
                    stringParam('manifestPath', "/opt/omnissa/dux/ts_manifest.yml", "Path to the ts_manifest.yml file for ${container}")
                }
            } */
            if (job == 'Deploy') {

            parameters {
                stringParam(
                    'manifestPath',
                    getManifestPath(container),
                    "Path to the manifest file for ${container}"
                )
                stringParam('CLUSTER_BRANCH', null, 'Branch to checkout from cluster secrets repository')

                }
            }

        }
    }
}