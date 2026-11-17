# Wormhole X-Treme — Copilot Instructions

## Project Overview
Wormhole X-Treme is a Bukkit/Spigot/Paper plugin that implements inter-dimensional "stargate" portals in Minecraft. Players (and vehicles — boats, minecarts) walk or ride through portal frames and are teleported to linked destination gates.

One jar supports **Minecraft 1.20 through 26.3**. It compiles against 1.20.4, an old API that stops us calling anything newer by accident (the one API carrying both `EntityDismountEvent` packages; CI builds 1.20 and 1.20.1 with `-Plegacy-api`), and CI builds it again against every version in the matrix in `.github/workflows/ci.yml`, which is the authority on what is supported.

## Build System
- **Java 17**, Maven. `maven-shade-plugin` bundles one third-party library, bStats, relocated into the plugin's own package. SnakeYAML is `provided` by the server, and there is no database driver, since storage is plain YAML files
- Build command: `mvn -o -q package -DskipTests` → produces `target/WormholeXTreme-<version>.jar`, versioned from the pom
- Test command: `mvn -o test` — JUnit 5 + Mockito, no live server needed
- Deploy by copying the JAR to `plugins/` on the server

## Vehicle teleport: why it looks the way it does

This is shipped and working, not open work, but the shape of the code is surprising enough to
be worth stating so it is not "simplified" back into the bug it solves.

- Paper 1.20+ requires the client to send `ServerboundAcceptTeleportationPacket` before it processes any subsequent packets (including `ClientboundSetPassengersPacket`).
- If `player.teleport()` is called and then `addPassenger()` fires too soon, the mount packet is silently dropped by the client even though the server reports success.

So in `GateWalkThrough` (player-enters-gate path, called from `WormholeXTremePlayerListener`) the **vehicle** is teleported and
the player is not: the vehicle move ejects the player server-side, event cancellation keeps the
player at the gate, and `addPassenger` two ticks later lands because the client never received a
player teleport to acknowledge. If `addPassenger` fails, the fallback teleports the player and
retries. `WormholeXTremeVehicleListener` runs the same reattach loop with a 5-tick initial delay
and a post-success re-teleport for client sync.

For what is actually open, read `CHANGELOG.md` and the repository's issues rather than this file.

---

## Code Style & Conventions

### Formatting
- **Allman-style braces**: opening brace on its own line for all blocks (class, method, if, for, try, etc.).
- **4-space indentation** (no tabs).
- `final` on every local variable and parameter that is not reassigned.
- One blank line between logical sections inside a method; two blank lines between methods.
- Import types; do not write `org.bukkit.World` or `java.util.UUID` inline. A package-qualified name in code is only for a clash between two types with the same simple name; `Map.Entry` and `ConfigManager.ConfigKeys` are fine.

### Java Idioms Used In This Codebase
- **Lambdas and method references for callbacks, scheduled tasks included.** No scheduler callback
  in `src/main` is an anonymous class. Most are lambdas or method references; a task with real
  state of its own is a named `Runnable` class (`StargateUpdateRunnable`, `BeamAnimation.Sequence`,
  `PassengerReattach.Reattacher`). Lambdas are also used for the
  tab-completion functions in `SubCommands`, the `computeIfAbsent` suppliers and the accessor
  pairs behind `MaterialCommand.Kind`. Sonar (S1604) flags a new anonymous class where a lambda
  would do. Do not ask for a lambda to be turned into an anonymous class, and do not flag a new
  lambda in review.
  ```java
  // CORRECT, for a scheduled task
  scheduler.scheduleSyncDelayedTask(plugin, () -> step(), ticks);
  ```
- **State a lambda has to change** (a retry count, or the task itself so it can schedule its own
  next run) goes in a field, or in a one-element array the lambda captures, as the rider
  re-attach in `WormholeXTremeVehicleListener` does:
  ```java
  final int[] attempts = { 0 };
  final Runnable[] taskHolder = new Runnable[1];
  taskHolder[0] = () -> { ... scheduler.scheduleSyncDelayedTask(plugin, taskHolder[0], 1L); };
  ```
