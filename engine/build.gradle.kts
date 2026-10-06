plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    // JDK 21 is what this machine provides; bytecode stays at 17 so the
    // Android app can keep consuming :engine without D8 surprises.
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
