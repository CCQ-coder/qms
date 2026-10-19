@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0"

if not exist ".env.windows" (
    echo [QMS] Missing .env.windows. Follow RUN_WINDOWS.md for first-time setup.
    pause
    exit /b 1
)
if not exist "QMS.exe" (
    echo [QMS] Missing QMS.exe. Extract the full Windows package first.
    pause
    exit /b 1
)

rem Read KEY=value without PowerShell so unsigned-script policy cannot block startup.
rem Delayed expansion stays disabled to preserve ! in passwords and URLs.
for /f "usebackq eol=# delims=" %%L in (".env.windows") do set "%%L"

if not defined QMS_DB_URL goto missing_config
if not defined QMS_DB_USERNAME goto missing_config
if not defined QMS_DB_PASSWORD goto missing_config
if not defined QMS_LOCAL_LOGIN_JWT_SECRET goto missing_config

set "QMS_SERVE_FRONTEND=true"
set "QMS_SECURITY_DEVELOPMENT_MODE=false"
set "QMS_LOCAL_PASSWORD_LOGIN_ENABLED=true"
set "QMS_OCR_ENABLED=false"
set "LOGGING_FILE_NAME=%CD%\logs\qms-api.log"
set "QMS_ATTACHMENT_LOCAL_ROOT=%CD%\data\uploads"
if not exist "logs" mkdir "logs"
if not exist "data\uploads" mkdir "data\uploads"

".\QMS.exe"
set "QMS_EXIT_CODE=%ERRORLEVEL%"
if not "%QMS_EXIT_CODE%"=="0" (
    echo.
    echo [QMS] Exited with code %QMS_EXIT_CODE%. Check logs\qms-api.log and the message above.
    pause
)
exit /b %QMS_EXIT_CODE%

:missing_config
echo [QMS] Missing required value in .env.windows. Check RUN_WINDOWS.md.
pause
exit /b 1
