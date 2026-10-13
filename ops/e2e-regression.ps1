param(
    [string]$MySqlUser = 'root',
    [string]$MySqlPassword = '123456',
    [switch]$SkipBuild,
    [switch]$KeepDatabase
)

$ErrorActionPreference = 'Stop'
$storeRoot = Split-Path $PSScriptRoot -Parent
$factoryRoot = Join-Path (Split-Path $storeRoot -Parent) 'laundryfactory--b'
$database = 'laundry_e2e_test'
$storePort = 18080
$factoryPort = 18081
$device = 'e2e-device'
$deviceToken = 'e2e-device-token-at-least-24-characters'
$env:MYSQL_PWD = $MySqlPassword
$processes = @()
$logDir = Join-Path ([IO.Path]::GetTempPath()) ('laundry-e2e-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $logDir | Out-Null

function Invoke-MySql([string]$Sql, [string]$Db = '') {
    $args = @("-u$MySqlUser", '--batch', '--skip-column-names')
    if ($Db) { $args += $Db }
    $Sql | & mysql @args
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed" }
}

function Reset-TestDatabase {
    Invoke-MySql "DROP DATABASE IF EXISTS $database; CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    & mysqldump "-u$MySqlUser" --no-data --skip-triggers --single-transaction laundry_db |
        & mysql "-u$MySqlUser" $database
    if ($LASTEXITCODE -ne 0) { throw 'Schema copy failed' }
    foreach ($table in @('store','clothes_category','member_card_type','factory_sort_type',
            'factory_system_config','schema_installation_guard','flyway_schema_history','shelf_config')) {
        Invoke-MySql "INSERT INTO $database.$table SELECT * FROM laundry_db.$table;"
    }
}

function Wait-Http([string]$Url) {
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-RestMethod -Uri $Url -TimeoutSec 2 | Out-Null; return }
        catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Service did not start: $Url"
}

function Wait-Port([int]$Port) {
    for ($i = 0; $i -lt 60; $i++) {
        $client = [Net.Sockets.TcpClient]::new()
        try { $client.Connect('127.0.0.1', $Port); return }
        catch { Start-Sleep -Milliseconds 500 }
        finally { $client.Dispose() }
    }
    throw "Service did not start on port $Port"
}

function Invoke-Json([string]$Method, [string]$Url, $Body = $null, [hashtable]$Headers = @{}) {
    $parameters = @{ Method = $Method; Uri = $Url; Headers = $Headers; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    $response = Invoke-RestMethod @parameters
    if ($response.code -ne 200) { throw "API failed: $Url - $($response.message)" }
    return $response.data
}

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ([string]$Expected -ne [string]$Actual) {
        throw "$Message; expected=$Expected actual=$Actual"
    }
}

