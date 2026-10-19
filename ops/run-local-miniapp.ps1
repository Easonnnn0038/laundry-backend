param([string]$TestPhone = '19900000000')

$ErrorActionPreference = 'Stop'
$storeRoot = Split-Path $PSScriptRoot -Parent
$miniappConfig = Join-Path (Split-Path (Split-Path $storeRoot -Parent) -Parent) 'WeChatProjects\xiaomubanglaundryminiprogram\dev.config.js'
$jar = Join-Path $storeRoot 'target\laundry-backend-1.0.0.jar'

if ($TestPhone -notmatch '^1\d{10}$') { throw 'Test phone must contain 11 digits.' }
if (-not (Test-Path -LiteralPath $miniappConfig)) { throw 'Miniapp local config was not found.' }
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is in use. Stop the store backend in IDEA, then rerun this script.'
}

$testKey = & node -e "process.stdout.write(require(process.argv[1]).testLoginKey || '')" $miniappConfig
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($testKey)) { throw 'Miniapp local config has no test login key.' }
$apiBaseUrl = & node -e "process.stdout.write(require(process.argv[1]).apiBaseUrl || '')" $miniappConfig
if ($apiBaseUrl -ne 'http://127.0.0.1:8080/api/miniapp') { throw 'Miniapp API URL must point to local port 8080.' }

$previous = @{
    DB_PASSWORD = $env:DB_PASSWORD
    MINIAPP_TEST_LOGIN_ENABLED = $env:MINIAPP_TEST_LOGIN_ENABLED
    MINIAPP_TEST_LOGIN_KEY = $env:MINIAPP_TEST_LOGIN_KEY
    MINIAPP_TEST_PHONE = $env:MINIAPP_TEST_PHONE
    MINIAPP_TEST_ACCOUNT_PHONE = $env:MINIAPP_TEST_ACCOUNT_PHONE
    MINIAPP_TEST_ACCOUNT_PASSWORD = $env:MINIAPP_TEST_ACCOUNT_PASSWORD
}
try {
    $accountConfig = Join-Path $PSScriptRoot 'local-miniapp-test-account.ps1'
    if (Test-Path -LiteralPath $accountConfig) { . $accountConfig }
    if (-not $env:DB_PASSWORD) {
        $securePassword = Read-Host 'Enter local MySQL root password (hidden)' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }

    $backupDir = Join-Path $storeRoot ('backups\miniapp-local-' + [guid]::NewGuid().ToString('N'))
    & (Join-Path $PSScriptRoot 'backup-mysql.ps1') -BackupDirectory $backupDir `
        -Database 'laundry_db' -HostName 'localhost' -Port 3306 -UserName 'root'
    if (-not $?) { throw 'Local database backup failed. Startup was stopped.' }

    Push-Location $storeRoot
    try { & mvn -q -DskipTests package }
    finally { Pop-Location }
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $jar)) { throw 'Store backend build failed.' }

    $env:MINIAPP_TEST_LOGIN_ENABLED = 'true'
    $env:MINIAPP_TEST_LOGIN_KEY = $testKey
    $env:MINIAPP_TEST_PHONE = $TestPhone
    Write-Host 'Starting local miniapp test backend on 127.0.0.1:8080. Press Ctrl+C to stop.'
    & java -jar $jar '--spring.profiles.active=local-miniapp' '--server.address=127.0.0.1' '--server.port=8080'
    if ($LASTEXITCODE -ne 0) { throw "Store backend exited with code $LASTEXITCODE" }
} finally {
    foreach ($name in $previous.Keys) {
        if ($null -eq $previous[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$name" $previous[$name] }
    }
}
