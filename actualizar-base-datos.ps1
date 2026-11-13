# Actualiza una base de datos de USCO LabControl que YA EXISTE a la versión 1.4 (insumos, laboratorios por sede,
# seguimiento de reportes y solicitudes de insumos). No borra nada y se puede ejecutar varias veces.
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
$ruta = (Resolve-Path (Join-Path $PSScriptRoot 'backend\db\05_insumos_laboratorios_reportes.sql')).Path -replace '\\', '/'
Write-Host "-> 05_insumos_laboratorios_reportes.sql"
& $mysql @credencial --host=127.0.0.1 --port=$Puerto --protocol=TCP --default-character-set=utf8mb4 --execute="source $ruta"
if ($LASTEXITCODE -ne 0) { Write-Error "Falló la actualización. Revisa el mensaje de MySQL de arriba."; exit 1 }
Write-Host "Base de datos actualizada a la v1.4. Reinicia el backend." -ForegroundColor Green
