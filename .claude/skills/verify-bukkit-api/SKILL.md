---
name: verify-bukkit-api
description: Verify a Bukkit/Spigot/Paper API's existence, behavior, or version boundary by checking the actual API jars, rather than answering from memory — for this repository (khanjal/Wormhole-X-Treme), which supports Minecraft 1.20 through 1.21.10 in one jar and has been bitten more than once by an API that quietly changed somewhere in that range. Use this whenever asked whether a Bukkit class, method, or field exists, is safe to call at a given point in the plugin lifecycle, behaves the same way across versions, or would work on Spigot/Paper/Purpur alike — and whenever proposing a new feature that touches an API not already used elsewhere in this codebase.
---

# Verifying a Bukkit/Spigot/Paper API claim

This project's supported range is wide (1.20 through 1.21.10 — seven versions proven in CI,
three server flavours, one jar) and its history has concrete, expensive examples of an API
that looked safe from memory but genuinely differed by version:

- `Material.isBlock()` goes through a live registry from Minecraft **1.20.6** onward, and
  throws rather than answering if called before the server has finished starting. A class
  whose static initialiser called it stayed broken for the life of the JVM on 1.20.6+ while
  working fine on 1.20 through 1.20.4 — invisible until the version matrix actually ran it.
- `EntityDismountEvent` lives in `org.spigotmc.event.entity` through **1.20.4** and in
  `org.bukkit.event.entity` from **1.20.4** on — 1.20.4 is the only version carrying both, which
  is exactly why it's this plugin's compile target: the jar compiles against both listeners and
  chooses one at runtime. The old package is gone from 1.20.6 on, not from 1.20.4.
- `Attribute.WAYPOINT_TRANSMIT_RANGE` (the locator-bar mechanism) does not exist before
  **1.21.6** — absent through 1.21.4, present from 1.21.6 on, confirmed by disassembling the
  actual enum in each version's jar rather than trusting a javadoc page's version number.

In every one of these cases, the wrong answer was "this has always worked this way" or "this is
probably fine" — a guess from general Bukkit familiarity that training data cannot be trusted
to have the fine-grained version boundary right. **Don't answer a Bukkit API question in this
project from memory. Check.**

## How to check

### 1. Look for the jar in the local Maven cache first

```bash
find ~/.m2 -path "*spigot-api*" -name "*.jar" | sort -V
find ~/.m2 -path "*paper-api*" -name "*.jar" | sort -V
```

If the versions needed are already cached (likely, since this project's CI matrix builds
against all of them), this needs no network access at all.

### 2. Check whether a class or member exists

For a class:
```bash
unzip -l path/to/spigot-api-X.Y.Z-R0.1-SNAPSHOT.jar | grep -i <ClassName>
```

For a specific field or method on a class already known to exist, extract and disassemble it
rather than trusting a name match in the listing — `javap` shows the real declared members:
```bash
unzip -o -q path/to/spigot-api-X.Y.Z-R0.1-SNAPSHOT.jar "org/bukkit/path/To/Class.class" -d /tmp/apicheck
javap /tmp/apicheck/org/bukkit/path/To/Class.class | grep -i <memberName>
```

### 3. Find the exact version boundary, not just "it exists on the newest one"

Loop across the cached jars from oldest to newest and note where the answer flips. This is
what actually matters for a plugin supporting a range — "it exists on 1.21.10" is a different
and less useful fact than "it exists from 1.21.6 on, absent before that."

```bash
for v in 1.20 1.20.4 1.21 1.21.4 1.21.6 1.21.10; do
  jar=$(find ~/.m2 -path "*spigot-api/$v*" -name "spigot-api-$v-R0.1-SNAPSHOT.jar" | head -1)
  [ -z "$jar" ] && { echo "$v -> not cached"; continue; }
  cd /tmp/apicheck && rm -f org/bukkit/path/To/Class.class
  unzip -o -q "$jar" "org/bukkit/path/To/Class.class" 2>/dev/null
  found=$(javap org/bukkit/path/To/Class.class 2>/dev/null | grep -c <memberName>)
  echo "$v -> $found"
done
```

### 4. Check whether it's Paper-only or genuinely cross-implementation

If the question is whether a plugin feature would work on plain Spigot/CraftBukkit (not just
Paper), check the **plain Spigot jar specifically**, not a Paper javadoc page — Paper's own
generated docs render Bukkit's API and Paper's extensions together, so a class showing up
there does not by itself prove it exists outside Paper. If a jar isn't cached locally and the
question genuinely needs an uncached or very recent version, `jd.papermc.io/paper/<version>/`
is searchable and can confirm a class/member exists on Paper at least — but confirm against
the plain `org.bukkit.*` jar before claiming something works on Spigot/CraftBukkit too.

### 5. Report the boundary precisely, and say what wasn't checked

State the exact version where behavior changes, not a vague "recent versions." If a version in
this plugin's supported range wasn't cached and couldn't be checked, say so explicitly rather
than assuming it behaves like its neighbours — a gap in the checked range is a gap in the
answer, not something to paper over.

## Why this matters more here than in a typical plugin

Most Bukkit plugins target one Minecraft version and get away with assumptions that happen to
be true for that version. This one deliberately spans 1.20 through 1.21.10 in a single jar and
proves seven versions across that range in CI on every push — which means an assumption that's
wrong for even one of those versions is a real, immediate, checkable bug, not a hypothetical
edge case. The version matrix exists to catch exactly this; using this skill *before* writing
code catches it earlier and cheaper than waiting for CI to catch it after.
