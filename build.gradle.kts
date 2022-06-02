plugins {
    kotlin("jvm") version "1.6.21"
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("s3backup.Main")
}

dependencies {
    //implementation(platform("com.amazonaws:aws-java-sdk-bom:1.12.141")) // 1.12.89 // 1.11.1016 // 1.11.923
    implementation(platform("software.amazon.awssdk:bom:2.17.203"))
    //implementation("com.amazonaws:aws-java-sdk-s3")
    implementation("software.amazon.awssdk:s3")

    implementation("com.amazonaws:aws-encryption-sdk-java:2.4.0")

    //implementation("javax.xml.bind:jaxb-api:2.3.1") // for AWS SDK on Java 9+
    implementation("org.bouncycastle:bcprov-ext-jdk18on:1.71")

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
    compileKotlin { kotlinOptions.jvmTarget = "11" }
    compileTestKotlin { kotlinOptions.jvmTarget = "11" }
    //build { dependsOn(fatJar) }
}