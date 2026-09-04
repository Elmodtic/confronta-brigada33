@echo off
REM ============================================================
REM  Confronta Diaria - Publicar el servidor por internet
REM
REM  Levanta el tunel de Cloudflare y muestra la direccion
REM  publica que debes pasar a quienes usen la app.
REM
REM  Requisitos (arrancalos ANTES):
REM    1. MySQL desde el panel de XAMPP
REM    2. iniciar_backend.bat
REM    3. Nginx:  cd C:\nginx  &&  start nginx
REM
REM  Doble clic para ejecutar. No cierres la ventana.
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar_tunel.ps1"
pause
