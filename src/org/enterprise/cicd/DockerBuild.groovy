
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

                    steps.echo 'DOCKER IMAGE BUILD COMPLETED SUCCESSFULLY'

                    return [
                            imageName: imageName,
                            imageTag: imageTag,
                            gitCommit: gitCommit
                    ]
                }
            }
        }
    }
}
