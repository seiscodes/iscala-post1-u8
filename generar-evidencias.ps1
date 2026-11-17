# Genera evidencias de los endpoints (salida de curl -i) en docs\evidencias.
# Uso: powershell -ExecutionPolicy Bypass -File generar-evidencias.ps1
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location $PSScriptRoot
$ev = Join-Path $PSScriptRoot 'docs\evidencias'
New-Item -ItemType Directory -Force -Path $ev | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding $false
$base = 'http://localhost:8080'

Write-Host '=== Compilando el jar ==='
& .\mvnw.cmd -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Write-Host '[ERROR] No compilo el proyecto.'; exit 1 }
$jar = Get-ChildItem 'target\auditoria-hallazgos-*.jar' | Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1

Write-Host '=== Iniciando la API ==='
$app = Start-Process java -ArgumentList '-jar', "`"$($jar.FullName)`"" -PassThru -WindowStyle Minimized
$lista = $false
for ($i = 0; $i -lt 60 -and -not $lista; $i++) {
    Start-Sleep -Seconds 2
    try { Invoke-WebRequest -UseBasicParsing "$base/api/hallazgos" -TimeoutSec 2 | Out-Null; $lista = $true } catch { }
}
if (-not $lista) { Stop-Process -Id $app.Id -Force; Write-Host '[ERROR] La API no respondio.'; exit 1 }

function Llamar([string]$nombre, [string]$metodo, [string]$ruta, [string]$cuerpo = '', [string]$usuario = '') {
    $argumentos = @('-s', '-i', '-X', $metodo, "$base$ruta")
    $comando = "`$ curl -i -X $metodo $base$ruta"
    if ($usuario) { $argumentos += @('-H', "X-Usuario: $usuario"); $comando += " -H `"X-Usuario: $usuario`"" }
    if ($cuerpo) {
        $tmp = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($tmp, $cuerpo, $utf8)
        $argumentos += @('-H', 'Content-Type: application/json', '--data-binary', "@$tmp")
        $comando += " -H `"Content-Type: application/json`" -d '$cuerpo'"
    }
    $salida = @(& curl.exe @argumentos)
    $texto = $comando + "`r`n`r`n" + ($salida -join "`r`n")
    [System.IO.File]::WriteAllText((Join-Path $ev "$nombre.txt"), $texto, $utf8)
    Write-Host "  $nombre"
    for ($i = 0; $i -lt $salida.Count; $i++) {
        if ([string]::IsNullOrWhiteSpace($salida[$i])) { return (($salida | Select-Object -Skip ($i + 1)) -join "`n") }
    }
    return ''
}

try {
    # ---------------- Parte 1 ----------------
    $r = Llamar '01-registrar-hallazgo-201' 'POST' '/api/hallazgos' '{"titulo":"Contrasenas por defecto en servidor de pruebas","descripcion":"El servidor QA usa credenciales por defecto del fabricante","areaResponsable":"Infraestructura","severidad":"ALTA","fechaDeteccion":"2026-08-01"}' 'auditor.lopez'
    $id1 = ($r | ConvertFrom-Json).hallazgoId
    $r = Llamar '02-registrar-segundo-hallazgo-201' 'POST' '/api/hallazgos' '{"titulo":"Backups sin cifrar","descripcion":"Copias en disco externo sin cifrado","areaResponsable":"Finanzas","severidad":"MEDIA","fechaDeteccion":"2026-08-05"}' 'auditor.lopez'
    $id2 = ($r | ConvertFrom-Json).hallazgoId
    Llamar '03-registrar-sin-titulo-400' 'POST' '/api/hallazgos' '{"areaResponsable":"TI","fechaDeteccion":"2026-08-01"}' | Out-Null
    Llamar '04-iniciar-remediacion-200' 'PATCH' "/api/hallazgos/$id1/iniciar-remediacion" '{"responsable":"Equipo de Infraestructura","fechaLimite":"2026-08-20","notas":"Rotar credenciales"}' 'jefe.infra' | Out-Null
    Llamar '05-cerrar-hallazgo-abierto-400' 'PATCH' "/api/hallazgos/$id2/cerrar" '' 'jefe.finanzas' | Out-Null
    Llamar '06-cerrar-hallazgo-en-remediacion-200' 'PATCH' "/api/hallazgos/$id1/cerrar" '' 'jefe.infra' | Out-Null
    Llamar '07-reabrir-hallazgo-cerrado-200' 'PATCH' "/api/hallazgos/$id1/reabrir" '{"motivo":"El escaneo de octubre volvio a encontrar la credencial"}' 'auditor.lopez' | Out-Null
    Llamar '08-reabrir-hallazgo-abierto-400' 'PATCH' "/api/hallazgos/$id2/reabrir" '{"motivo":"prueba"}' 'auditor.lopez' | Out-Null
    Llamar '09-consultar-hallazgo-200' 'GET' "/api/hallazgos/$id1" | Out-Null
    Llamar '10-consultar-inexistente-404' 'GET' '/api/hallazgos/6f1c2a7e-0000-4000-8000-000000000000' | Out-Null
    Llamar '11-listar-hallazgos-200' 'GET' '/api/hallazgos' | Out-Null

    # ---------------- Parte 2 ----------------
    $r = Llamar '12-registrar-tercer-hallazgo-201' 'POST' '/api/hallazgos' '{"titulo":"Cuentas sin MFA","descripcion":"Cuentas administrativas sin segundo factor","areaResponsable":"Infraestructura","severidad":"CRITICA","fechaDeteccion":"2026-09-01"}' 'auditor.lopez'
    $id3 = ($r | ConvertFrom-Json).hallazgoId
    Llamar '13-iniciar-remediacion-tercero-200' 'PATCH' "/api/hallazgos/$id3/iniciar-remediacion" '{"responsable":"Seguridad TI","fechaLimite":"2026-09-30"}' 'jefe.seguridad' | Out-Null
    Llamar '14-cerrar-tercero-200' 'PATCH' "/api/hallazgos/$id3/cerrar" '' 'jefe.seguridad' | Out-Null
    Llamar '15-dashboard-200' 'GET' '/api/hallazgos/dashboard' | Out-Null
    Llamar '16-historial-hallazgo-200' 'GET' "/api/hallazgos/$id1/historial" | Out-Null
    Llamar '17-historial-inexistente-404' 'GET' '/api/hallazgos/6f1c2a7e-0000-4000-8000-000000000000/historial' | Out-Null
}
finally {
    Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
}
Write-Host "Evidencias generadas en $ev"
