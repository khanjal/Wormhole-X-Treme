# Wormhole X-Treme — Copilot Instructions

## Project Overview
Wormhole X-Treme is a Bukkit/Paper plugin that implements inter-dimensional "stargate" portals in Minecraft. Players (and vehicles — boats, minecarts) walk or ride through portal frames and are teleported to linked destination gates. The active session is targeting **Paper 1.20+ (paper-26.1.2-59)**.

## Build System
- **Java 17**, Maven with `maven-shade-plugin` (shaded JAR includes `snakeyaml` and `sqlite-jdbc`)
- Build command: `mvn -DskipTests=true package` → produces `target/WormholeXTreme-1.0.0.jar`
- Test command: `mvn -DskipTests=false test` (67 JUnit 5 + Mockito tests)
- Deploy by copying JAR to `plugins/` on the Paper server

## Active Work Focus
Fixing **boat/vehicle passenger reattachment after gate teleport** on Paper 1.20+.

### Root Cause Understood
- Paper 1.20+ requires the client to send `ServerboundAcceptTeleportationPacket` before it processes any subsequent packets (including `ClientboundSetPassengersPacket`).
- If `player.teleport()` is called and then `addPassenger()` fires too soon, the mount packet is silently dropped by the client even though the server reports success.

### Current Strategy: Vehicle-First, No Player Teleport
In `WormholeXTremePlayerListener` (player-enters-gate path):
1. Teleport the **vehicle** to the destination (this ejects the player server-side).
2. **Do NOT call `player.teleport()`** — keep the player at the gate via event cancellation.
3. After 2 ticks, call `v.addPassenger(player)`. Since the client never received a teleport packet, it processes the mount packet immediately and repositions itself to the vehicle.
4. Fallback: if `addPassenger` fails, call `player.teleport(v.getLocation())` then retry.

In `WormholeXTremeVehicleListener` (vehicle-enters-gate path):
- Same reattach loop with 5-tick initial delay + post-success vehicle re-teleport for client sync.

---

## Code Style & Conventions

### Formatting
- **Allman-style braces**: opening brace on its own line for all blocks (class, method, if, for, try, etc.).
- **4-space indentation** (no tabs).
- `final` on every local variable and parameter that is not reassigned.
- One blank line between logical sections inside a method; two blank lines between methods.

### Java Idioms Used In This Codebase
- **Anonymous `Runnable` classes** — not lambdas. The codebase predates widespread lambda use; keep anonymous class style for consistency.
  ```java
  // CORRECT
  new Runnable() {
      @Override
      public void run() { ... }
  }
  // WRONG — do not introduce lambdas
  () -> { ... }
  ```
- **Array holders for mutable effectively-final state** in anonymous classes:
  ```java
  final int[] attempts = { 0 };
  final Runnable[] taskHolder = new Runnable[1];
  ```
- **Broad `Throwable` catches** around most Bukkit API calls — Bukkit/Paper can throw unchecked exceptions from anywhere. Use `catch (final Throwable t)` or `catch (final Throwable ignore)` as appropriate.
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
- Do not introduce lambdas or method references — keep anonymous class style.
- Do not add `@SuppressWarnings` without a specific reason.
- Do not call `Thread.sleep()` or any blocking operation on the main thread.
- Do not use NMS (net.minecraft.server) reflection unless all other options are exhausted and the approach is clearly documented.
- Do not add unnecessary abstractions or helper classes for one-off operations.
