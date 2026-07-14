@echo off
REM ============================================================
REM  Abre el puerto 3000 en el Firewall de Windows para que el
REM  emulador de Android (10.0.2.2) pueda conectarse al backend.
REM
REM  USO: clic derecho sobre este archivo -> "Ejecutar como
REM       administrador".
REM ============================================================
echo Creando regla de firewall para el puerto 3000...
netsh advfirewall firewall add rule name="Confronta Backend 3000" dir=in action=allow protocol=TCP localport=3000
echo.
if %errorlevel%==0 (
  echo Regla creada correctamente. Ya puedes usar la app en el emulador.
) else (
  echo Hubo un problema. Asegurate de ejecutar este archivo como administrador.
)
echo.
pause
