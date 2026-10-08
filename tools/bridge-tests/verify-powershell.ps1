$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$source = Join-Path $root 'windows-bridge-v1'
foreach ($name in @('worker.ps1', 'Setup.ps1')) {
    $tokens = $null; $errors = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $source $name), [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) { throw ($errors | Out-String) }
    Write-Output "Parser PASS: $name"
}
$workerSource = Get-Content -LiteralPath (Join-Path $source 'worker.ps1') -Raw
$match = [regex]::Match($workerSource, "(?s)Add-Type -TypeDefinition @'\r?\n(.*?)\r?\n'@")
if (-not $match.Success) { throw 'Native source was not found' }
Add-Type -TypeDefinition $match.Groups[1].Value
$size = [Runtime.InteropServices.Marshal]::SizeOf([type][GDRemoteNative+INPUT])
if ($size -ne 40) { throw "Windows x64 INPUT layout is wrong: $size" }
Write-Output 'Native C# compile and x64 INPUT layout PASS (no user32 calls executed)'
