# Changelog

All notable changes to this project are documented in this file.

## 1.4.0 (2026-09-04)

### Admin beaming: goto and send, from a player, console, or a command block

Two new `beam admin` actions, both gated behind a new `wormhole.beam.admin.teleport` node
(default op) rather than the existing `wormhole.beam.admin` -- curating the destination
list and instantly relocating any player are different orders of power, and holding the
first shouldn't automatically hand out the second.

- `/wormhole beam admin goto <player>` or `<x> <y> <z> [world]` -- beams the sender to a
  player or raw coordinates. Player-only: there's nowhere for console or a command block to
  beam *from*.
- `/wormhole beam admin send <target> <player>` or `<target> <x> <y> <z> [world]` -- beams
  a named, online player to another player or raw coordinates. The one place this command
  accepts console or a command block as the sender, since neither is the one being moved;
  whoever sent it gets told whether it worked, since the target's own "Beaming to X..."
  messages don't reach them.

Both run the full `BeamAnimation` sequence on the traveller -- same glow, rise, descend and
fade as any other beam trip, and the same terrain-drift correction described below. Neither
applies beam's cooldown or cost; this is an administrative move, not the target player
choosing to travel, the same reasoning `Go.java`'s gate branch already applies to its own
travel.

Along the way, fixed a real bug this feature would otherwise have landed directly in:
`Wormhole.java`'s dispatcher refused any `/wormhole` command with more than 4 arguments
before ever reaching a handler, silently -- which also means `/wormhole beam admin cost
<name> <amount>` (5 tokens) and `/wormhole ring edit <id> <field> <value>` (also 5) were
already unreachable through the real command, not just untested. Nothing needed that cap;
every handler already validates its own argument count and replies with its own usage
message on a mismatch.

### Beam and gate arrivals correct for terrain that has drifted since they were set

A beam destination, a place, or a gate's arrival point is only ever as good as the ground
was the moment it was recorded -- building up or digging out afterward doesn't move the
stored coordinates, so an exact teleport there could land somebody buried in a block that
had since risen to meet them, or hanging in the air over ground that had since dropped
away. `WormholeXTremePlayerListener` already searched outward for standable ground before
a gate's own walk-through arrival, so that search (`WorldUtils.findSafePlayerLocation`) is
now shared: `BeamTravel.travelTo` runs it once on the resolved destination before starting
the beam sequence, so the real teleport and the arrival column -- both already reading from
the same `Location` -- land in the same corrected spot with nothing further to keep in
sync. `/wormhole go`'s gate shortcut gets the same correction, closing the one path into a
gate that had been skipping it.

### Beam sequence decisions are now testable without a running server

`BeamAnimation.Sequence` computed every phase boundary and quantity inline, mixed in with
the Bukkit calls that acted on them -- the same tangle the ring subsystem split apart early
on, deferred for beaming until the sequence itself stopped changing shape every few days.
Now that it has, `BeamFrame.at(tick, timing)` is a pure function from a tick number and the
sequence's resolved durations to everything that tick should do; `Sequence` is left doing
only the Bukkit half, in the order `BeamFrame` says it applies, with no arithmetic of its
own left to get wrong. Thirteen new tests pin the exact tick every transition lands on by
hand -- envelop ending and rise beginning with no gap or overlap, the origin column still
playing on the same tick teleport fires (independent of the traveller, who has already
left), fade finishing on schedule rather than one tick early or late. An off-by-one at any
of these boundaries used to only be noticeable by actually watching a beam run; now it is
a failing assertion.

`/wormhole beam list` now shows a destination's cost next to its name when it has one of
its own -- easy to lose track of otherwise now that costs can vary per destination.

### Beaming: a third way to travel, alongside gates and rings

`/wormhole beam to <name>` moves a player straight to a named destination -- no physical
construct required on either end, unlike a gate or a ring. Public destinations are
admin-curated (`beam admin set|remove`); private places are per-player
(`beam place set|remove|list`), following the ownership shape rings already established
rather than gates' permission-node model, since beaming shares gates' networks and owner
rules as little as rings do -- its own `BeamPermissions` class, not `WXPermissions`.

`/wormhole go` now doubles as a beam shortcut: it tries a gate first, under `wormhole.go`,
and falls back to a beam destination or place if no gate matches or the player never held
that permission at all. A player with no gate-admin access can use `go` as a shortcut to
their own places, rather than beaming staying a second, unrelated command for the same
idea of "take me to X."

Cooldown (`BEAM_USE_COOLDOWN_ENABLED`/`_SECONDS`) and economy cost
(`BEAM_ECONOMY_USE_COST`) are both off by default, reusing the same `EconomySupport`/Vault
connection gate costs already use. Both are applied only once the teleport actually fires,
not at the point of starting the sequence -- the same reasoning gate travel already
applies to its own cooldown, so a player who disconnects mid-sequence is never charged or
cooled down for a trip that never happened.

### The beam effect matches its reference footage

The animation went through several shapes before landing on one that actually matches how
beaming looks on screen: a bright glow gathers at the traveller's body and appears to
absorb them; they and the light disappear into a beam that rises and departs; at the
destination the beam deposits them, with the light still there, and it fades quickly.

An early version used a `PotionEffectType.GLOWING` charge phase before any particles
appeared, which put a full second of nothing visible between running the command and
anything happening -- cut. A separate charging orb (a growing sphere of coloured dust
particles) was tried next and dropped for two reasons: `Particle.DUST`/`REDSTONE` is lit
by ambient block light rather than self-illuminating, so a configured "pure white" was
never fully reachable regardless of the hex value; and no amount of particle count or
spread made a randomly scattered cloud read as one solid ball. The current version uses
one particle system throughout (`Particle.END_ROD`, self-illuminating, no lighting
dependency) and gets both the glow and the beam from the same column -- dense and
body-height during the envelope, full height and constant brightness through the rise and
descent, then fading back down quickly once the traveller is deposited.

Every duration (`BEAM_ENVELOP_TICKS`, `BEAM_VANISH_AT_STEP`, `BEAM_RISE_TICKS`,
`BEAM_TELEPORT_AT_STEP`, `BEAM_DESCEND_TICKS`, `BEAM_FADE_TICKS`) is configurable and
resolved once per sequence through the new `BeamTiming`, which clamps them against each
other so no combination of config values can leave a player stuck: a
`BEAM_TELEPORT_AT_STEP` set equal to or past `BEAM_RISE_TICKS`, for instance, would
otherwise mean the teleport condition is never reached and the traveller is frozen and
invisible with no way out short of a restart.

### The traveller can move during the envelope, matching the reference

The envelope used to freeze the traveller in place from the moment the command ran, before
they had even vanished -- but the reference shows someone still walking, turning, reacting
while the glow gathers on them. `BeamFreeze` now only takes hold at the vanish tick, once
they have actually been "absorbed"; the envelope's particles track wherever the traveller
currently is rather than a fixed spot, since a fixed column would just miss them the moment
they stepped away from where the sequence began. The departure column that opens out of the
envelope roots itself wherever they happened to be standing at that exact tick, not
wherever they started.

This split the already-beaming guard from the position lock, which had been the same flag.
`BeamAnimation.start` used to refuse a second beam by checking whether the player was
frozen -- but nobody is frozen during the envelope anymore, so that check would have let a
second sequence start on top of a first one still gathering. `BeamFreeze` now tracks two
states: active (the whole sequence, envelope included, checked by the guard) and frozen
(only from vanish onward, checked by the movement listener). A player disconnecting during
the envelope clears both on the way out, the same as a frozen player always did -- an
active-but-never-frozen flag left standing would have refused that player every beam for
good, with nothing left running that could ever clear it.

### Public destinations can have their own beam cost, and admins can bypass it

`BEAM_ECONOMY_USE_COST` was one flat number for every destination. `BeamDestination` now
carries an optional cost override -- `/wormhole beam admin cost <name> <amount>`, or
`default` to go back to inheriting the global setting -- so spawn can stay free while a
boss-arena destination costs something, without touching the server-wide default at all.
Deliberately not offered for private places: a place is only ever reachable by the player
who made it, so letting them set its cost would just be them choosing what to pay
themselves.

`BeamDestination`'s cost is a `Double`, not a primitive, specifically so "no override" and
"explicitly free" stay distinguishable -- null inherits whatever the global default
currently says, `0.0` is a permanent "this one is free" that a later change to that default
cannot quietly override. An absent `Cost` field in a stored destination, or a malformed
one, both fall back to null rather than either being read as 0.0 -- the wrong one of those
two would make every destination written before this existed, or every place, quietly free
regardless of configuration.

Since a real cost is now something a player might not expect, the amount is stated up
front in chat before the sequence starts rather than only discovered once charged. A hard
confirm-before-travelling step felt like more friction than gate travel has ever needed
for the same kind of cost, so this is the middle ground: seen, not gated on.

`wormhole.beam.admin` now bypasses both the cooldown and the cost entirely -- neither
checked nor applied. Gate travel's own cooldown and cost apply uniformly regardless of permission
with no such bypass, so this is a deliberate departure for beaming specifically: staff
testing destinations or handling a support request are the common case a bypass is
actually for, and reusing the node that already gates managing public destinations costs
nothing new to wire up.

### `/wormhole go` now respects private network permission, like dial already does

