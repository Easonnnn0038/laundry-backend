param(
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '..\backups'),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'laundry_db' }),
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$UserName = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' })
)

$ErrorActionPreference = 'Stop'
if (-not $env:DB_PASSWORD) { throw '请先设置环境变量 DB_PASSWORD' }

$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $backupRoot "$Database-$stamp.sql"

$oldMysqlPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & mysqldump --host=$HostName --port=$Port --user=$UserName `
        --single-transaction --routines --triggers --events `
        --default-character-set=utf8mb4 --result-file=$backupFile $Database
    if ($LASTEXITCODE -ne 0) { throw "mysqldump失败，退出码 $LASTEXITCODE" }
} finally {
    if ($null -eq $oldMysqlPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $oldMysqlPwd }
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile
"$($hash.Hash)  $([System.IO.Path]::GetFileName($backupFile))" |
    Set-Content -Encoding ascii -LiteralPath "$backupFile.sha256"
Write-Output "备份完成: $backupFile"
Write-Output "SHA256: $($hash.Hash)"
