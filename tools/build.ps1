$ErrorActionPreference = "Stop"
if (-not $env:GEODE_SDK) { throw "Set GEODE_SDK to Geode SDK 5.10.1" }
$version = (Get-Content (Join-Path $env:GEODE_SDK "VERSION") -Raw).Trim()
if ($version -ne "5.10.1") { throw "This project needs Geode SDK 5.10.1, found $version" }
$bindingsCommit = "f050fd74ad531b0827377c97510cb807903ff1b3"
$buildRoot = Join-Path $PSScriptRoot "../build"
$bindingsRoot = Join-Path $buildRoot "deps/bindings-$bindingsCommit"
if (-not (Test-Path $bindingsRoot)) {
    $deps = Join-Path $buildRoot "deps"
    New-Item -ItemType Directory -Force -Path $deps | Out-Null
    $archive = Join-Path $deps "bindings.zip"
    Invoke-WebRequest -Uri "https://github.com/geode-sdk/bindings/archive/$bindingsCommit.zip" -OutFile $archive
    Expand-Archive -Path $archive -DestinationPath $deps -Force
}
cmake -S "$PSScriptRoot/.." -B $buildRoot -G Ninja -DCMAKE_BUILD_TYPE=Release "-DGEODE_BINDINGS_REPO_PATH=$bindingsRoot"
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed" }
cmake --build $buildRoot --config Release --parallel 2
if ($LASTEXITCODE -ne 0) { throw "Build failed" }
Write-Host "Package: $buildRoot/autogdbot.realtime.geode"
