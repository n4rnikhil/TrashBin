<#
.SYNOPSIS
  Idempotently creates the Trash Bin application role and databases on a local PostgreSQL server.

.DESCRIPTION
  Creates role "trashbin" and databases "trashbin_db" and "trashbin_test_db" if missing.
  No passwords are stored in this script. Provide them through environment variables:
    PGPASSWORD              password of the admin account (default admin: postgres)
    TRASHBIN_DB_PASSWORD    password to set for the application role "trashbin"

.PARAMETER PgBin
  Optional folder containing psql.exe if it is not on PATH.

.EXAMPLE
  $env:PGPASSWORD = '<admin password>'; $env:TRASHBIN_DB_PASSWORD = '<app role password>'
  .\create-local-databases.ps1 -PgBin 'C:\Program Files\PostgreSQL\17\bin'
#>
[CmdletBinding()]
param(
  [string]$PgBin = '',
  [string]$AdminUser = 'postgres',
  [string]$HostName = 'localhost',
  [int]$Port = 5432
)

$ErrorActionPreference = 'Stop'
if (-not $env:PGPASSWORD) { throw 'Set PGPASSWORD (admin password) first.' }
if (-not $env:TRASHBIN_DB_PASSWORD) { throw 'Set TRASHBIN_DB_PASSWORD (application role password) first.' }
if ($env:TRASHBIN_DB_PASSWORD -match "'") { throw "TRASHBIN_DB_PASSWORD must not contain a single quote." }

$psql = if ($PgBin) { Join-Path $PgBin 'psql.exe' } else { 'psql' }
function Invoke-Psql([string]$Sql, [string]$Db = 'postgres') {
  & $psql -h $HostName -p $Port -U $AdminUser -d $Db -v ON_ERROR_STOP=1 -tA -c $Sql
  if ($LASTEXITCODE -ne 0) { throw "psql failed for: $Sql" }
}

$role = 'trashbin'
$roleExists = Invoke-Psql "SELECT 1 FROM pg_roles WHERE rolname = '$role'"
if ($roleExists -eq '1') {
  Invoke-Psql "ALTER ROLE $role LOGIN PASSWORD '$($env:TRASHBIN_DB_PASSWORD)'" | Out-Null
  Write-Host "Role '$role' exists; password updated."
} else {
  Invoke-Psql "CREATE ROLE $role LOGIN PASSWORD '$($env:TRASHBIN_DB_PASSWORD)'" | Out-Null
  Write-Host "Role '$role' created."
}

foreach ($db in 'trashbin_db', 'trashbin_test_db') {
  $exists = Invoke-Psql "SELECT 1 FROM pg_database WHERE datname = '$db'"
  if ($exists -eq '1') { Write-Host "Database '$db' already exists." }
  else { Invoke-Psql "CREATE DATABASE $db OWNER $role ENCODING 'UTF8'" | Out-Null; Write-Host "Database '$db' created." }
}
