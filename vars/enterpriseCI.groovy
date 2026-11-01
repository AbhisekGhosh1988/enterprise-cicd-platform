
import org.enterprise.cicd.MavenBuild
import org.enterprise.cicd.CoverageValidator

def call(Map config = [:]) {

    def workingDirectory = config.get('workingDirectory', '.')
    def mavenWrapper = config.get('mavenWrapper', 'mvnw.cmd')
    def mavenGoals = config.get('mavenGoals', 'clean verify')
    def timeoutMinutes = config.get('timeoutMinutes', 30)
    def coverageReport = config.get(
            'coverageReport',
            'target/site/jacoco/jacoco.xml'
    )

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

        echo '======================================'
        echo 'ENTERPRISE CI PIPELINE COMPLETED'
        echo '======================================'

        return [
                buildPassed: true,
                coverage: coverageResult
        ]
    }
}
