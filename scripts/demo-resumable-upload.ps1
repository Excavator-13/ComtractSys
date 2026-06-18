param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$Username = "demo_drafter",
    [string]$Password = "123456",
    [string]$ContractKeyword = "演示采购合同",
    [string]$SampleFile = "",
    [int]$ChunkSize = 1048576
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RunDir = Join-Path $Root "output\demo-run"
if ([string]::IsNullOrWhiteSpace($SampleFile)) {
    $SampleFile = Join-Path $RunDir "resumable-upload-sample.pdf"
}

function Write-Step {
    param([string]$Message)
    Write-Host "[resumable-demo] $Message"
}

function New-SamplePdf {
    param([string]$Path)

    New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetDirectoryName($Path)) | Out-Null
    if (Test-Path $Path) {
        return
    }

    $streamText = @"
BT
/F1 18 Tf
72 720 Td
(ContractSys resumable upload demo) Tj
0 -28 Td
(This PDF is intentionally larger than one upload chunk.) Tj
0 -28 Td
(Use it to demonstrate interrupted chunk upload and resume.) Tj
ET
"@
    $objects = @(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        "<< /Length $($streamText.Length) >>`nstream`n$streamText`nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    )

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append("%PDF-1.4`n")
    $offsets = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -lt $objects.Count; $i++) {
        $offsets.Add($builder.Length)
        [void]$builder.Append("$($i + 1) 0 obj`n$($objects[$i])`nendobj`n")
    }
    $xrefOffset = $builder.Length
    [void]$builder.Append("xref`n0 $($objects.Count + 1)`n")
    [void]$builder.Append("0000000000 65535 f `n")
    foreach ($offset in $offsets) {
        [void]$builder.Append(("{0:0000000000} 00000 n `n" -f $offset))
    }
    [void]$builder.Append("trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`n")
    [void]$builder.Append("startxref`n$xrefOffset`n%%EOF`n")

    [System.IO.File]::WriteAllText($Path, $builder.ToString(), [System.Text.Encoding]::ASCII)

    $targetSize = 2.5MB
    $currentSize = (Get-Item $Path).Length
    if ($currentSize -lt $targetSize) {
        $padLine = [System.Text.Encoding]::ASCII.GetBytes("% resumable upload demo padding line`n")
        $fileStream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write)
        try {
            while ($fileStream.Length -lt $targetSize) {
                $fileStream.Write($padLine, 0, $padLine.Length)
            }
        } finally {
            $fileStream.Dispose()
        }
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$Token = ""
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers
    }
    return Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
}

function Send-Chunk {
    param(
        [string]$UploadId,
        [int]$Index,
        [string]$Path,
        [int]$Size,
        [string]$Token
    )

    $file = [System.IO.File]::OpenRead($Path)
    try {
        $start = [int64]$Index * [int64]$Size
        $remaining = $file.Length - $start
        $length = [int][Math]::Min($Size, $remaining)
        $buffer = New-Object byte[] $length
        $file.Seek($start, [System.IO.SeekOrigin]::Begin) | Out-Null
        $read = $file.Read($buffer, 0, $length)
        if ($read -ne $length) {
            throw "Failed to read chunk $Index from sample file."
        }
    } finally {
        $file.Dispose()
    }

    $client = [System.Net.Http.HttpClient]::new()
    $content = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        $chunkContent = [System.Net.Http.ByteArrayContent]::new($buffer)
        $chunkContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
        $content.Add($chunkContent, "chunk", "sample.part$Index")
        $response = $client.PutAsync("$BaseUrl/attachments/chunk-session/$UploadId/chunks/$Index", $content).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Chunk $Index upload failed: HTTP $([int]$response.StatusCode) $body"
        }
    } finally {
        $content.Dispose()
        $client.Dispose()
    }
}

New-SamplePdf $SampleFile
$sample = Get-Item $SampleFile
Write-Step "sample file: $($sample.FullName) ($($sample.Length) bytes)"

Write-Step "login as $Username"
$login = Invoke-Json -Method Post -Url "$BaseUrl/auth/login" -Body @{
    username = $Username
    password = $Password
}
$token = $login.data.token

Write-Step "finding draft contract by keyword '$ContractKeyword'"
$encodedKeyword = [uri]::EscapeDataString($ContractKeyword)
$contracts = Invoke-Json -Method Get -Url "$BaseUrl/contracts?keyword=$encodedKeyword&page=1&size=20" -Token $token
$contract = @($contracts.data.records) |
    Where-Object { $_.status -eq "DRAFT" } |
    Select-Object -First 1
if (-not $contract) {
    throw "No DRAFT contract found. Start demo data first, or pass -ContractKeyword for a draft contract owned by $Username."
}

$totalChunks = [int][Math]::Ceiling($sample.Length / $ChunkSize)
Write-Step "creating chunk session for contract #$($contract.id) $($contract.name), chunks=$totalChunks, chunkSize=$ChunkSize"
$session = Invoke-Json -Method Post -Url "$BaseUrl/contracts/$($contract.id)/attachments/chunk-session" -Token $token -Body @{
    originalName = $sample.Name
    fileSize = $sample.Length
    contentType = "application/pdf"
    chunkSize = $ChunkSize
    totalChunks = $totalChunks
}

Write-Step "uploading chunk 0, then simulating interruption"
Send-Chunk -UploadId $session.data.uploadId -Index 0 -Path $sample.FullName -Size $ChunkSize -Token $token

$status = Invoke-Json -Method Get -Url "$BaseUrl/attachments/chunk-session/$($session.data.uploadId)" -Token $token
Write-Step "server reports uploaded chunks: $($status.data.uploadedChunks -join ', ')"

Write-Step "resuming and uploading remaining chunks"
$uploaded = @($status.data.uploadedChunks)
for ($i = 0; $i -lt $totalChunks; $i++) {
    if ($uploaded -notcontains $i) {
        Send-Chunk -UploadId $session.data.uploadId -Index $i -Path $sample.FullName -Size $ChunkSize -Token $token
        Write-Step "uploaded chunk $i"
    }
}

Write-Step "completing chunk session"
$completed = Invoke-Json -Method Post -Url "$BaseUrl/attachments/chunk-session/$($session.data.uploadId)/complete" -Token $token
Write-Step "completed attachment #$($completed.data.id): $($completed.data.originalName), $($completed.data.fileSize) bytes"

Write-Host ""
Write-Host "Resumable upload demo finished."
Write-Host "Sample file: $($sample.FullName)"
Write-Host "Contract: #$($contract.id) $($contract.name)"
Write-Host "Attachment: #$($completed.data.id) $($completed.data.originalName)"
