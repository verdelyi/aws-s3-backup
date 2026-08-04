plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("s3backup.Main")
    // The AWS CRT S3 client loads a native library. Without this, Java 24+ prints a
    // "restricted method has been called" warning on every run, and a future release
    // would refuse the call outright.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.50.3"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    implementation("software.amazon.awssdk.crt:aws-crt:0.48.3") // not managed by the bom apparently
    implementation("com.amazonaws:aws-encryption-sdk-java:3.0.2") // not managed by the bom apparently
    implementation("org.slf4j:slf4j-simple:2.0.18")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    //implementation("javax.xml.bind:jaxb-api:2.3.1") // for AWS SDK on Java 9+
    //implementation("org.bouncycastle:bcprov-jdk18on:1.81")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
