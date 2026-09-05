# Changelog

All notable changes to this project are documented in this file.

## 1.5.0 (unreleased)

### A closing iris no longer buries whoever is standing in the gate

Found reading the bug trackers of the two forks this one descends from, to see what we had
inherited. Reported in 2011 against lycano's fork and never fixed there; it is still true here.

Dial a gate whose far end has an iris, hand over the IDC so it opens for you, then walk into
the event horizon as the shutdown timer runs out. The iris re-engages while you are in the
opening and you are inside it. Nobody has to mistime anything -- the timer picks the moment.

`fillGateIris` set every portal block to the iris material and looked at nothing else:

```java
for (final Location bc : gate.getGatePortalBlocks())
{
    gate.getGateWorld().getBlockAt(...).setType(material);
}
```

That is not a stray case to guard. The iris has to be real, server-side blocks -- a
client-side one could be walked straight through -- so closing it is, unavoidably, placing
solid blocks over the whole opening. Every path that closes one could do this: the gate
shutting down onto an iris that defaults closed, an activation timing out, someone flipping
the lever, an IDC being cleared.

The opening is now checked for living entities first, and any found are moved to the gate's
own arrival point, which the shape file already places one block outside the portal, plus a
few ticks of damage immunity so the block landing behind them cannot still reach. The iris
still closes: one that could be held open by standing in it would be worth nothing. Dropped
items and arrows are left where they are, since they do not suffocate.

A traveller who had already arrived was never at risk here -- `EP` puts them outside the
portal by construction. It is the person walking *into* the horizon who was in it.

I first wrote the check the way the entity sweep does it, on the block the entity stands in,
because that sweep is the existing code for "who is in this wormhole." The test caught it. The
sweep is deciding who travels, and feet are the right question for that; this is deciding who
gets a block in the face, and the answer is the head. Someone whose feet are on the block below
the opening has their head in its lowest portal block, and the naive version walked right past
them. Both blocks are checked now.

The per-entity guard around the move catches `RuntimeException`, not `Throwable`. One mob that
cannot be moved should not stop the iris closing or stop the rest of the sweep, which is the
whole point of guarding each entity separately; a JVM error is not that, and swallowing it here
would have made a broken build look like an ordinary immovable mob. What it logs now names the
entity and the gate -- "failed to move entity" on a server running several irises said almost
nothing.

### `HorizontalSignDial` could not be built at all

Found reading the shape files while working out whether the DHD could become a type a gate
has, rather than something welded into the ring. Build a `HorizontalSignDial`, hang the dial
sign, click the button, and what came back was a plain `Horizontal` gate -- `/dial` only, no
redstone -- with its name sign painted over the dial sign you had just written.

Detection reads a shape's frame and portal cells and nothing else. Strip the markers and those
two shapes are the same file:

```
Horizontal                          HorizontalSignDial
[I][I][I][I][I][I][I]               [I][I][RD][I][RA][I][I]
[I][I][S:A][S:N:L#8][S:IA][I][I]    [I][I][S:A][S:D:L#8][S:IA][I][I]
```

So both matched the same build, and which one a player got came down to a tiebreak. The
tiebreak was `REDSTONE_ACTIVATED`, both of these declare `FALSE`, and below that it fell
through to whichever shape the registry handed back first. The registry is a
`ConcurrentHashMap` keyed by shape name. `Horizontal` hashes ahead of `HorizontalSignDial`, so
it won every time.

The same coin toss was deciding `Minimal` against `MinimalSignDial`, where it happened to land
the right way round. That is not a fact about either shape: adding a twelfth shape to the
folder resizes the table and reshuffles all of it, so a server could have lost that toss by
installing an unrelated custom shape. `StandardSignDial` and `EvenSignDial` were only ever
safe because they declare `REDSTONE_ACTIVATED=TRUE`.

Matches are ranked properly now, most significant test first: a shape that **found a dial
sign** beats one that cannot look for one, then `REDSTONE_ACTIVATED`, then the shape that
accounts for more of the frame, then the name. The sign is the honest signal -- only a shape
carrying `:D` ever looks for one, so finding one is proof of what the player actually built.

A gate with no sign hung is a plain `/dial` gate whichever of a pair wins, so the lower rules
are there to make that answer stable rather than to make it different. Nothing about what you
build changed, and gates already standing are unaffected -- they store their block positions
rather than re-reading the shape.

### Choosing a sign dial no longer costs you the iris

`StandardSignDial` and `EvenSignDial` could not have an iris. Not a lever that was awkward to
reach -- no iris lever at all, and no way to get one.

A shape says where the iris lever hangs with `:IA`. Leave it out and `setupIrisLever` works
the position out from the DHD button instead, but only for a shape that does not declare
`REDSTONE_ACTIVATED=TRUE`, which switches that fallback off. Those two did both: no `:IA`, and
the flag set.

`MinimalSignDial` was missing `:IA` too and survived only because it declares `FALSE`, so the
fallback covered for it. `HorizontalSignDial` always marked its own. Nothing in the files, the
README or the code ever said a sign gate should not have an iris -- and the frame block was
still there in all three, sitting directly under the button. Only the marker was missing:

```
StandardSignDial, before          after
[I][I][I][I][RA][S][S]            [I][I][I][I][RA][S:IA][S]
```

All eleven shipped shapes declare `:IA` now, so none of them is left depending on the fallback
guessing right. Nothing changes about what you build: that block was always part of the DHD.

An existing gate keeps what it was detected with, since the position is recorded once and
stored with the gate. `/wormhole refresh` and a click on the DHD button re-reads the shape and
picks the marker up, without touching a block of what you built.

### A gate's redstone wiring can be taken back up again

Reported in testing: a redstone block sitting on top of a DHD could not be removed. The
plugin answered a pickaxe with an instruction to remove the entire gate first.

A gate's `[RD]`, `[RS]` and `[RA]` cells are indexed as gate blocks, and they have to be -- a
redstone event arrives carrying only the block it fired on, and the index is how that finds
the gate it belongs to. Protection is keyed off the same index, so being findable also meant
being unbreakable.

That is backwards for these three specifically. They are the one part of a gate the plugin
expects a person to place, change and remove: it does not put the dust or the levers there,
it only says where they go. Everything else the index covers is genuinely gate structure and
still refuses a pickaxe with the same message as before.

Breaking one leaves the gate's stored position alone, which is what makes this safe to allow.
A signal landing near that cell later still works, because the check is against the position
rather than against whatever is standing in it.

### No sign colour ships as dark grey any more

Reported in testing: the dark greys are too dark to read.

`DARK_GRAY` is the dimmest colour Minecraft has that is not black, and sign text sits on wood
rather than on a dark background -- so a line in it reads as almost absent on a light sign and
genuinely absent on a dark one. It was the wrong instinct for "secondary text": the point of
dimming the network, owner and unselected destinations is that they should sit behind the
line that matters, not disappear.

All three are `GRAY` now, which still steps back from the coloured lines without vanishing.
Nothing in the shipped defaults uses `DARK_GRAY`.

Worth knowing when reading a bug report about this: a colour already written into a server's
`config.yml` is never overwritten by a changed default, so an install that ran an earlier
build keeps whatever it was given first. `/wormhole config sign-color-owner GRAY` and its
siblings change a running server immediately; changing the default only affects an install
that has never seen the key.

### Saving the config no longer destroys the rest of the file

Found chasing a report that config edits reset on reboot. They did, and that was the smaller
half of it.

Every clean shutdown persists the running configuration. That meant regenerating `config.yml`
from the flat setting list -- opening it truncating and writing back only the keys it knew
about. Everything else in the file went with it on every single restart: the whole nested
`gate-material-groups:` block, `permissions-support-disable` (skipped deliberately by the
writer, and therefore deleted), and every comment an admin had written.

Material-group discovery is what kept this from being obvious. It rebuilds groups from the
gate shapes on the next startup, so a server running stock palettes saw its file churn and
nothing worse. A group somebody had tuned by hand came back with discovered defaults instead
of their own portal, iris, light and sign choices -- once per restart, indefinitely.

