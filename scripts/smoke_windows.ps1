# Installs the .msi without a user interface, starts the installed application and waits for its
# window, then stops it and uninstalls. Run on a Windows CI runner: it needs administrator rights.
param([Parameter(Mandatory = $true)][string]$Installer)

$ErrorActionPreference = 'Stop'
$title = 'Android Chrome Tabs Exporter'
$msi = (Resolve-Path $Installer).Path
$log = Join-Path $env:RUNNER_TEMP 'msi-install.log'

$install = Start-Process msiexec.exe -ArgumentList '/i', "`"$msi`"", '/qn', '/l*v', "`"$log`"" -Wait -PassThru
if ($install.ExitCode -ne 0) {
    Get-Content $log -Tail 50
    throw "cannot install `"$msi`": msiexec exited with $($install.ExitCode)"
}

$exe = Join-Path $env:ProgramFiles "$title\$title.exe"
if (-not (Test-Path $exe)) {
    throw "cannot find launcher `"$exe`": expected the default installation directory"
}

$application = Start-Process $exe -PassThru
try {
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        $application.Refresh()
        if ($application.HasExited) {
            throw "cannot start application `"exit $($application.ExitCode)`": the launcher stopped before its window opened"
        }
        if ($application.MainWindowTitle -eq $title) {
            break
        }
        Start-Sleep -Milliseconds 200
    }
    if ($application.MainWindowTitle -ne $title) {
        Get-Process | Where-Object { $_.MainWindowTitle } | Format-Table Id, ProcessName, MainWindowTitle -AutoSize | Out-String | Write-Output
        throw "cannot find window `"$title`": no window with that title within 60 seconds"
    }
} finally {
    if (-not $application.HasExited) {
        Stop-Process -Id $application.Id -Force
    }
}

$uninstall = Start-Process msiexec.exe -ArgumentList '/x', "`"$msi`"", '/qn' -Wait -PassThru
if ($uninstall.ExitCode -ne 0) {
    throw "cannot uninstall `"$msi`": msiexec exited with $($uninstall.ExitCode)"
}
Write-Output "PASS: installed application opened its window and uninstalled"
