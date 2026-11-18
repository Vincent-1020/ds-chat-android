<#
  构建前端（web/），并把产物同步到 android/assets/web。

  用法：
    powershell -ExecutionPolicy Bypass -File scripts/build-web.ps1
    powershell -ExecutionPolicy Bypass -File scripts/build-web.ps1 -Clean   # 先删 node_modules

  需要 Node.js 20+。
#>
param(
    [switch]$Clean,
    [string]$Npm = 'npm.cmd'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$web = Join-Path $repo 'web'
$assets = Join-Path $repo 'android\assets\web'

Push-Location $web
try {
    if ($Clean) {
        Remove-Item (Join-Path $web 'node_modules'), (Join-Path $web 'dist') -Recurse -Force -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path (Join-Path $web 'node_modules'))) {
        Write-Host '==> npm install'
        & $Npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    }

    Write-Host '==> npm run build'
    & $Npm run build
    if ($LASTEXITCODE -ne 0) { throw 'web build failed' }
}
finally {
    Pop-Location
}

Write-Host '==> sync dist -> android/assets/web'
Remove-Item $assets -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $assets) | Out-Null
Copy-Item (Join-Path $web 'dist') $assets -Recurse -Force

$count = (Get-ChildItem $assets -Recurse -File).Count
Write-Host "done: $count files under android/assets/web"
