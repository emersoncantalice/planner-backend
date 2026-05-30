@echo off
taskkill /FI "WINDOWTITLE eq Planner Backend" /T /F >nul 2>nul
echo Backend finalizado.
exit /b 0
