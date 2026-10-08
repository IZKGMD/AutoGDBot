@echo off
title AutoGDBot Windows Bridge
powershell.exe -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0Setup.ps1"
echo.
pause
