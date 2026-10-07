@echo off
rem Runs Kelp's tests. They use a temporary folder, so your real instances and settings aren't touched.
cd /d "%~dp0"
javac -d "%TEMP%\kelp-tests" src\kelp\*.java test\kelp\*.java || (pause & exit /b 1)
java -Djava.awt.headless=true -cp "%TEMP%\kelp-tests" kelp.KelpTest
pause
