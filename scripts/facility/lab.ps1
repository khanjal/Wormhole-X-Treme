<#
.SYNOPSIS
    Opens a Wormhole Research Facility lab in its own window: a Paper server with the campus
    built and held for you to join, run by run-facility.js.

.DESCRIPTION
    Works from a fresh clone: it installs the facility's Node modules the first time (and again
    after an install that never finished, or a new package-lock.json), and
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

.PARAMETER Design
    Design mode (design/facility/BRIEF.md): Minecraft 1.21.11 with WorldEdit, its own world kept
    between sessions, ops in creative, no tests. Say check, export or stop in chat. -Version and
    -Fresh do not apply.

.PARAMETER Export
    With -Design and the lab stopped: write the export zip to .local-server\exports\ from this
    window (it starts the design world, exports and stops).

.PARAMETER Check
    With -Design and the lab stopped: the keep-clear check, from this window. Not with -Export.

.PARAMETER Full
    With -Export: the zip holds your three worlds as well as the schematics (a much bigger file).

.PARAMETER Open
    With -Design: let other machines join, not only this one. The server does not check names
    (online-mode is off), so anyone who can reach the port can join as anyone, an op included.

.PARAMETER Watch
    Watch the self-test from inside the lab instead of holding it: a fresh world, and the
    self-test waits for you to join, then runs with you as a spectator it moves to each chamber
    and tells each cell and its result (run-facility --selftest --watch). The cells take no notice
    of you. Not with -Design.

.PARAMETER As
    With -Watch: the name you will join as; the self-test waits for that name (default: whoever
    joins first).

.PARAMETER Quick
    With -Watch: the short matrix, about 15 minutes, instead of the whole one (about an hour).

.PARAMETER Cells
    With -Watch: only the matrix cells whose names match (a|b, ^start, end$), e.g. '^g1' or 'b1 '.

.PARAMETER NoDashboard
    Do not start the Lab Dashboard (each lab's console and Dynmap, http://127.0.0.1:8200). lab.sh
    does not start it; run node scripts/facility/dashboard.js there.

.EXAMPLE
    .\scripts\facility\lab.ps1
    .\scripts\facility\lab.ps1 -Version 1.21.11 -Port 25620 -With dynmap,regions -Op YourName
    .\scripts\facility\lab.ps1 -Plugin target\WormholeXTreme.jar -Fresh
    .\scripts\facility\lab.ps1 -Design -Op YourName -Plugin WormholeXTreme.jar
    .\scripts\facility\lab.ps1 -Design -Export
    .\scripts\facility\lab.ps1 -Version 1.21.11 -Watch -Quick
    .\scripts\facility\lab.ps1 -Watch -As YourName -Cells '^r1'
#>
param(
    [string] $Version = '26.1.2',
    [int] $Port = 25590,
    [string] $Plugin = '',
    # Arrays: PowerShell reads -With dynmap,via as two values, which a [string] joins with a space.
    [string[]] $Op = @(),
    [switch] $Fresh,
    [string[]] $With = @(),
    [string] $PluginCache = '',
    [switch] $Design,
    [switch] $Export,
    [switch] $Check,
    [switch] $Full,
    [switch] $Open,
    [switch] $Watch,
    [string] $As = '',
    [switch] $Quick,
    [string] $Cells = '',
    [switch] $NoDashboard
)

$ErrorActionPreference = 'Stop'
$facility = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $facility '..\..')).Path

# Paths as the new window's PowerShell reads them: single-quoted, any quote in them doubled,
# the typographic ones too (PowerShell reads U+2018 and U+2019 as single quotes as well).
function Quote([string] $s)
{
    foreach ($q in "'", [string][char]0x2018, [string][char]0x2019) { $s = $s.Replace($q, $q + $q) }
    "'" + $s + "'"
}

if (($Export -or $Check -or $Open) -and -not $Design) { throw '-Export, -Check and -Open go with -Design' }
if ($Export -and $Check) { throw '-Export or -Check, one at a time' }
if ($Full -and -not $Export) { throw '-Full goes with -Export' }
if ($Design -and $Fresh) { throw 'Design mode keeps your world: to start over, delete .local-server\design-1.21.11 yourself' }
if ($Design -and $PSBoundParameters.ContainsKey('Version') -and $Version -ne '1.21.11') { throw 'Design mode runs Minecraft 1.21.11 only' }
if ($Design) { $Version = '1.21.11' }
if (($As -or $Quick -or $Cells) -and -not $Watch) { throw '-As, -Quick and -Cells go with -Watch' }
if ($Watch -and $Design) { throw 'Design mode runs no tests: -Watch or -Design' }