`wormhole.go` was a single blanket node with no way to know which network a target gate
was on, because `Go.java` called the permission check before looking the gate up --
`WXPermissions` had nothing to read a network name off of. That meant a private network's
`wormhole.network.use.<name>` node, which `Dial` already enforces, was bypassed entirely
for anyone holding `wormhole.go`. Fixed by looking the gate up first and passing it into
the permission check, whose `GO` case now consults `NETWORK_USE` the same way `DIALER`
does.

## 1.3.0 (2026-09-03)

### Holding forward against a locked gate no longer spams chat

Cancelling a move event returns the player to `event.getFrom()` -- the exact spot they tried
to leave -- so someone holding a movement key against a gate they cannot enter generates a
fresh event every tick with an identical from/to pair. `refuseGateEntry` sent its message
unconditionally on every one of those: holding forward against a locked exit, or against a
gate you just came out of, for a couple of seconds meant a wall of identical chat lines. This
is the same shape of bug fixed for rings earlier in this release, just not caught here at the
same time.

Two call sites shared the one method and both had it: walking into the exit end of a
one-way wormhole, and trying to walk straight back through the gate you just arrived at. Both
now remember the last gate a player was refused and when, and say nothing again for the same
gate within two seconds -- the move is still cancelled every time either way, only the
repeated chat line is suppressed.

### Gate management was never actually permission-gated

`/wormhole portalmaterial`, `irismaterial`, `lightmaterial`, `wooshdepth`, `redstone`,
`custom`, `owner`, `regenerate` (including the new `-all`), `restrict`, `cooldown`,
`activate_timeout`/`shutdown_timeout`, and the new `gate import` checked no permission at
all. Any player who could run `/wormhole` could reconfigure or reassign *any* gate on the
server -- not just their own -- and change server-wide settings that are not even per-gate.
`OwnerCommand`'s own class comment says "admin command"; nothing in the code enforced that.

All of them now require `wormhole.config` -- the node `/wormhole config` already used --
rather than inventing a second admin-only node that would mean the same thing. This predates
this release's other work; `gate import`, written fresh this session, inherited the same gap
and is fixed alongside the rest.

`gate edit group` had the same gap for a different reason: unlike every other `gate edit`
field, it does not delegate to one of the legacy handlers above, so it inherited no check by
riding along with one. The guard is now on `GateEditCommand` itself rather than repeated in
every field, which also covers whatever field is added to it next.

### Importing from other Wormhole X-Tremes

`/wormhole gate import` reads the SQLite database every build descended from the 2011 original
uses, and converts the gates into this fork's own storage. It covers the original itself,
lycano's line, and forks built on either -- anything writing
`WormholeXTremeDB/WormholeXTreme.sqlite`.

It needed almost no new code. Those databases hold each gate as a binary blob rather than as
columns, and this fork inherited the reader for that format -- `GateSerializer` still
understands binary versions 3 through 9, the whole history of it. The import gets the rows out
and hands each blob to a parser that was already there and already tested.

Nothing is written back to the old database and nothing is deleted, names that already exist
are skipped rather than replaced, and a gate whose world is not loaded is reported rather than
guessed at. Servers that have such a database and no gates of their own are told once, on
startup, that the option exists.

An imported gate gets the same arrival-point safety check every other gate does. Some of
these databases hold gates old enough that their stored exit sits inside the portal itself --
the same legacy case `StargateYamlManager.loadStargates()` already corrects for gates read
from disk -- and without the same check here, an imported gate would land travellers in the
water forever, even though every other gate in the plugin is guaranteed clear of it. The
import summary reports how many needed it, the same way loading a YAML file does.

`/wormhole gate regenerate -all` runs the fuller arrival-point recompute -- the one that
derives an exit from a gate's actual geometry rather than only nudging one that is provably
stuck inside a portal -- across every gate in one pass, and reports how many genuinely needed
it. Recomputing is deterministic, so a gate that was already correct comes back unchanged and
is not counted or rewritten to disk. Deliberately narrower than running `regenerate` on one
gate by hand: it only touches the arrival point, not the dial lever, iris lever, redstone or
sign that a single-gate regenerate also refreshes, since rewriting those for every gate on the
server without anyone looking is a different and much larger thing to do than fixing exits.

The SQLite driver is deliberately not shipped: thirteen megabytes of native libraries for a
one-time import most servers will never run. It costs nothing to leave out, because any server
that wrote one of these databases necessarily has the driver already -- the plugin that wrote
it needed the same one.

### Commands restructured

Twenty-two subcommands became four. Gates had fifteen top-level names while the rings had one
with verbs under it, and eight of the fifteen were per-gate settings -- which is how a plugin
gets to twenty-two: every new gate setting needed a new name at the top level, where a new
ring setting was one more case in `ring edit`.

    /wormhole gate <build|complete|list|remove|edit|regenerate|refresh|go|force>
    /wormhole ring <create|cancel|list|remove|edit|allow|deny|owner>
    /wormhole config <setting> [value]
    /wormhole compass

`gate edit <gate> <field> [value]` replaces `portalmaterial`, `irismaterial`,
`lightmaterial`, `wooshdepth`, `redstone`, `idc`, `owner` and `custom`, and adds a `group`
field that sets a whole material group at once rather than three materials one at a time. It
leaves the frame blocks alone: those are real blocks somebody built.

`config <setting> [value]` replaces `shutdown_timeout`, `activate_timeout`, `cooldown` and
`restrict` -- and reaches every other setting too. Sounds, ring timings and material defaults
could previously only be changed by editing config.yml and restarting; now they take effect
as they are typed, because settings are read where they are used rather than cached at
startup. Typing part of a name searches.

Nothing was re-implemented. Every verb and field hands off to the handler that already owned
it, so the validation, the permission checks and the messages are the originals.

**Every old name still works.** They are registered as hidden entries: they dispatch exactly
as before but are left out of help and tab completion, since a restructure that made the list
longer would have missed the point. Nothing in a command block or a script breaks.

Tab completion gained two things it never had: gate shape names for `gate build`, and every
setting name for `config`.

`gate regenerate` now also recomputes a gate's arrival point, worked out from the portal
blocks and the facing rather than from the shape file. The exit is computed once when a gate
is built and then stored, so a gate that landed travellers at its side went on doing it with
nothing to reach for. It cannot fix a gate whose facing is wrong, since the facing is what it
trusts.

`compass reset` puts the compass back to pointing at world spawn -- what an ordinary one does
when nothing has changed it. The heading is stored against the player and stays until
something moves it, so there was previously no way back.

`compass` explains itself when nothing will show the result. Setting a compass heading does
not need a compass and does not fail without one, so the command used to report success and
then appear to do nothing -- either because the player had no compass at all, or because the
one in their hand was bound to a lodestone and points there regardless. Neither is an error,
so it explains rather than refuses: the heading is set and waiting for a plain compass.

Asked the way round that stays true, too. Rather than listing the compasses that ignore the
heading, it asks whether the player has an ordinary one -- so recovery compasses, and whatever
Minecraft adds later, are covered without anybody having to remember to add them.

### Documentation split by audience

The README had grown to a thousand lines serving two readers at once. It is now for **server
owners** -- installing, configuring, building gates and rings, running them -- and the
plugin-developer material moved to **docs/API.md**: the events, what each one carries, when it
fires relative to the move, and worked examples.

The minecart event is documented properly for the first time as well -- a minecart does not
survive a gate, it is removed and respawned, which is why the event carries both carts.

**One breaking change.** `StargateMinecartTeleportEvent` moved from
`com.wormhole_xtreme.wormhole.event` to `...events`, joining every other event. It had been
alone in the singular package with nothing to justify it, and the compiler error that caused
did not explain itself. Anyone who wrote against it needs the plural import now; that is a
cheap fix today and a permanent wart otherwise.

### Transport rings

An invisible, permanently paired pad set into a floor or ceiling. Walk into one and it counts
down, deploys a stack of rings around you, and swaps everything at both ends in the same
instant. Gates stay the long-haul option; rings are local transport, and both ends fire
together, so two people standing on the two pads trade places.

Point to point rather than dialable. A ring with no partner can do nothing, so the pair is
the stored object and there are no dangling references, no second resolution pass on load and
no orphans. That also settles naming: nothing addresses a single ring at runtime, so pairs
get a generated id and an optional label rather than a player-chosen name.

Rings never cross worlds, by design rather than by config, and pairs are stored one file per
world -- the layout enforces the rule, because there is nowhere to write a pair that spans
two.

### Building a pair

Lay a circle of slabs, stand inside it, and run `/wormhole ring create`. Do it again
somewhere else and the two are paired. Only then are both circles consumed and both surfaces
returned to what they were: an unpaired ring does nothing, so leaving its slabs costs
nothing, and a crash or restart between the two halves no longer costs somebody a circle of
slabs for a ring that never existed.

The template says more than its shape. The slab you build with becomes the ring's material,
and the slab halves say which surface it is set into -- a bottom slab rests on a floor, a top
slab hangs from a ceiling. Both are facts the template states rather than guesses about its
surroundings.

Two shapes. The odd one **is the Standard gate's ring** -- the same `3,5,7,7,7,5,3` profile,
lying flat instead of standing up -- and the even one is a size down for tighter rooms. Both
turn each corner through two diagonal steps, which is what reads as round rather than as a
square with its corners clipped.

`/wormhole ring remove` lays both circles back out in the slab each was built from, so a pair
can be picked up and moved without re-mining anything.

### Nothing is written to the world

