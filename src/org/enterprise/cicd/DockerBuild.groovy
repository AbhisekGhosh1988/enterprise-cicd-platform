
package org.enterprise.cicd

class DockerBuild implements Serializable {

    private final def steps

    DockerBuild(def steps) {
        this.steps = steps
    }

    def run(Map config = [:]) {

        def workingDirectory = config.get('workingDirectory', '.')
        def imageName = config.get('imageName', 'cicd-demo')
        def imageRegistry = config.get('imageRegistry', 'ghcr.io')
        def dockerfile = config.get('dockerfile', 'Dockerfile')
        def jarPath = config.get(
                'jarPath',
                'target/cicd-demo-0.0.1-SNAPSHOT.jar'
        )
        def timeoutMinutes = config.get('timeoutMinutes', 15)

        // Publishing is opt-in. Existing validation jobs remain local-only.
        def publish = config.get('publish', false)
        def registryCredentialId = config.get(
                'registryCredentialId',
                'ghcr-publish-token'
        )

        steps.stage('Docker Image Build') {

            steps.timeout(time: timeoutMinutes, unit: 'MINUTES') {

                steps.dir(workingDirectory) {

                    steps.echo 'Starting Enterprise Docker Image Build'

                    if (!steps.fileExists(dockerfile)) {
                        steps.error("Dockerfile not found: ${dockerfile}")
                    }

                    if (!steps.fileExists(jarPath)) {
                        steps.error("Application JAR not found: ${jarPath}")
                    }

                    steps.echo 'Docker CLI version:'
                    steps.bat 'docker --version'

                    steps.echo 'Docker Engine connectivity:'
                    steps.bat 'docker version'

                    def gitCommit = steps.bat(
                            script: '@echo off\r\ngit rev-parse --short=12 HEAD',
                            returnStdout: true
                    ).trim()

                    steps.echo "Detected Git commit SHA: ${gitCommit}"

                    if (!(gitCommit ==~ /[0-9a-fA-F]{12}/)) {
                        steps.error('Invalid Git commit SHA')
                    }

                    def imageTag = "${imageRegistry}/${imageName}:${gitCommit}"

                    steps.echo "Building Docker image: ${imageTag}"

                    steps.bat "docker build -f \"${dockerfile}\" -t \"${imageTag}\" ."

                    steps.bat "docker image inspect \"${imageTag}\""

                    if (publish) {

                        steps.stage('Publish Docker Image to GHCR') {

                            steps.echo 'Authenticating with GitHub Container Registry'

                            steps.withCredentials([
                                    steps.usernamePassword(
                                            credentialsId: registryCredentialId,
                                            usernameVariable: 'GHCR_USERNAME',
                                            passwordVariable: 'GHCR_TOKEN'
                                    )
                            ]) {

                                try {

                                    steps.bat(
                                            script: '@echo off\r\necho %GHCR_TOKEN%| docker login ghcr.io -u %GHCR_USERNAME% --password-stdin'
                                    )

                                    steps.echo "Publishing Docker image: ${imageTag}"

                                    steps.bat "docker push \"${imageTag}\""

                                    steps.echo 'Verifying published image manifest'

                                    steps.bat "docker manifest inspect \"${imageTag}\""

                                    steps.echo 'GHCR IMAGE PUBLISHED AND VERIFIED SUCCESSFULLY'

                                } finally {

                                    steps.echo 'Logging out from GHCR'

                                    steps.bat(
                                            script: '@echo off\r\ndocker logout ghcr.io',
                                            returnStatus: true
                                    )
                                }
                            }
                        }
                    } else {
                        steps.echo 'GHCR publishing disabled. Image remains local.'
                    }

                    steps.echo 'DOCKER IMAGE BUILD COMPLETED SUCCESSFULLY'

                    return [
                            imageName: imageName,
                            imageTag: imageTag,
                            gitCommit: gitCommit,
                            published: publish
                    ]
                }
            }
        }
    }
}