Saving now edits the settings it owns in place and leaves every other line exactly as it found
it: nested blocks, comments, blank lines, commented-out settings, and keys it does not
recognise. A file that does not exist yet is still generated whole, which is the one case
where writing the entire thing is right. `permissions-support-disable` is still never written,
but that now means left alone rather than dropped.

The rule that does the work is that only a key starting in column zero is a setting. A
material group's own `sign:` entry is indented, so it can share a name with a top-level
setting without being overwritten by it -- which would have silently repainted every gate in
that palette. Removing that one condition fails
`anIndentedKeyIsNotTreatedAsASettingOfTheSameName`, which is what it is there for.

The tests run the real shipped `config.yml` through the same surgery a shutdown performs,
rather than a synthetic sample, because the block being lost is defined in that file and its
exact shape is what a hand-written example is most likely to get wrong.

**This also explains why editing `config.yml` on a running server never stuck.** The edit
changed the file, the shutdown rewrote the file from memory, and memory had never heard about
it. Editing while the server is stopped works; so does `/wormhole config <name> <value>`,
which changes the running value and writes it out immediately.

### One redstone circuit is one trigger, not several

Reported from in game: running dust past the button and up to the marker activated the gate
twice in rapid succession.

One circuit is not one event. Every dust block along a run fires its own `BlockRedstoneEvent`
as the signal propagates, a tick or so apart, and since the change above a gate answers to any
component touching its DHD as well as to its `[RD]` cell. So a single run legitimately powers
several blocks the gate is listening to, and each rising edge read as a separate press. The
synchronous "already open" guard does not catch it, because the events arrive in different
ticks and each one is a genuine new edge.

This was reachable before, in principle, with dust touching both the DHD and the marker.
Widening what counts as a trigger is what made it the normal case rather than a corner one.

A gate now ignores further triggers for 250ms after acting on one. Five ticks: longer than a
signal takes to cross the few blocks around a DHD, far shorter than anyone can deliberately
pulse a gate twice.

### A signal on an open gate extends it now, up to the existing maximum

Asked for while testing the above, and it is the third answer this has had. Originally a
trigger on an open gate closed it, which made repeated triggers useless -- a second cart shut
the wormhole the first one had opened. Then it did nothing at all, because re-dialling restarts
the shutdown timer and a cart every few seconds would have held the gate open for ever.

Doing nothing was safe by construction. Extending is safe for a different reason:
`max_open_seconds` already exists, already defaults to 300, and is already measured from when
the wormhole *first* opened rather than from the last dial. Nothing in the extension touches
that timestamp, so a signal can buy more time and cannot buy unlimited time -- once the maximum
is spent the gate closes on the next trigger regardless of how often it is poked.

It is deliberately not a re-dial: no chunk reload, no animation, no target lookup, no rebuilt
connection. It moves the shutdown task and nothing else. `redstone-extend-open-time: false`
restores a trigger on an open gate doing nothing.

The clamp that reconciles the shutdown timeout against the maximum was inline in the dial path
and is now `shutdownDelayTicks`, shared by both callers and tested directly. Writing those
tests turned up an edge the inline version had right by accident and would have been easy to
lose: integer division turns a few milliseconds of remaining maximum into zero ticks, and zero
means "no timer at all" everywhere else in this code -- so a gate a hair from its limit would
have been granted an indefinite stay by rounding.

### The sign colours were too loud

Reported as neon green with a white border on the selected gate, and light blue with white
around the gate name.

The white border is the glow. Glowing text draws a bright outline around every character, and
on top of an already-coloured line that reads as a halo -- it makes a sign carry further and
harder to actually read, which is the opposite of the point. It is off by default now, and the
colours carry the emphasis on their own.

The colours themselves came down a step as well: `DARK_AQUA` for a gate's name, `DARK_GREEN`
for the selected destination, and the two greys swapped so the dimmed lines sit further back.
All six remain config keys, so a server that liked the brighter set can put it back without a
new build -- and `sign-glowing-text: true` restores the glow for anyone whose gate room is dark
enough to want it.

### The dial sign is made to match the gate it belongs to

Following directly from the correction above. The dial sign is the one sign this plugin does
not place -- a player puts it on the `[D]` block in whatever wood they happened to be holding
-- so a themed gate ended up with an oak dial sign hanging on a crimson frame, right beside a
crimson name sign the plugin had placed itself.

It is now converted to the gate's own sign material when the gate is completed or regenerated.
`sign-dial-match-material: false` leaves a player's sign exactly as they placed it, for a
server that would rather the plugin did not replace a block someone else put down.

Changing a block's type wipes a sign's contents, so everything worth keeping is read first and
written back after: the text on both faces, whether each face glows, and the way the sign is
facing. The gate's cached sign state is replaced too -- it refers to the block as it was, and
every later write to the dial sign goes through it, so leaving the old one in place would have
aimed every destination update at a block that no longer existed.

Waxed state is deliberately neither read nor preserved, and this is the version boundary worth
recording rather than the feature. `Sign.isWaxed` and `setWaxed` do not exist before **1.20.4**
-- confirmed by disassembling the actual class in each jar, absent at 1.20 and present from
1.20.4 on. This project compiles against 1.20.4, so calling either would have compiled cleanly
and thrown `NoSuchMethodError` on a 1.20 or 1.20.1 server, invisible until one ran it. It costs
nothing to skip: the plugin rewrites the dial sign every time anyone clicks it, so a waxed dial
sign could never have worked as a dial sign in the first place.

Everything else the conversion touches was checked across the same range and is identical at
both ends: `getSide`, `Side.BACK`, `SignSide.setGlowingText`, and `Directional`.

### Signs are coloured now, and say which destination is selected

Both signs the plugin writes were plain white text. On a gate's name sign that was merely
plain; on a dial sign it was a real usability problem, because the destination you are about
to dial looked identical to the two either side of it. `>Name<` was the only thing
distinguishing the one a click would actually use.

The selected destination is now coloured against dimmed neighbours, and the gate's own name
is coloured on both signs. Glowing text was on by default here, on the reasoning that gate
rooms are dark and underground; in-game testing said otherwise and it now ships off, along
with softer colours -- see "The sign colours were too loud" above for what the defaults
actually are and why they changed.

Colours are named in config.yml rather than written as raw section-sign codes, following what
`Sounds` already established: a name is something an admin can read back and check, and a name
nobody recognises falls back to the default instead of putting a stray control character on a
sign, where there is nothing to be done about it short of breaking the gate. `MAGIC` and the
other non-colour formatting codes are refused for the same reason -- `ChatColor.valueOf` will
happily return one, and a destination rendered in MAGIC cannot be read at all.

The selected destination also keeps a pair of markers around it, now `»`/`«` rather than
`>`/`<`. Colour carries the distinction for most people; the markers are what carry it for a
colourblind player, or on a server that has turned the colours off.

One thing had to be fixed to make any of this safe. Detection reads line 0 of the dial sign as
the gate's name, and the plugin writes that same line itself once the gate is running -- so a
gate re-detected after being styled would have taken the colour codes into its own name, giving
it invisible characters in a name that has to be typed to dial it. Line 0 is now stripped of
formatting when it is read back.

### `SIGN_MATERIAL` never applied to the dial sign, whatever the README said

Noticed while doing the above. The README's shape-key table said `SIGN_MATERIAL=` was "the
wall-sign type used for the gate name sign and the dial sign". Only the first half is true:
`getEffectiveSignMaterial()` has exactly one caller, and it places the name sign. The dial sign
is put up by a player on the `[D]` block and detection accepts whatever wall sign is there.
The comment in the shipped `config.yml` had it right all along; only the README was wrong.

### A rider arrives facing where the cart is going

Also from in-game testing: riding a minecart through a gate left the traveller looking
whichever way they had been looking on the way in, rather than along the track they came out
on.

The arrival location has carried a yaw worked out from the exit velocity all along, but it was
only ever applied to the vehicle. A passenger's view is theirs and not the seat's -- teleporting
a cart does not turn the person in it, and neither does re-seating them.

