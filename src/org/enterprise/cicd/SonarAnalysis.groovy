
package org.enterprise.cicd

class SonarAnalysis implements Serializable {

    private final def steps

    SonarAnalysis(def steps) {
        this.steps = steps
    }

    def run(Map config = [:]) {

        def workingDirectory =
                config.get('workingDirectory', '.')

        def mavenWrapper =
                config.get('mavenWrapper', 'mvnw.cmd')

        def credentialId =
                config.get('credentialId', 'sonarcloud-token')

        def organization =
                config.get('organization', 'abhisekghosh1988')

        def projectKey =
                config.get(
                        'projectKey',
                        'AbhisekGhosh1988_cicd-demo'
                )

        def serverUrl =
                config.get('serverUrl', 'https://sonarcloud.io')

        def coverageReport =
                config.get(
                        'coverageReport',
                        'target/site/jacoco/jacoco.xml'
                )

        def pluginVersion =
                config.get('pluginVersion', '5.3.0.6276')

        def timeoutMinutes =
                config.get('timeoutMinutes', 15)

        def qualityGateTimeoutSeconds =
                config.get('qualityGateTimeoutSeconds', 300)

        if (!credentialId?.trim() ||
                !organization?.trim() ||
                !projectKey?.trim() ||
                !serverUrl?.trim()) {
            steps.error('SonarCloud configuration contains empty required values')
        }

        if (!(timeoutMinutes instanceof Number) ||
                timeoutMinutes <= 0) {
            steps.error('Sonar timeout must be positive')
        }

        if (!(qualityGateTimeoutSeconds instanceof Number) ||
                qualityGateTimeoutSeconds <= 0) {
            steps.error('Quality Gate timeout must be positive')
        }

        return steps.stage('SonarCloud Analysis and Quality Gate') {

            steps.echo 'Starting SonarCloud analysis'
            steps.echo "SonarCloud organization: ${organization}"
            steps.echo "SonarCloud project: ${projectKey}"

            steps.timeout(time: timeoutMinutes, unit: 'MINUTES') {

                steps.dir(workingDirectory) {

                    if (!steps.fileExists('pom.xml')) {
                        steps.error('Maven pom.xml not found')
                    }

                    if (!steps.fileExists(mavenWrapper)) {
                        steps.error("Maven Wrapper not found: ${mavenWrapper}")
                    }

                    if (!steps.fileExists(coverageReport)) {
                        steps.error("JaCoCo XML report not found: ${coverageReport}")
                    }

                    steps.withCredentials([
                            steps.string(
                                    credentialsId: credentialId,
                                    variable: 'SONAR_TOKEN'
                            )
                    ]) {

                        steps.withEnv([
                                "MAVEN_WRAPPER=${mavenWrapper}",
                                "SONAR_URL=${serverUrl}",
                                "SONAR_ORGANIZATION=${organization}",
                                "SONAR_PROJECT_KEY=${projectKey}",
                                "SONAR_COVERAGE_REPORT=${coverageReport}",
                                "SONAR_PLUGIN_VERSION=${pluginVersion}",
                                "SONAR_QG_TIMEOUT=${qualityGateTimeoutSeconds}"
                        ]) {

                            steps.echo 'Executing SonarScanner Maven analysis'

                            steps.bat('''
@echo off
call "%MAVEN_WRAPPER%" -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:%SONAR_PLUGIN_VERSION%:sonar -Dsonar.host.url="%SONAR_URL%" -Dsonar.organization="%SONAR_ORGANIZATION%" -Dsonar.projectKey="%SONAR_PROJECT_KEY%" -Dsonar.coverage.jacoco.xmlReportPaths="%SONAR_COVERAGE_REPORT%" -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=%SONAR_QG_TIMEOUT%
''')

                            steps.echo 'SONARCLOUD ANALYSIS AND QUALITY GATE PASSED'
                        }
                    }
                }
            }
        }
    }
}
