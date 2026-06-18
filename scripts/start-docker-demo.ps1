param(
    [switch]$DemoData,
    [switch]$NoDemoData,
    [switch]$ResetVolumes,
    [switch]$SkipBuild,
    [switch]$Smoke
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$demoEnabled = if ($NoDemoData) { "false" } elseif ($DemoData) { "true" } else { "false" }

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found in PATH: $Name"
    }
}

function Wait-HttpReady {
    param([string]$Url, [int]$TimeoutSeconds = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return
        } catch {
            if ($_.Exception.Response) {
                return
            }
            Start-Sleep -Seconds 3
        }
    }
    throw "Timed out waiting for $Url"
}

Assert-Command "docker"

Push-Location $Root
try {
    if ($ResetVolumes) {
        Write-Host "[docker-demo] stopping and removing compose volumes"
        docker compose down -v
    }

    if (-not $SkipBuild) {
        Assert-Command "mvn"
        Write-Host "[docker-demo] packaging backend jar"
        Push-Location (Join-Path $Root "backend")
        try {
            mvn package -DskipTests
        } finally {
            Pop-Location
        }
    }

    Write-Host "[docker-demo] starting docker environment, DEMO_DATA_ENABLED=$demoEnabled"
    $env:DEMO_DATA_ENABLED = $demoEnabled
    docker compose up -d --build

    Wait-HttpReady "http://localhost:18080/api/v1/auth/me" 180
    Wait-HttpReady "http://localhost:5173" 120

    Write-Host ""
    Write-Host "Docker demo environment is ready:"
    Write-Host "  Frontend: http://localhost:5173"
    Write-Host "  Backend:  http://localhost:18080"
    Write-Host "  Swagger:  http://localhost:18080/swagger-ui/index.html"
    Write-Host "  Demo data: $demoEnabled"
    Write-Host ""
    Write-Host "Accounts:"
    Write-Host "  admin / 123456"
    Write-Host "  demo_manager / 123456      (when -DemoData is used)"
    Write-Host "  demo_drafter / 123456      (when -DemoData is used)"
    Write-Host "  demo_countersign / 123456  (when -DemoData is used)"
    Write-Host "  demo_approver / 123456     (when -DemoData is used)"
    Write-Host "  demo_signer / 123456       (when -DemoData is used)"
    Write-Host ""
    Write-Host "Stop:"
    Write-Host "  docker compose down"

    if ($Smoke) {
        $env:CONTRACTSYS_BACKEND_URL = "http://localhost:18080"
        $env:CONTRACTSYS_FRONTEND_URL = "http://localhost:5173"
        & (Join-Path $PSScriptRoot "docker-smoke.ps1")
    }
} finally {
    Pop-Location
}