What made this easy to miss is that the code looked as though it handled it. The reattachment
failure path teleports the passenger to the arrival location, yaw and all. So the view came out
right exactly when `addPassenger` had failed, and wrong the rest of the time. Riders are now
turned before being re-seated, on the path that actually runs, for boats as well as minecarts
since both reattach the same way.

### A gate's owner no longer turns into a UUID

Reported after refreshing a gate: the name sign came back showing the owner's UUID instead of
their name. Refresh was where it showed, and not where it happened.

`Stargate.getGateOwnerName()` falls back to the raw owner id when no display name has been
resolved, which is right for display -- an id beats showing nothing. The bug was that saving
used that same getter. A gate whose owner the server had not seen yet wrote its UUID into the
file's `OwnerName` field, and the next load then saw a non-empty name, took it for a real one,
and never ran the resolve-from-UUID path again. The id was the name from then on.

The sign made it look like refresh's doing. Signs are world state: the one on the gate had
been showing a correct name written back when the gate was built, while the field behind it
had been empty on every load since. Refresh rewrites the sign, so it was the first thing to
show what was actually stored.

Saving now writes the *stored* name and never the fallback, and loading treats a name equal to
the owner id as no name at all -- which is what heals the files already written that way,
rather than only stopping new ones. `refresh` copies the stored name too, for the same reason:
it was reading the fallback and writing it onto the gate it rebuilt, moments before saving.

The decision is two small functions, `ownerNameToSave` and `ownerNameFromSave`, so it could be
tested without a running server -- nothing in this suite can call `Bukkit.getOfflinePlayer`.
Worth knowing while reading them: a legacy gate whose owner genuinely *is* a player name trips
the "name equals owner" rule and is read as having no name. That is correct and needs no
special case -- the caller then fails to parse the owner as a UUID and sets the owner string as
the name, which for those gates is the right answer.

### The DHD takes redstone from any component now, not only from dust

Reported from in-game testing of the change above. Running dust to the block above the
activation block worked; running a repeater into the DHD itself did nothing, and there was no
way to tell those apart from in game -- to whoever built it, it is the same circuit.

The listener had two different rules for the same idea. `[RD]` accepted any redstone
component within a block of it -- dust, a repeater, a comparator, a torch, a lever, a rail --
which is what the README has always described. The DHD accepted `Material.REDSTONE_WIRE` and
nothing else, both for the block the button sits on and for the three monitored cells around
it. Anything else touching the DHD was silently ignored.

Both now use the same test the `[RD]` cell already used. This matters most on a gate sunk a
block into the ground for a flush entrance, which is how they tend to get built: the marker
cell ends up above head height, while the block below the button is at hand level and is the
obvious place to bring a signal.

One thing had to be excluded to make that safe, and the test for it is the reason it is here.
`[RA]` is a lever the plugin switches on itself the instant the gate opens, and on some shapes
it sits close enough to the DHD to be adjacent to it. A lever is a redstone component, so
widening the rule turned the gate's own output into an input to its own dial trigger --
confirmed by removing the exclusion and watching
`theGatesOwnActivatedLeverDoesNotDialItEvenWhenItTouchesTheDhd` fail. The shapes keep `[RA]`
clear of `[RD]` by geometry; a small shape cannot always do the same for the DHD, so this one
is a rule rather than a distance.

### Every sign gate takes redstone, not just the one with "Redstone" in its name

Whether a sign gate could be dialled by redstone depended on which of two similarly-named
files an admin happened to build from. `MinimalSignDial` could not; `MinimalSignDialRedstone`
could. Nothing said so at build time, the two are not even the same footprint -- the redstone
one is a block taller with the pillars further apart -- and the only way to find out you had
built the wrong one was to wire it up and watch nothing happen.

That was never a real choice about the gate. Redstone capability is not a property a shape
opts into: `StargateHelper` turns it on the moment a shape's geometry has an `[RD]` block, and
an admin who wants a sign that can only be clicked already has `/wormhole redstone <gate>
false` per gate. The shape file was offering a decision that belonged somewhere else, and
offering it as a permanent, unlabelled consequence of which file you picked.

So the rule is now uniform: a shape with a `[D]` dial sign carries `[RD]` and `[RA]`.
`StandardSignDial` and `EvenSignDial` already did. `MinimalSignDial` and `HorizontalSignDial`
have gained them, on cells those shapes already left empty, so the frame an admin builds is
unchanged. The seven shapes with no dial sign keep no markers at all, which is the same rule
read the other way: with no sign to leave preset, a pulse would have nothing to dial.

A gate already standing does not pick this up, and the README now says so. A gate records
where its redstone blocks are once, when it is first detected, and `GateSerializer` stores
those positions with it; nothing re-reads the shape afterwards. So a sign gate built before
its shape gained `[RD]` has no dial-activation block stored, and the listener's null check
means no amount of wiring will fire it -- `/wormhole redstone <gate> true` sets the flag but
cannot invent the position. `/wormhole refresh`, clicking the gate's DHD button, re-detects
the geometry from the current shape and re-registers the gate with it, keeping name, owner,
IDC and network and destroying nothing. That is the fix, and it already existed.

I had written the opposite first, on the strength of the frame being unchanged -- true, and
not the part that matters -- and then described the fix as removing and re-completing the
gate before finding that `refresh` already does exactly this, in place.

`[RS]`, the sign-cycling input, is gone from the shipped shapes entirely. The point of
redstone dialling is a sign left preset on a destination and a pulse that fires it, and an
input that moves the sign works against that. The parser and listener still handle `[RS]` for
custom shapes, and `StargateHelper` still drops one that lands adjacent to `[RD]`.

I first talked myself out of `[RA]` on `MinimalSignDial`, having convinced myself its
footprint had nowhere to put a lever that was not either on a frame block or within reach of
`[RD]`. That was wrong, and the existing shapes said so: `StandardSignDial` and `EvenSignDial`
both hang `[RA]` off the ground row beside the DHD pillar with nothing beneath it. Minimal has
that cell too.

`MinimalSignDialRedstone` is retired -- the gap it existed to fill is closed, and its name now
describes a distinction that no longer exists. It is dropped from the jar and from the list of
defaults, not deleted from anyone's server: defaults are only ever written when missing, so an
existing install keeps its copy and gates built from it keep working. Only new installs stop
seeing it.

One thing deliberately left alone. `REDSTONE_ACTIVATED=TRUE` looks like the switch for all of
this and is not -- `[RD]` is. What the flag still does is stop `StargateBlockSetup` from
auto-placing the iris lever, so setting it on `MinimalSignDial` for the sake of tidiness would
have quietly taken that shape's iris away for a reason having nothing to do with redstone.
Both shapes keep `REDSTONE_ACTIVATED=FALSE` and get their redstone from the marker.

### The woosh had two implementations; now it has one

`StargateAnimator.animateOpening` carried two entirely separate woosh animations, chosen
between by whether the gate had any authored woosh blocks. A shape with `:W#N` markers
replayed them through one state machine counting `gateAnimationStep3D`. A shape without them
ran a second one counting `gateAnimationStep2D`, deriving its waves by extruding the portal
face outward a step at a time, on its own hardcoded 4/8/3-tick delays.

That split was fork lineage, not design -- 3D gate support arrived as a separate branch and
the older animation was kept beside it rather than replaced. The cost was real and already
paid: the off-by-one that left a wave drawn inside the gate on every opening (fixed just
below) existed in one of the two and not the other, so no amount of care spent on the second
could ever have surfaced it.

Both now run through the same state machine. Two small functions are all that remains of the
difference: `wooshWaveCount` answers how many waves a gate has, and `wooshWave` answers what
is in one -- read from the shape when it authors them, derived by the identical outward
extrusion when it does not. `gateAnimationStep2D` and its accessors are gone.

Derived waves are computed on demand rather than stored at detection time. That was the
tempting version, and it would have meant persisting them: gate woosh locations are written
to disk once when a gate is detected, so a stored version would have grown every save file
and needed a re-detect before a depth change took effect. Deriving keeps it out of the save
format entirely and makes a changed depth apply on the very next opening.

One behaviour change worth naming: gates that took the old 2D path now honour
`getEffectiveWooshTicks()` like every other gate, instead of the hardcoded delays that path
used. Every shipped shape authors its own waves, so no stock gate is affected.