Rings and their lights are drawn to nearby clients, the way a gate draws its portal. Building
them for real had three faults: a server stopped mid-cycle kept them for good, block loggers
recorded a floor being replaced on every trip, and for the seconds a ring stood there its
glowstone was an ordinary breakable block -- mine one and the restore skipped it, leaving a
hole in the floor. Drawing also made the code simpler rather than harder: with the real
blocks untouched, undoing a drawing is just showing the client what was always there.

The pad opens rather than lights. The surface the pattern is cut into is drawn as an
invisible barrier and the light shows from a block below it, so a waking ring reads as a lit
recess the rings climb out of instead of a pattern painted on the ground. A barrier rather
than air because the floor only opens on the client: told the ground had gone, a client
predicts a fall the server refuses, and walking across a waking ring feels like being stuck.
A drawing may make collision stronger than the block it covers, never weaker.

### Firing

The swap reads both ends before writing either. Reading A, moving them, then reading B would
find A's arrivals standing in B and send them straight back. This is the ordering everything
else depends on, and a test fails if it is ever inverted.

Abort is confined to the countdown. Once the rings start rising the cycle runs to the end,
which keeps the reversible phase trivially reversible and the phase with all the moving parts
uninterruptible.

Everything inside the ring travels; only players are subject to access rules. A rider and
their mount arrive together -- the mount is delivered and its passengers re-seated a tick
later, rather than teleporting both and letting the server separate them.

Deployment comes in two styles, concurrent and sequential, set per end. A ceiling ring drops
its rings to the floor and stacks up from there, because a stack hanging from the ceiling of
a tall room would leave the traveller standing under it rather than in it.

### Refusing to fire

A ring will not engage if the far end is unsafe: anything at all placed inside the teleport
area, or a single block missing from the ground under it. Both are refusals rather than
best-effort trips, since the alternative is depositing somebody inside a wall or over a void.

Because the ring itself is invisible, a refusal also flashes the pattern. Being told an end
is blocked while standing on ground that looks like any other is no help when the thing to
fix is inside a footprint you cannot see.

Reach is limited in two directions, because ground distance and height are different
questions. A ceiling ring needs a room between four and ten blocks tall -- near enough for
its rings to reach the floor, far enough for the stack to form.

The fallback ring material is `SMOOTH_STONE_SLAB` -- the plain stone slab people picture, not
`STONE_SLAB`, which is the rougher raw-stone one added later and reads as halved stone. It is
normally unused, since a ring keeps whatever slab it was built from, but it is what shows if
that cannot be read.

`ring edit reset` puts an end's appearance back to the slab it was laid in, not to a
configured default. Build in quartz, try a colour you do not like, reset, and the quartz comes
back. The lights and the deploy style have no such history -- nobody builds those -- so those
do take the server defaults.

`ring edit built` sets what that slab *is*, for the one case `reset` cannot recover on its
own: a ring imported or corrected after the fact, with no recorded history to restore. Hand
editing the stored `Built:` value in the YAML does not work for this -- the plugin resaves
every ring from memory on shutdown, so an on-disk edit is silently overwritten with the old
in-memory value before it is ever read back. This command changes the in-memory value
directly and saves immediately, so there is nothing left to clobber it.

### Sound

A ring drawn to clients and never built is otherwise a silent animation in somebody's floor,
so cycles now make noise: the pad opening, one sound per ring as the stack builds, the
transport itself, the pad closing, and a refusal heard by the player it concerns and nobody
else.

The transport sound plays at both beats, not just the first. The departure flash sounded at
both ends the instant a swap began, before anyone had actually moved -- it said a swap was
happening, not that anyone had arrived. The arrival sweep now sounds too, at the moment
travellers are already standing at their destination, settled back to the pitch the open and
close sounds use.

The pitch is what carries it. Each ring leaves a step higher than the one before, which makes
four repeats of one sound read as a machine rather than four clicks -- and because the pitch
follows the order rings leave rather than where they end up, the retract falls on its own and
a floor ring and a ceiling ring climb through the same notes.

Sounds are configured by name rather than chosen from a list, so anything the client knows
works, including a sound from a resource pack. Set one to `none` for silence, or
`sounds-enabled: false` for all of it. Names are never resolved to a `Sound` constant, which
keeps this working whichever way that type is defined in a given API version.

The transport light always runs **towards the pad** -- down a floor ring's stack, up a ceiling
ring's -- on the way out and on the way in alike, which is how the show does it. The pad is
where travellers are taken from and put back, so it is what the light moves to whichever job
it is doing. That replaced a configured departure direction and an arrival that ran its
opposite: both were wrong against the show, and both were arithmetic that could be got
backwards. What is left is the ring's own number, and needs no orientation, direction or sweep
to be asked about.

The light suggestions are lights rather than things that glow. Tab completion offered jack
o'lanterns, magma, crying obsidian, beacons and sculk catalysts -- blocks that emit light but
that nobody picks when they are trying to build a lamp -- and amethyst, which emits none at
all as a block. What is left is glowstone, sea lanterns, shroomlight, redstone lamps,
froglights and copper bulbs. Suggestions only: any block can still be set.

Redstone lamps and copper bulbs are now drawn switched on. Both default to off, so choosing
one used to light a ring with dark lamps. Anything the game calls lightable is switched on
wherever this plugin draws a block, so gate chevrons get it too.

### Access, permissions and events

Access belongs to the pair; materials and style belong to each end. Both ends fire together,
so there is no authorising half of a swap -- a pair whose ends disagreed would be one you
could leave by and not return to. Materials and timing are the opposite case: nobody watches
both ends at once, so a base can look and deploy differently from its outpost.

Access fails closed everywhere. A stored pair with a missing or unreadable access field loads
private. Publishing somebody's private link on upgrade is the one mistake here that cannot be
taken back once people have used it.

Four permission nodes of their own rather than more cases in `WXPermissions`, which is built
around gates and would have needed cases ignoring most of their own arguments. Building
defaults to operators; using a ring somebody built defaults to everyone.

`RingTravelEvent` fires once per travelling player, cancellable, after both ends are read and
before either is written -- so a listener always sees the whole trip as it was rather than a
half-finished one. Cancelling drops that passenger and leaves the rest of the trip alone.

### Fixes found on the way

Block positions are packed into a `long`, and `y` lived in the low twelve bits where a plain
mask loses its sign: `y = -64` came back as `4032`. The world starts at -64, so rings in
deepslate, caves or on the nether floor would have restored their blocks thousands of blocks
away -- leaving slabs standing in the floor for good and writing stray air into the sky, with
nothing thrown. Unpacking now sign-extends, and is tested at every height the world has.

`Ring` asked whether a material was a block while building its list of glowing materials, in
a static initialiser. From 1.20.6 on that question goes through the server's registry, which
is not there while the plugin is loading -- and a class whose initialisation fails stays
failed, so a single early call would have left rings dead for the rest of that server's run.
The check was redundant anyway. Tab completion asks the same thing for real, and now probes
once and offers everything rather than nothing when there is no registry to ask.

## 1.2.0 (2026-09-02)

### Minecraft 1.20 through 1.21.10

The range is measured, not assumed: every published `spigot-api` version was built against to
find both ends. The floor is 1.20, where `Material.CALIBRATED_SCULK_SENSOR` arrives and gate
detection switches on it; the ceiling is simply the newest API published.

Spanning that range meant handling an API move no single import covers. `EntityDismountEvent`
was `org.spigotmc.event.entity` up to 1.20.4 and `org.bukkit.event.entity` from 1.20.4 on, and
1.20.4 is the only version with both — which is why the plugin compiles against it. There is a
small listener per package and only the one the running server can load is registered, so a
server at either end keeps the behaviour and one with neither loses only that and says so. CI builds and tests the floor, the ceiling, and
the versions between them where the API actually changed.

Two things had to give to reach that range, and both were single symbols. A boat that failed
to teleport was respawned as `EntityType.BOAT`, which stopped existing in 1.21.3 when boats
split per wood type; it now respawns as whatever the original boat was, which also stops a
birch boat coming back oak. A test named the same constant and now looks it up by name.

The jar is compiled against the **oldest** supported server rather than the newest. A plugin
built against an old API runs on newer servers; one built against a new API can call
something an older server has never heard of, and nothing catches that until a player reports
a crash. Compiling against the floor makes the compiler enforce it. CI covers the other
direction — a newer server having removed something — by building against each supported
version in turn.

`spigot.api.version` in `pom.xml` selects the API, so CI can point one build at a different
server version without editing anything.

- Materials named in `config.yml` and the shape files are text resolved at runtime, so the
  compiler never sees them and a renamed or removed material would only surface on a live
  server. A test checks every shipped name against whatever API the build targets, which
  means changing that target is what runs the check.
- Arrow knockback and the shot-from-crossbow flag are deprecated from 1.21, because both are
  now derived from the weapon an arrow was fired from. A projectile crossing a gate is
  consumed and re-fired, so it has no weapon to derive them from, and the deprecated setters
  are the only way to carry them across. They are kept deliberately: dropping them would
  quietly weaken every arrow that made the trip.
- **Minecraft 1.20.5 and later require the server to run Java 21.** That is the server's
  requirement, not this plugin's; the jar remains Java 17 bytecode.

Nothing here has been runtime-verified on a live server of any version. CI proves the plugin
compiles and its tests pass against each API, not that a gate behaves correctly in game.

