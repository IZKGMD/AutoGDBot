@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
set "JAVA_CMD="
if exist "%~dp0runtime\bin\java.exe" set "JAVA_CMD=%~dp0runtime\bin\java.exe"
if not defined JAVA_CMD if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_CMD for %%J in (java.exe) do set "JAVA_CMD=%%~$PATH:J"
if not defined JAVA_CMD (
  echo Не найдена Java 21. Распакуйте полный Windows-архив или установите JDK 21 x64.
  echo https://adoptium.net/temurin/releases/?version=21
  pause
  exit /b 1
)
if exist "%~dp0lib\core-*.jar" goto packaged
if exist "%~dp0gradlew.bat" goto source
 echo Не найдены файлы игры. Сначала извлеките ZIP целиком, затем запустите run.bat.
pause
exit /b 1
:packaged
"%JAVA_CMD%" -Dfile.encoding=UTF-8 -cp "%~dp0lib\*" life.ctrl.desktop.DesktopLauncher %*
set "RESULT=%ERRORLEVEL%"
if "%~1"=="--check-install" exit /b %RESULT%
if not "%RESULT%"=="0" (
  echo Игра завершилась с ошибкой. Требуются Java 21 x64 и поддержка OpenGL 2.0.
  echo Текст ошибки показан выше. Сохранения находятся в %%LOCALAPPDATA%%\CtrlLife.
  pause
)
exit /b %RESULT%
:source
call "%~dp0gradlew.bat" :lwjgl3:run
set "RESULT=%ERRORLEVEL%"
if "%~1"=="--check-install" exit /b %RESULT%
if not "%RESULT%"=="0" (
  echo Сборка или запуск не удались. Для исходников нужен именно JDK 21, а не только JRE.
  pause
)
exit /b %RESULT%
