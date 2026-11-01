
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
                config.get(
                        'pluginVersion',
                        '5.3.0.6276'
                )

        def timeoutMinutes =
                config.get('timeoutMinutes', 15)

        def qualityGateTimeoutSeconds =
                config.get('qualityGateTimeoutSeconds', 300)

        if (!credentialId?.trim() ||
                !organization?.trim() ||
                !projectKey?.trim() ||
                !serverUrl?.trim()) {

            steps.error(
                    'SonarCloud configuration contains empty required values'
            )
        }

        if (!(timeoutMinutes instanceof Number) ||
                timeoutMinutes <= 0) {

            steps.error('Sonar timeout must be a positive number')
        }

        if (!(qualityGateTimeoutSeconds instanceof Number) ||
                qualityGateTimeoutSeconds <= 0) {

            steps.error(
                    'Quality Gate timeout must be a positive number'
            )
        }

        return steps.stage('SonarCloud Analysis and Quality Gate') {

            steps.echo 'Starting SonarCloud analysis'

            steps.echo "SonarCloud organization: ${organization}"
            steps.echo "SonarCloud project: ${projectKey}"
            steps.echo "SonarCloud server: ${serverUrl}"

            steps.timeout(
                    time: timeoutMinutes,
                    unit: 'MINUTES'
            ) {

                steps.dir(workingDirectory) {

                    if (!steps.fileExists('pom.xml')) {
                        steps.error('Maven pom.xml not found')
                    }

                    if (!steps.fileExists(mavenWrapper)) {
                        steps.error(
                                "Maven Wrapper not found: ${mavenWrapper}"
                        )
                    }

                    if (!steps.fileExists(coverageReport)) {
                        steps.error(
                                "JaCoCo XML report not found: ${coverageReport}"
                        )
                    }

                    steps.withCredentials([
                            steps.string(
                                    credentialsId: credentialId,
                                    variable: 'SONAR_TOKEN'
                            )
                    ]) {

                        steps.echo 'Executing SonarCloud scanner'

                        steps.bat(
                                """@echo off
call "${mavenWrapper}" -B -ntp org.sonarsource.scanner.maven:sonar-maven-plugin:${pluginVersion}:sonar -Dsonar.host.url=${serverUrl} -Dsonar.organization=${organization} -Dsonar.projectKey=${projectKey} -Dsonar.coverage.jacoco.xmlReportPaths=${coverageReport} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=${qualityGateTimeoutSeconds}
"""
                        )

                        steps.echo(
                                'SONARCLOUD ANALYSIS AND QUALITY GATE PASSED'
                        )
                    }
                }
            }
        }
    }
}
