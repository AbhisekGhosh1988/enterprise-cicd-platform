def call(Map config = [:]) {

    def workingDirectory = config.get('workingDirectory', '.')
    def mavenWrapper = config.get('mavenWrapper', 'mvnw.cmd')
    def mavenGoals = config.get('mavenGoals', 'clean verify')
    def timeoutMinutes = config.get('timeoutMinutes', 30)

    stage('Maven Build and Unit Tests') {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            dir(workingDirectory) {

                echo 'Starting Enterprise Maven Build'

                if (!fileExists('pom.xml')) {
                    error "Maven project not found: pom.xml in ${workingDirectory}"
                }

                if (!fileExists(mavenWrapper)) {
                    error "Maven Wrapper not found: ${mavenWrapper}"
                }

                echo 'Java version:'
                bat 'java --version'

                echo 'Maven Wrapper version:'
                bat "call \"${mavenWrapper}\" --version"

                echo "Executing Maven goals: ${mavenGoals}"

                bat "call \"${mavenWrapper}\" -B -ntp ${mavenGoals}"

                echo 'MAVEN BUILD COMPLETED SUCCESSFULLY'
            }
        }
    }
}