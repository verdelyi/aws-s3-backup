#!/bin/bash
set -e
TARGETHOST="$1"
echo "Building AWS S3 backup app..."
./gradlew distZip
echo "Deploying to $TARGETHOST..."
ssh "$TARGETHOST" "rm -rf aws-s3-backup && mkdir aws-s3-backup"
scp build/distributions/aws-s3-backup.zip "$TARGETHOST:aws-s3-backup/"
ssh "$TARGETHOST" "cd aws-s3-backup && unzip -q aws-s3-backup.zip && rm aws-s3-backup.zip"