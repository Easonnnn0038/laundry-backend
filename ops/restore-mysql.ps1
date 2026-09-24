param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'laundry_db_restore_test' }),
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$UserName = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' })
)

$ErrorActionPreference = 'Stop'
if (-not $env:DB_PASSWORD) { throw '请先设置环境变量 DB_PASSWORD' }
$source = [System.IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "备份文件不存在: $source" }
if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw '数据库名只能包含字母、数字和下划线' }

$oldMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & mysql --host=$HostName --port=$Port --user=$UserName --execute="CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) { throw '创建恢复数据库失败' }
    $process = Start-Process mysql -NoNewWindow -Wait -PassThru `
        -ArgumentList @("--host=$HostName", "--port=$Port", "--user=$UserName", $Database) `
        -RedirectStandardInput $source
    if ($process.ExitCode -ne 0) { throw "恢复失败，退出码 $($process.ExitCode)" }
} finally {
    if ($null -eq $oldMysqlPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $oldMysqlPwd }
}

Write-Output "恢复完成: $source -> $Database"
