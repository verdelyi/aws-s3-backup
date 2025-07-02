param([string]$TargetHost)
$ErrorActionPreference = "Stop"
Write-Host "Building AWS S3 backup app..."
./gradlew distZip
Write-Host "Deploying to $TargetHost..."
ssh $TargetHost "rm -rf aws-s3-backup && mkdir aws-s3-backup"
scp build/distributions/aws-s3-backup.zip "${TargetHost}:aws-s3-backup/"
ssh $TargetHost "cd aws-s3-backup && unzip -q aws-s3-backup.zip && rm aws-s3-backup.zip"