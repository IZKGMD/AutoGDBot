$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
if ($null -eq $nodeCommand) {
    Write-Host 'Install Node.js LTS from https://nodejs.org and run START.cmd again.'
    exit 1
}
$configPath = Join-Path $PSScriptRoot 'config.json'
$gameDirectory = $null
if (Test-Path -LiteralPath $configPath -PathType Leaf) {
    try {
        $saved = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
        if (Test-Path -LiteralPath (Join-Path $saved.gameDirectory 'GeometryDash.exe') -PathType Leaf) { $gameDirectory = $saved.gameDirectory }
    } catch { }
}
if ($null -eq $gameDirectory) {
    Add-Type -AssemblyName System.Windows.Forms
    $picker = New-Object Windows.Forms.OpenFileDialog
    $picker.Title = 'Select your GeometryDash.exe'
    $picker.Filter = 'Geometry Dash|GeometryDash.exe'
    $picker.CheckFileExists = $true
    try {
        if ($picker.ShowDialog() -ne [Windows.Forms.DialogResult]::OK) { exit 1 }
        if ([IO.Path]::GetFileName($picker.FileName) -ne 'GeometryDash.exe') { throw 'Select GeometryDash.exe' }
        $gameDirectory = [IO.Path]::GetDirectoryName($picker.FileName)
    } finally { $picker.Dispose() }
    $text = @{ gameDirectory=$gameDirectory } | ConvertTo-Json
    [IO.File]::WriteAllText($configPath, $text, (New-Object Text.UTF8Encoding($false)))
}
Write-Host ''
Write-Host 'AutoGDBot Windows bridge v1'
Write-Host 'It accepts game-window commands through your PRIVATE IZKGMD/AutoGDBot GitHub repository.'
Write-Host 'The assistant can launch/close/focus the game, click it, press game keys and read bot CSV.'
Write-Host 'It can replace only the AutoGDBot .geode while the game is closed, backing up its old version.'
Write-Host 'F8 stops control. Closing this console stops the bridge. Session limit: 60 minutes.'
Write-Host 'Keep the game foreground for capture/input. Other foreground apps are rejected.'
Write-Host ''
Write-Host 'Create a fine-grained token: Only select repositories -> AutoGDBot.'
Write-Host 'Repository permissions -> Contents -> Read and write. Use a short expiry.'
Write-Host 'Token setup: https://github.com/settings/personal-access-tokens/new?name=AutoGDBot-Bridge&target_name=IZKGMD&expires_in=1&contents=write'
Write-Host 'Paste the token below; input is hidden. Do not send the token in chat.'
$secret = Read-Host 'GitHub fine-grained token' -AsSecureString
$pointer = [IntPtr]::Zero
try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:AUTOGDBOT_GITHUB_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    & $nodeCommand.Source (Join-Path $PSScriptRoot 'agent.cjs')
    $agentExit = $LASTEXITCODE
} finally {
    Remove-Item Env:AUTOGDBOT_GITHUB_TOKEN -ErrorAction SilentlyContinue
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $secret.Dispose()
}
exit $agentExit
