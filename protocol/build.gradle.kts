// Pure Java wire contracts and public assets. No Fabric or server dependencies.
tasks.processResources {
    val releaseVersion = project.version.toString()
    inputs.property("version", releaseVersion)
    filesMatching("ancientrpg/release.txt") { expand("version" to releaseVersion) }
}
