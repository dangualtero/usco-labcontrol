# Crea (o vuelve a crear) la base de datos de USCO LabControl en el MySQL portátil (puerto 3307) a partir de los
# scripts de backend/db:  00 usuario -> 01 esquema -> 02 triggers del historial -> 03 datos iniciales.
#
#   .\crear-base-datos.ps1                 # pide confirmación porque BORRA las tablas existentes
#   .\crear-base-datos.ps1 -Confirmar      # sin preguntar (lo usa iniciar-mysql.ps1 la primera vez)
#   .\crear-base-datos.ps1 -SinDatos       # solo estructura y triggers, sin usuarios ni equipos de ejemplo
#   .\crear-base-datos.ps1 -ClaveRoot xxx  # si tu usuario root de MySQL tiene contraseña
param([int]$Puerto = 3307, [switch]$Confirmar, [switch]$SinDatos, [string]$ClaveRoot = '')

$mysql = Join-Path $env:USERPROFILE 'tools\mysql-8.4.6-winx64\bin\mysql.exe'
if (-not (Test-Path $mysql)) {
  $enPath = Get-Command mysql.exe -ErrorAction SilentlyContinue
  if ($enPath) { $mysql = $enPath.Source } else { Write-Error "No se encontró mysql.exe. Ejecuta primero .\iniciar-mysql.ps1"; exit 1 }
}
if (-not (Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue)) {
  Write-Error "MySQL no está escuchando en el puerto $Puerto. Ejecuta primero .\iniciar-mysql.ps1"; exit 1
}
if (-not $Confirmar) {
  Write-Host "Esto BORRA todas las tablas de 'labcontrol' (préstamos, historial, presupuesto...) y las crea de nuevo." -ForegroundColor Yellow
  if ((Read-Host "Escribe SI para continuar") -ne 'SI') { Write-Host 'Cancelado.'; exit 0 }
}

$scripts = @('00_usuario_mysql.sql', '01_esquema.sql', '02_inmutabilidad.sql')
if (-not $SinDatos) { $scripts += '03_datos_iniciales.sql' }
$credencial = @('-uroot')
if ($ClaveRoot) { $credencial += "--password=$ClaveRoot" }

foreach ($s in $scripts) {
  # Rutas con "/" para el comando source de mysql (admite espacios en la ruta)
  $ruta = (Resolve-Path (Join-Path $PSScriptRoot "backend\db\$s")).Path -replace '\\', '/'
  Write-Host "-> $s"
  & $mysql @credencial --host=127.0.0.1 --port=$Puerto --protocol=TCP --default-character-set=utf8mb4 --execute="source $ruta"
  if ($LASTEXITCODE -ne 0) { Write-Error "Falló $s. Revisa el mensaje de MySQL de arriba."; exit 1 }
}
Write-Host "Base de datos 'labcontrol' lista: 12 tablas, triggers del historial$(if (-not $SinDatos) { ', sede Neiva, usuarios e inventario' })." -ForegroundColor Green
