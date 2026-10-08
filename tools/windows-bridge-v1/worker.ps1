param([Parameter(Mandatory=$true)][string]$GameDirectory)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$AgentDirectory = $PSScriptRoot
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$GameDirectory = [IO.Path]::GetFullPath($GameDirectory)
$GameExe = Join-Path $GameDirectory 'GeometryDash.exe'
if (-not (Test-Path -LiteralPath $GameExe -PathType Leaf)) { throw 'GeometryDash.exe is missing' }
Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Threading;
public static class GDRemoteNative {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT {
        public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr extra;
    }
    [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT {
        public ushort vk, scan; public uint flags, time; public IntPtr extra;
    }
    [StructLayout(LayoutKind.Explicit)] public struct UNION {
        [FieldOffset(0)] public MOUSEINPUT mouse;
        [FieldOffset(0)] public KEYBDINPUT keyboard;
    }
    [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public UNION data; }
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr window, out RECT rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr window, ref POINT point);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr window);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr window, int command);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern short GetAsyncKeyState(int key);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll", SetLastError=true)] private static extern uint SendInput(uint count, INPUT[] inputs, int size);
    public static volatile bool Stopped;
    public static uint AllowedProcess;
    public static void StartMonitor() {
        Thread monitor = new Thread(delegate() {
            while (!Stopped) {
                if ((GetAsyncKeyState(0x77) & 0x8000) != 0) { Stopped = true; break; }
                Thread.Sleep(20);
            }
        });
        monitor.IsBackground = true; monitor.Start();
    }
    public static void Guard(IntPtr window) {
        if (Stopped) throw new InvalidOperationException("Stopped locally with F8");
        uint owner;
        GetWindowThreadProcessId(window, out owner);
        if (owner == 0 || owner != AllowedProcess) throw new InvalidOperationException("Game window ownership changed");
        if (GetForegroundWindow() != window || IsIconic(window) || !IsWindowVisible(window))
            throw new InvalidOperationException("Geometry Dash must be the foreground window");
    }
    private static void Send(INPUT[] inputs) {
        if (SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT))) != inputs.Length)
            throw new InvalidOperationException("Windows rejected input; use the same non-administrator privilege level for game and agent");
    }
    private static INPUT Keyboard(ushort key, bool up) {
        INPUT value = new INPUT(); value.type = 1;
        value.data.keyboard.vk = key;
        value.data.keyboard.flags = up ? 2u : 0u;
        if (key >= 0x25 && key <= 0x28) value.data.keyboard.flags |= 1u;
        return value;
    }
    public static void Tap(IntPtr window, ushort key, int milliseconds) {
        Guard(window);
        if (milliseconds < 10 || milliseconds > 1500) throw new InvalidOperationException("Invalid key duration");
        if (!(key == 0x20 || key == 0x0D || key == 0x1B || key == 0x52 || key == 0x09 || (key >= 0x25 && key <= 0x28)))
            throw new InvalidOperationException("Key is not allowed");
        try {
            Send(new INPUT[] { Keyboard(key, false) });
            for (int waited = 0; waited < milliseconds; waited += 10) {
                if (Stopped || GetForegroundWindow() != window) break;
                Thread.Sleep(Math.Min(10, milliseconds - waited));
            }
        } finally { Send(new INPUT[] { Keyboard(key, true) }); }
        Guard(window);
    }
    public static void Click(IntPtr window, double x, double y) {
        Guard(window);
        if (Double.IsNaN(x) || Double.IsNaN(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new InvalidOperationException("Invalid click coordinates");
        RECT rect; POINT origin = new POINT();
        if (!GetClientRect(window, out rect) || !ClientToScreen(window, ref origin))
            throw new InvalidOperationException("Cannot locate the game client area");
        int width = rect.Right - rect.Left, height = rect.Bottom - rect.Top;
        if (width < 1 || height < 1) throw new InvalidOperationException("Empty game client area");
        int px = origin.X + (int)Math.Round(x * (width - 1));
        int py = origin.Y + (int)Math.Round(y * (height - 1));
        int vx = GetSystemMetrics(76), vy = GetSystemMetrics(77);
        int vw = GetSystemMetrics(78), vh = GetSystemMetrics(79);
        if (vw < 2 || vh < 2 || px < vx || py < vy || px >= vx + vw || py >= vy + vh)
            throw new InvalidOperationException("Game click is outside the visible desktop");
        INPUT move = new INPUT();
        move.data.mouse.dx = (int)Math.Round((px - vx) * 65535.0 / (vw - 1));
        move.data.mouse.dy = (int)Math.Round((py - vy) * 65535.0 / (vh - 1));
        move.data.mouse.dwFlags = 0x0001 | 0x8000 | 0x4000;
        INPUT down = new INPUT(); down.data.mouse.dwFlags = 0x0002;
        INPUT up = new INPUT(); up.data.mouse.dwFlags = 0x0004;
        Guard(window);
        try { Send(new INPUT[] { move, down }); }
        finally { Send(new INPUT[] { up }); }
    }
}
'@
[void][GDRemoteNative]::SetProcessDPIAware()
[GDRemoteNative]::StartMonitor()

function Get-GameProcess {
    $unreadable = $false
    foreach ($candidate in @(Get-Process -Name GeometryDash -ErrorAction SilentlyContinue)) {
        try {
            $exePath = $candidate.MainModule.FileName
            if ([string]::IsNullOrEmpty($exePath)) { $unreadable = $true; continue }
            if ([string]::Equals($exePath, $GameExe, [StringComparison]::OrdinalIgnoreCase)) {
                $candidate.Refresh()
                return $candidate
            }
        } catch { $unreadable = $true }
    }
    if ($unreadable) { throw 'Cannot inspect a GeometryDash process. Run game and bridge as the same normal Windows user, without administrator mode.' }
    return $null
}
function Get-GameWindow {
    $gameProcess = Get-GameProcess
    if ($null -eq $gameProcess -or $gameProcess.MainWindowHandle -eq [IntPtr]::Zero) { throw 'Geometry Dash window is unavailable' }
    $window = $gameProcess.MainWindowHandle
    [uint32]$ownerProcess = 0
    [void][GDRemoteNative]::GetWindowThreadProcessId($window, [ref]$ownerProcess)
    if ($ownerProcess -ne $gameProcess.Id) { throw 'Game window ownership changed' }
    [GDRemoteNative]::AllowedProcess = $ownerProcess
    return $window
}
function Get-Status {
    $gameProcess = Get-GameProcess
    $running = $null -ne $gameProcess
    $foreground = $false
    if ($running -and $gameProcess.MainWindowHandle -ne [IntPtr]::Zero) {
        $foreground = [GDRemoteNative]::GetForegroundWindow() -eq $gameProcess.MainWindowHandle
    }
    $mod = Join-Path $GameDirectory 'geode\mods\autogdbot.realtime.geode'
    $modHash = $null
    if (Test-Path -LiteralPath $mod -PathType Leaf) { $modHash = (Get-FileHash -LiteralPath $mod -Algorithm SHA256).Hash.ToLowerInvariant() }
    return @{ running=$running; foreground=$foreground; stopped=[GDRemoteNative]::Stopped; exact_package_sha256=$modHash }
}
function Set-GameForeground {
    $window = Get-GameWindow
    if ([GDRemoteNative]::IsIconic($window)) { [void][GDRemoteNative]::ShowWindow($window, 9) }
    [void][GDRemoteNative]::SetForegroundWindow($window)
    Start-Sleep -Milliseconds 120
    [GDRemoteNative]::Guard($window)
    return @{ foreground=$true }
}
function Get-GameImage {
    $window = Get-GameWindow
    [GDRemoteNative]::Guard($window)
    $rect = New-Object GDRemoteNative+RECT
    $origin = New-Object GDRemoteNative+POINT
    if (-not [GDRemoteNative]::GetClientRect($window, [ref]$rect) -or -not [GDRemoteNative]::ClientToScreen($window, [ref]$origin)) { throw 'Cannot locate the game client area' }
    $width = $rect.Right - $rect.Left
    $height = $rect.Bottom - $rect.Top
    if ($width -lt 1 -or $height -lt 1 -or $width -gt 8192 -or $height -gt 8192) { throw 'Invalid game window dimensions' }
    $vx = [GDRemoteNative]::GetSystemMetrics(76); $vy = [GDRemoteNative]::GetSystemMetrics(77)
    $vw = [GDRemoteNative]::GetSystemMetrics(78); $vh = [GDRemoteNative]::GetSystemMetrics(79)
    if ($origin.X -lt $vx -or $origin.Y -lt $vy -or $origin.X + $width -gt $vx + $vw -or $origin.Y + $height -gt $vy + $vh) { throw 'Put the whole game window on a visible monitor' }
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $small = $null; $resize = $null; $stream = $null; $parameters = $null
    try {
        [GDRemoteNative]::Guard($window)
        $graphics.CopyFromScreen($origin.X, $origin.Y, 0, 0, $bitmap.Size, [Drawing.CopyPixelOperation]::SourceCopy)
        [GDRemoteNative]::Guard($window)
        $scale = [Math]::Min(1.0, [Math]::Min(1280.0 / $width, 960.0 / $height))
        $outWidth = [Math]::Max(1, [int][Math]::Floor($width * $scale))
        $outHeight = [Math]::Max(1, [int][Math]::Floor($height * $scale))
        $small = [Drawing.Bitmap]::new($outWidth, $outHeight)
        $resize = [Drawing.Graphics]::FromImage($small)
        $resize.DrawImage($bitmap, 0, 0, $outWidth, $outHeight)
        $stream = New-Object IO.MemoryStream
        $encoder = @([Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' })[0]
        $parameters = [Drawing.Imaging.EncoderParameters]::new(1)
        $parameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new([Drawing.Imaging.Encoder]::Quality, [long]65)
        $small.Save($stream, $encoder, $parameters)
        $bytes = $stream.ToArray()
        if ($bytes.Length -gt 400000) { throw 'Screenshot exceeds the upload limit; reduce the game window size' }
        return @{ mime='image/jpeg'; width=$outWidth; height=$outHeight; client_width=$width; client_height=$height; base64=[Convert]::ToBase64String($bytes) }
    } finally {
        if ($parameters) { $parameters.Dispose() }
        if ($stream) { $stream.Dispose() }
        if ($resize) { $resize.Dispose() }
        if ($small) { $small.Dispose() }
        $graphics.Dispose(); $bitmap.Dispose()
    }
}
function Read-SharedTail([string]$FilePath, [int]$Limit=32768) {
    $stream = [IO.File]::Open($FilePath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $count = [int][Math]::Min($stream.Length, $Limit)
        [void]$stream.Seek(-$count, [IO.SeekOrigin]::End)
        $buffer = New-Object byte[] $count
        $read = $stream.Read($buffer, 0, $count)
        $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $read)
        if ($stream.Length -gt $Limit) {
            $newline = $text.IndexOf("`n")
            if ($newline -ge 0) { $text = $text.Substring($newline + 1) }
        }
        return $text
    } finally { $stream.Dispose() }
}
function Get-BotLogs {
    # Geode getSaveDir(): LocalAppData/GeometryDash/geode/mods/<mod-id>.
    $gameSave = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'GeometryDash'
    $save = Join-Path $gameSave 'geode\mods\autogdbot.realtime'
    $items = @()
    if (Test-Path -LiteralPath $save -PathType Container) {
        foreach ($directory in @($gameSave, (Join-Path $gameSave 'geode'), (Join-Path $gameSave 'geode\mods'), $save)) {
            if (((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Bot log directory cannot be a link' }
        }
        foreach ($file in @(Get-ChildItem -LiteralPath $save -Filter 'timing-v*.csv' -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 3)) {
            if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { continue }
            $tail = Read-SharedTail $file.FullName
            $header = ''
            $reader = [IO.StreamReader]::new([IO.File]::Open($file.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite))
            try { $header = $reader.ReadLine() } finally { $reader.Dispose() }
            $items += @{ name=$file.Name; total_bytes=$file.Length; header=$header; tail=$tail }
        }
    }
    return @{ files=$items; note='Enable Save timing CSV in AutoGDBot settings. Only bot timing CSV files are collected.' }
}
function Install-BotPackage {
    if ($null -ne (Get-GameProcess)) { throw 'Close Geometry Dash before installing the bot package' }
    $packageFile = Join-Path $AgentDirectory 'local-state\package.geode'
    if (-not (Test-Path -LiteralPath $packageFile -PathType Leaf)) { throw 'Staged bot package is missing' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($packageFile)
    try {
        $manifestEntry = $archive.GetEntry('mod.json')
        if ($null -eq $manifestEntry -or $manifestEntry.Length -gt 65536) { throw 'Invalid Geode manifest' }
        $reader = [IO.StreamReader]::new($manifestEntry.Open())
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($manifest.id -ne 'autogdbot.realtime' -or $manifest.version -notmatch '^v?\d+\.\d+\.\d+$' -or $manifest.gd.win -ne '2.2081') { throw 'Wrong bot package identity or target game version' }
        $dll = $archive.GetEntry('autogdbot.realtime.dll')
        if ($null -eq $dll -or $dll.Length -lt 256 -or $dll.Length -gt 10000000) { throw 'Windows bot DLL is missing or invalid' }
        $stream = $dll.Open()
        $memory = [IO.MemoryStream]::new()
        try { $stream.CopyTo($memory); $dllBytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
        if ($dllBytes[0] -ne 0x4D -or $dllBytes[1] -ne 0x5A) { throw 'Invalid Windows DLL' }
        $pe = [BitConverter]::ToInt32($dllBytes, 60)
        if ($pe -lt 64 -or $pe + 6 -gt $dllBytes.Length -or [BitConverter]::ToUInt32($dllBytes, $pe) -ne 0x4550 -or [BitConverter]::ToUInt16($dllBytes, $pe + 4) -ne 0x8664) { throw 'Bot DLL must be Windows x64' }
    } finally { $archive.Dispose() }
    $mods = Join-Path $GameDirectory 'geode\mods'
    if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw 'Geode mods folder is missing' }
    foreach ($directory in @((Join-Path $GameDirectory 'geode'), $mods)) {
        if (((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Geode directories cannot be links' }
    }
    $target = Join-Path $mods 'autogdbot.realtime.geode'
    $originals = @()
    foreach ($file in @(Get-ChildItem -LiteralPath $mods -Filter '*.geode' -File)) {
        if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Geode packages cannot be links' }
        $oldArchive = $null; $oldReader = $null
        try {
            $oldArchive = [IO.Compression.ZipFile]::OpenRead($file.FullName)
            $oldManifest = $oldArchive.GetEntry('mod.json')
            if ($null -ne $oldManifest -and $oldManifest.Length -le 65536) {
                $oldReader = [IO.StreamReader]::new($oldManifest.Open())
                $oldData = $oldReader.ReadToEnd() | ConvertFrom-Json
                if ($oldData.id -eq 'autogdbot.realtime') { $originals += $file.FullName }
            }
        } catch {
            if ($file.FullName -eq $target) { throw 'Existing bot target cannot be read; refusing to overwrite it' }
        } finally {
            if ($oldReader) { $oldReader.Dispose() }
            if ($oldArchive) { $oldArchive.Dispose() }
        }
    }
    if ((Test-Path -LiteralPath $target) -and $originals -notcontains $target) { throw 'Bot target belongs to another package; refusing to overwrite' }
    $backup = Join-Path $AgentDirectory ('local-state\backups\' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($backup)
    $temporary = Join-Path $mods ('.autogdbot-' + [Guid]::NewGuid().ToString('N') + '.pending')
    $moved = @()
    try {
        [IO.File]::Copy($packageFile, $temporary, $false)
        foreach ($original in $originals) {
            if ([GDRemoteNative]::Stopped) { throw 'Stopped locally with F8' }
            $saved = Join-Path $backup ([IO.Path]::GetFileName($original))
            [IO.File]::Move($original, $saved)
            $moved += @{ original=$original; saved=$saved }
        }
        if ([GDRemoteNative]::Stopped -or $null -ne (Get-GameProcess)) { throw 'Installation interrupted; restoring previous package' }
        [IO.File]::Move($temporary, $target)
    } catch {
        foreach ($item in $moved) { if (-not (Test-Path -LiteralPath $item.original)) { [IO.File]::Move($item.saved, $item.original) } }
        throw
    } finally { if (Test-Path -LiteralPath $temporary) { [IO.File]::Delete($temporary) } }
    return @{ installed=$true; version=$manifest.version; sha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant(); backed_up=$originals.Count }
}
function Invoke-GameAction([string]$Operation, $ActionArgs) {
    if ($Operation -eq 'status') { return Get-Status }
    if ($Operation -eq 'safety') { return @{ stopped=[GDRemoteNative]::Stopped } }
    if ($Operation -eq 'stop') { [GDRemoteNative]::Stopped = $true; return @{ stopped=$true } }
    if ([GDRemoteNative]::Stopped) { throw 'Stopped locally with F8' }
    switch ($Operation) {
        'close_game' {
            $gameProcess = Get-GameProcess
            if ($null -eq $gameProcess) { return @{ closed=$true } }
            if (-not $gameProcess.CloseMainWindow()) { throw 'Game did not accept a normal close request' }
            if (-not $gameProcess.WaitForExit(10000)) { throw 'Game has not closed; no forced termination was used' }
            return @{ closed=$true }
        }
        'install_package' { return Install-BotPackage }
        'launch' {
            if ($null -eq (Get-GameProcess)) { [void](Start-Process -FilePath $GameExe -WorkingDirectory $GameDirectory -PassThru) }
            $deadline = [DateTime]::UtcNow.AddSeconds(20)
            do {
                if ([GDRemoteNative]::Stopped) { throw 'Stopped locally with F8' }
                $gameProcess = Get-GameProcess
                if ($null -ne $gameProcess -and $gameProcess.MainWindowHandle -ne [IntPtr]::Zero) { return Set-GameForeground }
                Start-Sleep -Milliseconds 200
            } while ([DateTime]::UtcNow -lt $deadline)
            throw 'Game launched but its window did not appear within 20 seconds'
        }
        'focus' { return Set-GameForeground }
        'capture' { return Get-GameImage }
        'click' {
            if ($null -eq $ActionArgs.x -or $null -eq $ActionArgs.y) { throw 'Click coordinates are missing' }
            [GDRemoteNative]::Click((Get-GameWindow), [double]$ActionArgs.x, [double]$ActionArgs.y)
            return @{ clicked=$true }
        }
        'key' {
            $keys = @{ SPACE=0x20; ENTER=0x0D; ESC=0x1B; R=0x52; TAB=0x09; LEFT=0x25; RIGHT=0x27; UP=0x26; DOWN=0x28 }
            if (-not $keys.ContainsKey([string]$ActionArgs.key)) { throw 'Key is not allowed' }
            $hold = 80
            if ($null -ne $ActionArgs.hold_ms) { $hold = [int]$ActionArgs.hold_ms }
            [GDRemoteNative]::Tap((Get-GameWindow), [uint16]$keys[[string]$ActionArgs.key], $hold)
            return @{ pressed=$ActionArgs.key; hold_ms=$hold }
        }
        'collect_logs' { return Get-BotLogs }
        default { throw 'Operation is not allowed' }
    }
}
try {
    while ($null -ne ($line = [Console]::ReadLine())) {
        $request = $null
        try {
            if ($line.Length -gt 8192) { throw 'Worker command is too large' }
            $request = $line | ConvertFrom-Json
            $result = Invoke-GameAction ([string]$request.op) $request.args
            $reply = @{ id=$request.id; ok=$true; data=$result }
        } catch {
            $requestId = $null
            if ($null -ne $request) { $requestId = $request.id }
            $message = $_.Exception.Message.Replace($GameDirectory, '<GAME>')
            $reply = @{ id=$requestId; ok=$false; error=$message }
        }
        [Console]::WriteLine(($reply | ConvertTo-Json -Compress -Depth 10))
        [Console]::Out.Flush()
    }
} finally { [GDRemoteNative]::Stopped = $true }
