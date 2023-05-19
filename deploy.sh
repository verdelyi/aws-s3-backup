#!/bin/bash
set -e

# Target host to deploy to. Reasonable options are "nsf-jst-cloudserver" and "nsf-jst-localserver"
TARGETHOST="$1"

echo "Building AWS S3 backup app..."
./gradlew distZip

echo "clean & recreate remote directory"
ssh $TARGETHOST "rm -rf aws-s3-backup && mkdir -p aws-s3-backup"

echo "copy distribution zip"
scp build/distributions/aws-s3-backup.zip "$TARGETHOST:aws-s3-backup/"

echo "unpack"
ssh $TARGETHOST "unzip -q aws-s3-backup/aws-s3-backup.zip && rm aws-s3-backup/aws-s3-backup.zip"
