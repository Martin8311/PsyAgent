# MindBridge start script (PowerShell)
# Credentials are loaded from env-local.ps1 (gitignored).
$env:JAVA_HOME = "D:\java17"

# Load local secrets
if (Test-Path "$PSScriptRoot\env-local.ps1") { . "$PSScriptRoot\env-local.ps1" }

if (-not $env:MYSQL_PASSWORD) {
    Write-Host "[ERROR] MYSQL_PASSWORD is not set. Configure it in env-local.ps1" -ForegroundColor Red
    return
}
if (-not $env:REDIS_PASSWORD) {
    Write-Host "[ERROR] REDIS_PASSWORD is not set. Configure it in env-local.ps1" -ForegroundColor Red
    return
}
if (-not $env:MAIL_USERNAME) {
    Write-Host "[WARN] MAIL_USERNAME not set - email alerts disabled, app still starts. See env-local.ps1" -ForegroundColor Yellow
}

# Ensure RabbitMQ container is up
docker start poker-rabbitmq 2>$null | Out-Null

Write-Host "[MindBridge] JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "[MindBridge] starting Spring Boot at http://localhost:8080 ..." -ForegroundColor Cyan
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
