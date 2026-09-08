[CmdletBinding(DefaultParameterSetName = "Start")]
param(
    [Parameter(ParameterSetName = "Stop", Mandatory = $true)]
    [switch]$Stop,
    [Parameter(ParameterSetName = "Start")]
    [string]$EnvironmentFile = ".env.backend.local"
)

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$stateDirectory = Join-Path $repository ".tooling/tmp"
$pidFile = Join-Path $stateDirectory "shared-backend-pids.txt"
$services = @(
    @{ Name = "auth-server"; Port = 18080; Jar = "backend/auth-server/build/libs/auth-server-0.1.0-SNAPSHOT.jar"; Profile = "local,local-shared-db"; Cookie = "SESSION" },
    @{ Name = "admin-server"; Port = 18081; Jar = "backend/admin-server/build/libs/admin-server-0.1.0-SNAPSHOT.jar"; Profile = "local"; Cookie = "SSO_LAB_ADMIN_SESSION" },
    @{ Name = "hr-server"; Port = 18082; Jar = "backend/hr-server/build/libs/hr-server-0.1.0-SNAPSHOT.jar"; Profile = "local"; Cookie = "SSO_LAB_HR_SESSION" },
    @{ Name = "approval-server"; Port = 18083; Jar = "backend/approval-server/build/libs/approval-server-0.1.0-SNAPSHOT.jar"; Profile = "local"; Cookie = "SSO_LAB_APPROVAL_SESSION" }
)

function Stop-RecordedBackends {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }
    foreach ($line in [IO.File]::ReadLines($pidFile)) {
        if ($line -notmatch '^([a-z-]+)=([0-9]+)$') {
            continue
        }
        $process = Get-Process -Id ([int]$matches[2]) -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq "java") {
            Stop-Process -Id $process.Id
            try { Wait-Process -Id $process.Id -Timeout 15 -ErrorAction Stop } catch {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

if ($Stop) {
    Stop-RecordedBackends
    Write-Host "Recorded local shared-database Backend processes are stopped."
    exit 0
}

Set-Location $repository
New-Item -ItemType Directory -Force $stateDirectory | Out-Null
$resolvedEnvironment = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$settings = @{}
foreach ($line in [IO.File]::ReadLines($resolvedEnvironment)) {
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $value = $matches[2]
        if ($value.Length -ge 2 -and (
            ($value[0] -eq '"' -and $value[-1] -eq '"') -or
            ($value[0] -eq "'" -and $value[-1] -eq "'")
        )) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$matches[1]] = $value
    }
}

$required = @(
    "POSTGRES_USER", "POSTGRES_PASSWORD", "SSO_LOCAL_SHARED_AUTH_DB_URL",
    "SSO_LOCAL_HR_CLIENT_ID", "SSO_LOCAL_APPROVAL_CLIENT_ID",
    "SSO_LOCAL_ADMIN_CLIENT_ID", "HR_CLIENT_SECRET", "APPROVAL_CLIENT_SECRET",
    "ADMIN_CLIENT_SECRET", "ADMIN_INTERNAL_API_SECRET", "SSO_JWT_PRIVATE_KEY",
    "SSO_JWT_PUBLIC_KEY"
)
foreach ($key in $required) {
    if (-not $settings.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($settings[$key])) {
        throw "$key must be set in the ignored local Backend environment file"
    }
}
if ($settings["SSO_LOCAL_SHARED_AUTH_DB_URL"] -notmatch '^jdbc:postgresql://(?:host\.docker\.internal|127\.0\.0\.1):15432/') {
    throw "Shared database JDBC URL must use the local SSH tunnel"
}

