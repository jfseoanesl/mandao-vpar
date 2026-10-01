<#
.SYNOPSIS
  Despliegue local completo de Mandao (PostGIS + backend + PWA/Caddy) con Docker Desktop.

.DESCRIPTION
  - Crea/completa infra/.env (secretos aleatorios y llaves de notificaciones si faltan).
  - Publica la app en http://localhost:8088 y, para probar en el celular dentro de la misma red,
    en https://<IP-del-PC>:8443 con la CA interna de Caddy.
  - Reconstruye las imágenes desde el código, espera a que todo esté sano y verifica la API.
  - Exporta el certificado raíz local a infra/mandao-local-ca.crt.

.PARAMETER Ip           IP de este PC en la red local (por defecto se detecta).
.PARAMETER NoLan        Solo localhost (sin HTTPS en la red).
.PARAMETER NoDemo       Sin datos de demostración.
.PARAMETER NoCache      Reconstruye las imágenes sin caché.
.PARAMETER Reset        Borra contenedores Y DATOS (base de datos, archivos, certificados) antes de desplegar.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File infra\deploy-local.ps1
#>
param(
  [string]$Ip,
  [switch]$NoLan,
  [switch]$NoDemo,
  [switch]$NoCache,
  [switch]$Reset
)
# 'Continue': en Windows PowerShell 5.1, lo que docker escribe en stderr (progreso, avisos) se convertiría en error
# fatal con 'Stop'. Los comandos nativos se validan por $LASTEXITCODE y los cmdlets críticos usan -ErrorAction Stop.
$ErrorActionPreference = 'Continue'
Set-Location -Path $PSScriptRoot
$envFile = Join-Path $PSScriptRoot '.env'

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function New-Secret([int]$bytes = 32) {
  $buf = New-Object byte[] $bytes
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
  return ([System.BitConverter]::ToString($buf) -replace '-', '').ToLower()
}
function Read-Env {
  $map = [ordered]@{}
  if (Test-Path $envFile) {
    foreach ($line in [System.IO.File]::ReadAllLines($envFile)) {
      if ($line -match '^\s*([A-Z0-9_]+)=(.*)$') { $map[$Matches[1]] = $Matches[2] }
    }
  }
  return $map
}
function Set-EnvValue([string]$key, [string]$value) {
  $lines = [System.Collections.Generic.List[string]]::new()
  if (Test-Path $envFile) { $lines.AddRange([System.IO.File]::ReadAllLines($envFile)) }
  $found = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match "^\s*$key=") { $lines[$i] = "$key=$value"; $found = $true }
  }
  if (-not $found) { $lines.Add("$key=$value") }
  # UTF-8 sin BOM: docker compose no reconoce la primera variable si el archivo tiene BOM.
  [System.IO.File]::WriteAllLines($envFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
}

Write-Step 'Verificando Docker'
docker info --format '{{.ServerVersion}}' 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker no está en ejecución. Abre Docker Desktop e intenta de nuevo.' }

if ($Reset) {
  Write-Step 'Reset: se borrarán contenedores y TODOS los datos locales'
  $answer = Read-Host 'Escribe BORRAR para confirmar'
  if ($answer -ne 'BORRAR') { throw 'Cancelado.' }
  docker compose down -v
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo hacer el reset.' }
}

Write-Step 'Configuración (.env)'
$isNewEnv = -not (Test-Path $envFile)
if ($isNewEnv) {
  Copy-Item (Join-Path $PSScriptRoot '.env.example') $envFile
  Write-Host 'Se creó .env a partir de .env.example'
}
$cfg = Read-Env
foreach ($k in 'DB_PASSWORD', 'JWT_SECRET', 'OTP_KEY') {
  if (-not $cfg[$k] -or $cfg[$k] -like 'cambia-*') { Set-EnvValue $k (New-Secret); Write-Host "Generado $k" }
}
# Solo en una instalación nueva: si el admin ya existe en la base, cambiar .env no cambia su clave.
if ($isNewEnv) {
  $adminPass = 'Mandao' + (Get-Random -Minimum 1000 -Maximum 9999) + (New-Secret 2)
  Set-EnvValue 'ADMIN_PASSWORD' $adminPass
  Write-Host "Generada ADMIN_PASSWORD (admin $($cfg['ADMIN_PHONE'])): $adminPass" -ForegroundColor Yellow
}

if (-not $NoLan -and -not $Ip) {
  $Ip = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)' -and $_.InterfaceAlias -notmatch 'vEthernet|WSL|Loopback' } |
    Sort-Object -Property InterfaceMetric | Select-Object -First 1 -ExpandProperty IPAddress
  if (-not $Ip) { Write-Warning 'No se detectó una IP de red local; se despliega solo en localhost.'; $NoLan = $true }
}