## 1.1.0 (2026-09-01)

First published release. The original project ended at 0.854; 1.0.0 was an internal
milestone of this fork and was never tagged or released.

Requires **Java 17** and a **Minecraft 1.20.4** server. Runs on CraftBukkit, Spigot, Paper,
Purpur and Pufferfish from a single jar. Folia is not supported.

### Gates

- Portal interiors are now server-side `AIR` with the portal material drawn to nearby
  clients, so travellers no longer drown or float in a water gate. The iris is deliberately
  still a real block — a client-only iris would let players walk through a closed one.
- Ridden animals travel with their rider, and riderless horses, camels, pigs, donkeys,
  llamas and striders can walk through on their own. They previously fell between two code
  paths because Bukkit classifies them as `Vehicle` while they raise no `VehicleMoveEvent`.
- Minecarts and boats teleport again. They had stopped entirely: the vehicle listener still
  compared the portal block's material, which can never match once the portal is air.
- Projectiles cross gates and keep flying, retaining their shooter so kills stay credited.
  They are consumed and re-fired rather than teleported, and detection walks the path
  travelled each tick because at bow speed an arrow steps over a one-block-thick portal.
- Mobs, animals, dropped items and XP orbs travel; item frames and paintings do not.
- Wormholes are one way. Only the origin holds a target, so a gate dialled out of a base is
  not a door mobs can wander back through.
- Redstone activates a gate from any component touching its activation block, so a detector
  rail wired into a gate now works.
- Redstone gates can actually be built. The shipped shapes marked their redstone cells one
  block off, so a gate watched a wall for a signal and looked for a lever where a frame
  block always is — no error, just redstone that never fired. Shapes write those markers two
  different ways and both are now handled. `StandardSignDial` gained working markers, so a
  Standard gate takes redstone without being rebuilt.
- Triggering an already-open gate does nothing rather than closing it, so a second minecart
  no longer shuts the wormhole the first one opened.

### Gates make noise

Gates were silent. Everything a gate does was already staged over time -- chevrons light one
at a time on `light-ticks`, the woosh rolls out over `woosh-ticks` -- so the animation was
there and only the sound was missing. There is now one as a gate begins to dial, one per
chevron as it locks, one as the wormhole establishes, and one as it closes.

The chevron pitch climbs through the sequence, spread across however many lighting steps the
shape actually has rather than across an assumed seven. A three-chevron gate starts and ends
on the same notes as a seven-chevron one, in bigger steps. It is driven off the same counter
that drives the lights, so the sound cannot drift out of step with what it is describing.

The iris has its own pair, closing pitched below opening because a shield coming down should
be the heavier of the two. Only played when the iris actually moves, so asking for a state it
is already in stays silent.

An open wormhole also runs, on repeat, until it closes -- `ambient.underwater.loop`, because an
event horizon looks like water and behaves like it, so that is the one ambience needing no
explanation. One sweep over the open gates drives
it rather than a task per gate -- the work is the same, and there is nothing per-gate to
cancel or leak when a gate is removed with its wormhole up. It plays at 40% of the gate volume
because a standing wormhole is a background rather than an event, and because Bukkit ties
range to volume that also keeps it near the gate rather than across a base.

Configured the same way as ring sounds, by name, with `gate-sounds-enabled` over all of it.
The README has both sets in one place, with the settings, what each one is for, and some
alternatives worth trying.

### Nothing a gate does is written to the world any more

The portal was already a drawing sent to nearby clients. The chevrons and the woosh were not:
both called `setType`, so dialling a gate really did place glowstone in the frame and really
did push portal material out into the air around it.

That carried the problems the portal had already been moved away from. A server that stopped
mid-dial left lit chevrons welded into the frame and a half-expanded woosh hanging in the air,
with the original materials it would have restored from having died with the process. Block
loggers recorded every chevron and every woosh frame. And for the seconds a chevron stood lit
it was an ordinary breakable glowstone block -- the same free-glowstone trade that decided
this question for the transport rings.

Both are drawings now. The bookkeeping they needed goes with them: there is no original
material to remember, because nothing is changed, and putting a drawing away is just showing
the client what was always there. A drawing also cannot outlive the process that drew it,
which is the whole point.

The one thing given up is real light. A drawn glowstone looks lit and illuminates nothing, so
a dialled gate no longer brightens the room around it. It still reads as lit, which is what
the chevrons are for.

Arriving near a gate that is already dialled now draws its chevrons as well as its portal, so
a wormhole is never found burning in an unlit frame.

Travellers now surface. Coming out of a gate shows that player a moment of water at eye
height -- the client draws its underwater overlay from whichever block it thinks its camera is
in, so one block is the whole effect. Nobody else sees anything and nothing is written.

It is redrawn every couple of ticks across its window rather than sent once. Arriving hands
the client a fresh copy of the chunk and a fresh copy erases anything drawn into the old one,
so a single block change lands before the chunk does on any trip long enough to need loading,
and is wiped by it -- the same thing that once made the portal redraw work for a nearby gate
and do nothing at all for a distant one.

One second by default, and short on purpose: water is physics to the client rather than
decoration, so for as long as it believes it is submerged it predicts swimming while the
server disagrees. It is the only drawing in the plugin that makes the client's world less
solid than the real one, which is the direction that caused the stuck-walking bug in the ring
pads, so it is kept to a moment and `gate-arrival-splash-ticks: 0` turns it off.

### Fixes

Shutdown logged one "Saved gate to YAML" line per gate, at INFO, every time the server
stopped -- whether or not that gate had changed. On a server with a few dozen gates that is
a few dozen identical lines on every restart, forever. The per-gate confirmation is now
FINE-level diagnostic noise instead of an INFO-level event, and shutdown prints one summary
line ("Saved N gates to disk") rather than one per gate. Every gate is still rewritten
unconditionally on a clean shutdown -- that safety net is unchanged, only its logging is
quieter.

A closed gate could keep showing its portal. The portal is a drawing in each nearby client's
copy of the chunk, and closing one only tells whoever is within range at that moment. A player
who was elsewhere kept the picture -- and a client only discards a drawing when something
hands it a fresh copy of the chunk, which walking a short distance away and back does not do,
because the chunk never left. The result was water standing in a gate that was off.

The refresh that runs on chunk boundaries now takes portals back as well as drawing them. What
each player has been shown is remembered, so correcting a stale drawing costs only what was
actually drawn for them rather than a walk over every gate in the world. The real block is
read from the world rather than assumed to be air, so a gate that closed onto its default iris
is not swapped for one wrong picture instead of another.

### Material groups

Shapes describe geometry; `config.yml` describes palettes, selected by the material a gate
is actually built from. One shape file now builds as a Standard, Atlantis or Universe gate.
`StandardAtlantis.shape` and `StandardUniverse.shape` are removed — they were `Standard.shape`
with four lines changed.

### Storage

Gates are one YAML file each. HSQLDB and SQLite are gone, along with the storage backend
abstraction and the `mysql`/`postgres` options that were advertised but never implemented.

Custom gate materials are persisted by name rather than enum ordinal. Ordinals shift between
Minecraft versions, so a gate could previously come back a different colour.

### Permissions

Permission checks go through `Player.hasPermission()`, which every permissions plugin
implements, so LuckPerms and anything else Bukkit-compatible works with no glue. Operators
are granted every gate permission outright rather than by a switch listing each type, which
silently denied any type nobody remembered to add to it.

The legacy built-in permission system is removed: 378 lines that read and wrote a store
nothing consulted. Vault is used only for economy, and only if it is installed.

### Commands

One registry now drives dispatch, tab completion and help. Nine subcommands — `list`, `go`,
`remove`, `idc`, `compass`, `force`, `refresh`, `build`, `complete` — were offered by tab
completion and dispatched by nothing. `wooshdepth` and `restrict` worked but were never
suggested.

`/wormhole custom -clean` clears material overrides snapshotted by the old custom-mode
behaviour. `/wormhole restrict` no longer takes a group and count: that form silently
rewrote cooldown timers and read a stub that always returned -1.

### Performance

- The plugin bundles nothing and has no runtime dependencies. The jar went from 15.3 MB to
  under 300 KB.
- The entity sweep issues one spatial query per gate rather than one per portal block.
- Gate detection resolves a palette with a single map lookup, so palettes cost nothing.
- Movement handlers skip work when the block has not changed, and hot logging is guarded.

### Fixes

- Fixed an NPE on every move event for a gate that was activated but never dialled.
- Fixed the light-material command offering `GLOWING_REDSTONE_ORE`, which is not a material
  on 1.20.
- Exception handling no longer swallows `Error`, and anything that changes plugin state now
  reports when it fails.
- The portal is redrawn for players who arrive after the gate opened. It is painted onto
  each nearby client's copy of the chunk and was sent once, at open, to whoever was in range
  then — so a traveller stepped out of a wormhole into an empty frame, and anyone who
  reloaded a chunk lost it. Redrawn on arrival, join, respawn, world change and chunk
  crossing, across a window rather than a single tick, since a client returning from a long
  trip takes time to receive the chunk the portal is drawn on.
- Travellers can walk out of the gate they arrived at. Refusing entry means cancelling the
  move, which holds a player exactly where they are: correct for someone stepping in from
  outside, and a trap for the one who just arrived inside. They were held in the ring, told
  once per move that they could not enter an incoming wormhole, and eventually disconnected.