- **Catch `RuntimeException`, not `Throwable`.** Bukkit/Paper can throw unchecked exceptions from
  anywhere, so guarding an API call is right; catching `Throwable` to do it is not. It swallows
  `Error` — `OutOfMemoryError`, `StackOverflowError`, a `NoClassDefFoundError` from a genuinely
  missing class — and hides real bugs behind a silently degraded feature. "Exception handling no
  longer swallows `Error`" is a recorded decision (see the 1.1.0 changelog); `catch (Throwable)` is not the target style.
  Where a missing method or class must be caught across versions, catch `Exception | LinkageError`
  and say why in a comment.

  The exception is deliberate cross-version compatibility: `plugin/EconomySupport` and
  `plugin/PermissionsSupport` catch `Exception | LinkageError` on purpose so the plugin stays
  standing across API versions. Where that is the reason, say so in a comment.
- **`instanceof` before cast** — always guard casts with `instanceof`.

### Logging
Use the plugin's own logger, not `System.out`:
```java
WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "message");
WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "message");
WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "verbose/debug message");
```
`Level.FINE` is for verbose diagnostics (only visible when debug logging is enabled). `Level.INFO` for normal operational events. `Level.WARNING` for unexpected conditions.

There is a three-argument overload, `prettyLog(Level, boolean, String)`, whose boolean puts the plugin version in the tag. It is for lifecycle lines only — its callers are all in `WormholeXTreme` itself. **Do not call it with `false`**; that is the two-argument form written the long way, and `PrettyLogTest` fails on it.

### Bukkit Scheduler
```java
// Delayed task (ticks)
WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), runnable, delayTicks);

// Immediate next-tick task
Bukkit.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), runnable, 1L);
```
Anything that touches Bukkit entities, blocks or worlds runs on the main server thread. The few async tasks (`runTaskAsynchronously`) do only work that touches none of that: reading and writing window captures, and sifting what is visible from a captured snapshot, in `Captures`, re-sending a clicked window's blocks to one player after a short wait in `WindowClicks` (packets only, nothing in the world), drawing the map pictures in `MapMarkers`, and the update check in `UpdateCheck`. Do not add an async task that reaches the world.

### Entity / Vehicle API (Paper 1.20+)
- `entity.teleport(location)` — ejects passengers first; use this intentionally.
- `vehicle.addPassenger(entity)` — returns `boolean`; always check the return value.
- `entity.isValid()` — always guard scheduled tasks with this before touching the entity.
- Teleporting a vehicle sends `ClientboundTeleportEntityPacket`. Mounting sends `ClientboundSetPassengersPacket`. On Paper 1.20+, the client will ignore mount packets that arrive before it has acknowledged a player teleport (`ServerboundAcceptTeleportationPacket`).

### Key Classes
| Class | Purpose |
|---|---|
| `WormholeXTremePlayerListener` | `PlayerMoveEvent` → hands gate entry to `GateWalkThrough` (detection & player/vehicle teleport) |
| `WormholeXTremeVehicleListener` | `VehicleMoveEvent` → unmanned or driver-controlled vehicle gate entry |
| `WormholeXTremeBlockListener` | Block break/place protection around gate frames |
| `StargateManager` | Gate registry, spatial lookup |
| `StargateRestrictions` | Use cooldowns and recent arrivals |
| `ConfigManager` | Plugin config (timeout, gate shapes, etc.) |
| `MaterialUtils` | Shared material classification helpers (isIce, etc.) |

### Testing
- Tests live in `src/test/java/`. Framework: JUnit 5 + Mockito.
- Bukkit API is mocked; no live server needed.
- Run all tests before committing: `mvn -DskipTests=false test`.
- Do not break existing tests when adding new behaviour.
- New features should have accompanying unit tests where feasible.

### What NOT To Do
- Do not turn a lambda into an anonymous class, a scheduled task's included — see the note above.
- Do not add `@SuppressWarnings` without a specific reason, written as a comment directly above
  it (or in the class Javadoc for a class-level one). Update the table in
  `docs/DEVELOPMENT.md` when you add or remove one.
- Do not call `Thread.sleep()` or any blocking operation on the main thread.
- Do not use NMS (net.minecraft.server) reflection unless all other options are exhausted and the approach is clearly documented.
- Do not add unnecessary abstractions or helper classes for one-off operations.
