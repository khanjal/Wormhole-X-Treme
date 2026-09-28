<#
.SYNOPSIS
    Starts a local Paper server with this checkout's plugin and the player bot waiting for you to
    join and watch it, the Windows way to run scripts/player-boot.sh with OBSERVE=1.

.DESCRIPTION
    Stops what an earlier local run left behind, builds the plugin, downloads the Paper jar the
    first time, and starts the server in .local-server\run-<version>. Join localhost:25599 with a
    Minecraft client of that version. After the trips, type a trip's name, "all" or "done" in chat.

    Needs Git for Windows (for bash), Node 22 or newer, Maven, Python, and JDK 17 and 21
    (Eclipse Adoptium is looked for; -Jdk17 and -Jdk21 name others). Where scripts are not allowed
    to run: powershell -ExecutionPolicy Bypass -File .\scripts\watch-local.ps1

    Ctrl+C can leave the server running; the next run stops it first.

.EXAMPLE
    .\scripts\watch-local.ps1
    Every trip on Paper 1.21.11, waiting for you to join.

.EXAMPLE
    .\scripts\watch-local.ps1 -Version 1.20.4 -Trips gate,boat
    Only those trips, on 1.20.4.

.EXAMPLE
    .\scripts\watch-local.ps1 -Headless -NoBuild
    No watcher: runs the trips and stops, with the jar already built.
#>
param(
    [string]$Version = '1.21.11',
    [string[]]$Trips = @(),
    [switch]$Headless,
    [switch]$NoBuild,
    [int]$WaitSeconds = 1800,
    [string]$Jdk17 = '',
    [string]$Jdk21 = ''
)
$ErrorActionPreference = 'Stop'
# PowerShell 7.3+ would otherwise throw on bash's exit code and lose the trips' own result.
$PSNativeCommandUseErrorActionPreference = $false

$repo = Split-Path -Parent $PSScriptRoot
$work = Join-Path $repo '.local-server'
$run = Join-Path $work "run-$Version"
New-Item -ItemType Directory -Force $work | Out-Null

function Find-Jdk([string]$major, [string]$given) {
    if ($given) { return $given }
    $found = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-$major.*" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $found) { throw "No JDK $major found under C:\Program Files\Eclipse Adoptium; pass -Jdk$major <folder>." }
    return $found.FullName
}

# Git Bash, not the WSL bash in System32.
function Find-Bash {
    foreach ($candidate in @('C:\Program Files\Git\bin\bash.exe', 'C:\Program Files (x86)\Git\bin\bash.exe')) {
        if (Test-Path $candidate) { return $candidate }
    }
    throw 'Git Bash not found; install Git for Windows.'
}

# C:\Users\me -> /c/Users/me, for paths handed to bash.
function ConvertTo-BashPath([string]$path) {
    if ($path -match '^([A-Za-z]):(.*)$') { return '/' + $matches[1].ToLower() + ($matches[2] -replace '\\', '/') }
    return $path -replace '\\', '/'
}

$bash = Find-Bash
$jdk17 = Find-Jdk '17' $Jdk17
$jdk21 = Find-Jdk '21' $Jdk21

# A server, bot or script left from an earlier run of this checkout holds the port, and a leftover
# boot-test.sh writes "stop" into the next run's console. Only this checkout's: its folder, between
# separators, is in every such command line, as a Windows or a Git Bash path. Braced, because a "?"
# after a variable name is read as part of it; escaped, for a folder name with [ or ] in it.
$ours = '*[\/]' + [WildcardPattern]::Escape((Split-Path -Leaf $repo)) + '[\/]'
function Get-Leftover {
    Get-CimInstance Win32_Process | Where-Object {
        ($_.Name -eq 'java.exe' -and $_.CommandLine -like "${ours}.local-server*") -or
        ($_.Name -eq 'node.exe' -and $_.CommandLine -like "${ours}scripts[\/]player-test[\/]journeys.js*") -or
        ($_.Name -eq 'bash.exe' -and $_.CommandLine -like "${ours}.local-server*" -and
            ($_.CommandLine -like '*boot-test.sh*' -or $_.CommandLine -like '*player-boot.sh*')) -or
        # The tail feeding a server's console names no folder, and its parent has exited, so any
        # checkout's is taken; a stray one only follows a deleted file, and holds nothing.
        ($_.Name -eq 'tail.exe' -and $_.CommandLine -like '*-f commands.txt')
    }
}

# Whether something is listening on the port, where Get-NetTCPConnection is missing too.
function Test-PortInUse([int]$port) {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        return [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    }
    return [bool](netstat -ano | Select-String ":$port\s.*LISTENING")
}

Write-Host 'Stopping anything left from an earlier local run...'
Get-Leftover | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -Confirm:$false -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2
# One that has already exited is fine; one still running, say started from an elevated window, is not.
$survivors = @(Get-Leftover | Where-Object { $_.Name -ne 'tail.exe' })
if ($survivors.Count -gt 0) {
    throw ('Could not stop an earlier run: ' + (($survivors | ForEach-Object { "$($_.Name) $($_.ProcessId)" }) -join ', ') + '. Stop it and try again.')
}
if (Test-PortInUse 25599) {
    throw 'Port 25599 is in use by another server, perhaps another checkout''s; stop it and try again.'
}

# Mvn and bash write warnings to stderr, which Windows PowerShell 5.1 turns into errors when its own
# output is redirected; failures from here on are told by exit codes.
$ErrorActionPreference = 'Continue'

$jar = Join-Path $work 'wx.jar'
if ($NoBuild -and -not (Test-Path $jar)) { Write-Host 'No build to reuse yet, so building once.' }
if (-not $NoBuild -or -not (Test-Path $jar)) {
    Write-Host 'Building the plugin...'
    Push-Location $repo
    try {
        $env:JAVA_HOME = $jdk17
        & mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'The build failed.' }
    } finally { Pop-Location }
    Copy-Item (Join-Path $repo 'target\WormholeXTreme.jar') $jar -Force -ErrorAction Stop
}

$paper = Join-Path $work "paper-$Version.jar"
if (-not (Test-Path $paper)) {
    Write-Host "Downloading Paper $Version..."
    Push-Location $repo
    try {
        & $bash scripts/fetch-server.sh paper $Version (ConvertTo-BashPath $paper)
        if ($LASTEXITCODE -ne 0) { throw "Could not download Paper $Version." }
    } finally { Pop-Location }
}

# A fresh world each time: a world that already has the gates makes every trip's setup fail.
if (Test-Path $run) { Remove-Item -Recurse -Force $run -ErrorAction Stop }

$env:BOOT_DIR = ConvertTo-BashPath $run
$env:JAVA = ConvertTo-BashPath (Join-Path $jdk21 'bin\java.exe')
$env:OBSERVE_WAIT = "$WaitSeconds"
$env:OBSERVE = if ($Headless) { '' } else { '1' }
$env:TRIPS = ($Trips -join ',')
if (-not $Headless) {
    Write-Host ''
    Write-Host "Join localhost:25599 with Minecraft $Version once the bot says it is waiting." -ForegroundColor Green
    Write-Host 'After each trip answer y or n in chat; at the end type a trip name, all, or done.' -ForegroundColor Green
    Write-Host ''
}
Push-Location $repo
try {
    & $bash scripts/player-boot.sh (ConvertTo-BashPath $paper) (ConvertTo-BashPath $jar) $Version
    $status = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in 'BOOT_DIR', 'JAVA', 'OBSERVE_WAIT', 'OBSERVE', 'TRIPS') { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
}
Write-Host "Server folder and console.log: $run"
exit $status