- Standing in a portal no longer gets a player kicked for flying. The client simulates the
  water it is shown and floats them; the server sees open air and calls that flight. Flight
  is allowed for as long as they are inside, and withdrawn on the way out from players it
  was granted to, so creative mode and other plugins keep what they gave.
- Arrival points that sat inside the portal are moved clear of it on load. Gates built
  before the exit was offset stored the old position, and loading restores what was stored
  rather than recomputing it, so no fix to the build path ever reached them.
- The version the server reports comes from the build. `plugin.yml` carried its own copy and
  had drifted, so a jar built as 1.1.0 announced itself as 1.0.0.

## 1.0.0 (2026-05-14)

### Bug fixes
- Fixed DHD activation to recognise all button material variants (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, bamboo, crimson, warped, stone, polished blackstone) via new `LegacyCompat.isButton()` helper.
- Fixed 2D gate exit location search in Nether/End dimensions: replaced `Material.AIR` equality check with `isAir()` so `CAVE_AIR` (present in Nether/End) is correctly treated as traversable.

### New features
- **Vault Economy integration** — optional gate use and build costs configurable via `economy-enabled`, `economy-use-cost`, and `economy-build-cost` in `config.yml`. Gracefully no-ops when Vault or an economy provider is absent.
- Added `LegacyCompat` utility class consolidating modern-Bukkit material/block compatibility shims.
- Added pluggable storage backend support and config keys (`storage-backend`, `storage-sqlite-path`, `storage-jdbc-*`).
- Implemented `StorageBackend` interface and `SqliteStorage` scaffold.
- Added `/wormhole storage` CLI: `backend` (set runtime backend) and `migrate` (DB -> YAML supported).
- Added `StorageMigrator` to export existing DB gates to per-gate YAML files (non-destructive by default).
- Added two new default gate shapes: `StandardAtlantis` and `StandardUniverse` (bundled in resources).

### Improvements
- `ConfigurationYAML` migration now excludes legacy permission keys and preserves storage keys when writing `config.yml`.
- Removed legacy `SimplePermission` support; Vault/LuckPerms recommended.
- Improved startup diagnostics and storage initialization logging.
- Fixed a number of persistence and teleport UX issues (teleport bounce mitigation and gate activation mapping fixes).

## 0.854 (5/17/11 @ 16:13 PST)

- Updated chunk (un)loading to happen when gate (de)activates and when dial lever state
  changes happen.

- Fixed iris levers not being added properly with 2d gates. (Oops, guess that code WAS needed)

- Fixed IndexOutOfBoundsException on 3d gate shapes without lighting blocks.

- Hamfisted fix for signs not updating. Now we nuke the sign and build it from scratch every
  time a gate sign is toggled. Causes a flash, but... who cares. It works EVERY time now.

## 0.853 (5/13/11 @ 23:05 PST)

- Fix for /dial gates breaking when a user who doesn't have dialer permission hit the lever.

- Switched to getTypeId() from getType(), hopefully this works around the getType() == Air bug
  that might be lingering. (doubt it :| )

## 0.852 (5/12/11 @ 07:55 PST)

- Added support for upcoming Permissions 3.0.x release.

- Fixed a NPE in 2d shape code dealing with light block positions.

- Added soft dependencies to plugin.yml.

- Minor log format changes for readability.

## 0.851 (5/10/11 @ 21:56 PST)

- Complete revamp of how we handle permissions checks. Lots more case statements, lots less
  if/else if. Much better. This is what enums are for.

- Added new gate use cooldowns. This feature will only work on complex permissions enabled
  servers. There are three groups you can assign a player to; 'wormhole.cooldown.groupone',
  'wormhole.cooldown.grouptwo', and 'wormhole.cooldown.groupthree'. If you have an '*' on any
  user/group, remember to '-wormhole.cooldown.groupone' etc. There is a new command to
  enable, disable, and modify cooldowns; 'wormhole cooldown [true|false|group] <time>', valid
  groups being 'one', 'two', and 'three', valid time being between 15 and 3600 seconds. There
  are also Settings.txt options for all of these new settings. Cooldowns are set when a player
  enters a stargate, not when they /dial. Cooldowns are removed via timer events, and even if
  the timer event fails, we do a fall back calculation when a player enters a stargate, and gets
  denied access.

- Added new gate build count restrictions. This feature will only work on complex permissions
  enabled servers. There are three groups you can assign a player to; 'wormhole.build.groupone',
  'wormhole.build.grouptwo', and 'wormhole.build.groupthree'. If you have an '*' on any
  user/group, remember to '-wormhole.build.groupone' etc. There is a new command to enable,
  disable, and modify build count restrictions; 'wormhole restrict [true|false|group] <count>',
  valid groups being 'one', 'two', and 'three', valid count being between 1 and 200. There are
  also Settings.txt options for all of these new settings.

- Updated the stargate sign dial sign reset code to be more reliable at causing the client to
  notice update changes.

- Added a thrown exception during stargate 3d shape parsing if the shape doesn't have an exit
  point. We depend on this location for pretty much everything. If it isn't there, really bad
  bad bad things happen.

- Now we have more than just Standard as our default shape. We extract Standard,
  StandardSignDial, Minimal, and MinimalSignDial to the gateShape folder if it is missing shapes.

- Now we don't toggle stargate signs when we start.

## 0.850 (5/5/11 @ 16:15 PST)

- iConomy support removed. I will not depend on plugins that decide to change their
  package name 5 major versions in and basically give everyone who depended on the
  package location the middle finger.

- 3d Gate shapes now implemented.

- Custom gate settings now in place (read: per gate material settings).
  This included the re-addition of the portalmaterial and irismaterial commands.
  lightmaterial, redstone, wooshdepth, and custom commands newly added.

- Massive internal overhaul, refactorings, cleanups, general goodness and bugfixes.

- Added support for Wormhole X-Treme Worlds. This allows Wormhole X-Treme to offload its
  chunk loading and world loading to WXW, for worlds that exist in WXW. Requires user to
  change Settings.txt option WORLDS_SUPPORT_ENABLED from false to true. This option requires
  Wormhole X-Treme Worlds v0.5 to be installed, and preferably configured for every existing
  world populated with stargates. If this option is set to true but WXW is not v0.5 (or not
  installed), WX will not load its stargates from its database.

- Removed many superfluous chunk load requests. Added graceful chunk unload queue when we
  are done with a chunk.

- Bumped supported version of permissions to include the 2.7 tree.

- Updated help text for new/modified commands.

- Added backwards compatibility, for those users who just don't want to upgrade to 3d shapes.

- Added loads of failsafe settings, for when users don't have any shapes installed, but have
  stargates already.

- wxidc now only works on non-sign powered gates which have iris activation blocks set.

## 0.833 (4/9/11 @ 23:36 PST)

- Fixed iConomy double(or many many more) charging issue. Tried to do something awesome,
  turned out to be a bad idea. We'll revisit these kind of changes when 3d shapes are in
  and I can do some major refactoring and method merges/splits.

- Bumped supported version of permissions to include the 2.6 tree.

- Merged some of the sign click schedule related methods. Should make sign click messages
  more reliable.

## 0.832 (4/5/11 @ 15:04 PST)

- Fixed NPE during database creation. Whoops, missing null-checks.

## 0.831 (4/4/11 @ 23:13 PST)

- Fixed erroneous messages sent when a plugin is attached to already and WXT receives
  a plugin event for it. Cosmetic bug, fixed.


## 0.830 (4/4/11 @ 01:12 PST)

- Water now will not flow over Stargate anythings. No more broken levers and magic
  blocks of water floating in their place.

- Buckets now will no longer work with stargate anythings. No free water and lava.

- Minor optimizations and code cleanups.

- PORTAL_MATERIAL, IRIS_MATERIAL, STARGATE_MATERIAL, ACTIVE_MATERIAL are all part of gate shape now.
    - All configuration values associated with these are gone now.
    - Gate shapes without these default to
      PORTAL_MATERIAL = STATIONARY_WATER
      IRIS_MATERIAL = STONE
      STARGATE_MATERIAL = OBSIDIAN
      ACTIVE_MATERIAL = GLOWSTONE
    - Updated default gate shapes that come in the zip to include these new values.
    - See gate shape files for more details
    - Known bug: If you teleport from a gate with portal type lava, to a gate
      that is NOT lava, you will be burned once you reach the other side.

- Removed version 1 DB conversion because new design doesn't allow for it anymore.
    - For users this means if you are upgrading from version 0.3 or less to this
      version you will need to remake your gates.

- Fixed NPE in onPlayerInteract caused by event not reporting the block the interact
  event was associated with.

- Logic tweak in the find safe teleport code. Should be *safererer*

- Sign powered stargates now can only target other sign powered stargates.

- Fixed so that when coming from a lava portal stargate to a non-lava portal stargate
  fire damage is canceled still. No more nasty fire after a teleport.

- Added the logic back in to stop people from randomly teleporting when next to the lever
  of an active gate. The side effect is, when block.getType() fails, gates don't work.
  Its one or the other.

- Fixed /wxcomplete permission deny issue with stargates on public networks.

- Added ICONOMY_OWNER_EXEMPT option to Settings.txt with a default value of true. When
  true this option disables the charging of gate owners for using their own gates.

## 0.821 (3/30/11 @ 17:42 PST)

- Update version of iConomy we build against and test for.

- Fix NPE in old non-shape based gates.

