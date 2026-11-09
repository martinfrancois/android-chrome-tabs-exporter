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

# Look the window up by process name: in CI the started launcher process never reported it, while
# a process with the launcher's name owned it.
$application = Start-Process $exe -PassThru
try {
    $window = $null
    $deadline = (Get-Date).AddSeconds(60)
    while (-not $window -and (Get-Date) -lt $deadline) {
        $application.Refresh()
        if ($application.HasExited) {
            throw "cannot start application `"exit $($application.ExitCode)`": the launcher stopped before its window opened"
        }
        $window = Get-Process -Name $title -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq $title }
        Start-Sleep -Milliseconds 200
    }
    if (-not $window) {
        Get-Process | Where-Object { $_.MainWindowTitle } | Format-Table Id, ProcessName, MainWindowTitle -AutoSize | Out-String | Write-Output
        Write-Output "Started launcher process: $($application.Id)"
        throw "cannot find window `"$title`": no window with that title within 60 seconds"
    }
} finally {
    Get-Process -Name $title -ErrorAction SilentlyContinue | Stop-Process -Force
}

$uninstall = Start-Process msiexec.exe -ArgumentList '/x', "`"$msi`"", '/qn' -Wait -PassThru
if ($uninstall.ExitCode -ne 0) {
    throw "cannot uninstall `"$msi`": msiexec exited with $($uninstall.ExitCode)"
}
Write-Output "PASS: installed application opened its window and uninstalled"
