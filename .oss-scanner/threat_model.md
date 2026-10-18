# Threat model

## What this project does and where untrusted input enters
Wormhole X-Treme is a Bukkit/Spigot/Paper plugin for Minecraft servers (1.20 to 26.x, one jar).
It adds stargates, transport rings, beaming and quantum mirrors. It runs inside the server's
JVM with the server's file permissions, so a crash or hang in the plugin is a crash or hang of
the whole server.

Untrusted input comes from **players**, who range from anyone allowed to join to a griefer:
- Commands (`/dial`, `/wormhole ...`, `/wx ...`) and their arguments, including tab-completion
  requests.
- Text on signs (a dial sign's gate name) and the names players give gates, rings, beam
  places and mirrors.
- Clicks, block placement and breaking, redstone, items and entities moving into a gate.

Trusted input, edited by the server admin: `config.yml`, the shape files under `shapes/`, and
the plugin's own data files under `WormholeXTremeDB/` (gates, rings, beams, mirrors). A
malformed file should be rejected with a message, not crash the plugin, but a file an admin
wrote on purpose to attack their own server is out of scope.

## Components that matter most / least
Most: command parsing and permission checks (`command/`), dial and travel logic, gate and
ring ownership and permission checks, anything that reads or writes a file whose name or
content a player influences, and anything that runs on every tick or every move event.

Least: the cosmetic drawing code (`model/window/`, `model/mirror/`, `model/beam/`
animations). It matters only when it can be made to lag or crash a server.

Out of scope: Bukkit, Paper, Vault, WorldGuard, and the other plugin APIs we call. Code
under `src/test/` and `scripts/`.

## How to exercise it
`mvn test` runs the unit tests, which mock the server API (Mockito, MockBukkit). There is no
way to run a real server in the offline image, so reason from the code: look for a player
action that reaches a bad path, rather than a network attack.

The only outbound requests are the optional update check (`plugin/UpdateCheck.java`), which
calls two fixed HTTPS URLs (the Modrinth and GitHub release APIs). It has no inbound listener and opens no port.

## How you rate severity
- A player getting travel, building or removal they have no permission for: high.
- A player reading or writing files outside the plugin's data folder (path traversal through
  a gate, ring or place name): high; critical if it can overwrite server files.
- A player crashing or hanging the server (unbounded loop, memory growth, exception that
  escapes an event handler and breaks later events): medium to high.
- A player getting free travel past a toll: medium.
- Wrong or ugly cosmetics, a wrong message, a failure only an admin can trigger: low.

## Anything to leave alone
- Use of deprecated Bukkit API: deliberate, so one jar runs on every supported version.
- Reflection and broad `catch` blocks around version-specific APIs: deliberate, for the same
  reason.
- Bundled `.shape` files are examples, not input.
