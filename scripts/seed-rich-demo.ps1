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

    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }

    return Invoke-RestMethod @params
}

function Login {
    param([string]$Username, [string]$Password)
    $res = Invoke-Api -Method POST -Path "/auth/login" -Body @{ username = $Username; password = $Password }
    return $res.data.token
}

function Current-User {
    param([string]$Token)
    return (Invoke-Api -Method GET -Path "/auth/me" -Token $Token).data
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
        [string]$Phone,
        [string]$Email,
        [string]$Token
    )

    $list = (Invoke-Api -Method GET -Path "/users?keyword=$Username&page=1&size=100" -Token $Token).data.records
    $existing = $list | Where-Object { $_.username -eq $Username } | Select-Object -First 1
    if ($existing) {
        Invoke-Api -Method PUT -Path "/users/$($existing.id)/roles" -Token $Token -Body @{ roleId = $RoleId } | Out-Null
        Invoke-Api -Method PUT -Path "/users/$($existing.id)" -Token $Token -Body @{
            username = $Username
            displayName = $DisplayName
            phone = $Phone
            email = $Email
            roleId = $RoleId
        } | Out-Null
        Invoke-Api -Method PATCH -Path "/users/$($existing.id)/status" -Token $Token -Body @{ status = "ENABLED" } | Out-Null
        $userToken = Login $Username $DemoPassword
        return [pscustomobject]@{ id = [long]$existing.id; username = $Username; displayName = $DisplayName; token = $userToken }
    }

    $created = Invoke-Api -Method POST -Path "/users" -Token $Token -Body @{
        username = $Username
        password = $DemoPassword
        displayName = $DisplayName
        phone = $Phone
        email = $Email
        roleId = $RoleId
    }
    return [pscustomobject]@{ id = [long]$created.data.id; username = $Username; displayName = $DisplayName; token = (Login $Username $DemoPassword) }
}

function Disable-User {
    param([object]$User, [string]$Token)
    Invoke-Api -Method PATCH -Path "/users/$($User.id)/status" -Token $Token -Body @{ status = "DISABLED" } | Out-Null
}

function Create-Customer {
    param(
        [string]$Name,
        [string]$Tel,
        [string]$Address,
        [string]$Remark,
        [string]$Token
    )

    $res = Invoke-Api -Method POST -Path "/customers" -Token $Token -Body @{
        name = $Name
        tel = $Tel
        address = $Address
        fax = "010-88000000"
        postalCode = "100000"
        bankName = "演示银行"
        bankAccount = "6222000000000000"
        remark = $Remark
    }
    return [long]$res.data.id
}

function New-ContractBody {
    param([string]$Scene, [string]$CustomerName)
    return @(
        "一、项目背景",
        "$CustomerName 与我方就 $Scene 建立合作关系，本合同用于演示合同起草、分配、会签、定稿、审批、签订和附件管理流程。",
        "",
        "二、主要条款",
        "1. 服务范围包含需求确认、过程交付、验收支持和归档管理。",
        "2. 双方以系统流程记录作为审批、会签、签订和附件留痕依据。",
        "3. 本示例数据仅用于系统演示，不代表真实商业合同。",
        "",
        "三、附件",
        "合同可包含空白模板、扫描件、图片凭证和 PDF 附件，用于演示上传、预览、下载和权限控制。"
    ) -join "`n"
}

function Create-Contract {
    param(
        [string]$Name,
        [long]$CustomerId,
        [string]$CustomerName,
        [object]$Drafter,
        [int]$BeginOffsetDays = 0,
        [int]$DurationMonths = 12
    )

    $begin = (Get-Date).AddDays($BeginOffsetDays).ToString("yyyy-MM-dd")
    $end = (Get-Date).AddDays($BeginOffsetDays).AddMonths($DurationMonths).ToString("yyyy-MM-dd")
    $res = Invoke-Api -Method POST -Path "/contracts" -Token $Drafter.token -Body @{
        name = $Name
        customerId = $CustomerId
        beginDate = $begin
        endDate = $end
        content = New-ContractBody $Name $CustomerName
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
    param([long]$ContractId, [object]$User, [string]$Opinion = "")
    if (-not $Opinion) {
        $Opinion = "$($User.displayName) 已会签，建议继续流转。"
    }
    Invoke-Api -Method POST -Path "/contracts/$ContractId/countersign" -Token $User.token -Body @{ opinion = $Opinion } | Out-Null
}

function Finalize-Contract {
    param([long]$ContractId, [object]$Drafter, [string]$Scene)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/finalize" -Token $Drafter.token -Body @{
        content = (New-ContractBody "定稿版 - $Scene" "演示客户") + "`n四、定稿说明`n已吸收全部会签意见，提交审批。"
    } | Out-Null
}

