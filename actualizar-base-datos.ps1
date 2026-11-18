# Actualiza una base de datos de USCO LabControl que YA EXISTE a la versión 2.0: roles nuevos (Administrador general,
# Coordinador de Laboratorio y Monitor de Laboratorio) y las tablas de los módulos de gestión de laboratorios.
# No borra datos y se puede ejecutar varias veces. Requiere que la base ya tenga la v1.4 (05_insumos_laboratorios_reportes.sql).
#
#   .\actualizar-base-datos.ps1                 # MySQL portátil en el puerto 3307, usuario root sin contraseña
#   .\actualizar-base-datos.ps1 -ClaveRoot xxx  # si tu usuario root tiene contraseña
#   .\actualizar-base-datos.ps1 -Puerto 3306    # otro puerto
param([int]$Puerto = 3307, [string]$ClaveRoot = '')

$mysql = Join-Path $env:USERPROFILE 'tools\mysql-8.4.6-winx64\bin\mysql.exe'
if (-not (Test-Path $mysql)) {
  $enPath = Get-Command mysql.exe -ErrorAction SilentlyContinue
  if ($enPath) { $mysql = $enPath.Source } else { Write-Error "No se encontró mysql.exe. Ejecuta primero .\iniciar-mysql.ps1"; exit 1 }
}
$credencial = @('-uroot')
if ($ClaveRoot) { $credencial += "--password=$ClaveRoot" }
$conexion = @('--host=127.0.0.1', "--port=$Puerto", '--protocol=TCP', '--default-character-set=utf8mb4')

# ¿La base todavía no tiene la v1.4? (sin tabla «laboratorios»): se aplica primero el 05
$tiene14 = & $mysql @credencial @conexion --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='labcontrol' AND table_name='laboratorios'"
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo conectar a MySQL. ¿Está encendido? (.\iniciar-mysql.ps1)"; exit 1 }
$scripts = @()
if ("$tiene14".Trim() -eq '0') { $scripts += '05_insumos_laboratorios_reportes.sql' }
$scripts += '06_roles_y_modulos.sql'

foreach ($s in $scripts) {
  $ruta = (Resolve-Path (Join-Path $PSScriptRoot "backend\db\$s")).Path -replace '\\', '/'
  Write-Host "-> $s"
  & $mysql @credencial @conexion --execute="source $ruta"
  if ($LASTEXITCODE -ne 0) { Write-Error "Falló $s. Revisa el mensaje de MySQL de arriba."; exit 1 }
}
Write-Host "Base de datos actualizada a la v2.0. Reinicia el backend." -ForegroundColor Green
Write-Host "Revisa en Administración -> Usuarios y roles los laboratorios asignados a cada coordinador y monitor." -ForegroundColor Yellow
