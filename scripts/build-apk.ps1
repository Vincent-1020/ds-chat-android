<#
  把 android/ 工程和 android/assets/web 里的前端打包成 APK。

  用法：
    $env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
    $env:ANDROID_SDK_ROOT = 'C:\Users\me\AppData\Local\Android\Sdk'
    powershell -ExecutionPolicy Bypass -File scripts/build-apk.ps1

  也可以直接传参：
    ... -JavaHome <jdk> -SdkRoot <sdk> -OutFile dist\DSChat.apk

  依赖：JDK 17、Android SDK（platform android-34 + build-tools 34.0.0）。
  不依赖 Gradle，也不需要联网下载 Maven 依赖。
#>
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$OutFile = '',
    [string]$Keystore = $env:DSCHAT_KEYSTORE
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot

if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw "JAVA_HOME 未设置或无效（需要 JDK 17）。当前值：$JavaHome"
}
if (-not $SdkRoot -or -not (Test-Path $SdkRoot)) {
    throw "ANDROID_SDK_ROOT 未设置或无效。当前值：$SdkRoot"
}

# ---- 定位 build-tools / platform ----
$buildToolsRoot = Join-Path $SdkRoot 'build-tools'
if (-not (Test-Path $buildToolsRoot)) { throw "找不到 $buildToolsRoot，请先装 Android SDK build-tools" }
$btDir = Get-ChildItem $buildToolsRoot -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'aapt2.exe') } |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending |
    Select-Object -First 1
if (-not $btDir) { throw '没有可用的 build-tools（需要含 aapt2.exe 的版本）' }

$platformRoot = Join-Path $SdkRoot 'platforms'
$platform = Get-ChildItem $platformRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $platform) { throw '找不到 android.jar，请安装 SDK platform（android-34）' }

$bt = $btDir.FullName
$androidJarLocal = Join-Path $platform.FullName 'android.jar'
Write-Host "build-tools : $bt"
Write-Host "platform    : $($platform.Name)"

# ---- 检查前端产物 ----
$webAssets = Join-Path $repo 'android\assets\web'
if (-not (Test-Path (Join-Path $webAssets 'index.html'))) {
    throw "缺少前端产物 $webAssets。请先运行 scripts/build-web.ps1"
}

# ---- 原生工具处理不了中文路径，统一在 ASCII 临时目录里构建 ----
$stage = Join-Path $env:TEMP 'ds-chat-apk'
$btS = Join-Path $stage 'tools\build-tools'
$jarS = Join-Path $stage 'tools\android.jar'
$projS = Join-Path $stage 'project'
$build = Join-Path $stage 'build'

if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'tools'), $build, (Join-Path $build 'classes'), (Join-Path $build 'dex') | Out-Null
Copy-Item $bt $btS -Recurse -Force
Copy-Item $androidJarLocal $jarS -Force
Copy-Item (Join-Path $repo 'android') $projS -Recurse -Force

$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

Write-Host '==> 1/6 compile resources'
& (Join-Path $btS 'aapt2.exe') compile --dir (Join-Path $projS 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '==> 2/6 link resources + assets'
& (Join-Path $btS 'aapt2.exe') link -o (Join-Path $build 'base.apk') -I $jarS `
    --manifest (Join-Path $projS 'AndroidManifest.xml') `
    --min-sdk-version 26 --target-sdk-version 34 `
    -A (Join-Path $projS 'assets') `
    (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '==> 3/6 compile java'
$javaFiles = Get-ChildItem (Join-Path $projS 'java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -nowarn -source 8 -target 8 `
    -classpath $jarS -d (Join-Path $build 'classes') $javaFiles
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '==> 4/6 dex'
$classFiles = Get-ChildItem (Join-Path $build 'classes') -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& (Join-Path $btS 'd8.bat') --lib $jarS --min-api 26 --output (Join-Path $build 'dex') $classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '==> 5/6 package + normalise asset paths'
Copy-Item (Join-Path $build 'base.apk') (Join-Path $build 'unsigned.apk') -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open((Join-Path $build 'unsigned.apk'), 'Update')
try {
    $existing = $zip.GetEntry('classes.dex')
    if ($existing) { $existing.Delete() }
    $entry = $zip.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $stream = $entry.Open()
    try {
        $bytes = [System.IO.File]::ReadAllBytes((Join-Path $build 'dex\classes.dex'))
        $stream.Write($bytes, 0, $bytes.Length)
    }
    finally { $stream.Dispose() }

    # Windows 版 aapt2 会把 assets 路径写成反斜杠（assets\web\index.html）。
    # AssetManager 只认 "/"，这样的条目运行时全部 404 -> 白屏。这里统一改回斜杠。
    $broken = @($zip.Entries | Where-Object {
        ($_.FullName.StartsWith('assets/') -or $_.FullName.StartsWith('assets\')) -and
        $_.FullName.Contains('\')
    })
    foreach ($e in $broken) {
        $ms = New-Object System.IO.MemoryStream
        $src = $e.Open()
        $src.CopyTo($ms)
        $src.Dispose()
        $data = $ms.ToArray()
        $ms.Dispose()
        $name = $e.FullName.Replace('\', '/')
        $e.Delete()
        $ne = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
        $ns = $ne.Open()
        $ns.Write($data, 0, $data.Length)
        $ns.Dispose()
    }
    Write-Host "     normalised $($broken.Count) asset entries"
}
finally { $zip.Dispose() }

$check = [System.IO.Compression.ZipFile]::OpenRead((Join-Path $build 'unsigned.apk'))
try {
    $left = @($check.Entries | Where-Object { $_.FullName.Contains('\') })
}
finally { $check.Dispose() }
if ($left.Count -gt 0) { throw "asset paths still contain backslashes: $($left.Count)" }

Write-Host '==> 6/6 align + sign'
& (Join-Path $btS 'zipalign.exe') -f -p 4 (Join-Path $build 'unsigned.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$ks = $Keystore
if (-not $ks) { $ks = Join-Path (Split-Path -Parent $repo) 'ds-chat-keys\debug.keystore' }
$keystoreDir = Split-Path -Parent $ks
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
    & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' `
        -keyalg RSA -keysize 2048 -validity 10000 2>&1 | Out-Null
}

if (-not $OutFile) { $OutFile = Join-Path $repo 'dist\DSChat.apk' }
$outDir = Split-Path -Parent $OutFile
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
if (Test-Path $OutFile) { Remove-Item $OutFile -Force }

& (Join-Path $btS 'apksigner.bat') sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --min-sdk-version 26 --out $OutFile (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& (Join-Path $btS 'apksigner.bat') verify $OutFile
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

Write-Host ''
Write-Host ("APK: {0} ({1:n0} bytes)" -f $OutFile, (Get-Item $OutFile).Length)
