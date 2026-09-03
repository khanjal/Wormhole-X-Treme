# Wormhole X-Treme — developer guide

For plugins that want to hook into gates and rings: watch a trip, stop one, or read where
somebody is going. Everything a server owner needs is in the [README](../README.md); this is
the other audience.

Requires **Java 17** and Minecraft **1.20 through 1.21.10**. The plugin is compiled against
the oldest supported API, so anything documented here works across that whole range.

## Contents

- [Depending on the plugin](#depending-on-the-plugin)
- [Events](#events)
- [Notes on the internals](#notes-on-the-internals)
- [Contributing](#contributing)

## Depending on the plugin

Add the jar to your build however you normally would, and declare the dependency in your own
`plugin.yml` so the server loads us first:

```yaml
depend: [WormholeXTreme]
```

Use `softdepend` instead if your plugin should still load when Wormhole X-Treme is absent —
then guard your listener registration on the plugin being present.

They are ordinary Bukkit events: register a `Listener`, annotate with `@EventHandler`, and
set `ignoreCancelled = true` if you only care about trips nobody else has already stopped.

One wrinkle worth knowing before you write the imports. Almost everything lives in
`com.wormhole_xtreme.wormhole.events` — plural — but `StargateMinecartTeleportEvent` sits on
its own in `com.wormhole_xtreme.wormhole.event`, singular. That is history rather than
design, and it is written down here because the compiler error it produces is not obvious.

## Events

Gate lifecycle is published as Bukkit events, so another plugin can react without this one
knowing it exists.

| Event | Fired |
| --- | --- |
| `StargateCreatedEvent` | after a gate is built, named, registered and saved |
| `StargateRemovedEvent` | while a gate is being removed, before it is torn down |
| `StargatePlayerTravelEvent` | before a player travels, and **cancellable** |
| `RingTravelEvent` | before a player is carried by transport rings, and **cancellable** |
| `StargateMinecartTeleportEvent` | after a minecart has crossed, carrying the old cart and the new |

```java
@EventHandler
public void onGateCreated(final StargateCreatedEvent event)
{
    getLogger().info(event.getStargateName() + " built by "
        + (event.getBuilder() != null ? event.getBuilder().getName() : "no player"));
}
```

`getStargate()` gives the gate itself. `getBuilder()` and `getRemover()` give the player
responsible, and are **null** when the gate was not created or removed by one — check before
using them.

The removal event fires *before* teardown, so the gate can still be read: name, owner,
network, blocks and teleport location are all still populated, which is what a listener
cleaning up its own records needs.

`StargateMinecartTeleportEvent` is the odd one out in more than its package. A minecart does
not survive a gate: it is removed and a fresh one spawned at the far end, so anything holding
a reference to the old cart needs telling. `getOldMinecart()` and `getNewMinecart()` are that
telling. It fires after the swap and is not cancellable — by then the trip has happened.

The lifecycle events are not cancellable. Both are sent after the decision has been made
and, for creation, after the gate is already on disk. To prevent a gate being built, deny
`wormhole.build` rather than listening for it.

### Rings

`RingTravelEvent` fires once per travelling player, after both ends of the pair have been
read and before either has been written — so a listener always sees the whole trip as it was
before any of it happened, never a half-finished one with the people from one end already
standing in the other.

Cancelling takes that player out of the trip and leaves everybody else in it: the rings still
fire, and they stay put while the others go. There is no way to cancel a whole cycle, because
by that point the rings are up and coming down again regardless.

It fires only for players. Mobs, items and vehicles ride along as cargo and raise nothing, so
cancelling stops a person and not the world around them.

```java
@EventHandler
public void onRingTravel(final RingTravelEvent event)
{
    if (combatTag.isTagged(event.getPlayer()))
    {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Not while you are in combat.");
    }
}
```

### Watching and stopping travel

`StargatePlayerTravelEvent` fires once every check this plugin makes has passed — permission,
iris code, cooldown, one-way, same-world — and before anything has moved. `getStargate()` is
the gate being entered, `getDestination()` is where it leads, and `getArrival()` is the exact
spot the player would land.

```java
@EventHandler
public void onTravel(final StargatePlayerTravelEvent event)
{
    if (inCombat(event.getPlayer()))
    {
        event.setCancelled(true);
    }
}
```

It fires for a player on foot and for one riding anything — a horse, a minecart, a boat. It
does not fire for the vehicle itself, nor for anything travelling on its own, so cancelling
stops the player rather than the world around them.

A cancelled traveller is held, not moved. If they were walking in they are kept out; if they
were already standing in the portal they stay free to walk away. Refusing every move of
someone already inside would leave them unable to leave the ring at all.

A listener that throws does not stop travel. Another plugin failing is not a decision to
strand somebody halfway into a wormhole.

Refreshing a gate does **not** raise a removal. A refresh deregisters the gate and registers
it again with freshly detected geometry, which is not the gate going away, so a listener is
not told to discard what it knows about it.

## Notes on the internals

- `LegacyCompat` utility class provides `isWallSign(Material)` and `isButton(Material)` helpers that cover all current wood, stone, and Nether variants so that detection code does not need explicit per-type checks.
- All air-type checks use `Material.isAir()` (covers `AIR`, `CAVE_AIR`, `VOID_AIR`) rather than a direct `== Material.AIR` comparison.
- Sign material for each gate is read from the shape's `SIGN_MATERIAL=` key and stored on `StargateShape` / `Stargate3DShape`; placement and detection code reads from the shape object rather than hardcoding `OAK_WALL_SIGN`.
- `StargateYamlManager` handles per-gate YAML read/write.
- `StorageMigrator` provides a CLI-accessible migration tool for `db -> file`.

## Contributing

Submit PRs against the `main` branch. Keep changes modular and add unit or integration tests
where possible — the suite runs on every supported Minecraft version in CI, so anything that
only works on one of them will be caught there.
