#!/usr/bin/env node
'use strict'
// Starts the lab: a Paper server with this checkout's plugin, a fresh world with the lab built on
// it, and the bot waiting at the panels. The same on Windows, macOS and Linux:
//
//   node scripts/run-lab.js                    Minecraft 26.1.2
//   node scripts/run-lab.js 1.21.11            that version
//   node scripts/run-lab.js --no-build         the jar already in target/
//
// Join localhost:25599 with that version of Minecraft once it says the lab is built. Say "stop" in
// chat, or press Ctrl+C here, to shut it down. It builds the plugin with Maven, downloads Paper the
// first time into .local-server/, and runs the server in .local-server/lab-<version>, wiped each
// time since the lab is built on a fresh world. See "The lab" in docs/DEVELOPMENT.md.
//
// Needs Node 22 or newer, Maven, and a Java new enough for that version: 21 for 1.20.5 to 1.21.x,
// 25 for 26.x. --java <path> names one, as does JAVA_HOME; otherwise java on PATH. Other options:
// --server <jar> and --plugin <jar> skip the download and the build; --port <n> (25599);
// --selftest[=gate,ring] has the bot work every panel itself and exit, failing if a run did.

const { spawn, spawnSync } = require('child_process')
const crypto = require('crypto')
const fs = require('fs')
const net = require('net')
const path = require('path')

const repo = path.resolve(__dirname, '..')
const client = path.join(__dirname, 'player-test')
const work = path.join(repo, '.local-server')
const windows = process.platform === 'win32'

// The newest Mineflayer 4.39 speaks; move it up when Mineflayer does.
const DEFAULT_VERSION = '26.1.2'
// Warnings the plugin logs on a plain server that are not faults: no permissions plugin, and the
// shipped shapes framed in obsidian with differing settings. The same list as player-boot.sh.
const ALLOW = /No Vault\/LuckPerms provider detected|Shapes framed in OBSIDIAN disagree/
const FLAGGED = /(WARN|ERROR|SEVERE)\]:? .*(WormholeXTreme|wormhole_xtreme)|^\s+at com\.wormhole_xtreme|Could not load .plugins\/|Error occurred while (enabling|disabling)/

function usage (problem) {
  console.error(`${problem}\nusage: node scripts/run-lab.js [version] [--no-build] [--server <jar>] [--plugin <jar>] [--java <path>] [--port <n>] [--selftest[=bays]]`)
  process.exit(2)
}

function parse (argv) {
  const o = { version: DEFAULT_VERSION, build: true, port: 25599, selftest: null }
  for (let i = 0; i < argv.length; i++) {
    const [flag, inline] = argv[i].split(/=(.*)/s)
    const value = () => {
      if (inline !== undefined) return inline
      if (i + 1 >= argv.length) usage(`${flag} needs a value`)
      return argv[++i]
    }
    if (flag === '--no-build') o.build = false
    else if (flag === '--server') o.server = path.resolve(value())
    else if (flag === '--plugin') o.plugin = path.resolve(value())
    else if (flag === '--java') o.java = value()
    else if (flag === '--port') o.port = Number(value())
    else if (flag === '--selftest') o.selftest = inline || '1'
    else if (flag === '--help' || flag === '-h') usage('Starts the lab.')
    else if (!flag.startsWith('-')) o.version = flag
    else usage(`unknown option ${flag}`)
  }
  return o
}

/** Runs a command to the end, its output shown; npm and mvn are .cmd files on Windows. */
function run (command, args, cwd) {
  const done = spawnSync(command, args, { cwd, stdio: 'inherit', shell: windows })
  if (done.status !== 0) throw new Error(`${command} ${args.join(' ')} failed`)
}

function javaCommand (given) {
  if (given) return given
  if (process.env.JAVA_HOME) return path.join(process.env.JAVA_HOME, 'bin', windows ? 'java.exe' : 'java')
  return 'java'
}

function portInUse (port) {
  return new Promise((resolve) => {
    const socket = net.connect(port, '127.0.0.1')
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('error', () => resolve(false))
  })
}

async function fetchPaper (version, out) {
  const ua = { 'User-Agent': 'WormholeXTreme-lab (https://github.com/khanjal/Wormhole-X-Treme)' }
  const response = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${version}/builds/latest`, { headers: ua })
  if (!response.ok) throw new Error(`Paper has no build for ${version} (HTTP ${response.status})`)
  const download = (await response.json()).downloads['server:default']
  const jar = await fetch(download.url, { headers: ua })
  if (!jar.ok) throw new Error(`could not download Paper ${version} (HTTP ${jar.status})`)
  const bytes = Buffer.from(await jar.arrayBuffer())
  const sum = crypto.createHash('sha256').update(bytes).digest('hex')
  if (sum !== download.checksums.sha256) throw new Error(`Paper ${version}'s checksum does not match`)
  fs.writeFileSync(out, bytes)
}

