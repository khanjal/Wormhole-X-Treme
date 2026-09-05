# Wormhole X-Treme — Copilot Instructions

## Project Overview
Wormhole X-Treme is a Bukkit/Spigot/Paper plugin that implements inter-dimensional "stargate" portals in Minecraft. Players (and vehicles — boats, minecarts) walk or ride through portal frames and are teleported to linked destination gates.

One jar supports **Minecraft 1.20 through 1.21.10**. It compiles against 1.20.4 — the oldest supported API, so the compiler enforces the floor — and CI builds it again against every version in the matrix in `.github/workflows/ci.yml`, which is the authority on what is supported.

## Build System
- **Java 17**, Maven with `maven-shade-plugin` (shaded JAR includes `snakeyaml` and `sqlite-jdbc`)
- Build command: `mvn -o -q package -DskipTests` → produces `target/WormholeXTreme-<version>.jar`, versioned from the pom
- Test command: `mvn -o test` — JUnit 5 + Mockito, no live server needed
- Deploy by copying the JAR to `plugins/` on the server

## Vehicle teleport: why it looks the way it does

This is shipped and working, not open work, but the shape of the code is surprising enough to
be worth stating so it is not "simplified" back into the bug it solves.

- Paper 1.20+ requires the client to send `ServerboundAcceptTeleportationPacket` before it processes any subsequent packets (including `ClientboundSetPassengersPacket`).
- If `player.teleport()` is called and then `addPassenger()` fires too soon, the mount packet is silently dropped by the client even though the server reports success.

So in `WormholeXTremePlayerListener` (player-enters-gate path) the **vehicle** is teleported and
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

### Java Idioms Used In This Codebase
- **Anonymous `Runnable` classes for scheduled tasks** — not lambdas. Every one of the 22 scheduler
  callbacks in the tree is an anonymous class, and the reason is concrete rather than stylistic:
  these tasks reschedule themselves and mutate retry state, which needs the `taskHolder` and
  array-holder idiom below and reads badly as a lambda.
  ```java
  // CORRECT, for a scheduled task
  new Runnable() {
      @Override
      public void run() { ... }
  }
  ```
- **Lambdas and method references elsewhere are fine, and are used.** Tab-completion functions in
  `SubCommands`, `computeIfAbsent` suppliers in `RingIndex` and `BeamManager`, the `FilenameFilter`
  in `StargateYamlManager`, and the `Function`/`BiConsumer` accessor pairs that parameterise
  `MaterialCommand.Kind` are all lambdas or method references. Do not rewrite them into anonymous
  classes, and do not flag new ones in review.
- **Array holders for mutable effectively-final state** in anonymous classes:
  ```java
  final int[] attempts = { 0 };
  final Runnable[] taskHolder = new Runnable[1];
  ```
- **Catch `RuntimeException`, not `Throwable`.** Bukkit/Paper can throw unchecked exceptions from
  anywhere, so guarding an API call is right; catching `Throwable` to do it is not. It swallows
  `Error` — `OutOfMemoryError`, `StackOverflowError`, a `NoClassDefFoundError` from a genuinely
  missing class — and hides real bugs behind a silently degraded feature. "Exception handling no
  longer swallows `Error`" is a recorded decision (see the 1.1.0 changelog notes); the ~86
  remaining `catch (Throwable)` blocks are legacy being narrowed, not the target style.

  The exception is deliberate cross-version compatibility: `utils/LegacyCompat`,
  `plugin/EconomySupport` and `plugin/PermissionsSupport` catch `NoSuchMethodError` on purpose so
  the plugin stays standing across API versions. Where that is the reason, say so in a comment.
- **`instanceof` before cast** — always guard casts with `instanceof`.

### Logging
Use the plugin's own logger, not `System.out`:
```java
WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "message");
WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "message");
WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "verbose/debug message");
```
`Level.FINE` is for verbose diagnostics (only visible when debug logging is enabled). `Level.INFO` for normal operational events. `Level.WARNING` for unexpected conditions.

### Bukkit Scheduler
```java
// Delayed task (ticks)
WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), runnable, delayTicks);

// Immediate next-tick task
Bukkit.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(), runnable, 1L);
```
All scheduled tasks run on the main server thread (sync). Do not use async tasks for anything that touches Bukkit entities or worlds.

### Entity / Vehicle API (Paper 1.20+)
- `entity.teleport(location)` — ejects passengers first; use this intentionally.
- `vehicle.addPassenger(entity)` — returns `boolean`; always check the return value.
- `entity.isValid()` — always guard scheduled tasks with this before touching the entity.
- Teleporting a vehicle sends `ClientboundTeleportEntityPacket`. Mounting sends `ClientboundSetPassengersPacket`. On Paper 1.20+, the client will ignore mount packets that arrive before it has acknowledged a player teleport (`ServerboundAcceptTeleportationPacket`).

### Key Classes
| Class | Purpose |
|---|---|
| `WormholeXTremePlayerListener` | `PlayerMoveEvent` → gate entry detection & player/vehicle teleport |
| `WormholeXTremeVehicleListener` | `VehicleMoveEvent` → unmanned or driver-controlled vehicle gate entry |
| `WormholeXTremeBlockListener` | Block break/place protection around gate frames |
| `StargateManager` | Gate registry, spatial lookup |
| `StargateRestrictions` | Cooldown, recent-arrival, permission checks |
| `ConfigManager` | Plugin config (timeout, gate shapes, etc.) |
| `MaterialUtils` | Shared material classification helpers (isIce, etc.) |

### Testing
- Tests live in `src/test/java/`. Framework: JUnit 5 + Mockito.
- Bukkit API is mocked; no live server needed.
- Run all tests before committing: `mvn -DskipTests=false test`.
- Do not break existing tests when adding new behaviour.
- New features should have accompanying unit tests where feasible.

### What NOT To Do
- Do not turn a scheduled task into a lambda — see the `Runnable` note above. Lambdas and method
  references are fine everywhere else.
- Do not add `@SuppressWarnings` without a specific reason.
- Do not call `Thread.sleep()` or any blocking operation on the main thread.
- Do not use NMS (net.minecraft.server) reflection unless all other options are exhausted and the approach is clearly documented.
- Do not add unnecessary abstractions or helper classes for one-off operations.