### `/wormhole wooshdepth` now says when it cannot change what you are looking at

Following directly from the above. The setting only ever fed the derived-wave path, and
every shipped shape authors its own waves -- so on an ordinary gate, setting a depth changed
no visuals whatsoever. It was not doing nothing (it still governs how far block and entity
protection reaches from the gate), but it was not doing the thing its name promises either,
and nothing said so.

It now tells you: setting or reading the depth on a gate whose shape defines its own woosh
waves prints a note saying the depth will not change how the gate looks, and what it does
still control. The alternative was renaming the command, which would break anyone's existing
scripts to fix a wording problem.

### Arrival ground correction is now the beam's job, not each caller's

Every beam corrects its destination for terrain that has drifted since the point was
recorded -- ground dug out or built up, which would otherwise strand a traveller hanging in
mid-air or buried in a block that had risen to meet them. That was already true, but it was
true by repetition: all four callers of `BeamAnimation.start` ran
`WorldUtils.findSafePlayerLocation` themselves, identically, on the line before calling in.

Four out of four getting it right is not a guarantee, it is a convention -- and the caller
that forgets is the one that drops somebody inside a wall. `BeamAnimation.start` now applies
the correction to every beam it runs, and the four call sites pass their stored point in
as-is.

Safe to fold inward because the correction is idempotent: it tries the exact stored spot
before searching, and re-centring an already-centred coordinate lands on the same block. A
caller that still snaps its own location first therefore loses nothing. That property is now
pinned by a test, since correctness depends on it and neither half of it is obvious from
reading the function.

The gate walk-through listener keeps its own separate call, deliberately -- it teleports
directly and never runs a beam sequence at all, so there is nothing there to inherit the
guarantee from.

### The kawoosh sounded like an explosion, not like water

`gate-sound-kawoosh` shipped as `block.end_portal.spawn`, and 1.4.0 was the first release in
which anyone actually heard it -- the sound had been wired up since 1.1.0 but never fired,
which is the bug that release fixed. What that fix revealed is that the sound was the wrong
one. `block.end_portal.spawn` is among the loudest samples the client owns and a low
resonant boom besides, and gate volume is set to 1.5 on purpose, because a gate is a
landmark you should hear from across a base. The two together made an opening gate the
loudest thing on a server, describing an event it sounds nothing like: a kawoosh is a
surge of water thrown out of the ring and falling back, not a detonation.

It is now `entity.player.splash.high_speed` -- the heavy splash of a body hitting water at
speed -- played at pitch 0.7 rather than its own. Dropping it that far lengthens and deepens
the sample until it reads as a far larger volume of water than one person, which is the
whole of the effect. It also puts the kawoosh in the same material as the hum that follows
it: the open wormhole has always been `ambient.underwater.loop`, so the gate now opens with
water and then keeps running with it, rather than opening with an explosion and then
inexplicably becoming a stream.

Nothing changed about volume or range. The complaint was the sample, and turning the volume
down to fix a sample would have quietly halved how far every *other* gate sound carries.

Two notes for anyone upgrading. A `config.yml` that already exists keeps whatever is written
in it -- defaults are only used for keys that are absent -- so set `gate-sound-kawoosh`
yourself or delete the line and let it be rewritten. And if you preferred the boom, it is
one config line away; the "sounds worth trying" table in the README now suggests
`item.trident.riptide_3` there instead, for a longer rush rather than a single burst.

Sound names are text the compiler never sees, so a new test walks every sound this plugin
ships and checks it is shaped like a sound event at all -- a dotted lowercase name rather
than a pasted-in `ENTITY_PLAYER_SPLASH_HIGH_SPEED` constant or a name with a stray capital
in it. It cannot ask whether the sound exists, because that is a registry lookup and a
registry needs a running server, but it catches the mistake that is actually easy to make
while editing this list. The failure it guards against is invisible: an unrecognised name is
silent, and silence is what a sound setting looks like when it is switched off deliberately.

### `/wormhole config` no longer accepts a value it cannot read back

```
> /wormhole config ring_default_style banana
RING_DEFAULT_STYLE is now banana.
```

It was not. `ConfigManager.applySetting` typed a value by what was already in the setting --
a boolean stayed a boolean, a number stayed a number, and anything else was stored exactly as
typed. Several settings are text with a closed set of valid values, and each of them reads
back through a getter that quietly substitutes a fallback when it cannot parse what it finds.
So the write succeeded, `getRingDefaultStyle()` went on answering `CONCURRENT`, and the only
message that could have said so said the opposite.

`RING_DEFAULT_ACCESS` was the same shape and worse in effect: it falls back to `PRIVATE`, so
a server owner who meant to publish rings and mistyped `public` was told they had. The ring
material, light and flash settings were unvalidated too, though the in-game
`/wormhole ring edit` commands had checked the identical values all along -- one path
enforced the rule, the other did not, on the same data.

`LOG_LEVEL` was the one with no safety net at all: `Setting.getLevel()` calls `Level.parse`
straight, uncaught and case-sensitive, so a mistyped level threw at every log call afterwards
-- from inside logging, which is a poor place to be the first to notice.

Values with a closed set are now checked before they are stored, and refused with the options
named. The rules live in a new `ParsedSetting`, which is pure -- no reading, no writing, no
config file -- because the refusals are the whole point and `applySetting` persists on the way
past, so there is no other way to see them.

Accepted values are stored canonically rather than as typed, and that is not tidiness.
`RingStyle.parse` accepts `slow` for `SEQUENTIAL` -- taken deliberately, so the two places a
style can be set agree on what a style is called -- while `getRingDefaultStyle()` reads the
stored text back with `valueOf`. Storing the word as typed would have made `slow` mean
`CONCURRENT`: this exact bug, reintroduced by the fix for it. Caught by reading the two
functions side by side rather than by testing, which is the only reason to write it down here
-- a rule that has to hold between a parser and a getter that never call each other is
exactly the kind that gets broken by someone changing one of them.

The block check moved to `MaterialUtils.isBlockOrUnknown` rather than being copied. Tab
completion already had it, along with the reason it is written the way it is: from 1.20.6 on
`Material.isBlock()` goes through the server's registry and throws if asked before the server
has finished starting, so it accepts rather than refuses when it cannot ask. On a live server
-- the only place either caller is reached by a player -- the registry is always there.

Sound names are deliberately still free text. Naming a sound instead of resolving it to a
`Sound` constant is what lets a resource pack's own sound through, and this plugin cannot know
those names; validating them would be a regression, not a fix. There is a test pinning that,
aimed at whoever tidies this up next.

### `/wormhole config` worked in English and not much else

Found in review of the change above, and larger than what it was found in. Every name in
that command was folded with a bare `toUpperCase()`, which uses whatever locale the JVM
happens to be running in. A Turkish JVM maps `i` to a dotted capital I (U+0130) rather than
to `I` -- so `ring_default_style` became `RİNG_DEFAULT_STYLE`, matched no setting, and the
command answered:

```
No setting called ring_default_style.
```

Most of this plugin's settings have an `i` somewhere in their name, so most of them were
unreachable on such a server. The name on screen looks perfectly correct, because the
mangling happens after it is read -- it reads as a typo that isn't one, which is why nobody
reported it.

`LOG_LEVEL` had the same fault in the value rather than the name, and would have refused a
level that is valid everywhere else. The setting search behind
`/wormhole config <partial name>` had it too, and so did the read-back of the two ring enums
when the value in config.yml was not already upper case.

All five now fold against `Locale.ROOT`. These names are fixed ASCII and were never the
server owner's language to begin with.

The rest of the plugin still folds case in the default locale in about seventy other places.
Those are a separate sweep, deliberately not swept up here.

### The rest of the plugin worked in English and not much else

The sweep the entry above deferred. Sixty-seven bare `toLowerCase()` / `toUpperCase()` calls
across twenty-two files now fold against `Locale.ROOT`, for the same reason the five in
`/wormhole config` already did: every one of them compares player text, a file's text, or an
enum's own name against a fixed ASCII identifier, and none of them was ever the server
owner's language.

