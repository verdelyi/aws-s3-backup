# aws-s3-backup

Encrypted backup & restore to Amazon AWS S3

## Prerequisites

* JDK 17+
* Gradle 8+

## Example configuration file

The config file is a single JSON file (credentials, settings, and batch items all in one place):

```json
{
  "aws": {
    "accessKey": ".............. (AWS credentials)",
    "secretKey": "................ (AWS credentials)",
    "bucketName": "............. (AWS S3 bucket)"
  },
  "encryptionKeyHex": ".......... (hex-encoded 32-byte AES key; run KEYGEN to generate one)",
  "storageClass": "............. (optional - S3 storage class, e.g. STANDARD; defaults to STANDARD_IA)",
  "batchItems": [
    { "command": "UPLOADFOLDERZIP", "localPath": "/xxxblahxxx/Documents", "remoteAWSPath": "Documents.zip", "encrypt": true },
    { "command": "UPLOADFOLDER", "localPath": "/xxxblahxxx/Photos", "remoteAWSPath": "photos", "encrypt": false },
    { "command": "UPLOADFILE", "localPath": "/xxxblahxxx/notes.txt", "remoteAWSPath": "notes.txt", "encrypt": true }
  ]
}
```

`batchItems` is only read by the `UPLOAD-BATCH` command; other commands ignore it. `storageClass` and `batchItems` are optional.

The encryption key is a hex-encoded 32-byte AES key, embedded directly in the config file (no separate key file). Run `KEYGEN` to print a freshly generated one to paste into `encryptionKeyHex`.

Temporary files (zips, ciphertext) are written to a `~/tmp-<random>` directory created at startup and deleted when the program exits, including on Ctrl-C.

## Integrity checking

Uploads are verified at each step, and any mismatch aborts the run:

* **Zipping** — every entry of a freshly created zip is read back and its CRC32 verified.
* **Encryption** — the encrypted file is decrypted to a discard sink before upload; AES-GCM's authentication tag fails on any corrupted ciphertext.
* **Upload** — uploads request a CRC64NVME checksum, which S3 computes over the whole object (even for multipart uploads), and it is compared against a locally computed one.

Corruption that already exists before these checks run (e.g. a source file misread while zipping) cannot be detected by them.

## How to run it locally

First, `cd` to the project directory.

Option 1: install on local system

* `./gradlew installDist` (installs into `./build/install/`)
* `build/install/aws-s3-backup/bin/aws-s3-backup <configfile> <command> <additional args>`

Option 2: Running directly via Gradle

* `./gradlew run --args "<configfile> <command> <additional args>"`

## Remote deployment

`scripts/deploy-remote.sh <target-host>` (or `scripts/deploy-remote.ps1 -TargetHost <target-host>` on Windows) builds a ZIP distribution and deploys it via ssh/scp, replacing whatever's at `~/aws-s3-backup` on the remote host.

## Available commands

- `KEYGEN`: Generate encryption keys
- `LIST`: List S3 objects
- `UPLOAD-BATCH`: Upload multiple files in batch
- `UPLOADFILE-ENCRYPT`: Upload and encrypt a file
- `UPLOADFILE-PLAINTEXT`: Upload a file without encryption
- `UPLOADFILE-PLAINTEXT-NOCREDS`: Upload without credentials (e.g., EC2 instances may have automatic access to S3)
- `DOWNLOAD`: Download a file from S3
- `DELETE`: Delete an object from S3

## Usage examples

* Upload one file in plaintext:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> UPLOADFILE-PLAINTEXT hello.txt dir/remote-hello.txt`

* Download one file to current directory:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> download dir/remote-hello.txt .`

* Batch upload (reads `batchItems` from the config file):
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> UPLOAD-BATCH`

* Delete an object from S3:
  `build/install/aws-s3-backup/bin/aws-s3-backup <CONFIG_FILE_PATH> DELETE dir/remote-hello.txt`
