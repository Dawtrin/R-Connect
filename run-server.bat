@echo off
cd /d "%~dp0"
title R-Connect Server Dashboard
echo ========================================================
echo   R-CONNECT SOCIAL SERVER - KHOI DONG SERVER VA MONITOR
echo ========================================================
java -Xmx256m -Dfile.encoding=UTF-8 -jar server/target/server-1.0.0-SNAPSHOT-shaded.jar server/config/social.properties
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Server gap loi hoac da tat.
    pause
)
