# =====================================================================
#  Confronta Diaria - Tunel de Cloudflare
#
#  Publica el servidor local (Nginx en https://localhost) en una direccion
#  HTTPS accesible desde cualquier red, sin abrir puertos en el router.
#
#  El tunel gratuito ("quick tunnel") asigna una direccion NUEVA cada vez
#  que se inicia. Por eso este script la muestra en pantalla y la guarda
#  en url_tunel.txt: hay que pasarsela a quien use la app.
#
#  En la app, esa direccion se cambia desde la pantalla de inicio de
#  sesion, en el boton "Servidor". No hace falta reinstalar el APK.
# =====================================================================

$ErrorActionPreference = 'Stop'
$base       = Split-Path -Parent $MyInvocation.MyCommand.Path
$cloudflared = 'C:\cloudflared\cloudflared.exe'
$log        = Join-Path $base 'tunel.log'
$archivoUrl = Join-Path $base 'url_tunel.txt'

if (-not (Test-Path $cloudflared)) {
    Write-Host "No se encontro $cloudflared" -ForegroundColor Red
    Write-Host "Descargalo de https://github.com/cloudflare/cloudflared/releases"
    exit 1
}

# --- Comprobaciones previas: sin backend y sin Nginx el tunel no sirve ---
Write-Host "Verificando servicios locales..." -ForegroundColor Cyan

$nodeVivo = @(Get-Process node -ErrorAction SilentlyContinue).Count -gt 0
$nginxVivo = @(Get-Process nginx -ErrorAction SilentlyContinue).Count -gt 0
$mysqlVivo = @(Get-Process mysqld -ErrorAction SilentlyContinue).Count -gt 0

if ($mysqlVivo) { Write-Host "  MySQL   OK" -ForegroundColor Green }
else { Write-Host "  MySQL   NO esta corriendo (arrancalo desde XAMPP)" -ForegroundColor Yellow }

if ($nodeVivo) { Write-Host "  Backend OK" -ForegroundColor Green }
else { Write-Host "  Backend NO esta corriendo (ejecuta iniciar_backend.bat)" -ForegroundColor Yellow }

if ($nginxVivo) { Write-Host "  Nginx   OK" -ForegroundColor Green }
else { Write-Host "  Nginx   NO esta corriendo (cd C:\nginx  y  start nginx)" -ForegroundColor Yellow }

if (-not ($nodeVivo -and $nginxVivo)) {
    Write-Host ""
    Write-Host "Faltan servicios. El tunel se abriria pero no respondera nada." -ForegroundColor Red
    Write-Host "Arrancalos y vuelve a ejecutar este script."
    exit 1
}

# --- Levantar el tunel ---
if (Test-Path $log) { Remove-Item $log -Force }
Write-Host ""
Write-Host "Abriendo el tunel..." -ForegroundColor Cyan

$proc = Start-Process -FilePath $cloudflared `
    -ArgumentList 'tunnel', '--url', 'https://localhost', '--no-tls-verify', '--logfile', $log `
    -PassThru -WindowStyle Hidden

# La direccion tarda unos segundos en aparecer en el log
$url = $null
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Milliseconds 1000
    if (Test-Path $log) {
        $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
             Select-Object -First 1
        if ($m) { $url = $m.Matches[0].Value; break }
    }
}

if (-not $url) {
    Write-Host "No se pudo obtener la direccion del tunel. Revisa $log" -ForegroundColor Red
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    exit 1
}

$url | Set-Content -Path $archivoUrl -Encoding utf8

# --- Publicar la direccion en el directorio que consulta la app ---
#
# La app lee servidor.json desde el repositorio publico al abrirse, asi
# que subir este archivo es lo que evita tener que avisarle a cada
# usuario. Si el push falla (sin internet, sin credenciales) no se
# aborta nada: el tunel ya esta arriba y queda la via manual.
$archivoDirectorio = Join-Path $base 'servidor.json'
$json = @"
{
  "url": "$url/",
  "actualizado": "$(Get-Date -Format 'yyyy-MM-dd HH:mm')",
  "nota": "Direccion actual del servidor de Confronta Diaria. La app la consulta al abrir, asi que un cambio de tunel no obliga a reinstalar el APK. Lo actualiza iniciar_tunel.ps1."
}
"@
$json | Set-Content -Path $archivoDirectorio -Encoding utf8

Write-Host "Publicando la direccion para las apps..." -ForegroundColor Cyan
Push-Location $base
try {
    git add servidor.json 2>&1 | Out-Null
    git commit -m "chore: direccion del tunel $url" 2>&1 | Out-Null
    git push origin main 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  Publicada. Las apps la tomaran solas al abrirse." -ForegroundColor Green
    } else {
        Write-Host "  No se pudo publicar. Pasa la direccion a mano." -ForegroundColor Yellow
    }
} catch {
    Write-Host "  No se pudo publicar. Pasa la direccion a mano." -ForegroundColor Yellow
}
Pop-Location

Write-Host ""
Write-Host "===============================================================" -ForegroundColor Green
Write-Host "  SERVIDOR PUBLICADO" -ForegroundColor Green
Write-Host ""
Write-Host "  $url" -ForegroundColor White
Write-Host ""
Write-Host "  Guardada en: url_tunel.txt y servidor.json" -ForegroundColor Gray
Write-Host "  Las apps consultan servidor.json al abrirse: si se publico," -ForegroundColor Gray
Write-Host "  nadie tiene que hacer nada. Si no, esta el boton 'Servidor'" -ForegroundColor Gray
Write-Host "  de la pantalla de inicio de sesion." -ForegroundColor Gray
Write-Host "===============================================================" -ForegroundColor Green
Write-Host ""

# --- Prueba real contra la direccion publica ---
try {
    $r = Invoke-WebRequest -Uri "$url/api/grados" -UseBasicParsing -TimeoutSec 25
    Write-Host "Comprobacion: la API respondio $($r.StatusCode) desde internet." -ForegroundColor Green
} catch {
    Write-Host "Aviso: la API no respondio todavia ($($_.Exception.Message))." -ForegroundColor Yellow
    Write-Host "Espera unos segundos y prueba abriendo la direccion en el navegador."
}

Write-Host ""
Write-Host "NO CIERRES ESTA VENTANA: si la cierras, el tunel se cae." -ForegroundColor Yellow
Write-Host "Ctrl+C para detenerlo." -ForegroundColor Gray

try {
    Wait-Process -Id $proc.Id
} finally {
    if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    Write-Host "Tunel detenido." -ForegroundColor Yellow
}
