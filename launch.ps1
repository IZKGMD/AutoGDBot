# PowerShell 5.1 and later. The bundled OpenJDK launcher cannot bootstrap from
# paths outside the Windows system code page. Stage only its runtime when needed.
$ErrorActionPreference = 'Stop'
$gameArgs = @($args)
$stage = $null
$exitCode = 1
try {
    $runtime = Join-Path $PSScriptRoot 'runtime'
    $java = Join-Path $runtime 'bin/java.exe'
    $classpath = Join-Path $PSScriptRoot 'lib/*'
    $nativeTemp = [IO.Path]::GetTempPath()
    if (($runtime + $nativeTemp) -match '[^\x00-\x7F]') {
        $base = [Environment]::GetFolderPath('CommonApplicationData')
        if ($base -match '[^\x00-\x7F]') { throw 'Move the game to a folder with Latin letters: Windows Java cannot start from this path.' }
        $stage = Join-Path $base ('CTRL-LIFE-runtime-' + [guid]::NewGuid().ToString('N'))
        $directory = New-Item -ItemType Directory -Path $stage
        # The runtime must be writable only by the current user before copying DLLs.
        $acl = Get-Acl $directory.FullName
        $acl.SetAccessRuleProtection($true, $false)
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $directory.FullName -AclObject $acl
        Copy-Item -Path (Join-Path $runtime '*') -Destination $stage -Recurse -Force
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'lib') -Destination $stage -Recurse -Force
        $classpath = Join-Path $stage 'lib/*'
        $nativeTemp = Join-Path $stage 'tmp'
        New-Item -ItemType Directory -Path $nativeTemp | Out-Null
        $java = Join-Path $stage 'bin/java.exe'
    }
    & $java '-Dfile.encoding=UTF-8' ('-Djava.io.tmpdir=' + $nativeTemp) '-cp' $classpath 'life.ctrl.desktop.DesktopLauncher' @gameArgs
    $exitCode = $LASTEXITCODE
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
} finally {
    if ($stage -and (Test-Path -LiteralPath $stage)) {
        Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
exit $exitCode
