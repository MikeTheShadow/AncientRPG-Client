import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    base
    id("com.diffplug.spotless") version "8.0.0" apply false
}

allprojects {
    group = "dev.ancientrpg"
    version = providers.gradleProperty("ancientrpgRelease").get()
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }
    extensions.configure<SpotlessExtension> {
        java { googleJavaFormat("1.28.0") }
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
}

tasks.named("check") { dependsOn(subprojects.map { it.tasks.named("check") }) }
tasks.named("build") { dependsOn(subprojects.map { it.tasks.named("build") }) }
