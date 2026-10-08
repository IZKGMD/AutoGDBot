$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$workerSource = Get-Content -LiteralPath (Join-Path $root 'windows-bridge-v1/worker.ps1') -Raw
$match = [regex]::Match($workerSource, "(?s)Add-Type -TypeDefinition @'\r?\n(.*?)\r?\n'@")
Add-Type -TypeDefinition $match.Groups[1].Value
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseInput($workerSource, [ref]$tokens, [ref]$errors)
foreach ($definition in $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
$temp = Join-Path ([IO.Path]::GetTempPath()) ('gdbridge-' + [Guid]::NewGuid().ToString('N'))
$GameDirectory = Join-Path $temp 'game'
$GameExe = Join-Path $GameDirectory 'GeometryDash.exe'
# Installer function references the script root; stage only its fixed package fixture here.
$AgentDirectory = Join-Path $temp 'agent'
$staged = Join-Path $AgentDirectory 'local-state/package.geode'
$mods = Join-Path $GameDirectory 'geode/mods'
[void][IO.Directory]::CreateDirectory((Join-Path $temp 'fixtures'))
Add-Type -AssemblyName System.IO.Compression.FileSystem
function New-Fixture([string]$Name, [string]$Identity, [int]$Machine) {
    $file = Join-Path $temp "fixtures/$Name.geode"
    $archive = [IO.Compression.ZipFile]::Open($file, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry = $archive.CreateEntry('mod.json')
        $writer = [IO.StreamWriter]::new($entry.Open())
        try { $writer.Write((@{id=$Identity;version='v0.22.0';gd=@{win='2.2081'}} | ConvertTo-Json -Compress)) } finally { $writer.Dispose() }
        $dll = [byte[]]::new(256)
        $dll[0]=0x4D; $dll[1]=0x5A; $dll[60]=64; $dll[64]=0x50; $dll[65]=0x45
        $dll[68]=$Machine -band 255; $dll[69]=($Machine -shr 8) -band 255
        $entry = $archive.CreateEntry('autogdbot.realtime.dll')
        $stream = $entry.Open()
        try { $stream.Write($dll,0,$dll.Length) } finally { $stream.Dispose() }
    } finally { $archive.Dispose() }
}
New-Fixture 'good' 'autogdbot.realtime' 0x8664
New-Fixture 'wrong-id' 'another.mod' 0x8664
New-Fixture 'arm' 'autogdbot.realtime' 0xAA64
[void][IO.Directory]::CreateDirectory((Split-Path $staged -Parent))
[void][IO.Directory]::CreateDirectory($mods)
$script:gameRunning = $false
function Get-GameProcess { if ($script:gameRunning) { return @{ running=$true } }; return $null }
function Check([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Must-Fail([scriptblock]$Action, [string]$Pattern) {
    $caught = $false
    try { & $Action | Out-Null } catch { if ($_.Exception.Message -notmatch $Pattern) { throw }; $caught=$true }
    Check $caught "Expected failure: $Pattern"
}
try {
    Copy-Item -LiteralPath (Join-Path $temp 'fixtures/good.geode') -Destination (Join-Path $mods 'old-bot.geode')
    Copy-Item -LiteralPath (Join-Path $temp 'fixtures/wrong-id.geode') -Destination (Join-Path $mods 'another.geode')
    $otherHash = (Get-FileHash -LiteralPath (Join-Path $mods 'another.geode')).Hash
    foreach ($fixture in @('wrong-id', 'arm')) {
        Copy-Item -LiteralPath (Join-Path $temp "fixtures/$fixture.geode") -Destination $staged -Force
        Must-Fail { Install-BotPackage } 'Wrong bot|Windows x64'
        Check (Test-Path -LiteralPath (Join-Path $mods 'old-bot.geode')) 'Old package lost on rejection'
    }
    Copy-Item -LiteralPath (Join-Path $temp 'fixtures/good.geode') -Destination $staged -Force
    $script:gameRunning = $true
    Must-Fail { Install-BotPackage } 'Close Geometry Dash'
    $script:gameRunning = $false
    [GDRemoteNative]::Stopped = $true
    Must-Fail { Install-BotPackage } 'Stopped locally|interrupted'
    Check (Test-Path -LiteralPath (Join-Path $mods 'old-bot.geode')) 'Old package lost on local stop'
    [GDRemoteNative]::Stopped = $false
    $installed = Install-BotPackage
    Check $installed.installed 'No installed result'
    Check ($installed.backed_up -eq 1) 'Duplicate bot was not backed up'
    Check (Test-Path -LiteralPath (Join-Path $mods 'autogdbot.realtime.geode')) 'Target missing'
    Check (-not (Test-Path -LiteralPath (Join-Path $mods 'old-bot.geode'))) 'Old duplicate still active'
    Check ((Get-FileHash -LiteralPath (Join-Path $mods 'another.geode')).Hash -eq $otherHash) 'Other mod changed'
    Check (@(Get-ChildItem -LiteralPath (Join-Path $AgentDirectory 'local-state/backups') -Recurse -Filter 'old-bot.geode').Count -eq 1) 'Backup missing'
    $csv = Join-Path $temp 'sample.csv'
    [IO.File]::WriteAllText($csv, "tick,x,y`n" + ((1..3000 | ForEach-Object { "$_,1,2`n" }) -join ''))
    $tail = Read-SharedTail $csv 4096
    Check ($tail.Length -le 4096 -and $tail -match '3000,1,2') 'CSV tail is incorrect'
    Write-Output 'PASS: wrong identity/CPU rejected, running game rejected, stop preserves old mod, backup and unrelated mod preserved, CSV tail bounded'
} finally { [IO.Directory]::Delete($temp, $true) }
