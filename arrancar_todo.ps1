# =====================================================================
#  Confronta Diaria - Arranque completo del servidor
#
#  Levanta, en orden, todo lo que la app necesita:
#
#     MySQL (XAMPP)  ->  Backend Node  ->  Nginx (HTTPS 443)  ->  Tunel
#
#  Es idempotente: si algo ya esta corriendo no lo duplica.
#
#  Al final deja la direccion publica en url_tunel.txt. Esa direccion
#  CAMBIA cada vez que se reinicia el tunel; hay que pasarsela a quien
#  use la app (boton "Servidor" en la pantalla de inicio de sesion).
#
#  Uso:  doble clic en arrancar_todo.bat
#        o automatico al iniciar sesion en Windows (tarea programada).
# =====================================================================

$ErrorActionPreference = 'Continue'
$base = Split-Path -Parent $MyInvocation.MyCommand.Path

$rutaMysql       = 'C:\xampp\mysql\bin\mysqld.exe'
$iniMysql        = 'C:\xampp\mysql\bin\my.ini'
$rutaNginx       = 'C:\nginx\nginx.exe'
$dirNginx        = 'C:\nginx'
$rutaCloudflared = 'C:\cloudflared\cloudflared.exe'
$dirBackend      = Join-Path $base 'backend'
$logTunel        = Join-Path $base 'tunel.log'
$archivoUrl      = Join-Path $base 'url_tunel.txt'
$logArranque     = Join-Path $base 'arranque.log'

function Registrar($texto, $color = 'Gray') {
    $linea = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $texto"
    Write-Host $texto -ForegroundColor $color
    Add-Content -Path $logArranque -Value $linea -Encoding utf8
}

function EstaVivo($nombre) {
    return @(Get-Process $nombre -ErrorAction SilentlyContinue).Count -gt 0
}

function EsperarPuerto($puerto, $segundos = 25) {
    for ($i = 0; $i -lt $segundos; $i++) {
        $c = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue
        if ($c) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

Registrar "=== Arranque de Confronta Diaria ===" 'Cyan'

# ---------------------------------------------------------------- MySQL
if (EstaVivo 'mysqld') {
    Registrar "MySQL ya estaba corriendo." 'Green'
} elseif (Test-Path $rutaMysql) {
    Registrar "Arrancando MySQL..." 'Cyan'
    # OJO: sin -WorkingDirectory, MariaDB no resuelve basedir/datadir y aborta
    # con "Could not open mysql.plugin table. Failed to initialize plugins".
    Start-Process -FilePath $rutaMysql `
        -ArgumentList "--defaults-file=`"$iniMysql`"", '--standalone' `
        -WorkingDirectory (Split-Path -Parent $rutaMysql) -WindowStyle Hidden
    if (EsperarPuerto 3306) { Registrar "MySQL arriba (3306)." 'Green' }
    else { Registrar "MySQL no respondio en el 3306." 'Red' }
} else {
    Registrar "No se encontro $rutaMysql" 'Red'
}

# -------------------------------------------------------------- Backend
if (EstaVivo 'node') {
    Registrar "Backend ya estaba corriendo." 'Green'
} elseif (Test-Path (Join-Path $dirBackend 'server.js')) {
    Registrar "Arrancando el backend..." 'Cyan'
    Start-Process -FilePath 'node' -ArgumentList 'server.js' `
        -WorkingDirectory $dirBackend -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $dirBackend 'server-run.log') `
        -RedirectStandardError  (Join-Path $dirBackend 'server-error.log')
    if (EsperarPuerto 3000) { Registrar "Backend arriba (3000)." 'Green' }
    else { Registrar "El backend no respondio en el 3000. Revisa backend\server-error.log" 'Red' }
} else {
    Registrar "No se encontro $dirBackend\server.js" 'Red'
}

# ---------------------------------------------------------------- Nginx
if (EstaVivo 'nginx') {
    Registrar "Nginx ya estaba corriendo." 'Green'
} elseif (Test-Path $rutaNginx) {
    Registrar "Arrancando Nginx..." 'Cyan'
    Start-Process -FilePath $rutaNginx -WorkingDirectory $dirNginx -WindowStyle Hidden
    if (EsperarPuerto 443) { Registrar "Nginx arriba (443)." 'Green' }
    else { Registrar "Nginx no respondio en el 443." 'Red' }
} else {
    Registrar "No se encontro $rutaNginx" 'Red'
}

# --------------------------------------------------------------- Tunel
if (EstaVivo 'cloudflared') {
    Registrar "El tunel ya estaba corriendo." 'Green'
    if (Test-Path $archivoUrl) {
        Registrar "Direccion actual: $(Get-Content $archivoUrl -Raw)" 'White'
    }
} elseif (Test-Path $rutaCloudflared) {
    Registrar "Abriendo el tunel de Cloudflare..." 'Cyan'
    if (Test-Path $logTunel) { Remove-Item $logTunel -Force -ErrorAction SilentlyContinue }
    # La ruta del proyecto tiene espacios: si no se entrecomilla, cloudflared
    # recibe la ruta partida y no escribe el log, y entonces no hay forma de
    # leer la direccion publica.
    Start-Process -FilePath $rutaCloudflared `
        -ArgumentList 'tunnel', '--url', 'https://localhost', '--no-tls-verify', '--logfile', "`"$logTunel`"" `
        -WindowStyle Hidden

    $url = $null
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $logTunel) {
            $m = Select-String -Path $logTunel -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
                 Select-Object -First 1
            if ($m) { $url = $m.Matches[0].Value; break }
        }
    }

    if ($url) {
        $url | Set-Content -Path $archivoUrl -Encoding utf8
        Registrar "Tunel abierto." 'Green'
        Write-Host ""
        Write-Host "=================================================================" -ForegroundColor Green
        Write-Host "  DIRECCION PUBLICA DEL SERVIDOR" -ForegroundColor Green
        Write-Host ""
        Write-Host "  $url" -ForegroundColor White
        Write-Host ""
        Write-Host "  Guardada en url_tunel.txt" -ForegroundColor Gray
        Write-Host "  Si cambio respecto a la anterior, pasasela a tus companeros" -ForegroundColor Gray
        Write-Host "  para que la peguen en el boton 'Servidor' de la app." -ForegroundColor Gray
        Write-Host "=================================================================" -ForegroundColor Green
        Write-Host ""
        Registrar "URL: $url" 'White'

        try {
            $r = Invoke-WebRequest -Uri "$url/api/grados" -UseBasicParsing -TimeoutSec 30
            Registrar "Comprobacion desde internet: HTTP $($r.StatusCode). Todo listo." 'Green'
        } catch {
            Registrar "La API aun no responde por el tunel: $($_.Exception.Message)" 'Yellow'
        }
    } else {
        Registrar "No se pudo obtener la direccion del tunel. Revisa tunel.log" 'Red'
    }
} else {
    Registrar "No se encontro $rutaCloudflared" 'Red'
}

Registrar "=== Fin del arranque ===" 'Cyan'
