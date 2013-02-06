@echo off
set SNOVA_HOME=%~dp0\..
@%JAVA_HOME%\bin\java -cp "%~dp0\..\lib\snova.jar;%~dp0\..\conf" "-DSNOVA_HOME=%SNOVA_HOME%" org.snova.framework.admin.Admin