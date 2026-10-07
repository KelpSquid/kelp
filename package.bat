@echo off
rem Builds Kelp's downloads (Windows, Linux and Mac) into the build folder.
rem Build Squid first (build.bat in the squid folder), so Squid comes along.
cd /d "%~dp0"
java tools\Package.java || (pause & exit /b 1)
pause
