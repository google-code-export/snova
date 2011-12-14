@echo off
set SNOVA_HOME=%~dp0\..
@java -cp "%~dp0\..\lib\framework.jar;%~dp0\..\etc" "-DSNOVA_HOME=%SNOVA_HOME%" org.snova.framework.util.SslCertificateHelper