# aws-s3-backup

Encrypted backup & restore to Amazon AWS S3

## Prerequisites
* JDK 11
* Gradle 7.4

Newer versions might also work.

## Configuration file
The location of the configuration file is taken from the JVM system property 
```config.properties.path```, defaulting to
```./config.properties``` if the property is not set. The file should look like this:

```
aws.accessKey=..............
aws.secretKey=................
aws.s3.bucketName=.............
config.temporaryZipFilePath=/tmp/aws-s3-backup.zip
config.temporaryEncryptedFilePath=/tmp/aws-s3-backup.enc
config.encryptionKeyFile=......encryption-202206.key
```

## Deploy/run
* Install: ```./gradlew installDist```
* Run installed: ```build/install/aws-s3-backup/bin/aws-s3-backup <command> <commandargs>```
* Make ZIP distribution: ```./gradlew distZip```
* Running directly via Gradle: ```./gradlew run --args "<command> <commandargs>"```

## Available commands
See the ```s3backup.Main``` class for the list of commands. Basically keygen, upload, batch-upload, download.

## Examples
Upload one file in plaintext:
```build/install/aws-s3-backup/bin/aws-s3-backup UPLOADFILEANDDELETE-PLAINTEXT hello.txt remote-hello.txt```

Batch upload (backup some locations):
```build/install/aws-s3-backup/bin/aws-s3-backup "-Dconfig.properties.path=G:\aws-s3-private\config-private.properties" upload-batch "G:/aws-s3-private/backup-private.conf"```

Download one file to current directory:
```build/install/aws-s3-backup/bin/aws-s3-backup "-Dconfig.properties.path=G:\aws-s3-private\config-private.properties" download blah.zip .```
