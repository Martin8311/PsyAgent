# MindBridge start script (PowerShell)
# Usage: ./run.ps1
$env:JAVA_HOME = "D:\java17"
Write-Host "[MindBridge] JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "[MindBridge] starting Spring Boot at http://localhost:8080 ..." -ForegroundColor Cyan
& "$PSScriptRoot\mvnw.cmd" spring-boot:run
