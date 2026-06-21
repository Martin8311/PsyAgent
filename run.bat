@echo off
rem MindBridge one-click start. Credentials are loaded from env-local.bat (gitignored).
setlocal
set JAVA_HOME=D:\java17
cd /d %~dp0

rem Load local secrets (MySQL/Redis/RabbitMQ/QQ mail)
if exist "%~dp0env-local.bat" call "%~dp0env-local.bat"

if "%MYSQL_PASSWORD%"=="" (
  echo [ERROR] MYSQL_PASSWORD is not set. Configure it in env-local.bat
  goto :end
)
if "%REDIS_PASSWORD%"=="" (
  echo [ERROR] REDIS_PASSWORD is not set. Configure it in env-local.bat
  goto :end
)
if "%MAIL_USERNAME%"=="" (
  echo [WARN] MAIL_USERNAME not set - email alerts disabled, app still starts. See env-local.bat
)

rem Ensure RabbitMQ container is up (ignored if already running)
docker start poker-rabbitmq >nul 2>&1

echo [MindBridge] JAVA_HOME=%JAVA_HOME%
echo [MindBridge] starting Spring Boot at http://localhost:8080 ...
call "%~dp0mvnw.cmd" spring-boot:run
:end
endlocal
