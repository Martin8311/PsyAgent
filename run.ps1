# MindBridge start script (PowerShell)
# Requires env vars: MYSQL_PASSWORD, REDIS_PASSWORD
$env:JAVA_HOME = "D:\java17"

if (-not $env:MYSQL_PASSWORD) {
    Write-Host "[ERROR] MYSQL_PASSWORD is not set. e.g.  `$env:MYSQL_PASSWORD='your_pwd'" -ForegroundColor Red
    return
}
if (-not $env:REDIS_PASSWORD) {
    Write-Host "[ERROR] REDIS_PASSWORD is not set. e.g.  `$env:REDIS_PASSWORD='your_pwd'" -ForegroundColor Red
    return
}

Write-Host "[MindBridge] JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "[MindBridge] starting Spring Boot at http://localhost:8080 ..." -ForegroundColor Cyan
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
