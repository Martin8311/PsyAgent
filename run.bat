@echo off
rem MindBridge - one-click start (command line)
rem Requires env vars: MYSQL_PASSWORD, REDIS_PASSWORD
setlocal
set JAVA_HOME=D:\java17
cd /d %~dp0

if "%MYSQL_PASSWORD%"=="" (
  echo [ERROR] MYSQL_PASSWORD is not set.
  echo   Set it for this window:   set MYSQL_PASSWORD=your_mysql_password
  echo   Or set it permanently:    setx MYSQL_PASSWORD "your_mysql_password"
  goto :end
)
if "%REDIS_PASSWORD%"=="" (
  echo [ERROR] REDIS_PASSWORD is not set.
  echo   Set it for this window:   set REDIS_PASSWORD=your_redis_password
  echo   Or set it permanently:    setx REDIS_PASSWORD "your_redis_password"
  goto :end
)

echo [MindBridge] JAVA_HOME=%JAVA_HOME%
echo [MindBridge] starting Spring Boot at http://localhost:8080 ...
call mvnw.cmd spring-boot:run
:end
endlocal
