param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "123456",
    [string]$DemoPassword = "123456"
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
    param(
        [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body = $null
    )

    $headers = @{}
    if ($Token) {
        $headers.Authorization = "Bearer $Token"
    }

    $uri = "$BaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
    }

    $json = $Body | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -ContentType "application/json; charset=utf-8" -Body $json
}

function Login {
    param([string]$Username, [string]$Password)
    $res = Invoke-Api -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
    return $res.data.token
}

function Find-RoleId {
    param([string]$RoleCode, [string]$Token)
    $roles = (Invoke-Api -Method GET -Path "/roles" -Token $Token).data
    $role = $roles | Where-Object { $_.roleCode -eq $RoleCode } | Select-Object -First 1
    if (-not $role) {
        throw "Role not found: $RoleCode"
    }
    return [long]$role.id
}

function Ensure-User {
    param(
        [string]$Username,
        [string]$DisplayName,
        [long]$RoleId,
        [string]$Token
    )

    $list = (Invoke-Api -Method GET -Path "/users?keyword=$Username&page=1&size=100" -Token $Token).data.records
    $existing = $list | Where-Object { $_.username -eq $Username } | Select-Object -First 1
    if ($existing) {
        Invoke-Api -Method PUT -Path "/users/$($existing.id)/roles" -Token $Token -Body @{ roleIds = @($RoleId) } | Out-Null
        return [pscustomobject]@{ id = [long]$existing.id; username = $Username; token = (Login $Username $DemoPassword) }
    }

    $created = Invoke-Api -Method POST -Path "/users" -Token $Token -Body @{
        username = $Username
        password = $DemoPassword
        displayName = $DisplayName
        roleIds = @($RoleId)
    }
    return [pscustomobject]@{ id = [long]$created.data.id; username = $Username; token = (Login $Username $DemoPassword) }
}

function Create-Customer {
    param([string]$Name, [string]$Token)
    $res = Invoke-Api -Method POST -Path "/customers" -Token $Token -Body @{
        name = $Name
        tel = "13800000000"
        address = "示例地址"
        remark = "seed demo customer"
    }
    return [long]$res.data.id
}

function Create-Contract {
    param(
        [string]$Name,
        [long]$CustomerId,
        [string]$Token
    )
    $today = Get-Date -Format "yyyy-MM-dd"
    $end = (Get-Date).AddMonths(12).ToString("yyyy-MM-dd")
    $res = Invoke-Api -Method POST -Path "/contracts" -Token $Token -Body @{
        name = $Name
        customerId = $CustomerId
        beginDate = $today
        endDate = $end
        content = "Demo contract body for $Name. Used to test assignment, countersign, finalize, approval, signing, and attachment permission."
    }
    return [long]$res.data.id
}

function Assign-Contract {
    param(
        [long]$ContractId,
        [object[]]$Countersigners,
        [object[]]$Approvers,
        [object]$Signer,
        [string]$Token
    )
    Invoke-Api -Method POST -Path "/contracts/$ContractId/assign" -Token $Token -Body @{
        countersignUserIds = @($Countersigners | ForEach-Object { [long]$_.id })
        approvalUserIds = @($Approvers | ForEach-Object { [long]$_.id })
        signUserId = [long]$Signer.id
    } | Out-Null
}

function Countersign-Contract {
    param([long]$ContractId, [object]$User)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/countersign" -Token $User.token -Body @{
        opinion = "$($User.username) countersign accepted"
    } | Out-Null
}

function Finalize-Contract {
    param([long]$ContractId, [object]$Drafter)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/finalize" -Token $Drafter.token -Body @{
        content = "Demo contract finalized after countersign opinions."
    } | Out-Null
}

function Approve-Contract {
    param([long]$ContractId, [object]$User)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/approve" -Token $User.token -Body @{
        result = "APPROVED"
        opinion = "$($User.username) approved"
    } | Out-Null
}

function Sign-Contract {
    param([long]$ContractId, [object]$User)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/sign" -Token $User.token -Body @{
        signInfo = "$($User.username) signed"
        signedDate = (Get-Date -Format "yyyy-MM-dd")
    } | Out-Null
}

$adminToken = Login $AdminUsername $AdminPassword
$operatorRoleId = Find-RoleId "ROLE_OPERATOR" $adminToken

$drafter = Ensure-User "demo_drafter" "Demo Drafter" $operatorRoleId $adminToken
$counter1 = Ensure-User "demo_counter1" "Demo Countersigner 1" $operatorRoleId $adminToken
$counter2 = Ensure-User "demo_counter2" "Demo Countersigner 2" $operatorRoleId $adminToken
$approver1 = Ensure-User "demo_approver1" "Demo Approver 1" $operatorRoleId $adminToken
$approver2 = Ensure-User "demo_approver2" "Demo Approver 2" $operatorRoleId $adminToken
$signer = Ensure-User "demo_signer" "Demo Signer" $operatorRoleId $adminToken

$runId = Get-Date -Format "MMddHHmmss"
$customerId = Create-Customer "Demo Customer $runId" $adminToken
$countersigners = @($counter1, $counter2)
$approvers = @($approver1, $approver2)

$draft = Create-Contract "Demo Pending Assign $runId" $customerId $drafter.token

$assigned = Create-Contract "Demo Pending Countersign $runId" $customerId $drafter.token
Assign-Contract $assigned $countersigners $approvers $signer $adminToken

$counterSigned = Create-Contract "Demo Pending Finalize $runId" $customerId $drafter.token
Assign-Contract $counterSigned $countersigners $approvers $signer $adminToken
$countersigners | ForEach-Object { Countersign-Contract $counterSigned $_ }

$finalized = Create-Contract "Demo Pending Approval $runId" $customerId $drafter.token
Assign-Contract $finalized $countersigners $approvers $signer $adminToken
$countersigners | ForEach-Object { Countersign-Contract $finalized $_ }
Finalize-Contract $finalized $drafter

$approved = Create-Contract "Demo Pending Sign $runId" $customerId $drafter.token
Assign-Contract $approved $countersigners $approvers $signer $adminToken
$countersigners | ForEach-Object { Countersign-Contract $approved $_ }
Finalize-Contract $approved $drafter
$approvers | ForEach-Object { Approve-Contract $approved $_ }

$signed = Create-Contract "Demo Signed $runId" $customerId $drafter.token
Assign-Contract $signed $countersigners $approvers $signer $adminToken
$countersigners | ForEach-Object { Countersign-Contract $signed $_ }
Finalize-Contract $signed $drafter
$approvers | ForEach-Object { Approve-Contract $signed $_ }
Sign-Contract $signed $signer

Write-Host "Demo data created."
Write-Host ""
Write-Host "All demo account passwords: $DemoPassword"
Write-Host "  demo_drafter   draft/finalize"
Write-Host "  demo_counter1  countersign"
Write-Host "  demo_counter2  countersign"
Write-Host "  demo_approver1 approve"
Write-Host "  demo_approver2 approve"
Write-Host "  demo_signer    sign"
Write-Host ""
Write-Host "Contract IDs:"
Write-Host "  Pending assign: $draft"
Write-Host "  Pending countersign: $assigned"
Write-Host "  Pending finalize: $counterSigned"
Write-Host "  Pending approval: $finalized"
Write-Host "  Pending sign: $approved"
Write-Host "  Signed: $signed"
