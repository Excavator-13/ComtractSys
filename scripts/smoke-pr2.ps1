param(
  [string]$BaseUrl = "http://127.0.0.1:8080/api/v1",
  [string]$AdminUsername = "admin",
  [string]$AdminPassword = "123456"
)

$ErrorActionPreference = "Stop"
$script:Passed = 0
$script:Failed = 0

function Write-Step([string]$Message) {
  Write-Host "[smoke] $Message"
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    $script:Failed++
    throw "ASSERT FAILED: $Message"
  }
  $script:Passed++
  Write-Host "  OK - $Message"
}

function Invoke-Api {
  param(
    [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")]
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [string]$Token = "",
    [int]$ExpectedStatus = 200
  )

  $headers = @{}
  if ($Token) {
    $headers.Authorization = "Bearer $Token"
  }

  $params = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
    SkipHttpErrorCheck = $true
  }
  if ($null -ne $Body) {
    $params.ContentType = "application/json; charset=utf-8"
    $params.Body = ($Body | ConvertTo-Json -Depth 20)
  }

  $response = Invoke-WebRequest @params
  $json = $null
  if ($response.Content) {
    $json = $response.Content | ConvertFrom-Json
  }

  Assert-True ($response.StatusCode -eq $ExpectedStatus) "$Method $Path returned HTTP $ExpectedStatus"
  return $json
}

function Login([string]$Username, [string]$Password) {
  $result = Invoke-Api POST "/auth/login" @{ username = $Username; password = $Password }
  Assert-True ($result.code -eq 0) "login succeeds for $Username"
  return $result.data.token
}

function New-SmokeUser([string]$Username, [long]$RoleId, [string]$Token) {
  Invoke-Api POST "/users" @{
    username = $Username
    password = "123456"
    displayName = $Username
    roleId = $RoleId
  } $Token | Out-Null
  return Login $Username "123456"
}

