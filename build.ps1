# MindBridge build script (PowerShell)
# Usage: ./build.ps1          compile only
#        ./build.ps1 package  build runnable jar (target/*.jar)
param([string]$goal = "compile")
$env:JAVA_HOME = "D:\java17"
Write-Host "[MindBridge] JAVA_HOME=$env:JAVA_HOME  goal=$goal" -ForegroundColor Cyan
& "$PSScriptRoot\mvnw.cmd" -B $goal
