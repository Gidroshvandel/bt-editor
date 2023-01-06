import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("java-library")
    kotlin("jvm")
}

sourceSets {
    named("main") {
        java.setSrcDirs(
            listOf("src/main/java")
        )
    }
}

dependencies {
    implementation(kotlin("stdlib", KotlinCompilerVersion.VERSION))

    implementation("com.badlogicgames.gdx:gdx:${properties["version.gdx"]}")
    implementation("com.kotcrab.vis:vis-ui:${properties["version.visui"]}")
    implementation("com.badlogicgames.gdx:gdx-ai:${properties["version.gdx.ai"]}")
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
