@echo off
cd /d "%~dp0"
title R-Connect Resource Sync
echo ========================================================
echo   DONG BO LICH SU TIN NHAN VA TAI KHOAN R-CONNECT
echo ========================================================
python tools/export_user_resources.py
echo.
echo Da cap nhat toan bo file txt va json trong resources/accounts/!
pause
