@echo off
cd /d "%~dp0"
title R-Connect Client
echo ========================================================
echo   R-CONNECT DESKTOP CLIENT - GIAO DIEN NGUOI DUNG
echo ========================================================
java -Xmx256m -Dfile.encoding=UTF-8 -Dsocial.host=127.0.0.1 -Dsocial.port=5000 -jar client/target/client-1.0.0-SNAPSHOT-shaded.jar
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Client gap loi hoac da tat.
    pause
)
