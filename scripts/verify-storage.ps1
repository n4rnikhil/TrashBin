<#
.SYNOPSIS
  Checks every stored file against the SHA-256 recorded at upload, for all users.
.DESCRIPTION
  Read-only. Reports missing and altered blobs and exits 1 if there are any. Run it after a restore
  or whenever disk trouble is suspected; per-file checks are also available in the app.
#>
param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\backend\.env'),
    [string]$PgBin = $env:TRASHBIN_PG_BIN
)

. (Join-Path $PSScriptRoot 'TrashBinConfig.ps1')

$config = Read-TrashBinConfig -EnvFile $EnvFile
$psql = Get-PgTool -Name 'psql' -PgBin $PgBin
$rows = Invoke-Pg -Tool $psql -Config $config -Arguments ((Get-PgConnectionArgs $config) + @(
        '-d', $config.DbName, '-tA', '-F', ',', '-c', 'SELECT id, storage_key, sha256 FROM archived_files'))

$blobs = Join-Path $config.StorageRoot 'blobs'
$ok = 0; $missing = @(); $altered = @()
foreach ($row in @($rows | Where-Object { $_ })) {
    $id, $key, $expected = "$row".Split(',')
    $path = Join-Path (Join-Path (Join-Path $blobs $key.Substring(0, 2)) $key.Substring(2, 2)) $key
    if (-not (Test-Path $path)) { $missing += $id; continue }
    if ((Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { $altered += $id; continue }
    $ok++
}

Write-Host "Verified $ok file(s) intact; $($missing.Count) missing; $($altered.Count) altered."
foreach ($id in $missing) { Write-Host "  missing: file $id" }
foreach ($id in $altered) { Write-Host "  altered: file $id" }
if ($missing.Count -or $altered.Count) { exit 1 }
exit 0