function Get-Task([object[]]$Tasks, [string]$Type, [long]$AssigneeId) {
  return @($Tasks | Where-Object {
    $_.taskType -eq $Type -and [long]$_.assigneeId -eq $AssigneeId
  })[0]
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

Write-Step "Login as admin"
$adminToken = Login $AdminUsername $AdminPassword

Write-Step "Validate short password rejection"
$shortPassword = Invoke-Api POST "/auth/register" @{
  username = "smoke_short_$suffix"
  password = "123"
  confirmPassword = "123"
} "" 400
Assert-True ($shortPassword.code -eq 40000) "register rejects passwords shorter than 6 chars"

Write-Step "Validate permission CRUD and delete guards"
$permissions = Invoke-Api GET "/permissions" $null $adminToken
$contractView = @($permissions.data | Where-Object { $_.permissionCode -eq "contract:view" })[0]
Assert-True ($null -ne $contractView) "core permission contract:view exists"
$coreDelete = Invoke-Api DELETE "/permissions/$($contractView.id)" $null $adminToken 409
Assert-True ($coreDelete.code -eq 40900) "core permission cannot be deleted"

$smokePermissionCode = "smoke:test:$suffix"
$createdPermission = Invoke-Api POST "/permissions" @{
  permissionCode = $smokePermissionCode
  permissionName = "Smoke Test"
  module = "SMOKE"
  url = "/smoke"
  description = "created by smoke-pr2.ps1"
} $adminToken
Assert-True ($createdPermission.data.permissionCode -eq $smokePermissionCode) "custom permission can be created"

$updatedPermission = Invoke-Api PUT "/permissions/$($createdPermission.data.id)" @{
  permissionCode = $smokePermissionCode
  permissionName = "Smoke Test Updated"
  module = "SMOKE"
  url = "/smoke"
  description = "updated by smoke-pr2.ps1"
} $adminToken
Assert-True ($updatedPermission.data.permissionName -eq "Smoke Test Updated") "custom permission can be updated without changing code"

Invoke-Api DELETE "/permissions/$($createdPermission.data.id)" $null $adminToken | Out-Null

Write-Step "Create workflow users"
$roles = Invoke-Api GET "/roles" $null $adminToken
$operatorRole = @($roles.data | Where-Object { $_.roleCode -eq "ROLE_OPERATOR" })[0]
Assert-True ($null -ne $operatorRole) "ROLE_OPERATOR exists"

$counterToken = New-SmokeUser "smoke_counter_$suffix" $operatorRole.id $adminToken
$approve1Token = New-SmokeUser "smoke_approve1_$suffix" $operatorRole.id $adminToken
$approve2Token = New-SmokeUser "smoke_approve2_$suffix" $operatorRole.id $adminToken
$signToken = New-SmokeUser "smoke_sign_$suffix" $operatorRole.id $adminToken

$counter = Invoke-Api GET "/auth/me" $null $counterToken
$approve1 = Invoke-Api GET "/auth/me" $null $approve1Token
$approve2 = Invoke-Api GET "/auth/me" $null $approve2Token
$sign = Invoke-Api GET "/auth/me" $null $signToken

Write-Step "Create and assign contract for return-to-draft test"
$customer = Invoke-Api POST "/customers" @{
  name = "Smoke Customer $suffix"
  tel = "13800000000"
  address = "Smoke Address"
  fax = ""
  postalCode = ""
  bankName = ""
  bankAccount = ""
  remark = "smoke"
} $adminToken

$today = (Get-Date).ToString("yyyy-MM-dd")
$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$contract = Invoke-Api POST "/contracts" @{
  name = "Smoke Return Draft $suffix"
  customerId = $customer.data.id
  beginDate = $today
  endDate = $tomorrow
  content = "smoke contract"
} $adminToken

Invoke-Api POST "/contracts/$($contract.data.id)/assign" @{
  countersignUserIds = @([long]$counter.data.id)
  approvalUserIds = @([long]$approve1.data.id, [long]$approve2.data.id)
  signUserId = [long]$sign.data.id
} $adminToken | Out-Null

Invoke-Api POST "/contracts/$($contract.data.id)/countersign" @{ opinion = "counter ok" } $counterToken | Out-Null
Invoke-Api POST "/contracts/$($contract.data.id)/finalize" @{ content = "final content" } $adminToken | Out-Null
Invoke-Api POST "/contracts/$($contract.data.id)/return" @{ targetStage = "DRAFT"; opinion = "return to draft" } $approve1Token | Out-Null

$returned = Invoke-Api GET "/contracts/$($contract.data.id)" $null $adminToken
Assert-True ($returned.data.contract.status -eq "RETURNED") "return endpoint sets status to RETURNED"
Assert-True (($returned.data.tasks | Where-Object { $_.taskType -eq "REVISE" -and $_.taskStatus -eq "PENDING" }).Count -eq 1) "return creates drafter revise task"

Invoke-Api POST "/contracts/$($contract.data.id)/resume" $null $adminToken | Out-Null
$draftAgain = Invoke-Api GET "/contracts/$($contract.data.id)" $null $adminToken
Assert-True ($draftAgain.data.contract.status -eq "DRAFT") "resume from returned draft first restores DRAFT"

Invoke-Api POST "/contracts/$($contract.data.id)/resume" $null $adminToken | Out-Null
$assignedAgain = Invoke-Api GET "/contracts/$($contract.data.id)" $null $adminToken
Assert-True ($assignedAgain.data.contract.status -eq "ASSIGNED") "second resume skips reassignment and restores ASSIGNED"
Assert-True (($assignedAgain.data.tasks | Where-Object { $_.taskType -eq "COUNTERSIGN" -and $_.taskStatus -eq "PENDING" }).Count -eq 1) "preserved countersigner is active after resume"

Write-Step "Create and cancel contract"
$cancelContract = Invoke-Api POST "/contracts" @{
  name = "Smoke Cancel $suffix"
  customerId = $customer.data.id
  beginDate = $today
  endDate = $tomorrow
  content = "cancel smoke contract"
} $adminToken

Invoke-Api POST "/contracts/$($cancelContract.data.id)/cancel" $null $adminToken | Out-Null
$cancelDetail = Invoke-Api GET "/contracts/$($cancelContract.data.id)" $null $adminToken
Assert-True ($cancelDetail.data.contract.status -eq "CANCELLED") "cancel endpoint sets contract status to CANCELLED"
Assert-True (($cancelDetail.data.tasks | Where-Object { $_.taskStatus -eq "PENDING" }).Count -eq 0) "cancel endpoint closes pending tasks"

$assignCancelled = Invoke-Api POST "/contracts/$($cancelContract.data.id)/assign" @{
  countersignUserIds = @([long]$counter.data.id)
  approvalUserIds = @([long]$approve1.data.id)
  signUserId = [long]$sign.data.id
} $adminToken 409
Assert-True ($assignCancelled.code -eq 40900) "cancelled contract rejects further assignment"

Write-Step "Smoke tests completed: $script:Passed passed, $script:Failed failed"
