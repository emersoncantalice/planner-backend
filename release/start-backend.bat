@echo off
setlocal

set APP_DIR=%~dp0
for %%I in ("%APP_DIR%") do set APP_DIR=%%~fI
set BACKEND_JAR=%APP_DIR%planner-backend.jar
set DEFAULT_DATA_DIR=%APP_DIR%data

if not exist "%BACKEND_JAR%" (
  echo ERRO: JAR nao encontrado em %BACKEND_JAR%
  pause
  exit /b 1
)

echo Informe a pasta de dados (ENTER para padrao):
echo %DEFAULT_DATA_DIR%
set /p DATA_DIR=Data dir: 
if "%DATA_DIR%"=="" set DATA_DIR=%DEFAULT_DATA_DIR%
set "DATA_DIR=%DATA_DIR:"=%"
for %%I in ("%DATA_DIR%") do set DATA_DIR=%%~fI
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

set "PLANNER_DATA_DIR=%DATA_DIR%"

:: Porta do backend: definida pelo lancador via PLANNER_SERVER_PORT; senao usa o default do app.
set "PORT_ARG="
if not "%PLANNER_SERVER_PORT%"=="" set "PORT_ARG=-Dserver.port=%PLANNER_SERVER_PORT%"

:: CORS: apenas a origem do front. O lancador define PLANNER_CORS_ORIGINS com o IP atual da maquina.
:: Standalone (sem lancador), libera o front local em 8080.
if "%PLANNER_CORS_ORIGINS%"=="" set "PLANNER_CORS_ORIGINS=http://localhost:8080"

pushd "%APP_DIR%"
start "Planner Backend" cmd /k java -Dplanner.data-dir="%DATA_DIR%" %PORT_ARG% -Dapp.cors.allowed-origins="%PLANNER_CORS_ORIGINS%" -jar "%BACKEND_JAR%"
popd
exit /b 0
