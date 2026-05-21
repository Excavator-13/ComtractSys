$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Net.Http

$backendBase = $env:CONTRACTSYS_BACKEND_URL
if (-not $backendBase) {
    $backendBase = 'http://localhost:18080'
}

$frontendBase = $env:CONTRACTSYS_FRONTEND_URL
if (-not $frontendBase) {
    $frontendBase = 'http://localhost:5173'
}

$apiBase = "$backendBase/api/v1"

function Invoke-HttpRequest($Method, $Url, $Body = $null, $Headers = @{}) {
    $client = [System.Net.Http.HttpClient]::new()
    try {
        foreach ($key in $Headers.Keys) {
            $client.DefaultRequestHeaders.TryAddWithoutValidation($key, $Headers[$key]) | Out-Null
        }

        $requestBody = $null
        if ($null -ne $Body) {
            $requestBody = [System.Net.Http.StringContent]::new($Body, [System.Text.Encoding]::UTF8, 'application/json')
        }

        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
        $request.Content = $requestBody
        $response = $client.SendAsync($request).GetAwaiter().GetResult()

        $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [PSCustomObject]@{
            Status = [int]$response.StatusCode
            Body = $content
        }
    } finally {
        $client.Dispose()
    }
}

function Wait-Http($Name, $Url) {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $response = Invoke-HttpRequest 'Get' $Url
        if ($response.Status -ge 200 -and $response.Status -lt 500) {
            Write-Output "[ok] $Name is reachable"
            return $response
        }
        Start-Sleep -Seconds 2
    }
    throw "$Name did not become reachable at $Url"
}

function Assert-Equal($Name, $Actual, $Expected) {
    if ($Actual -ne $Expected) {
        throw "$Name expected $Expected but got $Actual"
    }
    Write-Output "[ok] $Name = $Actual"
}

Wait-Http 'frontend' $frontendBase | Out-Null
Wait-Http 'swagger' "$backendBase/swagger-ui/index.html" | Out-Null

$anonymousContracts = Invoke-HttpRequest 'Get' "$apiBase/contracts"
Assert-Equal 'unauthenticated contracts status' $anonymousContracts.Status 401

$loginBody = @{ username = 'admin'; password = '123456' } | ConvertTo-Json
$loginResponse = Invoke-HttpRequest 'Post' "$apiBase/auth/login" $loginBody
Assert-Equal 'direct login status' $loginResponse.Status 200
$login = $loginResponse.Body | ConvertFrom-Json
Assert-Equal 'direct login code' $login.code 0

$token = $login.data.token
if (-not $token) {
    throw 'Login response did not contain a token'
}

$headers = @{ Authorization = "Bearer $token" }
$meResponse = Invoke-HttpRequest 'Get' "$apiBase/auth/me" $null $headers
Assert-Equal 'current user status' $meResponse.Status 200
$me = $meResponse.Body | ConvertFrom-Json
Assert-Equal 'current username' $me.data.username 'admin'

$statisticsResponse = Invoke-HttpRequest 'Get' "$apiBase/statistics" $null $headers
Assert-Equal 'statistics status' $statisticsResponse.Status 200
$statistics = $statisticsResponse.Body | ConvertFrom-Json
Assert-Equal 'statistics code' $statistics.code 0

$proxyLoginResponse = Invoke-HttpRequest 'Post' "$frontendBase/api/v1/auth/login" $loginBody
Assert-Equal 'frontend proxy login status' $proxyLoginResponse.Status 200
$proxyLogin = $proxyLoginResponse.Body | ConvertFrom-Json
Assert-Equal 'frontend proxy login code' $proxyLogin.code 0

$homeResponse = Invoke-HttpRequest 'Get' $frontendBase
Assert-Equal 'frontend home status' $homeResponse.Status 200
if ($homeResponse.Body -notmatch '<div id="app">') {
    throw 'Frontend HTML did not contain Vue mount node'
}
Write-Output '[ok] frontend HTML contains Vue mount node'

$swaggerResponse = Invoke-HttpRequest 'Get' "$backendBase/swagger-ui/index.html"
Assert-Equal 'swagger status' $swaggerResponse.Status 200

Write-Output 'Docker smoke test passed.'
