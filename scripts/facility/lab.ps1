<#
.SYNOPSIS
    Opens a Wormhole Research Facility lab in its own window: a Paper server with the campus
    built and held for you to join, run by run-facility.js.

.DESCRIPTION
    Works from a fresh clone: it installs the facility's Node modules the first time, and
    run-facility.js builds the plugin with Maven (or takes -Plugin), fetches and checks Paper,
    and picks the JDK the server and every plugin jar need. Everything is found from this
    script's own folder.

    The world is kept between labs (run-facility --keep-world) unless -Fresh. A lab that is
    already listening on its port is left alone. Say "stop" in the lab's chat, or press Ctrl+C
    in its window, to end it.

.PARAMETER Version
    The Minecraft version (default 26.1.2).

.PARAMETER Port
    The server port (default 25590). Another port gets its own server folder, and Dynmap's web
    map is at 8123 + (port - 25590).

.PARAMETER Plugin
    A plugin jar to test (a branch's build) instead of building this checkout.

.PARAMETER Op
    Players to op (comma-separated), so you can build and configure.

.PARAMETER Fresh
    Start from a new world instead of the one kept from the last lab.

.PARAMETER With
    Companion plugins (comma-separated): viaversion, viabackwards, dynmap, worldedit, worldguard,
    luckperms, vault, or the sets via, regions, permissions. See companions.json.

.PARAMETER PluginCache
    A folder of companion jars to read first (<folder>\<version>\ then <folder>\any\).

.PARAMETER NoDashboard
    Do not start the Lab Dashboard (each lab's console and Dynmap, http://127.0.0.1:8200).

.EXAMPLE
    .\scripts\facility\lab.ps1
    .\scripts\facility\lab.ps1 -Version 1.21.11 -Port 25620 -With dynmap,regions -Op YourName
    .\scripts\facility\lab.ps1 -Plugin target\WormholeXTreme.jar -Fresh
#>
param(
    [string] $Version = '26.1.2',
    [int] $Port = 25590,
    [string] $Plugin = '',
    [string] $Op = '',
    [switch] $Fresh,
    [string] $With = '',
    [string] $PluginCache = '',
    [switch] $NoDashboard
)

$ErrorActionPreference = 'Stop'
$facility = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $facility '..\..')).Path

if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
{
    Write-Host "Something is already listening on port $Port (a lab already running?); leaving it alone."
    exit 0
}

# Paths as the new window's PowerShell reads them: single-quoted, any quote in them doubled,
# the typographic ones too (PowerShell reads U+2018 and U+2019 as single quotes as well).
function Quote([string] $s)
{
    foreach ($q in "'", [string][char]0x2018, [string][char]0x2019) { $s = $s.Replace($q, $q + $q) }
    "'" + $s + "'"
}

if (-not (Test-Path (Join-Path $facility 'node_modules')))
{
    Write-Host 'Installing the facility''s Node modules (once)...'
    & npm install --prefix $facility
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
}

$arguments = @($Version, '--port', $Port)
if (-not $Fresh) { $arguments += '--keep-world' }
if ($Plugin) { $arguments += @('--plugin', (Resolve-Path $Plugin).Path) }
if ($Op) { $arguments += @('--op', $Op) }
if ($With) { $arguments += @('--with', $With) }
if ($PluginCache) { $arguments += @('--plugin-cache', (Resolve-Path $PluginCache).Path) }

$title = "Wormhole lab $Version :$Port"
$node = "node $(Quote (Join-Path $facility 'run-facility.js')) $(($arguments | ForEach-Object { Quote "$_" }) -join ' ')"
$command = "`$host.UI.RawUI.WindowTitle = $(Quote $title); Set-Location $(Quote $repo); $node"
Start-Process powershell -ArgumentList '-NoExit', '-Command', $command

# The read-only Lab Dashboard, once for all labs.
if (-not $NoDashboard)
{
    if (-not (Get-NetTCPConnection -LocalPort 8200 -State Listen -ErrorAction SilentlyContinue))
    {
        Start-Process powershell -WindowStyle Minimized -ArgumentList '-NoExit', '-Command',
            "`$host.UI.RawUI.WindowTitle = 'Lab Dashboard :8200'; node $(Quote (Join-Path $facility 'dashboard.js'))"
    }
    Start-Process 'http://127.0.0.1:8200'
}
Write-Host "Opened $title in its own window. Join localhost:$Port with Minecraft $Version once it says ready."
