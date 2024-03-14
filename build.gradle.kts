plugins {
    kotlin("jvm") version "1.9.23"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("s3backup.Main")
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.20.66"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    implementation("software.amazon.awssdk.crt:aws-crt:0.21.16")
    implementation("com.amazonaws:aws-encryption-sdk-java:2.4.0")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    //implementation("javax.xml.bind:jaxb-api:2.3.1") // for AWS SDK on Java 9+
    implementation("org.bouncycastle:bcprov-ext-jdk18on:1.77")

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

tasks {
    compileKotlin { kotlinOptions.jvmTarget = "17" }
    compileTestKotlin { kotlinOptions.jvmTarget = "17" }
    //build { dependsOn(fatJar) }
}