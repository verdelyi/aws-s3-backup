import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "2.0.0"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("s3backup.Main")
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.26.21"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    implementation("software.amazon.awssdk.crt:aws-crt:0.30.0") // not managed by the bom apparently
    implementation("com.amazonaws:aws-encryption-sdk-java:3.0.1") // not managed by the bom apparently
    implementation("org.slf4j:slf4j-simple:2.0.7")
    //implementation("javax.xml.bind:jaxb-api:2.3.1") // for AWS SDK on Java 9+
    implementation("org.bouncycastle:bcprov-ext-jdk18on:1.78")

}

/*val fatJar = task("fatJar", type = Jar::class) {
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(configurations.compileClasspath.map { config -> config.map { if (it.isDirectory) it else zipTree(it) } })
    with(tasks["jar"] as CopySpec)
}*/

/*val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(configurations.compileClasspath.map { config -> config.map { if (it.isDirectory) it else zipTree(it) } })
}*/

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
