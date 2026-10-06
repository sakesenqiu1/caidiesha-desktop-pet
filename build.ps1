# Caidiesha desktop-pet APK -- pure SDK build (no Gradle, no network needed)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File build.ps1
#   powershell -ExecutionPolicy Bypass -File build.ps1 -SkipAssets   # skip sprite regeneration
#
# Environment (all optional, auto-detected when omitted):
#   ANDROID_SDK_ROOT   Android SDK dir (defaults to D:\android-sdk, then common locations)
#   JAVA_HOME          JDK dir (defaults to C:\Program Files\Microsoft\jdk-17*)
#   or pass -SdkDir / -JdkHome
#
# NOTE: keep this file pure ASCII -- Windows PowerShell 5.1 reads scripts as ANSI/GBK.

param(
    [string]$SdkDir  = $env:ANDROID_SDK_ROOT,
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$SkipAssets
)

$ErrorActionPreference = 'Stop'
$root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Run($exe, $argv) {
    & $exe @argv
    if ($LASTEXITCODE -ne 0) { throw "FAILED: $exe" }
}

# ---------------- auto-detect SDK ----------------
if (-not $SdkDir) {
    foreach ($p in @('D:\android-sdk', 'D:\Android\Sdk',
                     "$env:LOCALAPPDATA\Android\Sdk")) {
        if (Test-Path $p) { $SdkDir = $p; break }
    }
}
if (-not $SdkDir -or -not (Test-Path $SdkDir)) {
    throw "Android SDK not found. Set ANDROID_SDK_ROOT or pass -SdkDir <path>."
}

# ---------------- auto-detect JDK ----------------
if (-not $JdkHome -or -not (Test-Path "$JdkHome\bin\javac.exe")) {
    $cands = @()
    foreach ($base in @('C:\Program Files\Microsoft', 'C:\Program Files\Java',
                        'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Android')) {
        if (Test-Path $base) {
            $cands += Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match 'jdk' -and (Test-Path "$($_.FullName)\bin\javac.exe") } |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
        }
    }
    if ($cands.Count -gt 0) { $JdkHome = $cands[0] }
}
if (-not $JdkHome -or -not (Test-Path "$JdkHome\bin\javac.exe")) {
    throw "JDK not found. Set JAVA_HOME or pass -JdkHome <path>."
}

# ---------------- auto-detect build-tools / platform ----------------
$btDir = Get-ChildItem "$SdkDir\build-tools" -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending | Select-Object -First 1
if (-not $btDir) { throw "build-tools not found under $SdkDir" }
$bt = $btDir.FullName

$plat = Get-ChildItem "$SdkDir\platforms" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match 'android-\d+' } |
    Sort-Object { [int]($_.Name -replace '[^0-9]', '') } -Descending | Select-Object -First 1
if (-not $plat) { throw "platforms not found under $SdkDir" }
$jar = Join-Path $plat.FullName 'android.jar'

