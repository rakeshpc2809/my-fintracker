plugins {
    kotlin("jvm") version "2.0.0"
}

dependencies {
    implementation(project(":tax-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    testImplementation(kotlin("test"))
}