# The Lab Dashboard, once for all labs; started even when the lab is already running,
# in case the dashboard was closed. The browser opens only when it starts.
if (-not $NoDashboard -and -not $Export -and -not $Check)
{
    if (Get-NetTCPConnection -LocalPort 8200 -State Listen -ErrorAction SilentlyContinue)
    {
        Write-Host 'Lab Dashboard: http://127.0.0.1:8200'
    }
    else
    {
        Start-Process powershell -WindowStyle Minimized -ArgumentList '-NoExit', '-Command',
            "`$host.UI.RawUI.WindowTitle = 'Lab Dashboard :8200'; node $(Quote (Join-Path $facility 'dashboard.js'))"
        # Node takes a moment to bind; a browser opened first shows a refused connection.
        for ($i = 0; $i -lt 20 -and -not (Get-NetTCPConnection -LocalPort 8200 -State Listen -ErrorAction SilentlyContinue); $i++)
        {
            Start-Sleep -Milliseconds 250
        }
        Start-Process 'http://127.0.0.1:8200'
    }
}

if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
{
    if ($Export -or $Check) { throw "Something is listening on port ${Port}: say export or check in the lab's chat instead, or stop it first." }
    if ($Watch) { throw "Something is listening on port ${Port}: stop that lab first, or give -Watch another -Port." }
    Write-Host "Something is already listening on port $Port (a lab already running?); leaving it alone."
    exit 0
}

# The lock file's copy goes in only after npm succeeds, so a missing or different one means an
# install that never finished, or a newer lock: npm ci starts node_modules over.
$lock = Join-Path $facility 'package-lock.json'
$stamp = Join-Path $facility 'node_modules\.facility-lock.json'
if (-not (Test-Path $stamp) -or (Get-FileHash $stamp).Hash -ne (Get-FileHash $lock).Hash)
{
    # npm ci deletes node_modules first, from under a lab on another port that is running from it.
    # Any slashes and case (git-bash passes C:/...), and a relative path counts too: it may be this one.
    $script = (Join-Path $facility 'run-facility.js').ToLowerInvariant()
    $running = Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object {
        $c = "$($_.CommandLine)".Replace('/', '\').ToLowerInvariant()
        $c.Contains($script) -or $c -match '(^|[\s"''])(\.\\)?scripts\\facility\\run-facility\.js'
    }
    if ($running) { throw "The facility's Node modules need reinstalling, but a lab may be running from them (node process $(@($running)[0].ProcessId)): stop it first." }
    # An application (npm.cmd, or Volta's npm.exe), never npm.ps1: under 'Stop', Windows PowerShell
    # ends npm.ps1 at its first warning on stderr, mid-install. And 'Continue' for the same reason.
    $npm = Get-Command npm -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $npm) { throw 'npm is not on the PATH' }
    Write-Host 'Installing the facility''s Node modules...'
    $ErrorActionPreference = 'Continue'
    # Not 0 beforehand: if npm cannot run, 'Continue' goes on with an earlier command's code.
    $global:LASTEXITCODE = 1
    & $npm.Source ci --prefix $facility
    $installed = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if (-not $installed) { throw 'npm ci failed' }
    Copy-Item $lock $stamp
}

$arguments = @($Version, '--port', $Port)
if ($Design)
{
    # Design mode keeps its own world; --keep-world is not for it.
    $arguments = @('--design', '--port', $Port)
    if ($Export) { $arguments[0] = '--design-export' }
    if ($Check) { $arguments[0] = '--design-check' }
    if ($Full) { $arguments += '--full' }
    if ($Open) { $arguments += '--design-open' }
}
elseif ($Watch)
{
    # A self-test starts from a fresh world: its first checks are that every cell is as built.
    $arguments += @('--selftest', '--watch')
    if ($As) { $arguments += $As }
    if ($Quick) { $arguments += '--quick' }
    if ($Cells) { $arguments += @('--cells', $Cells) }
}
elseif (-not $Fresh) { $arguments += '--keep-world' }
if ($Plugin) { $arguments += @('--plugin', (Resolve-Path $Plugin).Path) }
$Op = @($Op | Where-Object { $_ })
$With = @($With | Where-Object { $_ })
if ($Op) { $arguments += @('--op', ($Op -join ',')) }
if ($With) { $arguments += @('--with', ($With -join ',')) }
if ($PluginCache) { $arguments += @('--plugin-cache', (Resolve-Path $PluginCache).Path) }

if ($Export -or $Check)
{
    # A job that ends, run here so its result stays in this window. Node's warnings on stderr are
    # not errors: under 'Stop', Windows PowerShell would end the job at the first one.
    Set-Location $repo
    $ErrorActionPreference = 'Continue'
    & node (Join-Path $facility 'run-facility.js') @arguments
    exit $LASTEXITCODE
}

$title = if ($Design) { "Wormhole design lab :$Port" } elseif ($Watch) { "Wormhole self-test $Version :$Port (watched)" } else { "Wormhole lab $Version :$Port" }
$node = "node $(Quote (Join-Path $facility 'run-facility.js')) $(($arguments | ForEach-Object { Quote "$_" }) -join ' ')"
$command = "`$host.UI.RawUI.WindowTitle = $(Quote $title); Set-Location $(Quote $repo); $node"
Start-Process powershell -ArgumentList '-NoExit', '-Command', $command

if ($Watch) { Write-Host "Opened $title in its own window. Join localhost:$Port with Minecraft $Version$(if ($As) { " as $As" }) once it says so; the self-test waits for you." }
else { Write-Host "Opened $title in its own window. Join localhost:$Port with Minecraft $Version once it says ready." }