- Added custom StargateTeleportEvent for MinecartMania as we nuke the minecarts before
  teleporting them.

- Refactored package to com.wormhole_xtreme.wormhole in anticipation of adding more
  stargate related projects.

## 0.820 (3/29/11 @ 17:31 PST)

- Initial support for CraftBukkit Build 600.

- Got rid of the stupid double error that people got by not reading the readme. Now when
  we parse settings.txt, if the value is integer for the iconomy settings, we change it
  to a double by simply dropping a .0 at the end of it. Problem solved.

- Lots of optimizations to the distance finding method we were using. Also fixes to the
  gate shape parsing code. (Thanks lirelent)

- Overhaul of the way we handle permissions internally. More unified approach to the
  actual permissions checks.

- Gate block protection should now be compatible with plugins like mcMMO. "Should" being
  the operating word.

- Levers now properly move when used and toggle on and off when stargate
  and iris are activated.
  
- All Permissions deny events now log at Level.FINE. Got permissions problems with WXT?
  Now see what is happening.
  
- Optimized fire protection. Now we use timer events that go off 2 seconds after gates 
  close. This way we don't have to listen for fire type events 24/7. :)
  
- Fixed teleportation dropping people into very unsafe locations. Now we scan for safe
  place to drop people, if we can't find one we drop the player in front of the DHD. 
  This will also FIX wormholes by setting the stored teleport location to the new clean
  and safe location. 
  
- We now support using the help plugin along with WXT. 

- We now have settings.txt options to hard disable support of iConomy, Permissions, and
  the help plugin. No longer do we log a warning when unable to find the plugin we depend
  on. We log at INFO. :P
  
- Buttons are no longer really used. If a button exists on a stargate, it will be replaced
  with a shiny new lever on the first use. 

- Lava stargates are *really* safe to use now. For trees even.

- A whole host of debugging information has been added at Level.FINE. If you can trigger
  a bug reliably, set yourself to fine and provide the server.log details surrounding the
  bug. Not recomended for production servers as well... its exceissive.
  
- the '/wormhole regenerate' command is partially added. Will regenerate missing activation
  and iris levers.
  
- Minecarts work across chunks and worlds now! If you run into a location where it doesn't work,
  use the wormhole in both directions to correct the wormhole, then try again. :)

## 0.812 (3/23/11 @ 15:17 PST)

- /wxgo now works properly when traversing world bounderies. First we quickly pop into
  the default spawn location for the target world, then from there we go to our final
  destination. It is a hack, but it is a working hack. :)
  
- methodized the code to find closest stargates, and find distance from closest stargate
  blocks as well as the math to find distance.
  
- Updated block ignition events to only use proximity style checks. Block ignition event
  cancellation radius increased to active stargate woosh depth or 4 blocks, which ever is 
  further.
  
- Updated '/wxcompass' to use new FindClosestStargate method.

- Updated onEntityDamage to use only proximity style checks. On active gates a bubble of 
  no fire damage of either woosh_depth or 4 blocks, which ever is larger, is created. On
  closed gates a bubble of 2 blocks is created to stop fire ticks occuring right as a 
  gate closes. Stopped caring about potential drowning in stargate. If user decides they
  want to stand in the wrong side of a gate till they drown, that is their choice. 
  
- Re-added missing CONSTRUCT_NAME_TAKEN error string in ConfigManager. This stops an NPE
  in 'wxcomplete'.
  
- Added support for tkelly's Help plugin. Will generate proper config based on permissions
  type (simple or complex) or lack of permissions plugin altogether. 

- Refactored the heck out of iConomy and Permissions support. Own classes in a new package
  to go along with the Help support. Methodized a bunch of useful functions. Less
  duplicated code.
  
- Added some log output for 'wxforce'. Should help combat abuse.

- Ops are now always able to use 'wxremove'.

- The '/wormhole simple' command now refreshes Help entries to the proper permissions after
  being set.


## 0.811 (3/21/11 @ 20:27 PST)

- Came up with a proximity based check for stargates in the lava & fire event
  cancellation code. Now only 1 block radius around active lava portals
  gets its lava & fire events cancelled. STATIONARY_LAVA is safe for players
  to use in portals now. For reals.
  
- Version 4.5 of iConomy is now supported and verified as working.

- Now we actually check for Iris on gate use while in minecart...

- Back to the good ol kick the player out of the cart and stuff them through
  the stargate method. Doing a bit of a hackish teleport when going between 
  worlds as well. We tp to spawn, then instantly to destination. This is only
  when starting the tp while in minecart. If minecart is empty and passing
  into a stargate that will traverse worlds, we kick the minecart back. Otherwise
  it will dissapear into the void. 
  
- Now we cancel block ignite events on a proximity basis, same way we cancel 
  fire and lava events on player. No more trees bursting into flames near a stargate.
 

## 0.810 (3/20/11 @ 00:18 PST)

- Broke '/wxcompass' out into its own class. Removed '/wormhole compass'.

- Broke '/wxcomplete' out into its own class. Removed '/wormhole complete'.

- Broke '/wxidc' out into its own class.

- Broke '/wxremove' out into its own class. Removed '/wormhole remove'. 
  Fixed so it toggles iris to off state before removing gates with iris 
  active.
  
- Broke '/wxlist' out into its own class. Added no permissions error message.
  Removed from '/wormhole' command.

- Added command '/wxgo' and broke it out into its own class. Added no permissions 
  error message. Removed from '/wormhole' command.

- Broke '/dial' out into its own class.

- Broke '/wxbuild' out into its own class. 

- Broke '/wormhole' out into its own class.

- Added another message for active gates. Now it will say either remote activated, or 
  activated by someone else already. 

- Added SIMPLE_PERMISSIONS config option. The default value of 'false' makes permissions
  node settings use complex mode. While the setting of true sets the plugin to check for 
  extremely simplified permissions. Permission node details can be found in the README.
  
- Refactored the WXForce class to Force. Hopefully this shuts MSSE up. ^^;

- Moved a bunch of the initial loading out of onEnable and into onLoad. 
  Now we use onEnable only for events that should only happen at plugin Enable.
  
- Updated '/wormhole' command to have more descriptive errors and built in help. 
  Updated help information for this command as well. Command now has unified messaging
  string headers. Added new 'simple' option to enabling simple permissions while the game
  is live. Requires the user to have proper permissions node for configuration in target
  mode. Removed a bunch of duplicated permissions checks. Only one check is needed at 
  beginning of command call now. 
  
- Revamped readme to reflect important recent plugin package changes.

- Fire damage, combustion damage, and drown events now canceled in stargate. Now LAVA is 
  really a valid portal material. 
  
- Creeper explosions are now canceled when they would cause damage to stargates. This will
  stop signs and buttons from being destroyed during that mad dash to/from a stargate. ^^
  

## 0.801 (3/15/11 @ 22:33 PST)

- Update to the way data is pushed to signs in gate destruction and creation.
  Causes signs to update visually more reliably.
  
- Removed NPE during removal of sign gate if current sign gate's target doesn't
  have a gate target. 
  
- Initial addition of '/wxforce <gate|drop>' command, used to globally close all 
  gates and/or drop all irises temporarily (until they are dialed again). 
  Uses the 'wormhole.config' or 'wormhole.remove.all' permission nodes.
  
- Fixed '/wxremove' so that the permissions check doesn't fall through to the built
  in permissions check. :|
  
- Fixed permissions surrounding gate networks and WORMHOLE_USE_IS_TELEPORT

## 0.800 (3/9/11 @ 23:33 PST)

- Added pretty format messages! [dh/gyoza]

- Revamped the wormhole list and how it displays items. 

- Wormhole use cost of 0.0 no longer tells users that they were charged 0.0 when 
  using a gate. Also no longer bothers doing the iConomy calls with a 0.0 value.
  
- /wxbuild (/wormhole build) no longer blindly calls for permissions. This removes
  an NPE.
  
- /wxcompass (/wormhole compass) now has a permissions node. 'wormhole.use.compass'.
  Ops also can use the compass by default.

- New config value "WORMHOLE_USE_IS_TELEPORT"
    * Default is false (which doesn't change anything) (wormhole.use means a user
      can activate a gate, but others can still teleport form an active gate)
    * If set to true then users without wormhole.use will be unable to activate
      a gate OR TELEPORT from a gate.

- Fixed the gate active but no teleport bug (for real). 

- Fixed Iris to auto-open when dialing out. 
    * Dial gates iris will stay closed until actually connected.

- /wxcomplete properly checks for 'wormhole.network.build.NETNAME' permissions, 
  if permissions are enabled. 

- /dial now checks for 'wormhole.network.use.NETNAME' permissions if permissions 
    are enabled. The network 'Public' is always assumed to be just that. Public.
  
- /dial now properly kills timers associated with start gate when failing a dial.
  Instead of just the lights going out, and everything waiting for timers to finish.

- /wormhole go now has the permissions node 'wormhole.go'

- Check for WORMHOLE_USE_IS_TELEPORT in conjunction with 'wormhole.network.use.*'
  permissions node to disallow users who don't meet permissions requirements.

- Check for 'wormhole.network.use.*' permissions node on stargate activation 
  button/lever toggle.
  
- LAPIS_BLOCK is now an allowed Iris material.

- Properly tag gate sign dial signs with network gate was on at removal/break time.

