# aws-s3-backup

## Prerequisites
* JDK (11, 15, etc.)
* Gradle (7.x?)

## Configuration
The location of the configuration file is taken from the JVM system property 
```config.properties.path```, defaulting to
```./config.properties``` if the property is not set. The file should look like this:

```
aws.accessKey=....................
aws.secretKey=....................
aws.s3.bucketName=........
config.publicKeyFile=....backup-public-key.dat
config.privateKeyFile=.....backup-private-key.dat
config.temporaryZipPath=....../temp.zip
```

## Deployment:
* Make ZIP distribution: ```./gradlew distZip```
* Install: ```./gradlew installDist```
* Run: ```build/install/aws-s3-backup/bin/aws-s3-backup <command> <commandargs>```

## Running directly via Gradle:
```./gradlew run --args "<command> <commandargs>"```

## Commands
See ```s3backup.Main``` for the list of commands.

## Examples
Backup (batch mode):
```build/install/aws-s3-backup/bin/aws-s3-backup "-Dconfig.properties.path=G:\aws-s3-private\config-private.properties" upload-batch "G:/aws-s3-private/backup-private.conf"```

Download file:
```build/install/aws-s3-backup/bin/aws-s3-backup "-Dconfig.properties.path=G:\aws-s3-private\config-private.properties" download blah.zip .```
