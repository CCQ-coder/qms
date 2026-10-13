@echo off
cd /d "%~dp0"
"%~dp0HosonMesMock.exe" %*
if errorlevel 1 pause
