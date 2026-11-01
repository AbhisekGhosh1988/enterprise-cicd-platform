
package org.enterprise.cicd

class KubernetesDeploy implements Serializable {

    private final def steps

    KubernetesDeploy(def steps) {
        this.steps = steps
    }

    def run(Map config = [:]) {

        def workingDirectory = config.get('workingDirectory', '.')
        def manifestDirectory = config.get(
                'manifestDirectory',
                'k8s/dev'
        )

        def deploymentFile = config.get(
                'deploymentFile',
                'deployment.yaml'
        )

        def serviceFile = config.get(
                'serviceFile',
                'service.yaml'
        )

        def namespace = config.get('namespace', 'dev')
        def deploymentName = config.get(
                'deploymentName',
                'cicd-demo'
        )

        def containerName = config.get(
                'containerName',
                'cicd-demo'
        )

        def expectedContext = config.get(
                'expectedContext',
                'docker-desktop'
        )

        def imageTag = config.get('imageTag', '')
        def gitCommit = config.get('gitCommit', '')

        def expectedReplicas = config.get(
                'expectedReplicas',
                2
        )

        def timeoutMinutes = config.get(
                'timeoutMinutes',
                10
        )

        steps.stage('Kubernetes Dev Deployment') {

            steps.timeout(
                    time: timeoutMinutes,
                    unit: 'MINUTES'
            ) {

                steps.dir(workingDirectory) {

                    steps.echo 'Starting Kubernetes Deployment'

                    // -------------------------------------------------
                    // PHASE 1: Validate configuration
                    // -------------------------------------------------

                    if (!(namespace ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid Kubernetes namespace: ${namespace}")
                    }

                    if (!(deploymentName ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid Deployment name: ${deploymentName}")
                    }

                    if (!(containerName ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid container name: ${containerName}")
                    }

                    if (!(expectedReplicas instanceof Integer) ||
                            expectedReplicas < 1) {
                        steps.error('Expected replicas must be a positive integer')
                    }

                    if (!(gitCommit ==~ /[0-9a-fA-F]{12}/)) {
                        steps.error('Invalid Git commit SHA. Expected 12 hexadecimal characters.')
                    }

                    if (!imageTag || imageTag.contains(' ') ||
                            !imageTag.endsWith(":${gitCommit}")) {
                        steps.error(
                                "Image tag must end with the current Git commit SHA: ${gitCommit}"
                        )
                    }

                    steps.echo "Namespace: ${namespace}"
                    steps.echo "Deployment: ${deploymentName}"
                    steps.echo "Expected replicas: ${expectedReplicas}"
                    steps.echo "Image: ${imageTag}"

                    // -------------------------------------------------
                    // PHASE 2: Validate manifest files
                    // -------------------------------------------------

                    def deploymentPath =
                            "${manifestDirectory}/${deploymentFile}"

                    def servicePath =
                            "${manifestDirectory}/${serviceFile}"

                    if (!steps.fileExists(deploymentPath)) {
                        steps.error(
                                "Deployment manifest not found: ${deploymentPath}"
                        )
                    }

                    if (!steps.fileExists(servicePath)) {
                        steps.error(
                                "Service manifest not found: ${servicePath}"
                        )
                    }

                    steps.echo 'Both Kubernetes manifest files exist.'

                    // -------------------------------------------------
                    // PHASE 3: Verify Kubernetes connectivity
                    // -------------------------------------------------

                    steps.echo 'Checking Kubernetes CLI...'

                    steps.bat 'kubectl version --client'

                    def currentContext = steps.bat(
                            script: '@echo off\r\nkubectl config current-context',
                            returnStdout: true
                    ).trim()

                    steps.echo "Current Kubernetes context: ${currentContext}"

                    if (currentContext != expectedContext) {
                        steps.error(
                                "Unexpected Kubernetes context. Expected ${expectedContext}, found ${currentContext}"
                        )
                    }

                    // -------------------------------------------------
                    // PHASE 4: Verify namespace
                    // -------------------------------------------------

                    steps.bat(
                            "kubectl get namespace ${namespace}"
                    )

                    // -------------------------------------------------
                    // PHASE 5: Server-side manifest validation
                    // -------------------------------------------------

                    steps.echo 'Validating Deployment manifest...'

                    steps.bat(
                            "kubectl apply --dry-run=server -f \"${deploymentPath}\""
                    )

                    steps.echo 'Validating Service manifest...'

                    steps.bat(
                            "kubectl apply --dry-run=server -f \"${servicePath}\""
                    )

                    steps.echo 'Kubernetes manifests passed server-side validation.'

                    // -------------------------------------------------
                    // PHASE 6: Apply Kubernetes manifests
                    // -------------------------------------------------

                    steps.echo 'Applying Kubernetes Deployment manifest...'

                    steps.bat(
                            "kubectl apply -f \"${deploymentPath}\""
                    )

                    steps.echo 'Applying Kubernetes Service manifest...'

                    steps.bat(
                            "kubectl apply -f \"${servicePath}\""
                    )

                    // -------------------------------------------------
                    // PHASE 7: Deploy immutable image
                    // -------------------------------------------------

                    steps.echo "Updating Deployment image to ${imageTag}"

                    steps.bat(
                            "kubectl set image deployment/${deploymentName} ${containerName}=${imageTag} -n ${namespace}"
                    )

                    // -------------------------------------------------
                    // PHASE 8: Wait for rollout
                    // -------------------------------------------------

                    steps.echo 'Waiting for Kubernetes rollout...'

                    steps.bat(
                            "kubectl rollout status deployment/${deploymentName} -n ${namespace} --timeout=180s"
                    )

                    // -------------------------------------------------
                    // PHASE 9: Verify replica availability
                    // -------------------------------------------------

                    def readyReplicas = steps.bat(
                            script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.readyReplicas}\"",
                            returnStdout: true
                    ).trim()

                    def availableReplicas = steps.bat(
                            script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.availableReplicas}\"",
                            returnStdout: true
                    ).trim()

                    steps.echo "Ready replicas: ${readyReplicas}"
                    steps.echo "Available replicas: ${availableReplicas}"

                    if (!readyReplicas.isInteger() ||
                            !availableReplicas.isInteger()) {
                        steps.error(
                                'Unable to determine Deployment replica availability.'
                        )
                    }

                    if (readyReplicas.toInteger() != expectedReplicas) {
                        steps.error(
                                "Expected ${expectedReplicas} ready replicas, found ${readyReplicas}"
                        )
                    }

                    if (availableReplicas.toInteger() != expectedReplicas) {
                        steps.error(
                                "Expected ${expectedReplicas} available replicas, found ${availableReplicas}"
                        )
                    }

                    // -------------------------------------------------
                    // PHASE 10: Final deployment verification
                    // -------------------------------------------------

                    steps.echo 'Final Deployment status:'

                    steps.bat(
                            "kubectl get deployment ${deploymentName} -n ${namespace} -o wide"
                    )

                    steps.echo 'Application Pods:'

                    steps.bat(
                            "kubectl get pods -n ${namespace} -l app=${deploymentName} -o wide"
                    )

                    steps.echo 'Kubernetes Service:'

                    steps.bat(
                            "kubectl get service ${deploymentName} -n ${namespace}"
                    )

                    steps.echo 'KUBERNETES DEPLOYMENT COMPLETED SUCCESSFULLY'

                    return [
                            deploymentName: deploymentName,
                            namespace: namespace,
                            imageTag: imageTag,
                            gitCommit: gitCommit,
                            readyReplicas: readyReplicas.toInteger(),
                            availableReplicas: availableReplicas.toInteger(),
                            deployed: true
                    ]
                }
            }
        }
    }
}
