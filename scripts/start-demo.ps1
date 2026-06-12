param(
    [string]$Profile = "functional-test",
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$SkipInstall,
    [switch]$OpenBrowser,
    [ValidateSet("None", "Basic", "Rich")]
    [string]$SeedData = "Rich",
    [switch]$SeedExtraData,
    [switch]$KillExistingOnPorts
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"
$RunDir = Join-Path $Root "output\demo-run"
$BackendLog = Join-Path $RunDir "backend.out.log"
$BackendErr = Join-Path $RunDir "backend.err.log"
$FrontendLog = Join-Path $RunDir "frontend.out.log"
$FrontendErr = Join-Path $RunDir "frontend.err.log"
$BackendPidFile = Join-Path $RunDir "backend.pid"
$FrontendPidFile = Join-Path $RunDir "frontend.pid"
$BackendServicePidFile = Join-Path $RunDir "backend-service.pid"
$FrontendServicePidFile = Join-Path $RunDir "frontend-service.pid"
$BackendRunner = Join-Path $RunDir "run-backend.ps1"
$FrontendRunner = Join-Path $RunDir "run-frontend.ps1"

function Write-Step {
    param([string]$Message)
    Write-Host "[demo] $Message"
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found in PATH: $Name"
    }
}

function Test-PortOpen {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

function Stop-PortProcess {
    param([int]$Port, [string]$Name)
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $connection) {
        return
    }
    $processId = [int]$connection.OwningProcess
    Write-Step "stopping process $processId that is using $Name port $Port"
    Stop-Process -Id $processId -Force
    Start-Sleep -Seconds 2
}

function Assert-PortFree {
    param([int]$Port, [string]$Name)
    if (Test-PortOpen $Port) {
        if ($KillExistingOnPorts) {
            Stop-PortProcess -Port $Port -Name $Name
            if (-not (Test-PortOpen $Port)) {
                return
            }
        }
        throw "$Name port $Port is already in use. Stop the existing service first, or pass a different port."
    }
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
            return
        } catch {
            if ($_.Exception.Response) {
                return
            }
            Start-Sleep -Seconds 2
        }
    }

    throw "Timed out waiting for $Url"
}

if ($SeedExtraData -and $SeedData -eq "Rich") {
    $SeedData = "Basic"
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

Assert-Command "mvn"
Assert-Command "npm"
Assert-PortFree $BackendPort "Backend"
Assert-PortFree $FrontendPort "Frontend"

if (-not $SkipInstall) {
    if (-not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
        Write-Step "installing frontend dependencies"
        Push-Location $FrontendDir
        try {
            npm install
        } finally {
            Pop-Location
        }
    }
}

Write-Step "starting backend on http://localhost:$BackendPort with profile '$Profile'"
$backendCommand = @"
`$ErrorActionPreference = "Stop"
Set-Location "$BackendDir"
`$env:DEMO_DATA_ENABLED = "true"
mvn spring-boot:run "-Dspring-boot.run.profiles=$Profile" "-Dspring-boot.run.arguments=--server.port=$BackendPort"
"@
Set-Content -Path $BackendRunner -Value $backendCommand -Encoding UTF8
$backend = Start-Process -FilePath "powershell" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $BackendRunner) `
    -RedirectStandardOutput $BackendLog `
    -RedirectStandardError $BackendErr `
    -WindowStyle Hidden `
    -PassThru
$backend.Id | Set-Content -Path $BackendPidFile

try {
    Wait-HttpReady "http://localhost:$BackendPort/api/v1/auth/me"
} catch {
    if ($backend.HasExited) {
        throw "Backend exited early. Check $BackendLog and $BackendErr"
    }
    throw
}
$backendServicePid = (Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
if ($backendServicePid) {
    $backendServicePid | Set-Content -Path $BackendServicePidFile
}

Write-Step "starting frontend on http://localhost:$FrontendPort"
$frontendCommand = @"
`$ErrorActionPreference = "Stop"
Set-Location "$FrontendDir"
`$env:VITE_BACKEND_TARGET = "http://localhost:$BackendPort"
npm run dev -- --host 0.0.0.0 --port $FrontendPort
"@
Set-Content -Path $FrontendRunner -Value $frontendCommand -Encoding UTF8
$frontend = Start-Process -FilePath "powershell" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $FrontendRunner) `
    -RedirectStandardOutput $FrontendLog `
    -RedirectStandardError $FrontendErr `
    -WindowStyle Hidden `
    -PassThru
$frontend.Id | Set-Content -Path $FrontendPidFile

try {
    Wait-HttpReady "http://localhost:$FrontendPort"
} catch {
    if ($frontend.HasExited) {
        throw "Frontend exited early. Check $FrontendLog and $FrontendErr"
    }
    throw
}
$frontendServicePid = (Get-NetTCPConnection -LocalPort $FrontendPort -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess
if ($frontendServicePid) {
    $frontendServicePid | Set-Content -Path $FrontendServicePidFile
}

switch ($SeedData) {
    "Basic" {
        Write-Step "creating basic staged demo data"
        & (Join-Path $PSScriptRoot "seed-demo.ps1") -BaseUrl "http://localhost:$BackendPort/api/v1"
    }
    "Rich" {
        Write-Step "creating rich staged demo data"
        & (Join-Path $PSScriptRoot "seed-rich-demo.ps1") -BaseUrl "http://localhost:$BackendPort/api/v1"
    }
    default {
        Write-Step "skipping scripted demo data"
    }
}

Write-Host ""
Write-Host "Demo environment is ready:"
Write-Host "  Frontend: http://localhost:$FrontendPort"
Write-Host "  Backend:  http://localhost:$BackendPort"
Write-Host ""
Write-Host "Demo accounts:"
Write-Host "  admin / 123456"
Write-Host "  demo_drafter / 123456"
Write-Host "  demo_manager / 123456"
Write-Host "  demo_approver / 123456"
if ($SeedData -eq "Basic") {
    Write-Host "  demo_counter1 / 123456"
    Write-Host "  demo_counter2 / 123456"
    Write-Host "  demo_approver1 / 123456"
    Write-Host "  demo_approver2 / 123456"
    Write-Host "  demo_signer / 123456"
}
if ($SeedData -eq "Rich") {
    Write-Host "  demo_ext_drafter_a / 123456"
    Write-Host "  demo_ext_legal_1 / 123456"
    Write-Host "  demo_ext_approve_mgr / 123456"
    Write-Host "  demo_ext_signer_a / 123456"
}
Write-Host ""
Write-Host "Logs:"
Write-Host "  $BackendLog"
Write-Host "  $BackendErr"
Write-Host "  $FrontendLog"
Write-Host "  $FrontendErr"
Write-Host ""
Write-Host "PID files:"
Write-Host "  $BackendPidFile"
Write-Host "  $FrontendPidFile"
Write-Host "  $BackendServicePidFile"
Write-Host "  $FrontendServicePidFile"

if ($OpenBrowser) {
    Start-Process "http://localhost:$FrontendPort"
}
