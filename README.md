# aws-s3-backup

Encrypted backup & restore to Amazon AWS S3

## Prerequisites

* JDK 17+
* Gradle 8+

## Example configuration file

The config file should look like this:

```
aws.accessKey=.............. (AWS credentials)
aws.secretKey=................ (AWS credentials)
aws.s3.bucketName=............. (AWS S3 bucket)
config.temporaryZipFilePath=......... (file path for temp file. At least the directory should exist.)
config.temporaryEncryptedFilePath=......... (file path for temp file. At least the directory should exist.)
config.encryptionKeyFile=.......... (path to encryption key file)
```

The encryption key file holds the "password" (ideally a random byte array).

## How to run it locally

First, `cd` to the project directory.

Option 1: install on local system

* `./gradlew installDist` (installs into `./build/install/`)
* `build/install/aws-s3-backup/bin/aws-s3-backup <configfile> <command> <additional args>`

Option 2: Running directly via Gradle

* `./gradlew run --args "<configfile> <command> <additional args>"`

## Remote deployment

1. Make ZIP distribution: `./gradlew distZip`
2. scp to remote server and unzip

## Available commands

- `KEYGEN`: Generate encryption keys
- `LIST`: List S3 objects
- `UPLOAD-BATCH`: Upload multiple files in batch
- `UPLOADFILE-ENCRYPT`: Upload and encrypt a file
- `UPLOADFILE-PLAINTEXT`: Upload a file without encryption
- `UPLOADFILE-PLAINTEXT-NOCREDS`: Upload without credentials (e.g., EC2 instances may have automatic access to S3)
- `DOWNLOAD`: Download a file from S3

## Usage examples

* Upload one file in plaintext:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> UPLOADFILE-PLAINTEXT hello.txt dir/remote-hello.txt`

* Download one file to current directory:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> download dir/remote-hello.txt .`

* Batch upload:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> UPLOAD-BATCH "G:/aws-s3-private/backup-private.conf"`
