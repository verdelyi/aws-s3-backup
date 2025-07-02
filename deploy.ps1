param([string]$TargetHost)
$ErrorActionPreference = "Stop"
Write-Host "Building AWS S3 backup app..."
./gradlew distZip
Write-Host "Deploying to $TargetHost..."
ssh $TargetHost "rm -rf aws-s3-backup"
scp build/distributions/aws-s3-backup.zip "${TargetHost}:"
ssh $TargetHost "unzip -q aws-s3-backup.zip && rm aws-s3-backup.zip"