function Approve-Contract {
    param(
        [long]$ContractId,
        [object]$User,
        [ValidateSet("APPROVED", "REJECTED")]
        [string]$Result = "APPROVED",
        [string]$Opinion = ""
    )
    if (-not $Opinion) {
        $Opinion = if ($Result -eq "APPROVED") { "$($User.displayName) 审批通过。" } else { "$($User.displayName) 审批拒绝：需要补充预算或附件。" }
    }
    Invoke-Api -Method POST -Path "/contracts/$ContractId/approve" -Token $User.token -Body @{
        result = $Result
        opinion = $Opinion
    } | Out-Null
}

function Sign-Contract {
    param([long]$ContractId, [object]$User)
    Invoke-Api -Method POST -Path "/contracts/$ContractId/sign" -Token $User.token -Body @{
        signInfo = "$($User.displayName) 已完成电子签订，合同归档。"
        signedDate = (Get-Date).ToString("yyyy-MM-dd")
    } | Out-Null
}

function New-DemoAttachmentFiles {
    param([string]$RunId)

    $dir = Join-Path ([System.IO.Path]::GetTempPath()) "contractsys-rich-demo-$RunId"
    if (Test-Path $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dir | Out-Null

    $pdfPath = Join-Path $dir "blank-contract-template-$RunId.pdf"
    [System.IO.File]::WriteAllText($pdfPath, "%PDF-1.4`n1 0 obj << /Type /Catalog >> endobj`ntrailer << /Root 1 0 R >>`n%%EOF", [System.Text.Encoding]::ASCII)

    $pngPath = Join-Path $dir "blank-seal-placeholder-$RunId.png"
    [System.IO.File]::WriteAllBytes($pngPath, [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="))

    $gifPath = Join-Path $dir "blank-scan-placeholder-$RunId.gif"
    [System.IO.File]::WriteAllBytes($gifPath, [Convert]::FromBase64String("R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=="))

    $docxPath = Join-Path $dir "blank-word-template-$RunId.docx"
    $docxWork = Join-Path $dir "docx-work"
    New-Item -ItemType Directory -Path (Join-Path $docxWork "_rels") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $docxWork "word") -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $docxWork "[Content_Types].xml"), '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>', [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText((Join-Path $docxWork "_rels\.rels"), '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>', [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText((Join-Path $docxWork "word\document.xml"), '<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>空白合同模板</w:t></w:r></w:p></w:body></w:document>', [System.Text.Encoding]::UTF8)
    if (Test-Path $docxPath) {
        Remove-Item -LiteralPath $docxPath -Force
    }
    $docxZipPath = Join-Path $dir "blank-word-template-$RunId.zip"
    if (Test-Path $docxZipPath) {
        Remove-Item -LiteralPath $docxZipPath -Force
    }
    Compress-Archive -Path (Join-Path $docxWork "*") -DestinationPath $docxZipPath -Force
    Move-Item -LiteralPath $docxZipPath -Destination $docxPath -Force

    return @{
        Pdf = $pdfPath
        Png = $pngPath
        Gif = $gifPath
        Docx = $docxPath
    }
}

function Upload-Attachment {
    param(
        [long]$ContractId,
        [string]$FilePath,
        [string]$ContentType,
        [string]$Token
    )

    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $fileStream = $null
    $multipart = $null
    try {
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        $multipart = [System.Net.Http.MultipartFormDataContent]::new()
        $fileStream = [System.IO.File]::OpenRead($FilePath)
        $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $multipart.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
        $response = $client.PostAsync("$BaseUrl/contracts/$ContractId/attachments", $multipart).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Upload failed for contract $ContractId file $FilePath : HTTP $([int]$response.StatusCode) $body"
        }
        return $body | ConvertFrom-Json
    } finally {
        if ($multipart) { $multipart.Dispose() }
        if ($fileStream) { $fileStream.Dispose() }
        $client.Dispose()
    }
}

function Add-Standard-Attachments {
    param(
        [long]$ContractId,
        [hashtable]$Files,
        [object]$Uploader,
        [string[]]$Kinds = @("Pdf")
    )

    foreach ($kind in $Kinds) {
        switch ($kind) {
            "Pdf" { Upload-Attachment $ContractId $Files.Pdf "application/pdf" $Uploader.token | Out-Null }
            "Png" { Upload-Attachment $ContractId $Files.Png "image/png" $Uploader.token | Out-Null }
            "Gif" { Upload-Attachment $ContractId $Files.Gif "image/gif" $Uploader.token | Out-Null }
            "Docx" { Upload-Attachment $ContractId $Files.Docx "application/vnd.openxmlformats-officedocument.wordprocessingml.document" $Uploader.token | Out-Null }
        }
    }
}

$adminToken = Login $AdminUsername $AdminPassword
$adminUser = Current-User $adminToken
$operatorRoleId = Find-RoleId "ROLE_OPERATOR" $adminToken

$users = @{}
$userSpecs = @(
    @{ Key = "drafterA"; Username = "demo_ext_drafter_a"; DisplayName = "演示起草人A"; Phone = "13900010001"; Email = "drafter.a@example.test" },
    @{ Key = "drafterB"; Username = "demo_ext_drafter_b"; DisplayName = "演示起草人B"; Phone = "13900010002"; Email = "drafter.b@example.test" },
    @{ Key = "drafterC"; Username = "demo_ext_drafter_c"; DisplayName = "演示起草人C"; Phone = "13900010003"; Email = "drafter.c@example.test" },
    @{ Key = "legal1"; Username = "demo_ext_legal_1"; DisplayName = "法务会签一"; Phone = "13900020001"; Email = "legal.1@example.test" },
    @{ Key = "legal2"; Username = "demo_ext_legal_2"; DisplayName = "法务会签二"; Phone = "13900020002"; Email = "legal.2@example.test" },
    @{ Key = "finance1"; Username = "demo_ext_finance_1"; DisplayName = "财务会签一"; Phone = "13900030001"; Email = "finance.1@example.test" },
    @{ Key = "risk1"; Username = "demo_ext_risk_1"; DisplayName = "风控会签一"; Phone = "13900040001"; Email = "risk.1@example.test" },
    @{ Key = "tech1"; Username = "demo_ext_tech_1"; DisplayName = "技术会签一"; Phone = "13900050001"; Email = "tech.1@example.test" },
    @{ Key = "approveMgr"; Username = "demo_ext_approve_mgr"; DisplayName = "部门审批经理"; Phone = "13900060001"; Email = "approve.mgr@example.test" },
    @{ Key = "approveFinance"; Username = "demo_ext_approve_fin"; DisplayName = "财务审批经理"; Phone = "13900060002"; Email = "approve.fin@example.test" },
    @{ Key = "approveLegal"; Username = "demo_ext_approve_legal"; DisplayName = "法务审批经理"; Phone = "13900060003"; Email = "approve.legal@example.test" },
    @{ Key = "signerA"; Username = "demo_ext_signer_a"; DisplayName = "签订专员A"; Phone = "13900070001"; Email = "signer.a@example.test" },
    @{ Key = "signerB"; Username = "demo_ext_signer_b"; DisplayName = "签订专员B"; Phone = "13900070002"; Email = "signer.b@example.test" },
    @{ Key = "viewer"; Username = "demo_ext_viewer"; DisplayName = "演示查询员"; Phone = "13900080001"; Email = "viewer@example.test" },
    @{ Key = "disabled"; Username = "demo_ext_disabled"; DisplayName = "禁用演示用户"; Phone = "13900090001"; Email = "disabled@example.test" }
)

foreach ($spec in $userSpecs) {
    $users[$spec.Key] = Ensure-User $spec.Username $spec.DisplayName $operatorRoleId $spec.Phone $spec.Email $adminToken
}
Disable-User $users.disabled $adminToken

$runId = Get-Date -Format "yyyyMMddHHmmss"
$files = New-DemoAttachmentFiles $runId

$customerSpecs = @(
    @{ Name = "华东云服务有限公司"; Tel = "13801001001"; Address = "上海市浦东新区演示路 100 号"; Remark = "SaaS 年度服务" },
    @{ Name = "北方制造集团"; Tel = "13801001002"; Address = "北京市海淀区演示路 200 号"; Remark = "设备采购" },
    @{ Name = "南方零售连锁"; Tel = "13801001003"; Address = "广州市天河区演示路 300 号"; Remark = "门店系统" },
    @{ Name = "西部能源科技"; Tel = "13801001004"; Address = "成都市高新区演示路 400 号"; Remark = "运维服务" },
    @{ Name = "远航物流股份"; Tel = "13801001005"; Address = "深圳市南山区演示路 500 号"; Remark = "物流平台" },
    @{ Name = "星河教育咨询"; Tel = "13801001006"; Address = "杭州市西湖区演示路 600 号"; Remark = "培训服务" },
    @{ Name = "蓝海医疗器械"; Tel = "13801001007"; Address = "南京市建邺区演示路 700 号"; Remark = "合规采购" },
    @{ Name = "绿洲物业管理"; Tel = "13801001008"; Address = "苏州市工业园区演示路 800 号"; Remark = "物业外包" }
)

$customers = @()
foreach ($c in $customerSpecs) {
    $name = "$($c.Name) $runId"
    $customers += [pscustomobject]@{
        id = Create-Customer $name $c.Tel $c.Address $c.Remark $adminToken
        name = $name
    }
}

$created = New-Object System.Collections.Generic.List[object]

function Track-Contract {
    param([string]$Label, [long]$Id)
    $created.Add([pscustomobject]@{ label = $Label; id = $Id }) | Out-Null
}

$countersignGroupA = @($users.legal1, $users.finance1, $users.risk1)
$countersignGroupB = @($users.legal2, $users.tech1)
$approverGroupA = @($users.approveMgr, $users.approveFinance)
$approverGroupB = @($users.approveMgr, $users.approveLegal, $users.approveFinance)

$draft = Create-Contract "演示-待分配-含起草附件-$runId" $customers[0].id $customers[0].name $users.drafterA -10 6
Add-Standard-Attachments $draft $files $users.drafterA @("Pdf", "Docx")
Track-Contract "待分配" $draft

$assignedNone = Create-Contract "演示-待会签-尚无人处理-$runId" $customers[1].id $customers[1].name $users.drafterA -8 12
Assign-Contract $assignedNone $countersignGroupA $approverGroupA $users.signerA $adminToken
Add-Standard-Attachments $assignedNone $files $users.drafterA @("Pdf")
Track-Contract "待会签-无人处理" $assignedNone

$assignedPartial = Create-Contract "演示-待会签-部分已会签-$runId" $customers[2].id $customers[2].name $users.drafterB -7 10
Assign-Contract $assignedPartial $countersignGroupA $approverGroupB $users.signerB $adminToken
Countersign-Contract $assignedPartial $users.legal1 "法务条款无异议，财务和风控继续确认。"
Add-Standard-Attachments $assignedPartial $files $users.drafterB @("Png")
Track-Contract "待会签-部分完成" $assignedPartial

$counterSigned = Create-Contract "演示-待定稿-全部会签完成-$runId" $customers[3].id $customers[3].name $users.drafterB -6 9
Assign-Contract $counterSigned $countersignGroupB $approverGroupA $users.signerA $adminToken
$countersignGroupB | ForEach-Object { Countersign-Contract $counterSigned $_ }
Add-Standard-Attachments $counterSigned $files $users.drafterB @("Pdf", "Gif")
Track-Contract "待定稿" $counterSigned

$finalizedNone = Create-Contract "演示-待审批-尚无人审批-$runId" $customers[4].id $customers[4].name $users.drafterC -5 12
Assign-Contract $finalizedNone $countersignGroupB $approverGroupB $users.signerB $adminToken
$countersignGroupB | ForEach-Object { Countersign-Contract $finalizedNone $_ }
Finalize-Contract $finalizedNone $users.drafterC "待审批-尚无人审批"
Add-Standard-Attachments $finalizedNone $files $users.drafterC @("Docx")
Track-Contract "待审批-无人处理" $finalizedNone

$finalizedPartial = Create-Contract "演示-待审批-部分已通过-$runId" $customers[5].id $customers[5].name $users.drafterC -4 12
Assign-Contract $finalizedPartial $countersignGroupA $approverGroupB $users.signerA $adminToken
$countersignGroupA | ForEach-Object { Countersign-Contract $finalizedPartial $_ }
Finalize-Contract $finalizedPartial $users.drafterC "待审批-部分已通过"
Approve-Contract $finalizedPartial $users.approveMgr "APPROVED" "部门审批通过，等待法务和财务审批。"
Add-Standard-Attachments $finalizedPartial $files $users.drafterC @("Pdf", "Png")
Track-Contract "待审批-部分通过" $finalizedPartial

$rejected = Create-Contract "演示-已拒绝-审批退回-$runId" $customers[6].id $customers[6].name $users.drafterA -3 8
Assign-Contract $rejected $countersignGroupB $approverGroupA $users.signerA $adminToken
$countersignGroupB | ForEach-Object { Countersign-Contract $rejected $_ }
Finalize-Contract $rejected $users.drafterA "已拒绝"
Approve-Contract $rejected $users.approveMgr "REJECTED" "预算附件缺失，退回补充。"
Add-Standard-Attachments $rejected $files $users.drafterA @("Pdf")
Track-Contract "已拒绝" $rejected

$resubmitted = Create-Contract "演示-重新提交后待审批-$runId" $customers[7].id $customers[7].name $users.drafterB -2 8
Assign-Contract $resubmitted $countersignGroupA $approverGroupA $users.signerB $adminToken
$countersignGroupA | ForEach-Object { Countersign-Contract $resubmitted $_ }
Finalize-Contract $resubmitted $users.drafterB "重新提交前"
Approve-Contract $resubmitted $users.approveFinance "REJECTED" "补充付款节点后重新提交。"
Invoke-Api -Method POST -Path "/contracts/$resubmitted/resubmit" -Token $users.drafterB.token | Out-Null
Add-Standard-Attachments $resubmitted $files $users.drafterB @("Docx", "Pdf")
Track-Contract "重新提交后待审批" $resubmitted

$approved = Create-Contract "演示-待签订-审批已全部通过-$runId" $customers[0].id $customers[0].name $users.drafterA -1 12
Assign-Contract $approved $countersignGroupB $approverGroupA $users.signerA $adminToken
$countersignGroupB | ForEach-Object { Countersign-Contract $approved $_ }
Finalize-Contract $approved $users.drafterA "待签订"
$approverGroupA | ForEach-Object { Approve-Contract $approved $_ }
Add-Standard-Attachments $approved $files $users.drafterA @("Pdf", "Docx", "Png")
Track-Contract "待签订" $approved

$signed = Create-Contract "演示-已签订-完整归档-$runId" $customers[1].id $customers[1].name $users.drafterB -20 24
Assign-Contract $signed $countersignGroupA $approverGroupB $users.signerB $adminToken
$countersignGroupA | ForEach-Object { Countersign-Contract $signed $_ }
Finalize-Contract $signed $users.drafterB "已签订"
$approverGroupB | ForEach-Object { Approve-Contract $signed $_ }
Sign-Contract $signed $users.signerB
Add-Standard-Attachments $signed $files $users.drafterB @("Pdf", "Docx", "Png", "Gif")
Track-Contract "已签订" $signed

$cancelledDraft = Create-Contract "演示-已取消-起草后取消-$runId" $customers[2].id $customers[2].name $users.drafterC 1 6
Invoke-Api -Method POST -Path "/contracts/$cancelledDraft/cancel" -Token $adminToken | Out-Null
Track-Contract "已取消-起草后取消" $cancelledDraft

$cancelledFlow = Create-Contract "演示-已取消-分配后取消-$runId" $customers[3].id $customers[3].name $users.drafterC 2 6
Assign-Contract $cancelledFlow $countersignGroupB $approverGroupA $users.signerA $adminToken
Countersign-Contract $cancelledFlow $users.legal2
Add-Standard-Attachments $cancelledFlow $files $users.drafterC @("Pdf")
Invoke-Api -Method POST -Path "/contracts/$cancelledFlow/cancel" -Token $adminToken | Out-Null
Track-Contract "已取消-分配后取消" $cancelledFlow

for ($i = 0; $i -lt 6; $i++) {
    $drafter = @($users.drafterA, $users.drafterB, $users.drafterC)[$i % 3]
    $customer = $customers[$i % $customers.Count]
    $extra = Create-Contract "演示-填充草稿-$($i + 1)-$runId" $customer.id $customer.name $drafter ($i + 3) (6 + $i)
    if ($i % 2 -eq 0) {
        Add-Standard-Attachments $extra $files $drafter @("Pdf")
    }
    Track-Contract "填充草稿 $($i + 1)" $extra
}

Write-Host "Rich demo data created."
Write-Host ""
Write-Host "Run ID: $runId"
Write-Host "All enabled demo account passwords: $DemoPassword"
Write-Host ""
Write-Host "Users:"
$userSpecs | ForEach-Object {
    $suffix = if ($_.Key -eq "disabled") { " (DISABLED)" } else { "" }
    Write-Host ("  {0,-24} {1}{2}" -f $_.Username, $_.DisplayName, $suffix)
}
Write-Host ""
Write-Host "Customers: $($customers.Count)"
Write-Host "Contracts:"
$created | ForEach-Object {
    Write-Host ("  {0,-24} #{1}" -f $_.label, $_.id)
}
Write-Host ""
Write-Host "Blank sample attachments generated and uploaded from:"
Write-Host "  $($files.Pdf)"
Write-Host "  $($files.Docx)"
Write-Host "  $($files.Png)"
Write-Host "  $($files.Gif)"



