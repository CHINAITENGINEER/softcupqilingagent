[CmdletBinding()]
param(
    [switch]$SkipMilvus,
    [switch]$SkipInstall,
    [int]$BackendPort = 8088,
    [int]$FrontendPort = 5173
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root '.demo-runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

foreach ($command in @('java', 'mvn', 'node', 'npm')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $command"
    }
}
if (-not $SkipMilvus -and -not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Required command not found: docker'
}

$javaVersionOutput = cmd /c "java -version 2>&1"
$javaMajorVersion = [int](($javaVersionOutput | Select-Object -First 1) -replace '.*?"(\d+).*', '$1')
if ($javaMajorVersion -lt 21) {
    throw 'Java 21 or newer is required.'
}

if (-not $SkipMilvus) {
    docker compose -f (Join-Path $root 'docker-compose.milvus.yml') up -d
    if ($LASTEXITCODE -ne 0) { throw 'Failed to start Milvus services.' }
}
if (-not $SkipInstall) {
    Push-Location (Join-Path $root 'web')
    try { npm install } finally { Pop-Location }
}

$backend = $null
$frontend = $null
try {
    Push-Location $root
    try {
        mvn -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
    } finally {
        Pop-Location
    }
    $jar = Get-ChildItem (Join-Path $root 'target\qilingos-safeops-agent-*.jar') |
            Where-Object { $_.Name -notlike '*.original' } |
            Select-Object -First 1
    if (-not $jar) { throw 'Packaged backend JAR was not found.' }

    $backend = Start-Process java -ArgumentList '-jar', ('"' + $jar.FullName + '"'), "--server.port=$BackendPort" -WorkingDirectory $root -PassThru -WindowStyle Hidden
    $npmCommand = if ($IsWindows -or $env:OS -eq 'Windows_NT') { 'npm.cmd' } else { 'npm' }
    $frontend = Start-Process $npmCommand -ArgumentList 'run', 'dev', '--', '--host', '127.0.0.1', '--port', "$FrontendPort" -WorkingDirectory (Join-Path $root 'web') -PassThru -WindowStyle Hidden
    @{ backend = $backend.Id; frontend = $frontend.Id } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runtime 'pids.json')

    $health = "http://localhost:$BackendPort/actuator/health"
    $healthy = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            if ((Invoke-RestMethod $health -TimeoutSec 2).status -eq 'UP') {
                $healthy = $true
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) { throw 'Backend did not become healthy within 60 seconds.' }

    Write-Host "Frontend URL: http://localhost:$FrontendPort"
    Write-Host "Backend URL: http://localhost:$BackendPort"
    Write-Host "Actuator health URL: $health"
    Write-Host 'RAG/LLM mode: local demo defaults unless environment variables configure providers.'
} catch {
    if ($frontend) { Stop-Process -Id $frontend.Id -Force -ErrorAction SilentlyContinue }
    if ($backend) { Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue }
    Remove-Item (Join-Path $runtime 'pids.json') -Force -ErrorAction SilentlyContinue
    throw
}
