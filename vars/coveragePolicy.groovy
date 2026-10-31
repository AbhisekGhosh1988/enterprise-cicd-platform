def load(String policyFile = 'policies/coverage-policy.yml') {
    if (!fileExists(policyFile)) {
        error "Coverage policy file not found: ${policyFile}"
    }

    def policy = readYaml(file: policyFile)

    if (!(policy?.coverage instanceof Map)) {
        error "Invalid coverage policy: missing coverage configuration."
    }

    def coverage = policy.coverage

    if (!(coverage.enabled instanceof Boolean)) {
        error "Invalid coverage policy: coverage.enabled must be true or false."
    }

    if (!(coverage.minimum instanceof Map)) {
        error "Invalid coverage policy: coverage.minimum must be defined."
    }

    def lineThreshold = coverage.minimum.line
    def branchThreshold = coverage.minimum.branch

    if (!(lineThreshold instanceof Number) ||
        lineThreshold < 0 || lineThreshold > 100) {
        error "Invalid line coverage threshold: ${lineThreshold}"
    }

    if (!(branchThreshold instanceof Number) ||
        branchThreshold < 0 || branchThreshold > 100) {
        error "Invalid branch coverage threshold: ${branchThreshold}"
    }

    if (!(coverage.failure instanceof Map)) {
        error "Invalid coverage policy: coverage.failure must be defined."
    }

    def failurePolicy = coverage.failure
    def failMissing = failurePolicy.get('missing-report')
    def failBelow = failurePolicy.get('below-threshold')

    if (!(failMissing instanceof Boolean)) {
        error "Invalid policy: failure.missing-report must be true or false."
    }

    if (!(failBelow instanceof Boolean)) {
        error "Invalid policy: failure.below-threshold must be true or false."
    }

    return [
        enabled       : coverage.enabled,
        minimumLine   : lineThreshold,
        minimumBranch : branchThreshold,
        failMissing   : failMissing,
        failBelow     : failBelow
    ]
}
