@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 if not defined JAVA_HOME (
  echo Для сборки установите JDK 21 x64 и настройте JAVA_HOME или PATH.
  pause
  exit /b 1
)
call "%~dp0gradlew.bat" clean test :lwjgl3:distZip
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo Проверки или сборка завершились с ошибкой. Смотрите сообщение выше.
  pause
  exit /b %RESULT%
)
echo Готово: lwjgl3\build\distributions\ctrl-life-0.6.0.zip
pause
exit /b 0
