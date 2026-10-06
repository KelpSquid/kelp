@echo off
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -d out src\kelp\*.java || (pause & exit /b 1)
java -cp out kelp.Kelp
