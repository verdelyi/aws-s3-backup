plugins {
    kotlin("jvm") version "2.2.0"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("s3backup.Main")
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.31.70"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    implementation("software.amazon.awssdk.crt:aws-crt:0.38.5") // not managed by the bom apparently
    implementation("com.amazonaws:aws-encryption-sdk-java:3.0.2") // not managed by the bom apparently
    implementation("org.slf4j:slf4j-simple:2.0.17")
    //implementation("javax.xml.bind:jaxb-api:2.3.1") // for AWS SDK on Java 9+
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
}

kotlin {
    jvmToolchain(21)
}
