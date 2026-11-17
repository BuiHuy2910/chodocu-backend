@echo off
title PassUni Backend (chodocu-backend)
echo Dang khoi dong PassUni Backend...
cd /d "%~dp0"
call .\mvnw.cmd spring-boot:run
pause
