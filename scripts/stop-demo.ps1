$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RunDir = Join-Path $Root "output\demo-run"

function Get-ChildProcessIds {
    param([int]$ParentProcessId)

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ParentProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Get-ChildProcessIds -ParentProcessId ([int]$child.ProcessId)
        [int]$child.ProcessId
    }
}

function Stop-RecordedProcess {
    param([int]$ProcessId)

    $childIds = @(Get-ChildProcessIds -ParentProcessId $ProcessId)
    foreach ($childId in $childIds) {
        $child = Get-Process -Id $childId -ErrorAction SilentlyContinue
        if ($child) {
            Write-Host "[demo] stopping child process $childId"
            Stop-Process -Id $childId -Force
        }
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process) {
        Write-Host "[demo] stopping process $ProcessId"
        Stop-Process -Id $ProcessId -Force
    }
}

if (-not (Test-Path $RunDir)) {
    Write-Host "[demo] no recorded demo run found"
    exit 0
}

$processIds = Get-ChildItem -Path $RunDir -Filter "*.pid" -File |
    ForEach-Object { Get-Content $_.FullName | Select-Object -First 1 } |
    Where-Object { $_ -match '^\d+$' } |
    ForEach-Object { [int]$_ } |
    Sort-Object -Unique

foreach ($processId in $processIds) {
    Stop-RecordedProcess -ProcessId $processId
}

Get-ChildItem -Path $RunDir -Filter "*.pid" -File | ForEach-Object {
    Remove-Item -Path $_.FullName -Force
}

Write-Host "[demo] stopped recorded demo processes"
