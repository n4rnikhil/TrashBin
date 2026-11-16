# Shared helpers for the operations scripts (dot-sourced; not run directly).
# Configuration comes from real environment variables first, then backend/.env, the same
# precedence the backend uses. Secrets are never printed.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-TrashBinConfig {
    param([string]$EnvFile)

    $fromFile = @{}
    if ($EnvFile -and (Test-Path $EnvFile)) {
        foreach ($line in Get-Content $EnvFile) {
            if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$') { $fromFile[$Matches[1]] = $Matches[2] }
        }
    }
    $get = {
        param($name)
        $value = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($value)) { $value = $fromFile[$name] }
        if ([string]::IsNullOrWhiteSpace($value)) { throw "Missing $name (set it or add it to $EnvFile)" }
        return $value
    }

    $url = & $get 'TRASHBIN_DB_URL'
    if ($url -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') {
        throw "TRASHBIN_DB_URL is not a jdbc:postgresql://host[:port]/database URL"
    }
    $port = '5432'
    if ($Matches[2]) { $port = $Matches[2] }
    return [pscustomobject]@{
        DbHost      = $Matches[1]
        DbPort      = $port
        DbName      = $Matches[3]
        DbUser      = & $get 'TRASHBIN_DB_USERNAME'
        DbPassword  = & $get 'TRASHBIN_DB_PASSWORD'
        StorageRoot = & $get 'TRASHBIN_STORAGE_ROOT'
    }
}

function Get-PgTool {
    param([string]$Name, [string]$PgBin)
    if ($PgBin) {
        $candidate = Join-Path $PgBin "$Name.exe"
        if (Test-Path $candidate) { return $candidate }
        throw "$Name.exe not found in $PgBin"
    }
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "$Name not found on PATH; pass -PgBin <PostgreSQL bin folder> or set TRASHBIN_PG_BIN"
}

function Invoke-Pg {
    # Runs a PostgreSQL client tool with the password in PGPASSWORD (never on the command line).
    param([string]$Tool, [string[]]$Arguments, $Config)
    $previous = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Config.DbPassword
        $output = & $Tool @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) { throw "$([IO.Path]::GetFileName($Tool)) failed: $output" }
        return $output
    } finally {
        $env:PGPASSWORD = $previous
    }
}

function Get-PgConnectionArgs {
    param($Config)
    return @('-w', '-h', $Config.DbHost, '-p', $Config.DbPort, '-U', $Config.DbUser)
}

function Assert-BackendStopped {
    param([string]$HealthUrl = 'http://localhost:8080/api/v1/health')
    try {
        Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3 | Out-Null
        throw "The backend is running ($HealthUrl answered). Stop it first so the database and the blobs match."
    } catch [System.Net.WebException] {
        # Nothing listening: good.
    }
}
