
import org.enterprise.cicd.MavenBuild
import org.enterprise.cicd.CoverageValidator
import org.enterprise.cicd.SonarAnalysis

def call(Map config = [:]) {

    def workingDirectory = config.get('workingDirectory', '.')
    def mavenWrapper = config.get('mavenWrapper', 'mvnw.cmd')
    def mavenGoals = config.get('mavenGoals', 'clean verify')
    def timeoutMinutes = config.get('timeoutMinutes', 30)

    def coverageReport = config.get(
            'coverageReport',
            'target/site/jacoco/jacoco.xml'
    )

    def sonarEnabled = config.get('sonarEnabled', true)

    stage('Enterprise CI Pipeline') {

        echo 'Starting Enterprise CI Pipeline'

        echo '======================================'
        echo 'PHASE 1: MAVEN BUILD AND UNIT TESTS'
        echo '======================================'

        def buildComponent = new MavenBuild(this)

        buildComponent.run([
                workingDirectory: workingDirectory,
                mavenWrapper: mavenWrapper,
                mavenGoals: mavenGoals,
                timeoutMinutes: timeoutMinutes
        ])

        echo '======================================'
        echo 'PHASE 2: CODE COVERAGE VALIDATION'
        echo '======================================'

        def policy = coveragePolicy()

        echo "Coverage enabled: ${policy.enabled}"
        echo "Minimum line coverage: ${policy.minimumLine}%"
        echo "Minimum branch coverage: ${policy.minimumBranch}%"

        def validator = new CoverageValidator(this)

        def coverageResult = validator.validate([
                reportPath: coverageReport,
                policy: policy
        ])

        def sonarResult = [skipped: true]

        if (sonarEnabled) {

            echo '======================================'
            echo 'PHASE 3: SONARCLOUD ANALYSIS'
            echo '======================================'

            def sonarComponent = new SonarAnalysis(this)

            sonarResult = sonarComponent.run([
                    workingDirectory: workingDirectory,
                    mavenWrapper: mavenWrapper,
                    coverageReport: coverageReport,
                    timeoutMinutes: 15,
                    qualityGateTimeoutSeconds: 300
            ])

        } else {
            echo 'SonarCloud analysis disabled by pipeline configuration'
        }

        echo '======================================'
        echo 'ENTERPRISE CI PIPELINE COMPLETED'
        echo '======================================'

        return [
                buildPassed: true,
                coverage: coverageResult,
                sonar: sonarResult
        ]
    }
}
