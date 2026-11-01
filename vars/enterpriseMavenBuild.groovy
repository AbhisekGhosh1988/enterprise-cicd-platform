import org.enterprise.cicd.MavenBuild

def call(Map config = [:]) {
    def mavenBuild = new MavenBuild(this)
    mavenBuild.run(config)
}