The scope was written up beforehand as "not all sixty-seven are bugs" -- the reasoning being
that where both sides of a comparison go through the same fold they agree, so the prefix
matching behind tab completion was safe. That was wrong, and reading the code rather than the
pattern is what showed it. The two sides go through the same *function*, but not from the
same *kind* of input: one side is a literal spelled out in the source, the other is what a
player typed. Turkish lower-cases `I` to a dotless `ı` and leaves `i` alone, so `"LIST"` folds
to `lıst` while a typed `list` stays `list`. Folding both sides does not save a comparison
when only one side has an `I` in it to begin with.

So the failures are wider than the ones that got listed. On a Turkish server, before this:

```
> /wormhole RING
Invalid request.
```

`ring` and `config` are two of the five commands this plugin advertises, and both carry an
`i`. Typing either in upper case reached nothing. Tab completion had it in the other
direction -- typing `RI` and pressing tab offered nothing at all, because the folded prefix
no longer starts the word `ring`.

Below that: `gate edit <gate> IRIS <material>` answered "No such field"; the iris, light and
portal material commands refused every material with an `i` in it (`ice`, `iron_block`,
`brick_slab`); and `ring edit access private` was refused outright, the exact bug the entry
above fixed for `RING_DEFAULT_ACCESS`, still sitting in the sibling command that sets the
same value on a built pair.

Two were quieter than any of those. A `.shape` file's `SIGN_MATERIAL` line is resolved inside
a catch block that ignores unknown names, so a rejected material was not logged, not
reported, and not visible anywhere -- the shape just used its default sign as though the line
had never been written. And `config.yml`'s migration writer builds each key by lower-casing
the setting's own name, which on such a server produced `gate-sound-ırıs-open`: a key the
plugin then could not read back, written into the file by the plugin itself.

`RingStyle.parse` broke in the direction nobody would guess. It folds the typed text *and*
the enum constant's name, so `SEQUENTIAL` in upper case kept working -- both sides mangle
identically -- while ordinary lower-case `sequential` stopped matching, because only the
constant's name changes under the fold. The one spelling a player would actually type was the
one that failed.

Three of the sixty-seven were deleted rather than fixed. `Boolean.valueOf(x.toLowerCase())`
in the cooldown, restrict and redstone commands folds a value that `Boolean.parseBoolean`
already compares case-insensitively, behind a guard (`CommandUtilities.isBoolean`) that
already uses `equalsIgnoreCase`. Adding `Locale.ROOT` to a fold that should not exist would
have been the wrong repair. Those three now parse the argument as typed, and the two that
echo the result report the value that was actually set rather than a re-folded copy of the
input.

Beam destination names went the same way, and this one fixes nothing -- it is the only part
of the sweep that is consistency alone. Those names are player text rather than identifiers,
and are keyed in memory by their folded form, so the first reading of this was that a server
whose locale changed between runs would stop finding names written under the old fold.

Checking rather than assuming showed there is no such failure. `BeamYamlManager` persists
`destination.getName()`, the name as the player wrote it; the folded form is only ever a map
key, rebuilt from that stored name every time the file is read. Both halves of every lookup
are therefore folded by the same code in the same run, and nothing on disk is keyed at all.
The same is true of gate names, which were already folding against `Locale.ROOT` before this.

`Locale.ROOT` is still the right thing to write there -- it is what the other sixty-six now
say, and a reader should not have to work out which folds are load-bearing -- but it changes
no behaviour, and the entry above claimed a fix it does not make.

Six tests, all confirmed failing against the old code first, split by what they guard:
`CommandLookupLocaleTest` for finding a command, `IdentifierParsingLocaleTest` for reading an
identifier out of a file or an argument. Both set the default locale to `tr_TR` and put it
back afterwards, in the shape `SettingLookupLocaleTest` established -- Surefire runs
sequentially here, so swapping JVM-wide state is safe as long as it is restored.

569 tests pass.

### `/wormhole config` now takes the setting name as config.yml spells it

`config.yml` writes `gate-sound-kawoosh`. The settings are enum constants, so the command
wanted `gate_sound_kawoosh` and handed whatever was typed straight to `ConfigKeys.valueOf`.
Copying the key out of the file therefore got you:

```
> /wormhole config gate-sound-kawoosh entity.player.splash.high_speed
No setting called gate-sound-kawoosh.
```

Which reads as the setting having been removed, when the only difference is punctuation
between two words that nobody has reason to think is load-bearing. The file is the one place
a server owner is guaranteed to have seen the name, so it is the spelling they will type.

`settingKey` now folds a dash to an underscore before the lookup, and both spellings reach the
same setting. The search behind `/wormhole config <partial>` folds its fragment the same way
-- it had the same gap and failed more quietly, since an empty list reads as a server with no
such settings rather than as a name spelled the wrong way -- and tab completion takes a
hyphenated prefix, completing it to the underscored name the command echoes back.

The test walks every key this plugin writes into `config.yml` and asks the command for each
one, so the two spellings cannot drift apart again without something going red. Three of its
five assertions failed against the old code, on every hyphenated name.

Accepting either spelling is not accepting anything: `gate-sound-banana` is still not a
setting, and there is a test for that too.

### `/wormhole cooldown` reported a setting it never saved

A `ConfigKeys` constant only becomes a real setting by appearing in
`DefaultSettings.config`. That array is the only thing that ever populates the settings map,
and seven keys had accessors in `ConfigManager` without an entry in it.

The effect was invisible from both ends. `setConfigValue` checks `isConfigurationKey` first
and returns quietly when it is false, so the setter discarded the write. The matching getter
is written as `isConfigurationKey(...) ? getSetting(...) : literal`, so it always took the
literal branch and answered a hardcoded constant. The key never reached `config.yml` either,
so there was no editing the file to work around it.

So `/wormhole cooldown one 300` printed `Wormhole cooldown time set to: 300` and the cooldown
stayed at 120 seconds, on every server, since the groups were introduced.

Only group one was ever read -- by `StargateRestrictions`, for the actual wait -- and groups
two and three by nothing at all. The three collapse to one registered `use-cooldown-seconds`,
which is also the shape beaming already uses with `beam-use-cooldown-seconds`. The command now
takes a plain number of seconds:

```
/wormhole cooldown 300        # was: /wormhole cooldown one 300
/wormhole cooldown true       # unchanged
```

Its confirmation line reads the value back through the getter rather than echoing the argument,
so the message is now evidence the write landed instead of a restatement of what was typed --
which is precisely what the old one got wrong. Anyone with the group form in a script is told
the groups are gone rather than being quietly ignored.

Making the setting real is what first put an arbitrary number into the scheduler. The wait is
converted to ticks for `scheduleSyncDelayedTask`, which takes an `int`, and
`(int) (seconds * 20L)` wraps past 107,374,182 seconds -- coming back negative, which Bukkit
runs on the next tick. A cooldown set absurdly long would have cleared itself immediately: the
exact inverse of what was asked for, and silent, since a task firing early looks nothing like
an arithmetic fault. That was unreachable while the value was a hardcoded 120 no file could
change, so it arrives as a bug in the same change that fixes the setting. `cooldownTicks`
saturates instead, and treats a negative wait as no wait.

The first version of that guard multiplied and then checked the product, which is wrong for the
same reason at one remove: the product overflows `long` as well, so a large enough value came
back negative from the very comparison meant to catch it. The test found it -- the case with
`Long.MAX_VALUE / 2` in it failed on the first run. It now tests the input against the limit
before multiplying.

### `/wormhole restrict` was three layers of doing nothing

The same fault, further along. `BUILD_RESTRICTION_ENABLED` was unregistered, so the setter
discarded the write as above. Nothing read the value back. And
`StargateRestrictions.isPlayerBuildRestricted` had already been reduced to `return false` when
the feature was removed, so all four of its call sites were constants and the "you are at your
max number of built gates" message was unreachable.

Any one of those three would have made the command inert; it had all three. What is left of
build restriction is now deleted, including the unreachable message and the always-true guards
that were reading as though they still decided something.

The subcommand name stays dispatchable -- this registry's standing rule is that a name in
someone's command block keeps working -- but it now says the feature is gone instead of
claiming to set it. Gate building is governed by the `wormhole.build` permission.

