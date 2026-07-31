@echo off
REM ============================================================
REM  Inicia el backend de Confronta (API en http://localhost:3000).
REM  Requisitos: XAMPP con MySQL encendido.
REM  USO: doble clic. Deja esta ventana ABIERTA mientras usas la app.
REM ============================================================
cd /d "%~dp0backend"
echo Iniciando el backend de Confronta...
echo (Deja esta ventana abierta. Para detenerlo: cierra la ventana o Ctrl+C)
echo.
node server.js
pause
