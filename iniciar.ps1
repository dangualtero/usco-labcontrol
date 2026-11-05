# Inicia USCO LabControl completo: MySQL -> API Spring Boot -> frontend React (abre el navegador).
#   .\iniciar.ps1
# Requisitos: Java 21 + Maven, Node 20+ y MySQL (ver README.md). Las credenciales de la base se toman de las
# variables MYSQL_URL / MYSQL_USER / MYSQL_PASSWORD; si no existen se usa la instancia local de iniciar-mysql.ps1.
$raiz = $PSScriptRoot

# Node portátil en %USERPROFILE%\tools\node (si no hay Node en el PATH)
$nodePortatil = Join-Path $env:USERPROFILE 'tools\node'
if (-not (Get-Command node -ErrorAction SilentlyContinue) -and (Test-Path $nodePortatil)) { $env:PATH = "$nodePortatil;$env:PATH" }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Error 'Node.js no encontrado. Instala Node LTS (https://nodejs.org).'; exit 1 }

if (-not $env:MYSQL_URL) { & (Join-Path $raiz 'iniciar-mysql.ps1') }

$jar = Join-Path $raiz 'backend\target\labcontrol-backend-1.0.0.jar'
if (-not (Test-Path $jar)) { Write-Host 'Compilando backend...'; Push-Location (Join-Path $raiz 'backend'); mvn -q -DskipTests package; Pop-Location }
if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
  Write-Host 'Iniciando la API (puerto 8080)...'
  Start-Process -WindowStyle Minimized -FilePath java -ArgumentList '-jar', "`"$jar`""
  for ($i = 0; $i -lt 60; $i++) { Start-Sleep 1; try { if ((Invoke-WebRequest http://127.0.0.1:8080/api/salud -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { break } } catch { } }
}

$front = Join-Path $raiz 'frontend'
if (-not (Test-Path (Join-Path $front 'node_modules'))) { Write-Host 'Instalando dependencias del frontend...'; Push-Location $front; npm install --no-audit --no-fund; Pop-Location }
Write-Host 'Iniciando el frontend en http://localhost:5173 ...'
Start-Process 'http://localhost:5173'
Push-Location $front
npm run dev -- --host 127.0.0.1
Pop-Location