The new test is the structural guard rather than a test of either command: every `ConfigKeys`
constant must be registered, so adding a setting and forgetting `DefaultSettings` reintroduces
exactly this for whatever the new setting is. Run against the previous commit it fails listing
all seven broken keys.

### The three material commands were one command copied twice

`PortalMaterialCommand`, `IrisMaterialCommand` and `LightMaterialCommand` were 97 lines each
and identical but for a material whitelist, a display noun, and one getter/setter pair. Each
also stated its whitelist twice over -- once as a chain of `==` comparisons, once as English
spread across three or four message strings -- with nothing holding the two together.

They had already drifted. The iris variant printed its valid-material line under `normalHeader`
in two of the four places the other two used `errorHeader`, so the same advice arrived in a
different colour depending on which of the three you had typed and how you had got it wrong.
Nothing was broken by that. It is just what happens to three copies given time.

`MaterialCommand` takes a `Kind`, and the set in `Kind` is now the only statement of what a
material may be: the membership test reads it, the sentence listing the options is generated
from it, and so is tab completion. That last one is new -- none of the three offered material
completion before, because a hand-written list in the completer would have been a fourth copy
to keep in step.

Behaviour is otherwise unchanged, apart from the iris colour inconsistency now matching the
other two. One small thing did go: an unrecognised material name is no longer logged at `FINE`.
That is a player typing, not an event, and they are already told what would have worked.

The test transcribes each accepted set from the deleted files rather than from the new enum --
copying the new values across would make it agree with whatever the code says. It also sets
through one `Kind` and reads back through all three, which is what catches an entry wired to
another's accessor: the one mistake this refactor could make that still compiles. Mis-wiring
`IRIS` to the portal accessors on purpose fails it with that message.

### Twelve dead dispatch stubs

`Wormhole.java` carried twelve private `do*` methods, each a single line delegating to a handler
in `command/handlers`, and every one of them was called from nowhere. Dispatch had moved into
`SubCommands` -- now the one place a `/wormhole` subcommand is declared -- and the `if`/`else`
chain that used to call these went with it. An orphaned `Do simple permissions.` javadoc with no
method under it had been sitting there too. The class is 246 lines shorter and keeps `onCommand`,
which is the only part Bukkit ever calls.

### A gate named `Cafe` with an accent on the e no longer becomes `Caf?` on restart

Every file this plugin wrote went out in whatever charset the host happened to default to, and
every file it read came back as UTF-8. Those are the same thing on a developer's machine and on
most desktops, which is why this survived so long. They are not the same thing on a minimal
container with a POSIX/C locale -- a common way to run a Minecraft server -- where the default
is effectively ASCII.

On that host, saving a gate whose name carried an accent wrote the accent as a `?`. SnakeYAML
read the `?` back as the gate's name, the next shutdown saved it, and the original was gone.
Nothing failed and nothing was logged: `FileWriter` encoded exactly as instructed, and the
reader decoded exactly as instructed, in a different charset. The same applied to owner names,
to anything an admin typed into `config.yml`, and to shape files.

The nastiest instance was inside one file. `GateSerializer` wrote the iris deactivation code
with an explicit `getBytes("UTF8")` and read it back forty lines later with a bare
`new String(idcBytes)`. A gate whose iris code was not plain ASCII stopped accepting the code
its owner had set -- locked out of their own gate by their own password, on a gate they could
still see working for everyone else.

Twenty-seven sites across eight files now name `StandardCharsets.UTF_8`. That is what SnakeYAML
and `Files.readAllLines` already assumed, so nothing needs migrating: files that were written
correctly stay correct, and files that were mangled were already unreadable.

Two tests, doing different jobs. `Utf8GateStorageTest` saves a gate with an accented name and an
accented iris code and checks the bytes on disk, not just the round trip -- a round trip alone
passes on a UTF-8 host, which is precisely why nobody noticed. `PlatformCharsetIsNeverUsedTest`
reads the sources and fails on any charset-less `FileWriter`, `FileReader`, `InputStreamReader`,
`OutputStreamWriter`, `getBytes()` or byte-decoding `new String(...)`, because that guard works
on every machine including the ones where the bug is invisible, and the broken form is shorter
to type than the correct one.

The first draft of that guard matched `new FileWriter(` and let two real offenders through:
`ConfigurationYAML` spelled them `new java.io.FileWriter(...)`. Matching without the `new` is
what caught them -- a guard that only recognises the unqualified form rewards writing it the
long way.

Verified by running the whole suite with `-Dfile.encoding=US-ASCII`, which is the host this bug
needs. 652 tests pass. Reverting the fix under that same flag fails with
`expected: <cafe-gate> but was: <caf?-gate>` and an iris code of `??ppna`, which is the bug
report as an assertion.

`StargateYamlManager.saveStargate` gained a package-private overload taking the target
directory. `getGatesDir()` resolves through `JavaPlugin.getDataFolder()`, which is `final` and
cannot be stubbed, and the only other way to redirect it is reflecting into a private Bukkit
field -- someone else's implementation detail, and not something a test should depend on.

## 1.4.0 (2026-09-05)

### Fix: a failed beam left the traveller in the dark, literally

Caught reviewing the beam work before merging it, and a drift between two places that were
each correct the day they were written. A beam applies three potion effects across two
different ticks -- invisibility at the vanish, blindness and darkness together at the
teleport -- and two separate endings have to undo them: the normal arrival, and the
mid-flight recovery added just below. Each ending listed the effects itself.

Darkness was stacked onto blindness *after* that recovery path already existed (see the
vision fix below for why), and only the arrival path was updated to take it back off. So a
beam that threw anywhere between its teleport and its deposit did everything else right --
unfroze the traveller, made them visible, cleared their blindness -- and left them sitting
in the warden's dark vignette until it expired on its own. Not permanent, but recovery
exists precisely so a failed beam leaves nothing behind, and it was already going to the
trouble of removing blindness, which has exactly the same duration.

Fixed structurally rather than by adding a third copy of the list: both endings now read
one shared `TRAVELLER_EFFECTS`, so an effect added to the sequence cannot be taught to one
ending and forgotten by the other.

No test pins it, and not for lack of trying -- naming any `PotionEffectType` constant from
a test fails with `NoClassDefFoundError: Could not initialize class
org.bukkit.potion.PotionEffectType`, since the class is registry-backed in modern Bukkit
and its static initialiser wants a running server. That is why nothing in this suite
touches potion effects at all, and why the list being shared *is* the guarantee here
instead of a test being it.

### Fix: a beam that fails mid-sequence no longer strands the traveller

Once `isVanish()` fires, a traveller is frozen and invisible until `isFinished()` clears
them -- or until something in between throws. A Bukkit call failing mid-tick
(`spawnParticle`, `teleport`, `addPotionEffect`) used to kill the whole sequence with the
freeze never lifted and nothing left running to lift it: position-locked, invisible or
blind, and permanently `ACTIVE` in `BeamFreeze`, which refuses every later beam for them
too. Exactly the "frozen with no way out" failure `BeamTiming` already exists to prevent
in the timing math, reachable a different way. The tick now runs inside a try/catch, the
same shape `RingTransit`'s own mid-cycle recovery already uses: clear the freeze, remove
whichever potion effects were applied, and tell the traveller they've been freed rather
than leave them guessing.

### Fix: the traveller's own vision no longer arrives ahead of the beam

The real teleport fires mid-rise, so the traveller is physically at the destination for
the entire descend phase -- but nothing was stopping them from freely looking around
before the column had finished settling. Invisibility only ever hid them from *other*
players; it never touched what the traveller themselves could see. The effect ended up
backwards from the intended read: a busy view, then a clear one, and only after that the
"arrival" effect finishing around them.

