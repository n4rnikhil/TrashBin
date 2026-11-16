<#
.SYNOPSIS
  Backs up the Trash Bin database and stored files into one timestamped folder.
.DESCRIPTION
  Stop the backend first: the database dump and the blob copy must describe the same moment.
  Writes <Destination>\trashbin-<timestamp>\ with database.dump (pg_dump custom format), blobs\
  and manifest.json. See docs/OPERATIONS.md.
.EXAMPLE
  .\scripts\backup.ps1 -PgBin "C:\Program Files\PostgreSQL\17\bin"
#>
param(
    [string]$Destination = (Join-Path $PSScriptRoot '..\backups'),
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\backend\.env'),
    [string]$PgBin = $env:TRASHBIN_PG_BIN,
    [string]$HealthUrl = 'http://localhost:8080/api/v1/health'
)

. (Join-Path $PSScriptRoot 'TrashBinConfig.ps1')

$config = Read-TrashBinConfig -EnvFile $EnvFile
Assert-BackendStopped -HealthUrl $HealthUrl
$pgDump = Get-PgTool -Name 'pg_dump' -PgBin $PgBin

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $Destination "trashbin-$stamp"
New-Item -ItemType Directory -Force $target | Out-Null

Write-Host "Dumping database '$($config.DbName)'..."
$dump = Join-Path $target 'database.dump'
Invoke-Pg -Tool $pgDump -Config $config -Arguments ((Get-PgConnectionArgs $config) + @(
        '-d', $config.DbName, '-Fc', '--no-owner', '--no-acl', '-f', $dump)) | Out-Null

Write-Host 'Copying stored files...'
$sourceBlobs = Join-Path $config.StorageRoot 'blobs'
$targetBlobs = Join-Path $target 'blobs'
New-Item -ItemType Directory -Force $targetBlobs | Out-Null
if (Test-Path $sourceBlobs) {
    robocopy $sourceBlobs $targetBlobs /E /COPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }
}

$files = @(Get-ChildItem $targetBlobs -Recurse -File)
$bytes = ($files | Measure-Object Length -Sum).Sum
if (-not $bytes) { $bytes = 0 }
[pscustomobject]@{
    createdAt      = (Get-Date).ToUniversalTime().ToString('o')
    database       = $config.DbName
    databaseSha256 = (Get-FileHash $dump -Algorithm SHA256).Hash.ToLowerInvariant()
    blobCount      = $files.Count
    blobBytes      = $bytes
} | ConvertTo-Json | Set-Content (Join-Path $target 'manifest.json') -Encoding ASCII

Write-Host "Backup complete: $target ($($files.Count) files, $bytes bytes)"
