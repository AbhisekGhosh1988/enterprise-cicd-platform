
package org.enterprise.cicd

import groovy.util.XmlSlurper

class CoverageValidator implements Serializable {

    private final def steps

    CoverageValidator(def steps) {
        this.steps = steps
    }

    def validate(Map config = [:]) {

        def reportPath = config.get(
                'reportPath',
                'target/site/jacoco/jacoco.xml'
        )

        def policy = config.get('policy')

        if (!(policy instanceof Map)) {
            steps.error('Coverage policy must be supplied as a Map')
        }

        def enabled = policy.enabled
        def minimumLine = policy.minimumLine
        def minimumBranch = policy.minimumBranch
        def failMissing = policy.failMissing
        def failBelow = policy.failBelow

        if (!(enabled instanceof Boolean) ||
                !(failMissing instanceof Boolean) ||
                !(failBelow instanceof Boolean)) {
            steps.error('Coverage policy contains invalid Boolean settings')
        }

        if (!(minimumLine instanceof Number) ||
                minimumLine < 0 || minimumLine > 100 ||
                !(minimumBranch instanceof Number) ||
                minimumBranch < 0 || minimumBranch > 100) {
            steps.error('Coverage policy contains invalid thresholds')
        }

        if (!enabled) {
            steps.echo 'Code coverage validation is disabled by policy'
            return [skipped: true]
        }

        return steps.stage('Code Coverage Validation') {

            steps.echo 'Starting JaCoCo coverage validation'

            if (!steps.fileExists(reportPath)) {

                if (failMissing) {
                    steps.error("JaCoCo report not found: ${reportPath}")
                }

                steps.echo "WARNING: JaCoCo report not found: ${reportPath}"
                steps.echo 'Coverage validation skipped because policy allows a missing report'

                return [skipped: true, reason: 'Missing JaCoCo report']
            }

            def reportXml = steps.readFile(reportPath)

            def xml = new XmlSlurper(false, false).parseText(reportXml)

            def lineCounter = xml.counter.find {
                it.@type.text() == 'LINE'
            }

            def branchCounter = xml.counter.find {
                it.@type.text() == 'BRANCH'
            }

            if (!lineCounter) {
                steps.error('LINE counter missing from JaCoCo report')
            }

            if (!branchCounter) {
                steps.error('BRANCH counter missing from JaCoCo report')
            }

            long lineMissed = lineCounter.@missed.toString().toLong()
            long lineCovered = lineCounter.@covered.toString().toLong()

            long branchMissed = branchCounter.@missed.toString().toLong()
            long branchCovered = branchCounter.@covered.toString().toLong()

            long totalLines = lineMissed + lineCovered
            long totalBranches = branchMissed + branchCovered

            if (totalLines == 0) {
                steps.error('JaCoCo report contains zero executable lines')
            }

            double lineCoverage =
                    (lineCovered * 100.0) / totalLines

            double branchCoverage = totalBranches == 0
                    ? 100.0
                    : (branchCovered * 100.0) / totalBranches

            def formattedLine = String.format(
                    java.util.Locale.ROOT, '%.2f', lineCoverage
            )

            def formattedBranch = String.format(
                    java.util.Locale.ROOT, '%.2f', branchCoverage
            )

            steps.echo "Line coverage: ${formattedLine}%"
            steps.echo "Branch coverage: ${formattedBranch}%"

            steps.echo "Required line coverage: ${minimumLine}%"
            steps.echo "Required branch coverage: ${minimumBranch}%"

            boolean lineFailed = lineCoverage < minimumLine
            boolean branchFailed = branchCoverage < minimumBranch

            if (lineFailed || branchFailed) {

                def failures = []

                if (lineFailed) {
                    failures.add(
                            "LINE coverage ${formattedLine}% is below ${minimumLine}%"
                    )
                }

                if (branchFailed) {
                    failures.add(
                            "BRANCH coverage ${formattedBranch}% is below ${minimumBranch}%"
                    )
                }

                if (failBelow) {
                    steps.error(
                            "CODE COVERAGE FAILED: ${failures.join('; ')}"
                    )
                }

                steps.echo "WARNING: ${failures.join('; ')}"
                steps.echo 'Coverage is below policy thresholds, but policy allows the build to continue'

            } else {
                steps.echo 'CODE COVERAGE VALIDATION PASSED'
            }

            return [
                    skipped        : false,
                    passed         : !lineFailed && !branchFailed,
                    lineCoverage   : lineCoverage,
                    branchCoverage : branchCoverage,
                    lineCovered    : lineCovered,
                    lineMissed     : lineMissed,
                    branchCovered  : branchCovered,
                    branchMissed   : branchMissed
            ]
        }
    }
}
