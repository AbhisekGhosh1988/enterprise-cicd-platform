def call(Map config = [:]) {
    def mavenBuild = new org.enterprise.cicd.MavenBuild(this)
    mavenBuild.run(config)
}