foreach ($entry in $settings.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
}
$env:AUTH_DB_URL = $settings["SSO_LOCAL_SHARED_AUTH_DB_URL"].Replace(
    "host.docker.internal", "127.0.0.1"
)
$env:SPRING_FLYWAY_ENABLED = "false"
$env:SSO_LOCAL_SHARED_DB_ACKNOWLEDGED = "true"
$env:BOOTSTRAP_ADMIN_ENABLED = "false"
$env:TURNSTILE_ENABLED = "false"
$env:GMAIL_SMTP_ENABLED = "false"
$env:SSO_TEST_SUPPORT_ENABLED = "false"
$env:SESSION_COOKIE_SECURE = "false"
$env:SERVER_FORWARD_HEADERS_STRATEGY = "none"
$env:TRUSTED_PROXY_CIDRS = "127.0.0.1/32"
$env:AUTH_PUBLIC_URL = "http://127.0.0.1:5173"
$env:AUTH_INTERNAL_URL = "http://127.0.0.1:18080"
$env:HR_CLIENT_ID = $settings["SSO_LOCAL_HR_CLIENT_ID"]
$env:APPROVAL_CLIENT_ID = $settings["SSO_LOCAL_APPROVAL_CLIENT_ID"]
$env:ADMIN_CLIENT_ID = $settings["SSO_LOCAL_ADMIN_CLIENT_ID"]
$env:HR_REGISTRATION_ID = "hr-client"
$env:APPROVAL_REGISTRATION_ID = "approval-client"
$env:ADMIN_REGISTRATION_ID = "admin-client"
$env:HR_REDIRECT_URI = "http://127.0.0.1:5175/login/oauth2/code/hr-client"
$env:HR_POST_LOGOUT_REDIRECT_URI = "http://127.0.0.1:5175/"
$env:HR_BACKCHANNEL_LOGOUT_URI = "http://127.0.0.1:18082/internal/oidc/backchannel-logout"
$env:APPROVAL_REDIRECT_URI = "http://127.0.0.1:5176/login/oauth2/code/approval-client"
$env:APPROVAL_POST_LOGOUT_REDIRECT_URI = "http://127.0.0.1:5176/"
$env:APPROVAL_BACKCHANNEL_LOGOUT_URI = "http://127.0.0.1:18083/internal/oidc/backchannel-logout"
$env:ADMIN_REDIRECT_URI = "http://127.0.0.1:5174/login/oauth2/code/admin-client"
$env:ADMIN_POST_LOGOUT_REDIRECT_URI = "http://127.0.0.1:5174/"
$env:ADMIN_BACKCHANNEL_LOGOUT_URI = "http://127.0.0.1:18081/internal/oidc/backchannel-logout"
$env:ADMIN_INTERNAL_URL = "http://127.0.0.1:18080/internal/admin/v1"

$java = (Resolve-Path ".tooling/jdk21/jdk-21.0.12+8/bin/java.exe").Path
$started = [Collections.Generic.List[object]]::new()
try {
    foreach ($service in $services) {
        $occupied = netstat -ano -p tcp | Select-String ":$($service.Port)\s+.*LISTENING"
        if ($occupied) {
            throw "Port $($service.Port) is already in use"
        }
        $jar = (Resolve-Path $service.Jar).Path
        $out = Join-Path $stateDirectory "$($service.Name)-shared.out.log"
        $err = Join-Path $stateDirectory "$($service.Name)-shared.err.log"
        Remove-Item -LiteralPath $out, $err -Force -ErrorAction SilentlyContinue
        $arguments = @(
            "-Xms64m", "-Xmx256m",
            "-jar", $jar,
            "--server.address=127.0.0.1",
            "--server.port=$($service.Port)",
            "--spring.profiles.active=$($service.Profile)",
            "--server.servlet.session.cookie.name=$($service.Cookie)"
        )
        $process = Start-Process -FilePath $java -ArgumentList $arguments `
            -RedirectStandardOutput $out -RedirectStandardError $err `
            -WindowStyle Hidden -PassThru
        $started.Add(@{ Name = $service.Name; Process = $process })
    }
    [IO.File]::WriteAllLines(
        $pidFile,
        @($started | ForEach-Object { "$($_.Name)=$($_.Process.Id)" }),
        [Text.UTF8Encoding]::new($false)
    )
    $deadline = (Get-Date).AddMinutes(3)
    do {
        $healthy = 0
        foreach ($service in $services) {
            try {
                $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 `
                    "http://127.0.0.1:$($service.Port)/actuator/health"
                $content = if ($response.Content -is [byte[]]) {
                    [Text.Encoding]::UTF8.GetString($response.Content)
                } else {
                    [string]$response.Content
                }
                if ($response.StatusCode -eq 200 -and $content -match '"status"\s*:\s*"UP"') {
                    $healthy++
                }
            } catch {}
        }
        if ($healthy -lt $services.Count) { Start-Sleep -Seconds 2 }
    } while ($healthy -lt $services.Count -and (Get-Date) -lt $deadline)
    if ($healthy -ne $services.Count) {
        throw "Only $healthy of $($services.Count) local Backends became healthy"
    }
    Write-Host "All four local shared-database Backends are healthy on loopback ports."
} catch {
    Stop-RecordedBackends
    throw
}