Set-EnvValue 'HTTP_PORT' '8088'
Set-EnvValue 'HTTPS_PORT' '8443'
Set-EnvValue 'COOKIE_SECURE' 'false'
Set-EnvValue 'SEED_DEMO' $(if ($NoDemo) { 'false' } else { 'true' })
Set-EnvValue 'PUBLIC_URL' 'http://localhost:8088'
if ($NoLan) {
  Set-EnvValue 'SITE_ADDRESS' ':80'
  Set-EnvValue 'CORS_ORIGINS' 'http://localhost:8088'
  Set-EnvValue 'CADDY_DEFAULT_SNI' ''
  Set-EnvValue 'CADDY_AUTO_HTTPS' ''
} else {
  Set-EnvValue 'SITE_ADDRESS' ":80, https://${Ip}:8443"
  Set-EnvValue 'CORS_ORIGINS' "http://localhost:8088,https://${Ip}:8443"
  # Por IP los navegadores no envían SNI; y el certificado debe poder bajarse por HTTP (sin redirección).
  Set-EnvValue 'CADDY_DEFAULT_SNI' "default_sni $Ip"
  Set-EnvValue 'CADDY_AUTO_HTTPS' 'auto_https disable_redirects'
}

Write-Step 'Construyendo imágenes'
$buildArgs = @('compose', 'build')
if ($NoCache) { $buildArgs += '--no-cache' }
& docker @buildArgs
if ($LASTEXITCODE -ne 0) { throw 'Falló la construcción de las imágenes.' }

$cfg = Read-Env
if (-not $cfg['VAPID_PUBLIC_KEY'] -or -not $cfg['VAPID_PRIVATE_KEY']) {
  Write-Step 'Generando llaves de notificaciones push (una sola vez)'
  $keys = docker compose run --rm --no-deps --entrypoint java backend `
    '-Dloader.main=com.mandao.notifications.VapidKeyGenerator' -cp app.jar `
    org.springframework.boot.loader.launch.PropertiesLauncher
  foreach ($line in $keys) { if ($line -match '^(VAPID_(PUBLIC|PRIVATE)_KEY)=(.+)$') { Set-EnvValue $Matches[1] $Matches[3] } }
}

Write-Step 'Iniciando servicios (puede tardar 1-2 minutos)'
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { docker compose ps; throw 'Algún servicio no quedó sano. Revisa: docker compose logs backend' }
docker compose ps

Write-Step 'Verificando'
$config = Invoke-RestMethod -Uri 'http://localhost:8088/api/public/config' -TimeoutSec 20 -ErrorAction Stop
Write-Host "API OK (zona: $($config.centerLat), $($config.centerLng); radio $($config.radiusKm) km)"
$index = Invoke-WebRequest -Uri 'http://localhost:8088/' -UseBasicParsing -TimeoutSec 20 -ErrorAction Stop
Write-Host "PWA OK (HTTP $($index.StatusCode))"

if (-not $NoLan) {
  # Fuerza la emisión del certificado local y lo exporta.
  try { Invoke-WebRequest -Uri "https://${Ip}:8443/" -UseBasicParsing -TimeoutSec 20 -ErrorAction Stop | Out-Null } catch { }
  $crt = Join-Path $PSScriptRoot 'mandao-local-ca.crt'
  docker compose cp web:/data/caddy/pki/authorities/local/root.crt $crt 2>&1 | Out-Null
  if (Test-Path $crt) { Write-Host "Certificado raíz local exportado: $crt" }
  $rule = Get-NetFirewallRule -DisplayName 'Mandao local 8443' -ErrorAction SilentlyContinue
  if (-not $rule) {
    Write-Warning ("Si el celular no conecta, permite el puerto 8443 en el firewall (PowerShell como administrador):`n" +
      "  New-NetFirewallRule -DisplayName 'Mandao local 8443' -Direction Inbound -Protocol TCP -LocalPort 8443 -Action Allow -Profile Private,Domain")
  }
}

Write-Host "`n================ Mandao desplegado ================" -ForegroundColor Green
Write-Host "En este PC:        http://localhost:8088"
if (-not $NoLan) {
  Write-Host "En el celular:     https://${Ip}:8443   (misma red Wi-Fi/LAN)"
  Write-Host "  1. Abre http://${Ip}:8088/mandao-local-ca.crt e instala el certificado"
  Write-Host "     (Android: Ajustes > Seguridad > Cifrado y credenciales > Instalar certificado > Certificado de CA)."
  Write-Host "  2. Abre https://${Ip}:8443 en Chrome y usa 'Instalar app' para probar como PWA."
}
if (-not $NoDemo) {
  Write-Host "Usuarios demo (contraseña Mandao2026): admin 3000000000 · mandaderos 3001000001-05 · clientes 3002000001-02 · empresa 3003000001"
}
Write-Host "Detener: docker compose stop    ·    Logs: docker compose logs -f backend"
