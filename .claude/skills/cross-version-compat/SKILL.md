---
name: cross-version-compat
description: Review a change to this repository (khanjal/Wormhole-X-Treme) for the ways it can break on a Minecraft version other than the one it was built against — API calls newer than the 1.20 floor, APIs removed on newer servers, registry-backed Material methods, catch blocks that must keep LinkageError, tests that construct version-specific events, Paper-only methods reached reflectively, and config defaults that never reach an upgraded server — then build against the edge versions locally. Use this before pushing any change to src/main or src/test that calls a Bukkit/Spigot/Paper API, touches a catch block, adds or changes a config setting, or constructs a Bukkit event in a test; when a CI matrix leg fails while local tests pass; and when reviewing someone else's PR here.
---

# Cross-version review

One jar supports Minecraft 1.20 through 26.2, on Spigot and Paper. A local `mvn test`
compiles against one version (1.20.4) and runs its tests once. The CI matrix builds ten Spigot
versions and three Paper versions, and every bug in this skill passed locally before one of
those legs failed. This review catches them before the push.

It complements `verify-bukkit-api`, which answers "does this API exist on version X?" This
skill asks which of your changes needs that question asked. Use `verify-bukkit-api` to answer
it.

## 1. Read the diff for these shapes

**A direct call to anything newer than 1.20.** The shipped jar compiles against 1.20.4, but CI
also compiles against 1.20 and 1.20.1, so the floor for a direct call is **1.20**. Before
PR #313, `World.createEntity`/`addEntity` (added in 1.20.2) passed locally and failed there
with "cannot find symbol". For any API not already used elsewhere in `src/main`, check it
against the 1.20 jar. If it's missing there, you have two options:
- Reach it reflectively: a cached `getMethod` lookup that returns null when absent, and a
  feature that switches off quietly. `MirrorFog`, `MirrorPackets` and `HiddenEntities` are the
  pattern.
- Or propose raising the floor. The user has said an old version can be dropped when it blocks
  a feature. Say which versions go, and that the CI matrix and README's supported range change
  with it. Never drop one silently.

**Something removed on newer servers.** Compiling against 1.20.4 cannot see a removal. Known
ones:
- `org.spigotmc.event.entity.EntityDismountEvent` is gone from 1.20.6. That's why the
  `legacy-api` and `modern-api` profiles exclude one dismount listener each.
- `EntityType.BOAT` is gone from 1.21.4, which split boats into one type per wood.
- Enum constants renamed across 1.21 (patterns, attributes, sounds, particles). Look names up
  at runtime (`MirrorStamp` does this for `PatternType`) instead of naming the constant.

**Registry-backed `Material` methods.** From 1.20.6, `isAir()`, `isSolid()`, `isOccluding()`,
`isBlock()` and similar go through the block registry. They throw if called before the server
has started, and a static initialiser that throws stays broken for the life of the JVM.
Compare enum constants (`== Material.AIR`) or ask the `BlockData`, and never call these from
static init.

**Catch blocks around version-sensitive calls.** A missing method or class arrives as a
`LinkageError`, not an exception. The settled forms are `Exception | LinkageError`, or
`ReflectiveOperationException | RuntimeException | LinkageError` around a reflective call.
- Never narrow one of these to `RuntimeException`. That silently breaks the fallback.
- Never widen to `Throwable`. That swallows `OutOfMemoryError`, and Sonar flags it (S1181).
- The load-bearing sites include `MaterialUtils.probeBlockCheck`,
  `Ring.resolveSlabTag`, the sign restore in `StargateBlockSetup`, `EconomySupport`,
  `PermissionsSupport`, `GateEntityScanner`, `MirrorFog`, `MirrorPackets`, `MirrorStamp` and
  `HiddenEntities`.
- If a Sonar sweep asks to narrow one of these, read whether the guarded call is
  version-sensitive first.

**Tests compile against every version too.** CI builds `src/test` in every matrix leg.
- Don't construct a Bukkit event whose constructor changed. `new EntityExplodeEvent(...)` has
  no form that compiles on both 1.20 and 1.21 (1.21 added `ExplosionResult`). Mock the event,
  or pull the logic into a helper that takes plain values. `BlockBreakEvent` and
  `BlockDamageEvent(Player, Block, ItemStack, boolean)` are safe across the range.
- Don't mock a method that is only reached reflectively, because the test won't compile on the
  version that lacks it. Give the class a seam instead, such as `HiddenEntities.creationWith`
  or `MirrorFog.sendDistanceWith`.

**Paper-only methods reached reflectively.** A misspelt name doesn't fail, it just turns the
feature off on Paper. Add every new reflective Paper name to `PaperApiTest`, which runs only in
the `paper-api` legs. That test is the only thing proving the name is real.

**Config changes reach new installs, not upgraded ones.** `ConfigurationYAML` fills in *absent*
keys from the defaults. A key already in a server's `config.yml` keeps the value written there.
The kawoosh sound change cost real debugging time because of this.
- **Changing a default** changes nothing for an existing server. Say so in the CHANGELOG, and if
  the old value is actively wrong, migrate it explicitly.
- **Renaming a key** needs an entry in `ConfigurationYAML.RENAMED`, so an old file still loads
  and is written back under the new name.
- **Removing a key** leaves it in old files. Check it's ignored rather than fatal.

## 2. Build against the edges

```bash
bash .claude/skills/cross-version-compat/edge-builds.sh
```

This runs `clean test` offline against Spigot 1.20 (the floor), 1.20.6, 1.21.4 and 26.2, and
Paper 26.2, each with the JDK and profile CI uses. It prints one PASS/FAIL line per version,
with the compiler or test errors under each failure. Pass a test pattern to run only some of
the tests (`'Mirror*Test,PaperApiTest'`). Expect several minutes for the full suite, so run it
in the background.

These versions are where changes have actually broken, not the whole matrix. A PASS here makes
a CI failure less likely but doesn't rule one out. CI remains the proof. For one of the other
versions, run its line from `.github/workflows/ci.yml` by hand:

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot" mvn -o clean test -Pmodern-api -Dspigot.api.version=1.21.1-R0.1-SNAPSHOT
```

`java` on PATH is Java 8. Maven defaults to 17, and the newest Paper API needs 25, which is why
the script sets `JAVA_HOME` for each version.

## 3. Report

For each finding, name the file and line, the versions it breaks on, and how you know: the jar
you checked or the edge build that failed. If a version wasn't checked, say so. Don't assume it
behaves like its neighbours.
