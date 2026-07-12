[CmdletBinding()]
param([switch]$SkipMilvus, [switch]$SkipInstall, [int]$BackendPort = 8088, [int]$FrontendPort = 5173)
$ErrorActionPreference = 'Stop'; $root = Split-Path -Parent $PSScriptRoot; $runtime = Join-Path $root '.demo-runtime'; New-Item -ItemType Directory -Force -Path $runtime | Out-Null
foreach ($command in @('java','mvn','node','npm')) { if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required command not found: $command" } }
if (-not $SkipMilvus -and -not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Required command not found: docker' }
if (([int]((java -version 2>&1 | Select-Object -First 1) -replace '.*?"(\d+).*','$1')) -lt 21) { throw 'Java 21 or newer is required.' }
if (-not $SkipMilvus) { docker compose -f (Join-Path $root 'docker-compose.milvus.yml') up -d; }
if (-not $SkipInstall) { Push-Location (Join-Path $root 'web'); try { npm install } finally { Pop-Location } }
$backend = Start-Process mvn -ArgumentList "spring-boot:run -Dspring-boot.run.arguments=--server.port=$BackendPort" -WorkingDirectory $root -PassThru -WindowStyle Hidden
$frontend = Start-Process npm -ArgumentList "run dev -- --port $FrontendPort" -WorkingDirectory (Join-Path $root 'web') -PassThru -WindowStyle Hidden
@{ backend = $backend.Id; frontend = $frontend.Id } | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $runtime 'pids.json')
$health = "http://localhost:$BackendPort/actuator/health"; for ($i=0; $i -lt 30; $i++) { try { if ((Invoke-RestMethod $health -TimeoutSec 2).status -eq 'UP') { break } } catch {}; Start-Sleep -Seconds 2 }; if ($i -eq 30) { throw 'Backend did not become healthy within 60 seconds.' }
Write-Host "Frontend URL: http://localhost:$FrontendPort"; Write-Host "Backend URL: http://localhost:$BackendPort"; Write-Host "Actuator health URL: $health"; Write-Host "RAG/LLM mode: local demo defaults unless environment variables configure providers."
