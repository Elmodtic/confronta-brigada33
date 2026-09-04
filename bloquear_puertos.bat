@echo off
REM ============================================================
REM  Confronta Diaria - Endurecimiento del Firewall de Windows
REM
REM  Bloquea TODO acceso entrante desde la red a:
REM    - Puerto 3000  (Node / Express)
REM    - Puerto 3306  (MySQL / MariaDB)
REM    - Puerto 80    (Apache / phpMyAdmin de XAMPP)
REM
REM  El trafico de loopback (127.0.0.1 / localhost) NO se ve
REM  afectado: Windows lo exceptua del firewall, por lo que el
REM  proxy Nginx local, el emulador de Android (10.0.2.2) y las
REM  herramientas locales siguen funcionando.
REM
REM  Los clientes externos deben entrar SIEMPRE por Nginx (443).
REM
REM  USO: clic derecho -> "Ejecutar como administrador".
REM ============================================================

net session >nul 2>&1
if %errorlevel% neq 0 (
  echo [ERROR] Ejecuta este archivo como Administrador.
  pause
  exit /b 1
)

echo Activando el firewall en los tres perfiles...
REM  Sin esto las reglas se crean pero NO se aplican: la interfaz Ethernet
REM  de esta PC esta clasificada como "Publica" y ese perfil venia apagado.
netsh advfirewall set domainprofile state on
netsh advfirewall set privateprofile state on
netsh advfirewall set publicprofile state on

echo.
echo Eliminando reglas previas...
netsh advfirewall firewall delete rule name="Confronta Backend 3000" >nul 2>&1
netsh advfirewall firewall delete rule name="Confronta - Bloquear 3000 (Node) entrante" >nul 2>&1
netsh advfirewall firewall delete rule name="Confronta - Bloquear 3306 (MySQL) entrante" >nul 2>&1
netsh advfirewall firewall delete rule name="Confronta - Bloquear 80 (Apache) entrante" >nul 2>&1
netsh advfirewall firewall delete rule name="Confronta - Permitir 443 (Nginx) entrante" >nul 2>&1

echo Bloqueando el puerto 3000 (Node) para accesos externos...
netsh advfirewall firewall add rule name="Confronta - Bloquear 3000 (Node) entrante" dir=in action=block protocol=TCP localport=3000 profile=any enable=yes

echo Bloqueando el puerto 3306 (MySQL) para accesos externos...
netsh advfirewall firewall add rule name="Confronta - Bloquear 3306 (MySQL) entrante" dir=in action=block protocol=TCP localport=3306 profile=any enable=yes

echo Bloqueando el puerto 80 (Apache / phpMyAdmin) para accesos externos...
REM  Una regla de BLOQUEO tiene prioridad sobre la regla "Apache HTTP Server"
REM  que crea XAMPP, asi que no hace falta borrar esa. phpMyAdmin sigue
REM  funcionando en http://localhost/phpmyadmin (el loopback no se filtra).
netsh advfirewall firewall add rule name="Confronta - Bloquear 80 (Apache) entrante" dir=in action=block protocol=TCP localport=80 profile=any enable=yes

echo Permitiendo el puerto 443 (Nginx / HTTPS) para la app...
netsh advfirewall firewall add rule name="Confronta - Permitir 443 (Nginx) entrante" dir=in action=allow protocol=TCP localport=443 profile=any enable=yes

echo.
echo Estado del firewall por perfil (los tres deben decir ACTIVAR/ON):
netsh advfirewall show allprofiles state

echo.
echo Reglas activas:
netsh advfirewall firewall show rule name="Confronta - Bloquear 3000 (Node) entrante"
netsh advfirewall firewall show rule name="Confronta - Bloquear 3306 (MySQL) entrante"
netsh advfirewall firewall show rule name="Confronta - Bloquear 80 (Apache) entrante"
netsh advfirewall firewall show rule name="Confronta - Permitir 443 (Nginx) entrante"

echo.
echo Listo. Puertos 3000, 3306 y 80 cerrados a la red; 443 abierto para Nginx.
pause
