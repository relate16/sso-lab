[CmdletBinding()]
param(
    [string]$SshHost = "today-sso.duckdns.org",
    [string]$SshUser = "today",
    [string]$PostgresContainer = "sso-lab-postgres-1",
    [ValidateRange(1, 65535)]
    [int]$LocalPort = 15432
)

$ErrorActionPreference = "Stop"

foreach ($value in @($SshHost, $SshUser, $PostgresContainer)) {
    if ($value -notmatch '^[A-Za-z0-9._-]+$') {
        throw "SSH host, user and container names may contain only letters, numbers, dot, underscore and hyphen"
    }
}

$occupied = Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort $LocalPort `
    -State Listen -ErrorAction SilentlyContinue
if ($occupied) {
    throw "127.0.0.1:$LocalPort is already in use"
}

$remoteCommand = "docker inspect --format '{{range .NetworkSettings.Networks}}{{println .IPAddress}}{{end}}' $PostgresContainer"
$containerAddresses = @(
    ssh -T "$SshUser@$SshHost" $remoteCommand |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)
if ($LASTEXITCODE -ne 0 -or $containerAddresses.Count -ne 1) {
    throw "Could not resolve exactly one current PostgreSQL container address"
}

$parsedAddress = $null
if (-not [System.Net.IPAddress]::TryParse($containerAddresses[0], [ref]$parsedAddress)) {
    throw "Docker returned an invalid PostgreSQL container address"
}
if ($parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
    throw "Only an IPv4 Docker bridge address is supported"
}
$octets = $parsedAddress.GetAddressBytes()
$isPrivate = $octets[0] -eq 10 -or
    ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or
    ($octets[0] -eq 192 -and $octets[1] -eq 168)
if (-not $isPrivate) {
    throw "Refusing to forward to a non-private Docker address"
}

Write-Host "Opening 127.0.0.1:$LocalPort to the current private PostgreSQL container endpoint."
Write-Host "Keep this PowerShell window open. Ctrl+C closes database access."
ssh -N -T `
    -o ExitOnForwardFailure=yes `
    -o ServerAliveInterval=30 `
    -o ServerAliveCountMax=3 `
    -L "127.0.0.1:${LocalPort}:$($parsedAddress.IPAddressToString):5432" `
    "$SshUser@$SshHost"

if ($LASTEXITCODE -ne 0) {
    throw "SSH tunnel exited with code $LASTEXITCODE"
}