- New command "/wxidc <gatename> <optional_set_idc>". The set can be "-clear" which 
  will clear the IDC. This command is available to OPs, wormhole.config, the console, 
  and the owner of the gate.


## 0.755 (3/4/11 @ 16:51 PST)

- Added /wxbuild, /wxlist, and /wxremove commands as short form of their /wormhole 
  counterparts.
  
- Added /wxcompass and /wxcomplete.

- /wormhole complete and /wxcomplete optional arguments require key=value
  * To add IDC you do idc=<value>
  * To add a network you would do net=<value>
  * Example: /wxcomplete MyGate idc=Haha net=Awesome

- Fixed bug in database code which was pushing data to wrong fields.

- Minor rework in PlayerListener code pretaining to players entering stargates.

- No longer scream about iConomy 4.2 or 4.3 being unsupported.

- No longer scream about Permissions 2.5.x being unsupported.

- Reduced potential thread safety issue when accessing Iconomy.

- Lots of logging added at Level.FINE and Level.FINEST for stargate operations. 
  Most useful for debugging.
  
- Closed a potential file descriptor leak in configuration code.



## 0.754 (3/3/11 @ 00:54 PST)

- Iris activation levers are now destroyed when a stargate is removed. Also if
  anything is in the iris activation lever block location when creating an 
  ICD protected gate it will be destroyed properly before lever is placed.
  
- We now properly destroy all name signs before replacing them with new name sign.
  Was not checking before causing a NPE. 

- Fixed '/wormhole build <gateshape>' to use the proper argument for <gateshape>
  This of course means that users with 'wormhole.build' permissions can use 
  '/wormhole build <gateshape>' after building a DHD, and press the button to 
  instantly generate a StarGate in the shape specified.
  
- Levers got the same treatment as signs now. No more wacky floating levers on
  idc enabled gates. Lever creation code broken out into own method. Stargate
  regeneration command is going to use this.

## 0.753 (3/2/11 @ 19:27 PST)

- Fixed Gate Sign placement. Now no longer at right angles to the gatesign block.

- Updated serverListener onPluginEnabled checks for iConomy and Permissions to
  only go off if the plugin is not already bound to. No more multiple
  notifications about attaching to Permissions. Also fixed a minor casting issue
  in the Permissions plugins attach section of onPluginEnabled. No more casting 
  error.
  
- Error messages for onEnable checks for iConomy and Permissions should be a bit 
  useful now as we may have been loaded before Permissions.

## 0.752 (2/28/11 @ 20:57 PST)

- Initial iConomy 4.1 Support 

- Initial Permissions 2.5 Support (should just be drop in)

- Fixed Stargate destruction detection. Removed blockDamageEvent detection and
  added blockBreakEvent detection in its place.

- Removed dead/unused code.

## 0.751 (2/25/11 @ 07:53 PST)

- Initial support for iconomy 3.0

## 0.750 (2/22/11 @ 22:19 PST)

- Major refactoring and package name changes.

- Initial support for minecraft 1.3.

 
## 0.741 (2/21/11 @ 21:48 PST)

- Stopped using playerListener for commands. Use the new onCommand structure. 
  This puts us as fully onCommand compliant for when they decide to put nags 
  about how horrible the coders are who are using the examples previously given 
  to them by bukkit. ^^
  
- Bumped up our BLOCK_PHYSICS and BLOCK_FLOW priorities to Highest. When other 
  plugins cancel these for us really bad things happen. (NPE)
  Bumped our PLAYER_MOVE up to High. Once again, other plugins canceling these 
  events causes us to break in most interesting ways. (NPE)
  
- Stopped listening on PLAYER_QUIT. We never did anything with it anyways. 
  No need to hold the resources.
  
- getLogLevel() added to ConfigManager for getting Log Level from the config. 
  getLevel() added to Settings for pulling the Level data from the ConfigKeys.



## 0.740 (2/21/11 @ 05:45 PST)

- Initial revamp of config system. Now with 100% less chances of a dereference 
  based NPE. EVERYTHING is checked for a null. Hard coded defaults. Will update
  this to use the defaults used to generate default conf file next config push.
  
- Minor logic changes in block listener code.

- Fixed sign not being breakable? Again?

- Broke DeleteBlocks() out into DeleteGateBlocks(), DeletePortalBlocks(), 
  DeleteNameBlock(), and DeleteTeleportSignBlock(). Allows use of these 
  functions in other commands individually.
  
- Added DeleteNameSign() for deleting the name sign. This gets called when using
  "/wormhole remove" and when destroying the gate by hand. No more magic free 
  signs when destroying stargates. Still no cure for free signs when destroying
  just the Name sign.
  
- DeleteTeleportSign() and ResetTeleportSign() added. Delete nukes the sign 
  altogether. Reset sets the name on it back to the old name of the Stargate and
  wipes all other lines of text. No more gates accidentally named -gatename-.


## 0.736 (2/20/11 @ 03:20 MST)
- Added configurable WOOSH_DEPTH for custom gate shapes.
- Updated to work properly with latest bukkit onEntityDamage changes.
- Stargates should no longer linger in half living states after a server shutdown while stargates are active.
  On onDisable we close all gates properly. onEnable we check and close gates again, just in case.
- Woosh Depth changes require user to remove one stargate and re-add it to set the woosh_depth properly. Any attempt to start up a stargate
  before doing this will cause a NPE. Also recommended users update their .shape files with 'WOOSH_DEPTH=3', or something similar, before doing this 
  (or conversely, remove their GateShapes folder).
- Fixed stray permission 'wormhole.use' which was causing problems. Permission was broken into two permissions few releases back. 
  'wormhole.use.dialer' and 'wormhole.use.sign' or 'wormhole.use.*' Also made it so players without 'wormhole.use.sign' can not change
  wormhole sign destinations.

## 0.735 (2/19/11 @ 03:40 PST)
- Modified Standard.shape to have 7 chevrons that light up.
- Modified shape parsing code to support an [O:E:L:S] block in the gate design.
- Rewrote logger setup. Now there is a config option for LOG_LEVEL. Uses Java logging.Level log levels.
  Only directly effects the server log file. Allows for some extra debugging of gate shapes. Defaults to INFO. 
  Directly effects the minimum level of logging in Bukkit, which at current is set to 'null'. Should have no effect on
  anything else. This is only able to be set at startup time.
- Updated gate destruction mechanics so if the gate is in a lit state but not portaling when it is destroyed, it flips back to
  an off visual state and disables all times associated with the gate. The act of turning off the lighting means that if the block
  that was destroyed was a lit block, it will be replaced with the initial material.
- DHD no longer lights up thus allowing the DHD to be properly destroyed allowing for one way gates.
- General code cleanups.
- Updated gate splash effect to use whatever PORTAL_MATERIAL is set to. Beware with STATIONARY_LAVA. It is LAVA. It hurts. BAD. Like REALLY BAD.
  BURNS BURNS BURNS. You have been WARNED. 
- Consider this build a Beta? 

## 0.730 (2/16/11 @ 20:40 MST)
- Added custom gate shapes!
      After starting server there should be a new directory and file plugins/WormholeXTreme/GateShapes/Standard.shape
      You can look in that file to see how to make custom gates.
- Flipping the Iris switch on a gate that is locked but not active will no longer create a false event horizon.
- The DHD on outbound dialing non-signed gates activates and deactivates properly with lightstone block effect and proper messages again.
  No more instant re-activating, must DHD must be deactivated first (or time out) before reactivation. 
- Small code cleanups and dead code removal (or commenting)
- Added '/wormhole irismaterial' command and associated it with the 'wormhole.config' permission.
  Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK. This is a global command. 
- Fixed gate light deactivation in conjunction with the inactive timer and the /dial command.
- Added 'wormhole.list' permissions node. 
- New gate design Minimal
- Added initial gate shape debugging output. 
- Added optional extra paramater to remove - /wormhole remove <name> all (the all is optional)
        If 'all' is specified at the end, the remove will also remove all blocks associated with the gate (other than the DHD)

## 0.727 (2/16/11 @ 13:39 PST)
- Made gate connection logic a bit more reliable with regards to scheduling system.
  Gates Now will never open in a half working state. If one doesn't open, neither will stay open.
  If scheduling for timed gate closings fails, try again, if that fails cry in the log and don't open 
  infinite time wormhole.
- Fixed iris causing inability to use a gate due to lack of wormhole event horizon (water) which is 
  destroyed by the iris closing/open
- Unstable wormholes event horizons are contained by Titanium/Trinium iris. (No splash/woosh through iris)
- Iris material now user configurable via Settings.txt. Defaults to STONE.
- Iris now unable to be broken, even if player is able to destroy wormhole. Use the lever to remove the iris.
  This stops accidental stargate breaking.
- Stargate will now deactivate and drain if it is active when any of its blocks are destroyed. This should stop the flooding issue.
In Progress/Partially working:
- Debug logs have been added for the gate scheduling system and will be added for most major break points. 
  Logs will take configuration setting allowing user to set the level of logging from the config file.
  Currently prettyLog only understands info, warning, and critical. Any call to it for anything else 
  like using 0 or 4 or 400 will result in an info log.
- Changed permissions for wormhole use and removal:
    Note : this means that you *MUST* change permissions or it won't work!
    wormhole.use.sign - lets a user use sign gates.
    wormhole.use.dialer - lets a user use /dial gates
    wormhole.remove.own - lets a user remove a gate that they own.
    wormhole.remove.all - lets a user remove any gate
