<#
  一键构建：前端 + APK。

  用法：
    $env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
    $env:ANDROID_SDK_ROOT = 'C:\Users\me\AppData\Local\Android\Sdk'
    powershell -ExecutionPolicy Bypass -File scripts/build-all.ps1
#>
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'build-web.ps1')
if ($LASTEXITCODE -ne 0) { throw 'build-web failed' }

& (Join-Path $PSScriptRoot 'build-apk.ps1')
if ($LASTEXITCODE -ne 0) { throw 'build-apk failed' }
