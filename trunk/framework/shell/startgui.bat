@echo off
set SNOVA_HOME=%~dp0\..
@start javaw -cp "%~dp0\..\lib\framework.jar;%~dp0\..\conf" "-DSNOVA_HOME=%SNOVA_HOME%" org.snova.framework.launch.ApplicationLauncher gui