<#
.SYNOPSIS
  Sets a new password for an account whose owner forgot it (ADR-003).
.DESCRIPTION
  Prompts for the new password without echoing it, then runs the backend jar once with the
  'recovery' profile: the password is replaced, any lockout lifted, and every session of that
  account ends. The backend web server can keep running. Build the jar first (mvnw package).
.EXAMPLE
  .\scripts\reset-password.ps1 -Email sam@example.com
#>
param(
    [Parameter(Mandatory = $true)][string]$Email,
    [string]$Jar = (Get-ChildItem (Join-Path $PSScriptRoot '..\backend\target') -Filter 'trashbin-backend-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1 -ExpandProperty FullName)
)

$ErrorActionPreference = 'Stop'
if (-not $Jar -or -not (Test-Path $Jar)) { throw 'Backend jar not found; run "cd backend; .\mvnw.cmd -DskipTests package" first' }

$first = Read-Host -AsSecureString 'New password (min. 12 characters)'
$second = Read-Host -AsSecureString 'Repeat new password'
$plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($first))
$again = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($second))
if ($plain -cne $again) { throw 'The passwords do not match' }

try {
    # Visible only to this process and its child; cleared below.
    $env:TRASHBIN_RECOVERY_EMAIL = $Email
    $env:TRASHBIN_RECOVERY_PASSWORD = $plain
    Push-Location (Join-Path $PSScriptRoot '..\backend')  # so backend\.env is found
    & java -jar $Jar --spring.profiles.active=recovery
    if ($LASTEXITCODE -ne 0) { throw "Recovery failed (exit code $LASTEXITCODE); see the log above" }
    Write-Host "Done. $Email can sign in with the new password; all of its sessions were ended."
} finally {
    Pop-Location
    Remove-Item Env:TRASHBIN_RECOVERY_PASSWORD, Env:TRASHBIN_RECOVERY_EMAIL -ErrorAction SilentlyContinue
    $plain = $null; $again = $null
}
