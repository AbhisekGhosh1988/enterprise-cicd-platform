package org.enterprise.cicd

class KubernetesDeploy implements Serializable {

    private final def steps

    KubernetesDeploy(def steps) {
        this.steps = steps
    }

    def run(Map config = [:]) {

        def workingDirectory = config.get('workingDirectory', '.')
        def manifestDirectory = config.get('manifestDirectory', 'k8s/dev')
        def deploymentFile = config.get('deploymentFile', 'deployment.yaml')
        def serviceFile = config.get('serviceFile', 'service.yaml')

        def environmentName = config.get('environmentName', 'dev')
        def namespace = config.get('namespace', environmentName)

        def deploymentName = config.get('deploymentName', 'cicd-demo')
        def serviceName = config.get('serviceName', 'cicd-demo')
        def containerName = config.get('containerName', 'cicd-demo')

        def expectedContext = config.get('expectedContext', 'docker-desktop')

        def imageRepository = config.get(
                'imageRepository',
                'ghcr.io/abhisekghosh1988/cicd-demo'
        )

        def imageTag = config.get('imageTag', '')
        def gitCommit = config.get('gitCommit', '')

        def expectedReplicas = config.get('expectedReplicas', 2)
        def timeoutMinutes = config.get('timeoutMinutes', 10)
        def rolloutTimeoutSeconds = config.get('rolloutTimeoutSeconds', 180)
        def healthCheckPort = config.get('healthCheckPort', 18081)

        steps.stage("Kubernetes ${environmentName.toUpperCase()} Deployment") {

            steps.timeout(time: timeoutMinutes, unit: 'MINUTES') {

                steps.dir(workingDirectory) {

                    steps.echo "Starting Enterprise Kubernetes Deployment for ${environmentName.toUpperCase()}"

                    // PHASE 1: Protect the target environment.
                    def allowedEnvironments = ['dev', 'qa']

                    if (!(environmentName in allowedEnvironments)) {
                        steps.error("Unsupported deployment environment: ${environmentName}")
                    }

                    if (namespace != environmentName) {
                        steps.error(
                                "Environment and namespace mismatch. " +
                                        "Environment: ${environmentName}, Namespace: ${namespace}"
                        )
                    }

                    if (!(deploymentName ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid Deployment name: ${deploymentName}")
                    }

                    if (!(serviceName ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid Service name: ${serviceName}")
                    }

                    if (!(containerName ==~ /[a-z0-9]([-a-z0-9]*[a-z0-9])?/)) {
                        steps.error("Invalid container name: ${containerName}")
                    }

                    if (!(expectedReplicas instanceof Integer) || expectedReplicas < 1) {
                        steps.error('Expected replicas must be a positive integer')
                    }

                    if (!(healthCheckPort instanceof Integer) ||
                            healthCheckPort < 1024 || healthCheckPort > 65535) {
                        steps.error('Invalid health check port')
                    }

                    if (!(rolloutTimeoutSeconds instanceof Integer) || rolloutTimeoutSeconds < 1) {
                        steps.error('Rollout timeout must be a positive integer')
                    }

                    if (!(timeoutMinutes instanceof Integer) || timeoutMinutes < 1) {
                        steps.error('Pipeline timeout must be a positive integer')
                    }

                    if (!(gitCommit ==~ /[0-9a-f]{12}/)) {
                        steps.error('Git commit must be a 12-character lowercase SHA')
                    }

                    if (!(imageRepository ==~ /[a-z0-9.-]+(\/[a-z0-9._-]+)+/)) {
                        steps.error('Invalid image repository')
                    }

                    def expectedImage = "${imageRepository}:${gitCommit}"
                    if (imageTag != expectedImage) {
                        steps.error("Image tag mismatch. Expected ${expectedImage}, received ${imageTag}")
                    }

                    steps.echo "Target namespace: ${namespace}"
                    steps.echo "Deployment: ${deploymentName}"
                    steps.echo "Service: ${serviceName}"
                    steps.echo "Image: ${imageTag}"
                    steps.echo "Expected replicas: ${expectedReplicas}"

                    // PHASE 2: Locate manifests.
                    def deploymentPath = "${manifestDirectory}/${deploymentFile}"
                    def servicePath = "${manifestDirectory}/${serviceFile}"

                    if (!steps.fileExists(deploymentPath)) {
                        steps.error("Deployment manifest not found: ${deploymentPath}")
                    }

                    if (!steps.fileExists(servicePath)) {
                        steps.error("Service manifest not found: ${servicePath}")
                    }

                    // PHASE 3: Verify Kubernetes connectivity and context.
                    steps.echo 'Checking Kubernetes CLI...'
                    steps.bat 'kubectl version --client'

                    def currentContext = steps.bat(
                            script: '@echo off\r\nkubectl config current-context',
                            returnStdout: true
                    ).trim()

                    if (currentContext != expectedContext) {
                        steps.error("Unexpected Kubernetes context. Expected ${expectedContext}, found ${currentContext}")
                    }

                    steps.echo "Kubernetes context verified: ${currentContext}"

                    // PHASE 4: Verify namespace.
                    steps.bat("kubectl get namespace ${namespace}")

                    // PHASE 5: Verify existing Deployment and container.
                    steps.bat("kubectl get deployment ${deploymentName} -n ${namespace}")

                    def existingContainer = steps.bat(
                            script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.spec.template.spec.containers[?(@.name=='${containerName}')].name}\"",
                            returnStdout: true
                    ).trim()

                    if (existingContainer != containerName) {
                        steps.error("Container ${containerName} was not found in Deployment ${deploymentName}")
                    }

                    // PHASE 5A: Capture the known-good image before changing anything.
                    def previousImage = steps.bat(
                            script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.spec.template.spec.containers[?(@.name=='${containerName}')].image}\"",
                            returnStdout: true
                    ).trim()

                    def previousRevision = steps.bat(
                            script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.metadata.annotations.deployment\\.kubernetes\\.io/revision}\"",
                            returnStdout: true
                    ).trim()

                    if (!previousImage) {
                        steps.error('Unable to capture the previous Deployment image. Deployment aborted.')
                    }

                    steps.echo "Previous image captured: ${previousImage}"
                    steps.echo "Previous Deployment revision: ${previousRevision ?: 'unknown'}"

                    // PHASE 6: Render the new image into a temporary manifest.
                    steps.echo 'Rendering Deployment manifest with immutable image'

                    def renderedDirectory = 'target/k8s'
                    def renderedDeploymentPath = "${renderedDirectory}/${environmentName}-deployment-rendered.yaml"

                    steps.bat('if not exist target\\k8s mkdir target\\k8s')
                    steps.bat("kubectl set image -f \"${deploymentPath}\" ${containerName}=${imageTag} --local -o yaml > \"${renderedDeploymentPath}\"")

                    if (!steps.fileExists(renderedDeploymentPath)) {
                        steps.error('Rendered Deployment manifest was not created')
                    }

                    steps.echo 'Rendered Deployment manifest successfully.'

                    // PHASE 7: Validate manifests before applying them.
                    steps.echo 'Validating rendered Deployment...'
                    steps.bat("kubectl apply --dry-run=server -f \"${renderedDeploymentPath}\"")

                    steps.echo 'Validating Service...'
                    steps.bat("kubectl apply --dry-run=server -f \"${servicePath}\"")

                    // PHASE 8: Apply the Service. No Deployment change has happened yet.
                    steps.echo 'Applying Service...'
                    steps.bat("kubectl apply -f \"${servicePath}\"")

                    def readyReplicas = ''
                    def availableReplicas = ''

                    // PHASES 9-11: Deploy, verify rollout, and verify application health.
                    // Any failure after the Deployment apply attempt triggers restoration
                    // of the previously captured image.
                    try {
                        steps.echo 'Applying Deployment with new image...'
                        steps.bat("kubectl apply -f \"${renderedDeploymentPath}\"")

                        steps.echo 'Waiting for Deployment rollout...'
                        steps.bat("kubectl rollout status deployment/${deploymentName} -n ${namespace} --timeout=${rolloutTimeoutSeconds}s")

                        // Verify replica availability.
                        readyReplicas = steps.bat(
                                script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.readyReplicas}\"",
                                returnStdout: true
                        ).trim()

                        availableReplicas = steps.bat(
                                script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.availableReplicas}\"",
                                returnStdout: true
                        ).trim()

                        steps.echo "Ready replicas: ${readyReplicas}"
                        steps.echo "Available replicas: ${availableReplicas}"

                        if (!readyReplicas.isInteger() || !availableReplicas.isInteger()) {
                            steps.error('Unable to determine replica availability')
                        }

                        if (readyReplicas.toInteger() != expectedReplicas) {
                            steps.error("Expected ${expectedReplicas} ready replicas, found ${readyReplicas}")
                        }

                        if (availableReplicas.toInteger() != expectedReplicas) {
                            steps.error("Expected ${expectedReplicas} available replicas, found ${availableReplicas}")
                        }

                        // Verify the application through the Kubernetes Service.
                        steps.echo 'Starting temporary Kubernetes port-forward for HTTP health checks'
                        verifyHttpHealth(namespace, serviceName, healthCheckPort, 'APPLICATION')

                    } catch (Exception deploymentFailure) {
                        steps.echo '================================================'
                        steps.echo "${environmentName.toUpperCase()} DEPLOYMENT FAILED - AUTOMATIC ROLLBACK STARTING"
                        steps.echo '================================================'
                        steps.echo "Deployment failure: ${deploymentFailure.getMessage()}"
                        steps.echo "Restoring previously deployed image: ${previousImage}"

                        try {
                            // This is deliberately unconditional. Setting the same image
                            // is safe if the failed apply did not change the Deployment.
                            rollbackDeployment(
                                    environmentName,
                                    namespace,
                                    deploymentName,
                                    serviceName,
                                    containerName,
                                    previousImage,
                                    expectedReplicas,
                                    rolloutTimeoutSeconds,
                                    healthCheckPort
                            )
                        } catch (Exception rollbackFailure) {
                            steps.echo '!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!'
                            steps.echo "AUTOMATIC ${environmentName.toUpperCase()} ROLLBACK FAILED"
                            steps.echo '!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!'
                            steps.echo "Rollback failure: ${rollbackFailure.getMessage()}"
                            steps.echo 'Manual investigation is required. Current Deployment state:'

                            // Best-effort diagnostics must not replace the original failure.
                            try {
                                steps.bat("kubectl get deployment ${deploymentName} -n ${namespace} -o wide")
                                steps.bat("kubectl get pods -n ${namespace} -l app=${deploymentName} -o wide")
                                steps.bat("kubectl get events -n ${namespace} --sort-by=.lastTimestamp")
                            } catch (Exception diagnosticFailure) {
                                steps.echo "Could not collect all rollback diagnostics: ${diagnosticFailure.getMessage()}"
                            }
                        }

                        // Fail the pipeline with the original deployment exception.
                        throw deploymentFailure
                    }

                    // PHASE 12: Final verification after all deployment checks pass.
                    steps.echo 'Final Deployment status:'
                    steps.bat("kubectl get deployment ${deploymentName} -n ${namespace} -o wide")

                    steps.echo 'Application Pods:'
                    steps.bat("kubectl get pods -n ${namespace} -l app=${deploymentName} -o wide")

                    steps.echo 'Kubernetes Service:'
                    steps.bat("kubectl get service ${serviceName} -n ${namespace}")

                    steps.echo "KUBERNETES ${environmentName.toUpperCase()} DEPLOYMENT COMPLETED SUCCESSFULLY"

                    return [
                            deploymentName: deploymentName,
                            namespace: namespace,
                            imageTag: imageTag,
                            previousImage: previousImage,
                            previousRevision: previousRevision,
                            gitCommit: gitCommit,
                            readyReplicas: readyReplicas.toInteger(),
                            availableReplicas: availableReplicas.toInteger(),
                            healthCheckPassed: true,
                            deployed: true
                    ]
                }
            }
        }
    }

    /**
     * Restores the previously deployed image and verifies that the restored
     * Deployment and application are healthy before reporting rollback success.
     */
    private void rollbackDeployment(
            String environmentName,
            String namespace,
            String deploymentName,
            String serviceName,
            String containerName,
            String previousImage,
            Integer expectedReplicas,
            Integer rolloutTimeoutSeconds,
            Integer healthCheckPort
    ) {

        steps.echo '================================================'
        steps.echo "STARTING ${environmentName.toUpperCase()} KUBERNETES DEPLOYMENT ROLLBACK"
        steps.echo '================================================'
        steps.echo "Restoring image: ${previousImage}"

        // Restore the previous known-good image.
        steps.bat(
                "kubectl set image deployment/${deploymentName} " +
                        "${containerName}=${previousImage} -n ${namespace}"
        )

        // Wait for the previous version to become available.
        steps.echo 'Waiting for rollback rollout...'
        steps.bat(
                "kubectl rollout status deployment/${deploymentName} " +
                        "-n ${namespace} --timeout=${rolloutTimeoutSeconds}s"
        )

        // Verify that the original image has been restored.
        def restoredImage = steps.bat(
                script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.spec.template.spec.containers[?(@.name=='${containerName}')].image}\"",
                returnStdout: true
        ).trim()

        if (restoredImage != previousImage) {
            steps.error("Rollback image mismatch. Expected ${previousImage}, found ${restoredImage}")
        }

        // Verify replica availability.
        def readyReplicas = steps.bat(
                script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.readyReplicas}\"",
                returnStdout: true
        ).trim()

        def availableReplicas = steps.bat(
                script: "@echo off\r\nkubectl get deployment ${deploymentName} -n ${namespace} -o jsonpath=\"{.status.availableReplicas}\"",
                returnStdout: true
        ).trim()

        if (!readyReplicas.isInteger() ||
                !availableReplicas.isInteger() ||
                readyReplicas.toInteger() != expectedReplicas ||
                availableReplicas.toInteger() != expectedReplicas) {
            steps.error(
                    "Rollback replica verification failed. Expected ${expectedReplicas}, " +
                            "ready=${readyReplicas}, available=${availableReplicas}"
            )
        }

        // Verify that the restored application responds through the Service.
        steps.echo 'Verifying restored application HTTP health...'
        verifyHttpHealth(namespace, serviceName, healthCheckPort, 'ROLLBACK')

        steps.echo 'Rollback image verified successfully.'
        steps.echo "Restored image: ${restoredImage}"
        steps.echo "Ready replicas: ${readyReplicas}"
        steps.echo "Available replicas: ${availableReplicas}"
        steps.echo "${environmentName.toUpperCase()} KUBERNETES DEPLOYMENT ROLLBACK COMPLETED"
    }

    /**
     * Checks readiness and liveness through a temporary kubectl port-forward.
     * The port-forward process is always stopped in the finally block.
     */
    private void verifyHttpHealth(
            String namespace,
            String serviceName,
            Integer healthCheckPort,
            String checkLabel
    ) {
        steps.powershell(
                script: """
\$ErrorActionPreference = 'Stop'
\$namespace = '${namespace}'
\$serviceName = '${serviceName}'
\$localPort = ${healthCheckPort}
\$forward = \$null

try {
    \$forward = Start-Process `
        -FilePath 'kubectl' `
        -ArgumentList @(
            'port-forward',
            '-n', \$namespace,
            \"service/\$serviceName\",
            \"\${localPort}:8080\",
            '--address=127.0.0.1'
        ) `
        -PassThru `
        -WindowStyle Hidden

    \$forwardReady = \$false

    for (\$i = 0; \$i -lt 30; \$i++) {
        if (\$forward.HasExited) {
            throw 'kubectl port-forward exited unexpectedly.'
        }

        try {
            \$null = Invoke-RestMethod `
                -Uri \"http://127.0.0.1:\$localPort/actuator/health/readiness\" `
                -TimeoutSec 3

            \$forwardReady = \$true
            break
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    if (-not \$forwardReady) {
        throw 'Application readiness HTTP check failed.'
    }

    Write-Host '${checkLabel} readiness endpoint: UP'

    \$null = Invoke-RestMethod `
        -Uri \"http://127.0.0.1:\$localPort/actuator/health/liveness\" `
        -TimeoutSec 5

    Write-Host '${checkLabel} liveness endpoint: UP'
    Write-Host '${checkLabel} HTTP HEALTH CHECKS PASSED'
}
finally {
    if (\$null -ne \$forward -and -not \$forward.HasExited) {
        Stop-Process -Id \$forward.Id -Force
        Write-Host 'Temporary port-forward stopped.'
    }
}
"""
        )
    }
}