First attempt was `PotionEffectType.BLINDNESS` alone, applied the instant the real
teleport fires and removed the instant the column settles. Play-testing caught what the
reasoning missed: blindness is mostly a render-distance fog, not an opaque blackout --
nearby terrain and anything bright (daylight, torches, the beam's own `END_ROD`
particles) still showed straight through it, so the traveller could still see the
destination clearly before arriving. `PotionEffectType.DARKNESS` -- the real dark
vignette a warden or sculk shrieker applies -- stacked on top of blindness is what
actually blocks the view; confirmed present across this project's full supported range
(1.20 through 1.21.10) before adding it. Both key off the same two ticks invisibility
already uses, so the traveller's own vision now resolves in sync with the visual instead
of running ahead of it.

### Fix: beam cost no longer charges a message with nothing behind it

`BeamTravel.resolveCost` computed a destination's cost -- its own override, or the global
`BEAM_ECONOMY_USE_COST` default -- unconditionally, unlike every other cost path in this
plugin (a gate's use cost, its build cost), which both collapse to free whenever economy
is not actually active (`ConfigManager.isEconomyEnabled()` false, or no Vault provider
attached). `EconomySupport.canAfford`/`charge` already fail open in that situation --
nothing is actually withdrawn -- so a non-zero cost here meant a player saw "This will cost
X..." and "Charged X..." for a charge that never happened, with config off or Vault
missing. Found by Copilot's review of #21. Fixed to match the existing pattern; three new
`BeamTravelTest` cases pin it (economy never configured, explicitly disabled, and enabled
in config with no Vault provider attached -- the exact scenario that was slipping through).

### Admin beaming: goto and send, from a player, console, or a command block

Two new `beam admin` actions, both gated behind a new `wormhole.beam.admin.teleport` node
(default op) rather than the existing `wormhole.beam.admin` -- curating the destination
list and instantly relocating any player are different orders of power, and holding the
first shouldn't automatically hand out the second.

- `/wormhole beam admin goto <player|destination>` or `<x> <y> <z> [world]` -- beams the
  sender to a player, a public beam destination, or raw coordinates. Player-only: there's
  nowhere for console or a command block to beam *from*.
- `/wormhole beam admin send <target> <player|destination>` or `<target> <x> <y> <z>
  [world]` -- beams a named, online player to another player, a public beam destination, or
  raw coordinates. The one place this command accepts console or a command block as the
  sender, since neither is the one being moved; whoever sent it gets told whether it worked,
  since the target's own "Beaming to X..." messages don't reach them.

Destination names were not accepted at first -- both actions took a player or raw
coordinates and nothing else, so "send someone to spawn" meant looking spawn's coordinates
up by hand to say it. That was a gap rather than a decision, and it showed up the moment
anyone asked the obvious question. A name that isn't an online player now falls through to
the public destination list before erroring, and the failure message names both things it
looked for rather than only mentioning players.

Public destinations only, deliberately. A private place belongs to whoever set it, which
for `send` is the *target*, not the sender -- so a bare name could silently mean something
the sender can't see and never chose. An admin move shouldn't route through another
player's private list. Players still reach their own places through `beam to`, which checks
them first by design. `send`'s *first* argument stays players-only too: that slot names who
is being moved, and a destination there would be meaningless.

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

Tab completion covers both new actions the same way every other beam command already does,
driven from the same `SubCommands` registry: `goto`/`send` themselves, online player names
and public destination names together in the slots that accept either, players alone in the
slot naming who `send` is moving, and loaded world names for the trailing `[world]` slot on
raw coordinates -- the one piece of `goto`/`send`'s arguments that is both completable and
had nothing offered for it at first. A destination sharing a name with an online player is
offered once rather than twice, since the resolver checks players first and the two would
otherwise be one string describing two different outcomes.

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

### Fix: the woosh could leave water (or any portal material) stuck outside a gate, or an extra layer inside it

Reported directly: "gates are leaving water one block from the gate," visible only to the
player who had just gone through, and only sometimes -- both details that point at a
timing-dependent interruption rather than something wrong on every trip. The woosh is
drawn client-side, not written to the world, and the only thing that ever undraws it is its
own step-by-step retraction inside `StargateAnimator.animateOpening`. A gate that closes --
its own lever, a partner gate shutting down, an idle timeout -- while that retraction is
still mid-flight never gets a chance to finish it: whatever was drawn so far (the woosh
material, one block out from the portal on a 2D gate's very first step) is left showing to
anyone nearby, and nothing about closing ever told those positions to revert. Deep gates
made the window easy to hit: `Grand`'s nine-step woosh and `Massive`'s thirteen both take
long enough that an early close has a real chance of landing inside one.

Made worse by a second gap: each woosh step reschedules its own continuation with a raw
`scheduleSyncDelayedTask` call, and nothing keeps a task id to cancel if the gate closes
first. That continuation still fires afterward regardless -- and finding the counters
freshly reset to zero, it would have read that as "nothing has happened yet" and started an
entire new opening (kawoosh included) on a gate that had already closed.

Both are fixed together. `StargateAnimator.lightStargate`'s closing branch now undraws
whatever is left in `getGateAnimatedBlocks()` and resets both step counters
unconditionally, the same way it already unconditionally undraws every chevron light block
regardless of which ones were actually lit -- closing reverts whatever was left showing,
not just whatever it expected to find. `animateOpening` now also returns immediately on an
inactive gate, so a stale continuation firing after that reset reads the gate as closed
rather than as a fresh start.

A third, related bug turned up while confirming the second fix in-game: "the event horizon
is still showing an additional layer... in the gate," on *every* completed opening, not
just an interrupted one. The 3D woosh path's own retraction ended one tick early --
`step3D == 1` was read as the last step, but that check runs after the tick's own undraw of
`getGateWooshBlocks().get(step3D)`, so ending at step 1 meant the undraw *for* step 1 had
already happened, and the method settled without ever taking a further tick to undraw step
0: the shallowest wave, sitting directly behind the portal. It stayed lit as woosh material
for as long as the gate stayed open. The check is against `step3D == 0` now, so the last
step is genuinely the last one undrawn, not one short of it.

Three new `StargateAnimatorTest` cases pin all three directly.

### A Milky Way material group, and depth made proportional to size

Added a fourth gate palette, `MilkyWay`, alongside `Standard`/`Atlantis`/`Universe`: `DEEPSLATE`
frame rather than obsidian's glossy black, `IRON_BLOCK` iris, and `SHROOMLIGHT` chevrons for an
amber glow instead of glowstone's warm yellow -- closer to the film/show's grey naquadah ring
and orange-lit chevrons than the existing default.

Separately: `Large` shipped with Standard's exact proportions, just wider -- a one-layer ring
and three woosh steps. That was wrong for a gate its size. It was reworked to a three-layer
ring matching `Grand`'s (a front bezel, the real portal ring, and a second lit ring) with four
woosh steps behind it.

That rework surfaced a worse inconsistency: `Grand`, at 22 wide, had *fewer* woosh steps than
`Large` at 10 wide -- three against four -- despite being more than double the width. Both
gates were hand-built independently with no shared depth policy, so nothing had ever compared
them side by side. `Grand`'s woosh recession is widened from 3 steps to 9, tapering its 18-wide
portal interior down to a point in steps of 2 (18, 16, ..., 2) rather than jumping straight
from a 10-wide diamond to a 4-wide one. Depth by width across every shipped gate now reads as a
smooth curve: `Large` (10 wide) 4 steps, `Grand` (22 wide) 9, `Massive` (23 wide) 13 --
`Massive`'s remains deliberately disproportionate, the one gate meant to feel absurdly deep
regardless of width.

`BigGateShapeTest` is updated for both: the new taper rule was validated by reproducing the
two diamonds it replaces (`Grand`'s old `W#2`/`W#3`) from the same generation rule before
trusting it for the seven new ones, rather than hand-typing over a thousand new grid cells.

### Three big, deep gates: Large, Grand and Massive

Three more shapes, hand-built outside this codebase and brought in after review: `Large`
(10x10, six layers -- two lit blocks per chevron instead of one, and see the depth rework
above for why it is six rather than Standard's four), `Grand` (22x22, eleven layers -- its ring
is three layers deep, a front bezel and the real portal ring both carrying the same lit
chevrons, then a second lit ring, before the woosh recedes), and `Massive` (23x23, fifteen
layers -- thirteen woosh steps of recession, by far the deepest gate shipped).

None of the three threw on load, which is exactly the problem: `Stargate3DShape` derives one
width and height from `Layer#1` and trusts every later row and layer number to match it, so a
mistake here is silent rather than a parse error. Two real ones turned up under that pressure.
`Grand` had three rows one cell short of its declared width -- a block dropped while hand-
copying its ring pattern into a second layer -- which shifts every column after the gap rather
than failing anything. `Massive` skipped straight from `Layer#11` to `Layer#13`; `Layer#12`
was never declared, leaving a silent one-block dead gap in the middle of what should have been
a continuous thirteen-step recession. Both are fixed: the dropped cells were restored by
mirroring the row they were copied from, and the layers from 12 on were renumbered down to
close the gap.

Also brought in line with every other shipped gate's convention while reviewing: `Grand` had no
`:EM`, so minecarts had no entry point -- added, on `Layer#4` rather than the layer directly
behind `:EP`, since `Grand`'s ring is three layers deep and `Layer#3` still duplicates solid
frame at that position. `Massive` had no `:N` and only six lit chevron orders instead of seven,
its top cap sharing `L#1` with an unrelated band instead of getting its own order the way every
other shipped gate's top light does -- both added. All three also named `PORTAL_MATERIAL` as
`STATIONARY_WATER`, a pre-1.13 Bukkit name that does not resolve to anything in the versions
this plugin targets and would have failed `ShippedMaterialsExistTest`; removed in favor of the
same convention every other current shape file uses -- material comes from the palette a gate
is actually built in, not pinned in the shape file, since none of these three need a fixed
material the way `Horizontal`'s glass iris genuinely does.

`BigGateShapeTest` pins the two structural fixes specifically (no gap in the layer array, all
seven light orders present with none merged into another) so a future edit to any of the three
has to own up to reintroducing that shape rather than changing behaviour quietly.

### The first even-width gate shape

Every shipped ring -- Standard, Minimal, Horizontal -- is an odd number of blocks wide, which
gives it one true center column for the markers that only ever appear once: the top light,
`:N`, `:EP`. `Even.shape` and `EvenSignDial.shape` are the first shapes that are not: an 8x8
ring with no single center column at all, the middle falling between columns 3 and 4 instead
of landing on one.

Rather than split those markers across both middle columns, every one of them -- the top
light, the name sign, the entry point -- is pinned to column 3 throughout the shape, so they
still read as one straight vertical line even though it is not the geometric center. Standard's
ring has one row at its widest, giving it one pair of lit corners; an 8-wide ring needs four
full-width rows to stay symmetric, so only the one nearest that same column-3 line is lit and
the rest stay plain -- the same way Standard's own off-center full-width rows already do.

`EvenSignDial.shape` pairs with it the way `StandardSignDial.shape` pairs with `Standard.shape`:
same ring, `:D` and two redstone points (`[RD]`, `[RA]`) in place of the iris switch, and no
`[RS]` for the same reason `StandardSignDial` ships without one -- the only free cell left is
adjacent to `[RD]`, and adjacent redstone dust connects, so a pulse would cycle the destination
and dial it in the same signal.

`EvenGateShapeTest` pins the design decisions that would otherwise only be noticed by eye in
game: both shapes parse without throwing, the light count and woosh depth match Standard's
scaled up (7 lights, 3 receding layers), and the redstone/dial-only split lands on the right
layer. The existing shape-sweeping tests (`RedstoneBlockPlacementTest`,
`ArrivalIsOutsideThePortalTest`) already cover any newly shipped shape automatically and both
pass against these two unmodified.

### Validating and reloading a gate shape without restarting the server

`Stargate3DShape` derives one width and height from `Layer#1` and trusts every later row and
layer number to match it -- a row one cell short of that width does not throw, it just shifts
every column after the gap; a skipped `Layer#N=` does not throw either, it leaves a `null` in
the middle of the layer array, a silent dead gap in the woosh recession. Both mistakes actually
shipped in this project's own gate shapes before being caught by hand. `ShapeFileValidator` is
those checks (plus duplicate singleton markers, gaps in `:L#`/`:W#` ordering, materials that do
not resolve, and redstone markers landing on the frame) formalized into one pass over a shape
file, so the next one is caught by running a command instead.

`/wormhole gate shapes validate <name>` runs that pass without touching anything loaded.
`/wormhole gate shapes reload [name]` runs it and, if the file is valid, replaces its entry in
`StargateShapeRegistry` -- or reloads every shape in the directory if no name is given. This
needed an actual behavior change in the registry: `loadShapes()`'s own "name already exists"
rule *keeps* the earlier entry, which is exactly backwards for reloading a shape someone is
actively editing -- every reload after the first would have silently done nothing.
`StargateShapeRegistry.replaceIfValid` is the decision an edit-and-recheck loop actually needs,
and a failed reload leaves the last good version in place rather than tearing it down.

Both `/wormhole gate shapes` verbs require `wormhole.config`, the same node the rest of gate
management already does -- this reaches into the GateShapes directory and changes what every
future gate on the server can be built from.

### A gate's ambient hum no longer outlives the gate

`tickAmbient()` re-triggers the ambient sound every `getGateSoundAmbientTicks()` (70 ticks by
default) -- deliberately a little under the sample's own length, so retriggers overlap and the
hum sounds continuous rather than gasping. That means at almost any instant a gate is open,
there is already an in-flight instance of a multi-second sample playing. Shutting the gate down
removed it from `getOpenGates()`, which stopped *new* triggers, but nothing ever stopped the
one already dispatched -- so the hum kept playing to its own natural end, up to a sample's
length after the gate had actually closed.

`World` turns out to have no `stopSound` of its own; only `Player` does, so unlike playing --
which Bukkit broadcasts to whoever is in range on its own -- stopping has to be told to each
player in the gate's world individually. `Sounds.stopForEveryoneIn` does that, and
`GateSounds.stopAmbient` (called from `StargateLifecycle.shutdownStargate`, right alongside the
close sound) is the actual fix.

### A timed-out gate's chevrons could stay lit forever

`StargateLifecycle.timeoutStargate` decided which gate to turn the lights off on by reading
`activatedStargates`, a map keyed on the *player*, not the gate. If the same player activated a
second gate before the first one's 30-second activation timer expired, that second activation
silently overwrote the first gate's entry in the map. When gate one's timer then fired, the
code resolved "the gate to deactivate" through that map -- landing on gate two instead of the
one that actually timed out. Gate one's chevrons were never told to turn off and stayed lit
until someone manually toggled its lever; gate two was switched off early and lost its own
pending-activation entry, so when *its* timer later fired there was nothing left in the map and
its own cleanup (iris restore, lights, message) was silently skipped too.

The fix operates on the gate that actually timed out throughout, and clears that gate's own map
entry by identity (`StargateManager.removeActivatorForStargate`, the same method
`GateInteractionHandler`'s manual-deactivation path already uses for this exact reason) rather
than removing whatever the player happens to be mapped to right now.

### The wormhole woosh sound never actually played

The lever's activation sound and each chevron's lock sound both played fine; the kawoosh --
the sound of the wormhole itself establishing -- never did, on any gate, ever. Both the
sound and a visual detail traced back to the same field: `gateAnimationStep3D` defaulted to
1 instead of 0, and `StargateAnimator.animateOpening`'s "closing just finished" branch reset
`isGateAnimationRemoving` but never reset the step counter back to 0 either. The kawoosh only
fires when `step2D == 0 && step3D == 0` -- with the counter starting at 1 and never returning
to 0 after a close, that condition was never true, not on a gate's first-ever opening and not
on any opening after. The same counter indexes directly into the shape's own woosh-depth
blocks, so every opening was additionally starting one depth layer short of the shape's first
one -- quieter than the missing sound, easy to not notice was wrong on its own.

Both are fixed: the field now defaults to 0, matching `gateAnimationStep2D` right beside it,
and the closing branch resets the counter the same way the 2D woosh path already resets its
own. `StargateAnimatorTest` pins both directly against the counter's value rather than trying
to observe a sound call, so a regression here fails a fast, Bukkit-free test instead of only
being noticed by someone dialing a gate and not hearing anything.

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

