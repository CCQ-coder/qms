@echo off
setlocal
set SCRIPT_DIR=%~dp0
set OUT_DIR=%SCRIPT_DIR%..\..\outputs\hoson-mes-mock
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" (
  echo .NET Framework 4.x compiler was not found.
  exit /b 1
)

"%CSC%" /nologo /target:winexe /main:HosonMesMock /platform:anycpu /optimize+ /win32icon:"%SCRIPT_DIR%lyr-mes.ico" /out:"%OUT_DIR%\HosonMesMock.exe" /reference:System.dll /reference:System.Core.dll /reference:System.Web.Extensions.dll /reference:System.Security.dll /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:"%SCRIPT_DIR%lib\M2Mqtt.dll" "%SCRIPT_DIR%HosonMesMock.cs" "%SCRIPT_DIR%HosonMesMockCli.cs" "%SCRIPT_DIR%HosonMesServer.cs" "%SCRIPT_DIR%MesConfig.cs" "%SCRIPT_DIR%S1MqttConfiguration.cs" "%SCRIPT_DIR%HosonDeviceSessionStore.cs" "%SCRIPT_DIR%S1MqttPipeline.cs" "%SCRIPT_DIR%S1MqttPublisher.cs" "%SCRIPT_DIR%S1DeviceMappingDialog.cs"
if errorlevel 1 exit /b %errorlevel%

if not exist "%OUT_DIR%\mes_server_config.json" copy /Y "%SCRIPT_DIR%mes_server_config.json" "%OUT_DIR%\mes_server_config.json" >nul
if not exist "%OUT_DIR%\mes_server_device_config.json" copy /Y "%SCRIPT_DIR%mes_server_device_config.json" "%OUT_DIR%\mes_server_device_config.json" >nul
if not exist "%OUT_DIR%\s1_mqtt_config.json" copy /Y "%SCRIPT_DIR%s1_mqtt_config.json" "%OUT_DIR%\s1_mqtt_config.json" >nul
copy /Y "%SCRIPT_DIR%mes_server_defaults.json" "%OUT_DIR%\mes_server_defaults.json" >nul
copy /Y "%SCRIPT_DIR%start-mock.cmd" "%OUT_DIR%\start-mock.cmd" >nul
copy /Y "%SCRIPT_DIR%README.md" "%OUT_DIR%\README.md" >nul
copy /Y "%SCRIPT_DIR%lib\M2Mqtt.dll" "%OUT_DIR%\M2Mqtt.dll" >nul
echo Build complete: %OUT_DIR%\HosonMesMock.exe