$JdkHome = $JdkHome.TrimEnd('\')
$java    = "$JdkHome\bin\java.exe"
$javac   = "$JdkHome\bin\javac.exe"
$keytool = "$JdkHome\bin\keytool.exe"
$jarr    = "$JdkHome\bin\jar.exe"

$app      = "$root\app"
$res      = "$app\src\main\res"
$jsrc     = "$app\src\main\java"
$build    = "$app\build"
$work     = "$root\work"
$tools    = "$root\tools"
$keystore = "$root\debug.keystore"
$utf8     = New-Object System.Text.UTF8Encoding($false)

Write-Host "SDK        : $SdkDir"
Write-Host "build-tools: $($btDir.Name)"
Write-Host "platform   : $($plat.Name)"
Write-Host "JDK        : $JdkHome"

foreach ($p in @($bt, $jar, $JdkHome)) { if (-not (Test-Path $p)) { throw "missing: $p" } }

# ---------------- 0: rebuild sprites + icons from the reference image ----------------
if (-not $SkipAssets) {
    Step '0/9 rebuild sprites from reference images'
    New-Item -ItemType Directory -Force -Path "$work\strict" | Out-Null
    Run $javac @('-encoding', 'UTF-8', '-nowarn', '-d', "$work\classes",
        "$tools\StrictCut.java", "$tools\ExportStrict.java", "$tools\IconStrict.java")
    Run $java @('-cp', "$work\classes", 'StrictCut', "$work\ref_full.jpg",
        "$work\strict", '30', '480', 'pet_full', '')
    Run $java @('-cp', "$work\classes", 'ExportStrict', "$work\strict\pet_full.png", $res)
    Run $java @('-cp', "$work\classes", 'IconStrict', "$work\strict\pet_full.png", $res)
} else {
    Step '0/9 skip asset rebuild'
}

# ---------------- clean ----------------
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$build\gen", "$build\classes", "$build\dex" | Out-Null

Step '1/9 aapt2 compile'
Run "$bt\aapt2.exe" @('compile', '--dir', $res, '-o', "$build\res.zip")

Step '2/9 aapt2 link'
Run "$bt\aapt2.exe" @(
    'link', '-o', "$build\base.apk", '-I', $jar,
    '--manifest', "$app\src\main\AndroidManifest.xml",
    '--java', "$build\gen",
    '--min-sdk-version', '26', '--target-sdk-version', '34',
    '--no-version-vectors',
    "$build\res.zip")

Step '3/9 javac'
$srcs = @(Get-ChildItem $jsrc -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$gen = @(Get-ChildItem "$build\gen" -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
Write-Host "  sources: $($srcs.Count) + R.java $($gen.Count)"
# No -source/-target: android.jar and the JDK's lambda support must both be visible.
# d8 handles the class-file downgrade (it accepts up to Java 11 class format).
$argLines = @('-encoding', 'UTF-8', '-nowarn', '-Xlint:none',
              '-XDignore.symbol.file',
              '-classpath', $jar, '-d', "$build\classes")
$argLines += ($srcs + $gen)
# javac reads @argfiles as UTF-8 (JDK9+), so write without BOM, one arg per line
[System.IO.File]::WriteAllLines("$build\javac.args", $argLines, $utf8)
Run $javac @("-J-Dfile.encoding=UTF-8", "@$build\javac.args")

Step '4/9 d8'
$classes = @(Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
Write-Host "  classes: $($classes.Count)"
$d8 = @('--min-api', '26', '--lib', $jar, '--output', "$build\dex") + $classes
Run "$bt\d8.bat" $d8

Step '5/9 package'
Copy-Item "$build\base.apk" "$build\unsigned.apk" -Force
Push-Location "$build\dex"
& $jarr --update --file "$build\unsigned.apk" classes.dex | Out-Null
$rc = $LASTEXITCODE
Pop-Location
if ($rc -ne 0) { throw 'FAILED: add classes.dex' }

Step '6/9 zipalign'
Run "$bt\zipalign.exe" @('-f', '-p', '4', "$build\unsigned.apk", "$build\aligned.apk")

Step '7/9 sign'
if (-not (Test-Path $keystore)) {
    Run $keytool @('-genkeypair', '-keystore', $keystore, '-storepass', 'android',
        '-keypass', 'android', '-alias', 'caidiesha', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '10950', '-dname', 'CN=Caidiesha Pet, OU=Personal, O=DSH, C=CN')
}
Run "$bt\apksigner.bat" @('sign', '--ks', $keystore, '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android', '--ks-key-alias', 'caidiesha',
    '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true',
    '--out', "$build\caidiesha-pet.apk", "$build\aligned.apk")

Step '8/9 verify signature'
Run "$bt\apksigner.bat" @('verify', '--print-certs', "$build\caidiesha-pet.apk")

Step '9/9 quality gates (the static checkers that caught real bugs)'
New-Item -ItemType Directory -Force -Path "$work\classes" | Out-Null
Run $javac @('-encoding', 'UTF-8', '-nowarn', '-d', "$work\classes",
    "$jsrc\com\dsh\caidiesha\Physics.java",
    "$tools\RecursionCheck.java", "$tools\DragCheck.java", "$tools\MenuCheck.java",
    "$tools\PhysicsCheck.java", "$tools\LockFlowCheck.java", "$tools\SpriteOverlap.java")
$phys = "$jsrc\com\dsh\caidiesha\Physics.java"
$petv = "$jsrc\com\dsh\caidiesha\PetView.java"
# Pass args through a file: PowerShell mangles a separate "-cp <path>" pair when the
# path contains characters it wants to interpret. @argfile is what javac already uses.
function RunJava($mainArgs) {
    $lines = @('-cp', "$work\classes") + $mainArgs
    [System.IO.File]::WriteAllLines("$work\java.args", $lines, $utf8)
    Run $java @("@$work\java.args")
}
RunJava @('RecursionCheck', $jsrc)
RunJava @('DragCheck')
RunJava @('MenuCheck')
RunJava @('PhysicsCheck', $phys)
RunJava @('LockFlowCheck', $petv)
RunJava @('SpriteOverlap', "$work\strict\pet_full.png", '540', '336', '90', '541')

$apk = Get-Item "$build\caidiesha-pet.apk"
Write-Host "`nAPK OK: $($apk.FullName)  $([math]::Round($apk.Length/1KB,1)) KB" -ForegroundColor Green