/** A fresh server folder: the plugin, the flat grass world, and the settings the lab needs. */
function prepare (dir, plugin, port) {
  fs.rmSync(dir, { recursive: true, force: true })
  fs.mkdirSync(path.join(dir, 'plugins', 'bStats'), { recursive: true })
  fs.copyFileSync(plugin, path.join(dir, 'plugins', path.basename(plugin)))
  // Every boot has a fresh bStats id, so each one would count as another server on the public page.
  fs.writeFileSync(path.join(dir, 'plugins', 'bStats', 'config.yml'), 'enabled: false\n')
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n')
  // Command blocks carry the panels' buttons, and the lab is wide, so the view reaches across a bay.
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false',
    `server-port=${port}`,
    'level-type=minecraft\\:flat',
    'generator-settings={"layers"\\:[{"block"\\:"minecraft\\:grass_block","height"\\:1}],"biome"\\:"minecraft\\:plains"}',
    'generate-structures=false',
    'spawn-protection=0',
    'enable-command-block=true',
    'view-distance=8',
    'simulation-distance=6',
    'motd=Wormhole X-Treme lab',
    ''
  ].join('\n'))
  fs.writeFileSync(path.join(dir, 'commands.txt'), '')
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function main () {
  const o = parse(process.argv.slice(2))
  if (Number(process.versions.node.split('.')[0]) < 22) usage(`Node 22 or newer runs the bot; this is ${process.version}.`)
  if (await portInUse(o.port)) usage(`Something is already on port ${o.port}: an earlier lab or another server. Stop it, or pass --port.`)
  fs.mkdirSync(work, { recursive: true })

  let plugin = o.plugin
  if (!plugin) {
    plugin = path.join(repo, 'target', 'WormholeXTreme.jar')
    if (o.build || !fs.existsSync(plugin)) {
      console.log('Building the plugin...')
      run('mvn', ['-q', '-DskipTests', 'package'], repo)
    }
  }
  if (!fs.existsSync(plugin)) usage(`No plugin jar at ${plugin}.`)

  let server = o.server
  if (!server) {
    server = path.join(work, `paper-${o.version}.jar`)
    if (!fs.existsSync(server)) {
      console.log(`Downloading Paper ${o.version}...`)
      await fetchPaper(o.version, server)
    }
  }
  if (!fs.existsSync(server)) usage(`No server jar at ${server}.`)

  // Checks for the bot's own dependency, not the folder, so an install cut short is done again.
  if (!fs.existsSync(path.join(client, 'node_modules', 'mineflayer'))) {
    console.log('Installing the bot...')
    run('npm', ['ci', '--no-audit', '--no-fund'], client)
  }

  const dir = path.join(work, `lab-${o.version}`)
  prepare(dir, plugin, o.port)
  const consoleFile = path.join(dir, 'commands.txt')
  const logFile = path.join(dir, 'console.log')
  const log = fs.openSync(logFile, 'w')

  console.log(`Starting Paper ${o.version} in ${dir}...`)
  const java = spawn(javaCommand(o.java), ['-Xmx2G', '-DIReallyKnowWhatIAmDoingISwear=true', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', server, 'nogui'],
    { cwd: dir, stdio: ['pipe', log, log] })
  let javaExited = false
  const javaDone = new Promise((resolve) => java.once('exit', (code) => { javaExited = true; resolve(code) }))
  java.once('error', (e) => usage(`Could not start Java (${e.message}). Pass --java <path to java>.`))

  // The bot sends a console command by appending a line to commands.txt; this carries new lines on.
  let sent = 0
  const feed = setInterval(() => {
    const size = fs.statSync(consoleFile).size
    if (size <= sent || javaExited) return
    const fd = fs.openSync(consoleFile, 'r')
    const chunk = Buffer.alloc(size - sent)
    fs.readSync(fd, chunk, 0, chunk.length, sent)
    fs.closeSync(fd)
    sent = size
    java.stdin.write(chunk)
  }, 100)
  const send = (line) => { if (!javaExited) java.stdin.write(line + '\n') }

  let stopping = false
  const stop = async () => {
    if (stopping) return javaDone
    stopping = true
    clearInterval(feed)
    send('stop')
    const timer = setTimeout(() => java.kill(), 120000)
    const code = await javaDone
    clearTimeout(timer)
    return code
  }
  // Ctrl+C reaches the bot too; the server is stopped cleanly rather than left running.
  process.on('SIGINT', () => { console.log('\nStopping the server...'); stop().then(() => process.exit(130)) })

  const deadline = Date.now() + 300000
  while (!fs.readFileSync(logFile, 'utf8').includes('Done (')) {
    if (javaExited || Date.now() > deadline) {
      console.error(`The server did not start. Its last lines (${logFile}):`)
      console.error(fs.readFileSync(logFile, 'utf8').split('\n').slice(-40).join('\n'))
      if (!javaExited) java.kill()
      process.exit(1)
    }
    await sleep(1000)
  }

  if (!o.selftest) {
    console.log(`\nThe server is up. The bot is building the lab; join localhost:${o.port} with Minecraft ${o.version} once it says the lab is built.`)
    console.log('Say "stop" in chat, or press Ctrl+C here, to shut it down.\n')
  }
  const bot = spawn(process.execPath, [path.join(client, 'lab.js'), o.version], {
    stdio: 'inherit',
    env: { ...process.env, BOOT_CONSOLE: consoleFile, BOOT_LOG: logFile, LAB_SELFTEST: o.selftest || '' }
  })
  const botCode = await new Promise((resolve) => bot.once('exit', (code) => resolve(code === null ? 1 : code)))
  await sleep(5000)
  await stop()
  console.log(`Server folder and console.log: ${dir}`)

  if (!o.selftest) process.exit(botCode)
  // A self-test also fails on the plugin not enabling, or on a warning it logged.
  const text = fs.readFileSync(logFile, 'utf8')
  const failures = []
  if (botCode !== 0) failures.push(`the bot exited with status ${botCode}`)
  if (!text.includes('Enable Completed')) failures.push('the plugin never logged Enable Completed')
  const flagged = text.split('\n').filter((line) => FLAGGED.test(line) && !ALLOW.test(line))
  if (flagged.length) failures.push(`warnings or stack frames from the plugin:\n${flagged.join('\n')}`)
  if (failures.length) {
    console.error(`lab self-test FAILED\n- ${failures.join('\n- ')}`)
    process.exit(1)
  }
  console.log('lab self-test passed')
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
