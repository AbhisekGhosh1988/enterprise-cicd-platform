
package org.enterprise.cicd

class MavenBuild implements Serializable {

    private final def steps

    MavenBuild(def steps) {
        this.steps = steps
    }

    def run(Map config = [:]) {

        def workingDirectory = config.get('workingDirectory', '.')
        def mavenWrapper = config.get('mavenWrapper', 'mvnw.cmd')
        def mavenGoals = config.get('mavenGoals', 'clean verify')
        def timeoutMinutes = config.get('timeoutMinutes', 30)

        steps.stage('Maven Build and Unit Tests') {

            steps.timeout(time: timeoutMinutes, unit: 'MINUTES') {

                steps.dir(workingDirectory) {

                    steps.echo 'Starting Enterprise Maven Build'

                    if (!steps.fileExists('pom.xml')) {
                        steps.error(
                                "Maven project not found: pom.xml in ${workingDirectory}"
                        )
                    }

                    if (!steps.fileExists(mavenWrapper)) {
                        steps.error(
                                "Maven Wrapper not found: ${mavenWrapper}"
                        )
                    }

                    steps.echo 'Java version:'
                    steps.bat 'java --version'

                    steps.echo 'Maven Wrapper version:'
                    steps.bat "call \"${mavenWrapper}\" --version"

                    steps.echo "Executing Maven goals: ${mavenGoals}"

                    steps.bat "call \"${mavenWrapper}\" -B -ntp ${mavenGoals}"

                    steps.echo 'MAVEN BUILD COMPLETED SUCCESSFULLY'
                }
            }
        }
    }
}