try {
    Reset-TestDatabase
    if (-not $SkipBuild) {
        & mvn -q -f (Join-Path $storeRoot 'pom.xml') clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Store backend build failed' }
        & mvn -q -f (Join-Path $factoryRoot 'pom.xml') clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Factory backend build failed' }
    }

    $storeJar = Get-ChildItem (Join-Path $storeRoot 'target\*.jar') |
        Where-Object Name -NotLike '*.original' | Select-Object -First 1
    $factoryJar = Get-ChildItem (Join-Path $factoryRoot 'target\*.jar') |
        Where-Object Name -NotLike '*.original' | Select-Object -First 1
    if (-not $storeJar -or -not $factoryJar) { throw 'Backend jar not found' }

    $dbUrl = "jdbc:mysql://localhost:3306/$database`?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    $processes += Start-Process java -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logDir\store.log" -RedirectStandardError "$logDir\store.err.log" -ArgumentList @(
        '-jar', $storeJar.FullName, "--server.port=$storePort", "--spring.datasource.url=$dbUrl",
        "--spring.datasource.username=$MySqlUser", "--spring.datasource.password=$MySqlPassword",
        '--app.seed-default-users=true', '--spring.rabbitmq.listener.simple.auto-startup=false',
        '--app.rabbitmq.outbox-poll-ms=3600000', '--knife4j.enable=false', '--springdoc.api-docs.enabled=false')
    $processes += Start-Process java -WindowStyle Hidden -PassThru -RedirectStandardOutput "$logDir\factory.log" -RedirectStandardError "$logDir\factory.err.log" -ArgumentList @(
        '-jar', $factoryJar.FullName, "--server.port=$factoryPort", "--spring.datasource.url=$dbUrl",
        "--spring.datasource.username=$MySqlUser", "--spring.datasource.password=$MySqlPassword",
        "--factory.security.device-tokens=$device`:$deviceToken", '--knife4j.enable=false', '--springdoc.api-docs.enabled=false')

    $storeBase = "http://127.0.0.1:$storePort"
    $factoryBase = "http://127.0.0.1:$factoryPort"
    Wait-Http "$factoryBase/api/factory/public/status"
    Wait-Port $storePort

    $login = Invoke-Json POST "$storeBase/api/auth/login" @{ username='admin'; password='admin123' }
    $storeHeaders = @{ Authorization = "Bearer $($login.token)" }
    $factoryHeaders = @{ Authorization = "Bearer $deviceToken"; 'X-Factory-Device' = $device }

    $catalog = Invoke-Json GET "$storeBase/api/category/list" $null $storeHeaders
    $clothes = $catalog | Where-Object { $_.categoryGroup -eq 'CLOTHES' } | Select-Object -First 1
    $shoes = $catalog | Where-Object { $_.categoryGroup -eq 'SHOES' } | Select-Object -First 1
    if (-not $clothes -or -not $shoes) { throw 'Need enabled clothes and shoes categories' }
    $categories = @($clothes, $shoes)
    $total = [decimal]$categories[0].price + [decimal]$categories[1].price
    $phone = '139' + (Get-Random -Minimum 10000000 -Maximum 99999999)
    $items = @($categories | ForEach-Object { @{
        categoryId=$_.id; quantity=1; unitPrice=$_.price; color='测试色'; brand='E2E'; size='M'
    } })
    $received = Invoke-Json POST "$storeBase/api/order/receive" @{
        requestId=('e2e-' + [guid]::NewGuid().ToString('N')); customerName='端到端测试'; customerPhone=$phone
        newCardFlag=0; rechargeFlag=0; items=$items; paymentMethod='CASH'; totalPaid=$total; urgentFlag=0
        remark='automated e2e regression'
    } $storeHeaders
    Assert-Equal 2 $received.totalCount 'Receive item count'
    Assert-Equal $total $received.totalPaid 'Receive accounting'

    $sent = Invoke-Json POST "$storeBase/api/factory-dispatch/create" @{ orderIds=@($received.orderId); remark='e2e' } $storeHeaders
    Assert-Equal 1 $sent.orderCount 'Dispatch order count'

    $imported = Invoke-Json POST "$factoryBase/api/factory/workflow/manual-import" @{ orderNo=$received.orderNo } $factoryHeaders
    Assert-Equal 'MIXED' $imported.currentProcess 'Factory import state'
    Invoke-Json POST "$factoryBase/api/factory/workflow/confirm" @{ orderNo=$received.orderNo; process='WASH'; remark='e2e' } $factoryHeaders | Out-Null
    Invoke-Json POST "$factoryBase/api/factory/workflow/confirm" @{ orderNo=$received.orderNo; process='SHOE_WASH'; remark='e2e' } $factoryHeaders | Out-Null
    $qualified = Invoke-Json POST "$factoryBase/api/factory/workflow/confirm" @{
        orderNo=$received.orderNo; process='QUALITY'; qualityResult='PASSED'; remark='e2e'; photoPaths=@()
    } $factoryHeaders
    Assert-Equal 'PACK' $qualified.currentProcess 'Quality state'
    $packed = Invoke-Json POST "$factoryBase/api/factory/workflow/confirm" @{ orderNo=$received.orderNo; process='PACK'; remark='e2e' } $factoryHeaders
    Assert-Equal 'RETURN' $packed.currentProcess 'Pack state'

    $ready = Invoke-Json GET "$factoryBase/api/factory/return-dispatch/ready" $null $factoryHeaders
    $package = $ready | Where-Object { $_.orderNo -eq $received.orderNo } | Select-Object -First 1
    if (-not $package) { throw 'Packed order not ready for return' }
    $returned = Invoke-Json POST "$factoryBase/api/factory/return-dispatch/dispatch" @{ packageIds=@($package.id) } $factoryHeaders
    Assert-Equal 2 $returned.itemCount 'Return item count'

    $batches = Invoke-Json GET "$storeBase/api/store-return/batches" $null $storeHeaders
    $batch = $batches | Where-Object { $_.batchNo -eq $returned.batchNo } | Select-Object -First 1
    if (-not $batch) { throw 'Return batch not visible to store' }
    $detail = Invoke-Json GET "$storeBase/api/store-return/batches/$($batch.id)/orders/$($received.orderNo)" $null $storeHeaders
    foreach ($item in $detail.items) {
        $scan = Invoke-Json POST "$storeBase/api/store-return/batches/$($batch.id)/orders/$($received.orderNo)/scan" @{ barcode=$item.barcode } $storeHeaders
        if (-not $scan.assignedShelfNo) { throw 'Shelf was not assigned' }
    }

    $pickup = Invoke-Json POST "$storeBase/api/pickup/lookup" @{ identifier=$phone } $storeHeaders
    Assert-Equal 2 $pickup.itemCount 'Pickup item count'
    $pickupItems = @($pickup.orders[0].items)
    $partial = Invoke-Json POST "$storeBase/api/pickup/close" @{ identifier=$phone; itemIds=@($pickupItems[0].id) } $storeHeaders
    Assert-Equal 1 $partial.remainingCount 'Partial pickup remaining count'
    $closed = Invoke-Json POST "$storeBase/api/pickup/close" @{ identifier=$phone; itemIds=@($pickupItems[1].id) } $storeHeaders
    Assert-Equal 0 $closed.remainingCount 'Final pickup remaining count'

    $audit = Invoke-MySql "SELECT CONCAT(o.status,'|',COUNT(i.id),'|',SUM(i.status='PICKED_UP'),'|',SUM(i.shelf_status=2),'|',o.total_paid,'|',o.debt_amount) FROM laundry_order o JOIN order_item i ON i.order_id=o.id WHERE o.id=$($received.orderId) GROUP BY o.id;" $database
    $expectedPaid = '{0:F2}' -f $total
    Assert-Equal "PICKED_UP|2|2|2|$expectedPaid|0.00" ($audit | Select-Object -First 1) 'Final database state'
    Write-Host "E2E_OK order=$($received.orderNo) items=2 status=PICKED_UP paid=$total debt=0.00"
}
finally {
    foreach ($process in $processes) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    }
    if (-not $KeepDatabase) { Invoke-MySql "DROP DATABASE IF EXISTS $database;" }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    Write-Host "Logs: $logDir"
}
