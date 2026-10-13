@echo off
cd /d "%~dp0"
if "%~1"=="" (
  "%~dp0HosonMesMock.exe"
) else (
  "%~dp0HosonMesMockCli.exe" %*
  if errorlevel 1 pause
)
