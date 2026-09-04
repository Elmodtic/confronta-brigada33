@echo off
REM ============================================================
REM  Confronta Diaria - Arranque completo del servidor
REM
REM  Levanta MySQL, el backend, Nginx y el tunel de Cloudflare,
REM  y muestra la direccion publica para la app.
REM
REM  Doble clic para ejecutar.
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0arrancar_todo.ps1"
echo.
pause