- Woosh has been added back into the plugin
    There is a new configuration value that lets you disable this if desired.    

## 0.726 (2/15/11 @ 05:40 PST)
- Log output has been totally revamped. Now it should be easier to tell what we are doing in the logs.
- Fixed the ability to use the craftbukkit /reload command without giving an NPE.
- Removed a few potential file descriptor, memory, and database performance leaks.


## 0.725 (2/13/11 @ 21:15 MST)
- Iris and IDC is now completed for non-sign gates!
- When completing the gate you use /wormhole complete <name> <idc> to set the iris deactivation code.
- A lever will appear below the activation button.
- Pressing the lever will toggle the Iris on and off.
- When the Iris is active people will be unable to come through the gate!
- When dialing a remote gate you can optionally type "/dial <name> <idc>" if the IDC is correct you will deactivate the remote iris.
- Gates will not light up when active.
- Using double quotes in commands now works!
	Examples:
	/wormhole complete "A Fun Gate"
	/dial "A Fun Gate"
	/wormhole remove "A Fun Gate"

## 0.71 (2/12/11 @ 22:42 MST)
- Fixed an error message to be more specific (when trying to dial from an activated gate).
- Fixed problems with TickNextTick exception (needed to use sync instead of async).
- This may cause more issues though until the underlying issue is fixed in bukkit.
- This should be *more* stable than what was done before, but isn't guaranteed to be 100% perfect.
  Edit: Looks like the fix has been pulled into the repo - just need the next build and we should be good!
- Hitting the activate button again after you have previously hit it, but before you dial something will now 'deactivate' the gate.
- Due to these big issues the handling of double quotes in names has been pushed off for another day or two, sorry for everyone with that issue!

## 0.70 (2/12/11 @ 04:10 MST)
- Fixed multi-worlds!!!! GO AND EXPLORE THE MULTIVERSE!
- TIMEOUT_SHUTDOWN now works properly. Feel free to set this to something other than 0!!
- Gates now have owners
- First thing I am doing with owners is allowing owners to charge a percent of the iConomy cost
- After running this new version once you will see a new configuration value available.
- Settings are no longer overwritten on version upgrade 
- Logging is now done properly thanks to Alron @ github.
- There are some other small fixes I just don't remember them all right now.

## 0.67 (2/7/11 @ 18:57 MST)
- Version 0.66 is dumb, so I had to fix it up.
- Fixed the saving bug.
- Fixed the "gate timed out" message from repeating. FINALLY!!!
- You really want to get this version. It is much better than 65 and 66

## 0.66 (2/7/11 @ 15:31 MST)
- It just isn't a release without a bug....
- This release fixes a small bug from the last release where wormholes fail to save properly after shutting down the server.
- You DO NOT WANT TO UPGRADE TO THIS!!!!

## 0.65 (2/7/11 @ 12:10 MST)
- Wormhole now supports multiple worlds! (and works for the newest bukkit builds). Requires craftbukkit version 271+
- Wormhole sign targets are now stored across restarts
  This means that if you target a gate at a gate via the sign, then destroy the sign, the gate will always target the last targetted gate (Across restarts!)
- Wormholes that are active on server restart will remain active when restarted.
- IDC(Iris Deactivation Code)s now work! (only for non-sign gates)
  When creating a gate you can type /wormhole create <name> <idc>
  To dial a gate with an IDC you type /dial <name> <idc>
  Currently if there is an IDC set it will always be active (later you will be able to disable).
- Network can now be specified for a sign gate!
  When creating a signed gate the first line is the gate name, the second is the network name.
  If nothing is put in second line gates will default to the "Public" network.
  The sign will only cycle through gates with the same network !

## 0.62 (2/5/11 @ 10:37 MST)
- Fixed being unable to dial after newly creating a non-sign gate.

## 0.61 (2/4/11 @ 15:41 MST)
- Stupid copy/paste on an error loading iConomy fixed.

## 0.60 (2/4/11 @ 15:02 MST)
- Added configuration file
  Restart the server once and the file will be created, including your old settings.
  Changes to configuration require restart to go into effect (of course)
  Any changes made while the server is running will be written to the file on shutdown.
- Improved performance over previous fix for CraftBukkit.
- Fixed a few small issues
- Sign dialer should now be set and no longer have "No Other Gates" after a restart.
- Chunks should now be saved when server is shutdown 
- Download for jar file is now available at GitHub as per requested by several users.
  This is the jar file only!
- iConomy support!!!!
  Configuration file will have the values for this:
  Use cost: how much it costs to use a wormhole (cost to actually teleport, activating the gate is free. This should stop people from running into a gate someone else opened.)
  Build cost: How much it costs to build a wormhole.
  Ops Exempt: if set to true Ops will not be charged to use/build gates.
  I can add/change configurations as needed, but hopefully this is a good start.

## 0.59 (2/2/11 @ 00:07 MST)
- Quick patch to fix the issues with the newest version of CraftBukkit (231)

## 0.55 (2/1/11 @ 13:10 MST)
- Fixed some debug messages that shouldn't have been left in.
- Added /wormhole compass (Make compass point to nearest wormhole)
  Typing again will recalculate nearest wormhole.
In Progess:
- iConomy will be the next release, I just needed to remove those debug messages.

## 0.51 (1/31/11 @ 11:22 MST)
- Fixed the spamming of timeout message.
- Added config "/wormhole activate_timeout" to change the default timeout after activating a wormhole before it dials.
- Changed config name of timeout "/wormhole timeout" to "/wormhole shutdown_timeout"
  So now there is the activation timeout (activate_timeout) and the timeout after dialing when the gate needs to shutdown (/wormhole shutdown_timeout).
  Default value for shutdown_timeout is now 0 to reduce the instances of people falling through the earth.
- Added an OP only command (to go along with listing, you probably want to be able to see where the gate actually is)
  /wormhole go <NAME>
  This will instantly teleport you to the gate so know where it is.
  This was actually in 0.39 but I have been including the source in the JAR now and you can see my source at GitHub
  Feel free to comment and suggest changes in code if you want to.
  Feel free to also make changes on your own and send them to me via GitHub - I will integrate the changes if I like them!

## 0.50 (1/30/11)
- Minecarts now can go through wormholes.
  If a player is in the cart they will go through the gate but will not be in the cart when they arrive at the other side
  I would not recommend riding carts through the gate yet until I can figure out how to keep the player in the cart on the other side. Sometimes riding a cart through the gate results in weird behavior.
- Activated but not dialed gates now timeout after 60 seconds
- Fixed a bug with portal material of WATER (it can only be STATIONARY_WATER)
  It will automatically update any WATER to STATIONARY_WATER.
  I only added the STATIONARY_LAVA as a material on a whim - but when I found out people actually wanted to use it I tried to stop the damage but was unable.
  For now STATIONARY_LAVA is probably not your best option if you want to actually use the wormholes.
- Added command /wormhole list for Ops only. Lists all stargates on your server.
- Fixed a bug with portals being created out of thin air when you hit a button.
- Portals are now required to have AIR blocks for all blocks inside the gate when constructing.
- Possibly fixed issues with linux and DB
  changed the sql connection string to ./plugins/WormholeXTremeDB from plugins/WormholeXTremeDB
- I can't seem to find any issues with the newest craftbukkit (187)
Open issues:
- DHDs can't be repaired after removing a button/dialing sign on a sign-dialing gate (workaround is to /wormhole remove and then add the button/sign, and then it will function correctly.)

## 0.39 (1/27/11)
Added support for the Permissions plugin.
Without the plugin it defaults to the previous permission settings.
Changing the built in permissions will not change the Permissions plugin at all.
Permission nodes are as follows:
wormhole.use - Able to use wormholes
wormhole.build - Able to build new wormholes
wormhole.remove - Able to remove wormholes
wormhole.config - Able to configure settings like material and timeout. (By default Ops will be able to do this as well)
Fixed the weird names on the signs issue.
Part of fixing the falling through the world is pre-loading chunks
You will need bukkit v157 or newer!
Fixed a bug stopping the PORTAL material from working (Thanks Dinnerbone)
/wormhole timeout 0 should help STOP falling through the world.
I have tried to fix the point where you teleport when making a new gate. Hopefully this fixes it it.
Existing gates SHOULD start to work again unless you are really unlucky. If a gate just refuses to put you in the right place, a /wormhole remove <name> and then just add it again and it should be fine!

## 0.32 (1/26/11)
Again fixing small issue stopping gates from being built.

## 0.31 (1/26/11)
Forgot to change one last reference to /stargate (as all commands are now /wormhole)

## 0.3 (1/26/11)
Name change to "WORMHOLE EXTREME"!!!
To successfully use this rename and already had "Stargates" you will need to
Rename the "StargatesDB" and all files inside it to "WormholeXTremeDB"
Delete the old Stargates.jar
Fixed sign scrolling again.
Breaking the DHD will not remove the gate - it just makes it unable to dial out
Replacing the DHD will re-enable dialing.
Permissions are now fully working and stored
Use /wormhole perms for more detail
Configuration options are now available
/wormhole timeout (3-60) : # seconds to timeout wormhole
/wormhole material <MATERIAL> : Gets or sets your portal material (air, water, lava, etc)
Possible performance improvement using ConcurrentHashMap instead of locking and using HashMap.
Iris is still in progress 


## Notes

- This changelog is concise; include more details per commit when preparing releases.

