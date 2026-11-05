# Inicia (y la primera vez inicializa) una instancia MySQL 8 PORTÁTIL para USCO LabControl, en el puerto 3307,
# sin tocar otros servidores MySQL del equipo (que suelen usar el 3306).
#   Requisito: carpeta %USERPROFILE%\tools\mysql-8.4.6-winx64 (zip "Windows (x86, 64-bit), ZIP Archive" de mysql.com).
#   Alternativa sin portátil: docker compose up -d  (ver README.md) y MYSQL_URL apuntando a su puerto.
param([int]$Puerto = 3307)

$base = Join-Path $env:USERPROFILE 'tools\mysql-8.4.6-winx64'
$datos = Join-Path $env:USERPROFILE 'tools\mysql-data'
$mysqld = Join-Path $base 'bin\mysqld.exe'
$mysql = Join-Path $base 'bin\mysql.exe'
if (-not (Test-Path $mysqld)) { Write-Error "No se encontró $mysqld. Descarga MySQL 8.4 (ZIP) y descomprímelo en $base."; exit 1 }

if (Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue) { Write-Host "MySQL ya está escuchando en el puerto $Puerto."; exit 0 }

$primera = -not (Test-Path (Join-Path $datos 'mysql'))
if ($primera) {
  Write-Host 'Inicializando el directorio de datos...'
  New-Item -ItemType Directory -Force $datos | Out-Null
  & $mysqld --initialize-insecure --basedir=$base --datadir=$datos --console 2>&1 | Out-Null
}

Write-Host "Iniciando MySQL en 127.0.0.1:$Puerto ..."
Start-Process -WindowStyle Hidden -FilePath $mysqld -ArgumentList "--basedir=`"$base`"", "--datadir=`"$datos`"", '--bind-address=127.0.0.1', "--port=$Puerto", '--mysqlx=OFF',
  '--default-time-zone=-05:00', '--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci', '--log-bin-trust-function-creators=1'
for ($i = 0; $i -lt 30; $i++) { Start-Sleep 1; if (Get-NetTCPConnection -LocalPort $Puerto -State Listen -ErrorAction SilentlyContinue) { break } }

if ($primera) {
  # Usuario de la aplicación (credenciales por defecto de desarrollo; cámbialas en producción con MYSQL_USER / MYSQL_PASSWORD)
  & $mysql -uroot -h127.0.0.1 -P $Puerto -e "CREATE DATABASE IF NOT EXISTS labcontrol CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'labcontrol'@'%' IDENTIFIED BY 'LabControl#2026'; GRANT ALL ON labcontrol.* TO 'labcontrol'@'%';"
}
Write-Host "MySQL listo en el puerto $Puerto (base de datos: labcontrol)."
