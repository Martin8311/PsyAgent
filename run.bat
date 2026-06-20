@echo off
rem MindBridge - one-click start (command line)
rem Usage: run.bat   (or double click)
setlocal
set JAVA_HOME=D:\java17
cd /d %~dp0
echo [MindBridge] JAVA_HOME=%JAVA_HOME%
echo [MindBridge] starting Spring Boot at http://localhost:8080 ...
call mvnw.cmd spring-boot:run
endlocal
