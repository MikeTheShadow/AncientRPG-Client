plugins { id("net.fabricmc.fabric-loom") version "1.17.21" }

val demoFabricApi by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    implementation(project(":protocol"))
    include(project(":protocol"))
    demoFabricApi("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
}
tasks.processResources {
    val releaseVersion = project.version.toString()
    inputs.property("version", releaseVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to releaseVersion)
    }
}

tasks.register<Sync>("prepareDemoMods") {
    from(tasks.named("jar"))
    from(demoFabricApi)
    into(layout.buildDirectory.dir("demo-mods"))
}
