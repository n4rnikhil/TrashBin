<#
.SYNOPSIS
  Restores a backup made by backup.ps1, replacing the current database contents and stored files.
.DESCRIPTION
  Destructive: everything the app's database role owns in the target database is dropped, and the
  current blobs folder is moved aside to blobs.pre-restore-<timestamp> (delete it yourself once the
  restore is verified). Requires -Force. Stop the backend first. Ends with verify-storage.ps1.
.EXAMPLE
  .\scripts\restore.ps1 -BackupPath .\backups\trashbin-20261116-101500 -Force
#>
param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\backend\.env'),
    [string]$PgBin = $env:TRASHBIN_PG_BIN,
    [string]$HealthUrl = 'http://localhost:8080/api/v1/health',
    [switch]$Force
)

. (Join-Path $PSScriptRoot 'TrashBinConfig.ps1')

$config = Read-TrashBinConfig -EnvFile $EnvFile
$dump = Join-Path $BackupPath 'database.dump'
$backupBlobs = Join-Path $BackupPath 'blobs'
$manifest = Get-Content (Join-Path $BackupPath 'manifest.json') -Raw | ConvertFrom-Json
if (-not (Test-Path $dump) -or -not (Test-Path $backupBlobs)) { throw "$BackupPath is not a backup folder" }
if ((Get-FileHash $dump -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.databaseSha256) {
    throw 'database.dump does not match its manifest checksum; refusing to restore'
}

if (-not $Force) {
    Write-Host "This replaces database '$($config.DbName)' and the files under $($config.StorageRoot)"
    Write-Host "with the backup from $($manifest.createdAt). Re-run with -Force to proceed."
    exit 1
}

Assert-BackendStopped -HealthUrl $HealthUrl
$psql = Get-PgTool -Name 'psql' -PgBin $PgBin
$pgRestore = Get-PgTool -Name 'pg_restore' -PgBin $PgBin
$conn = Get-PgConnectionArgs $config

Write-Host "Clearing database '$($config.DbName)'..."
Invoke-Pg -Tool $psql -Config $config -Arguments ($conn + @(
        '-d', $config.DbName, '-v', 'ON_ERROR_STOP=1', '-c', "DROP OWNED BY CURRENT_USER")) | Out-Null

Write-Host 'Restoring database...'
Invoke-Pg -Tool $pgRestore -Config $config -Arguments ($conn + @(
        '-d', $config.DbName, '--no-owner', '--no-acl', '--exit-on-error', $dump)) | Out-Null

Write-Host 'Restoring stored files...'
$blobs = Join-Path $config.StorageRoot 'blobs'
if (Test-Path $blobs) {
    $aside = "$blobs.pre-restore-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item $blobs $aside
    Write-Host "Previous files kept at $aside"
}
New-Item -ItemType Directory -Force $blobs, (Join-Path $config.StorageRoot 'staging') | Out-Null
robocopy $backupBlobs $blobs /E /COPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy failed with exit code $LASTEXITCODE" }

& (Join-Path $PSScriptRoot 'verify-storage.ps1') -EnvFile $EnvFile -PgBin $PgBin
if ($LASTEXITCODE -ne 0) { throw 'Restore finished but verification found problems (see above)' }
Write-Host 'Restore complete and verified. Start the backend.'
