@echo off
setlocal
set SCRIPT_DIR=%~dp0
set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" exit /b 1
"%CSC%" /nologo /target:exe /out:"%TEMP%\QmsGatewayAuthSelfTest.exe" /main:AuthSelfTest /reference:System.dll /reference:System.Core.dll /reference:System.Security.dll /reference:System.Web.Extensions.dll /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "%SCRIPT_DIR%JingdeEapForwarder.cs" "%SCRIPT_DIR%AuthSelfTest.cs"
if errorlevel 1 exit /b %errorlevel%
"%TEMP%\QmsGatewayAuthSelfTest.exe"
exit /b %errorlevel%
