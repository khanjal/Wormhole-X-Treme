# Plugin API

For plugins that want to hook into gates and rings: watch a trip, stop one, or read where
somebody is going. Everything a server owner needs is in the [guide](guide/README.md); how each
subsystem is put together is in [GATES.md](GATES.md), [RINGS.md](RINGS.md), [BEAMS.md](BEAMS.md)
and [MIRRORS.md](MIRRORS.md).

The plugin needs **Java 17** and runs on Minecraft **1.20 through 26.3**. It is compiled against
the oldest supported API, so everything here works across that whole range. Beaming and mirrors
raise no events yet.

## Depending on the plugin

Add the jar to your build however you normally would, and declare the dependency in your own
`plugin.yml` so the server loads us first:

```yaml
depend: [WormholeXTreme]
```

Use `softdepend` instead if your plugin should still load when Wormhole X-Treme is absent, and
guard your listener registration on the plugin being present.

## Events

Every event lives in `com.wormhole_xtreme.wormhole.events`. They are ordinary Bukkit events:
register a `Listener`, annotate with `@EventHandler`, and set `ignoreCancelled = true` if you
only care about trips nobody else has already stopped.

| Event | Fired | Cancellable |
| --- | --- | --- |
| `StargateCreatedEvent` | after a gate is built, named, registered and saved | no |
| `StargateRemovedEvent` | while a gate is being removed, before it is torn down | no |
| `StargateActivatedEvent` | after a gate's wormhole opens | no |
| `StargateShutdownEvent` | after a gate's wormhole closes | no |
| `StargatePlayerTravelEvent` | before a player travels through a gate | **yes** |
| `RingTravelEvent` | before a player is carried by transport rings | **yes** |
| `StargateMinecartTeleportEvent` | after a minecart has crossed a gate | no |

### Gate lifecycle

```java
@EventHandler
public void onGateCreated(final StargateCreatedEvent event)
{
    getLogger().info(event.getStargateName() + " built by "
        + (event.getBuilder() != null ? event.getBuilder().getName() : "no player"));
}
```

`getStargate()` gives the gate itself. `getBuilder()` and `getRemover()` give the player
responsible, and are **null** when the gate was not created or removed by one.

The removal event fires *before* teardown, so the gate can still be read: name, owner, network,
blocks and teleport location are all still populated, which is what a listener cleaning up its
own records needs. Refreshing a gate does **not** raise a removal: a refresh re-detects the
geometry of a gate that is not going away, so listeners are not told to discard what they know.

Neither lifecycle event is cancellable; both are sent after the decision has been made and, for
creation, after the gate is already on disk. To prevent a gate being built, deny `wormhole.build`
rather than listening for it.

### Wormholes opening and closing

`StargateActivatedEvent` and `StargateShutdownEvent` are about the thing a gate does, rather
than about the gate existing. A dialled pair raises one of each per end, because each end
opened and each end closed.

```java
@EventHandler
public void onOpened(final StargateActivatedEvent event)
{
    getLogger().info(event.getStargateName() + " opened");
}

@EventHandler
public void onClosed(final StargateShutdownEvent event)
{
    getLogger().info(event.getStargateName() + " closed: " + event.getReason());
}
```

**Neither carries a destination.** A gate is marked active before it is linked, and the far
end never receives a reciprocal target, so a destination field would read null at both ends.
Read `getStargate().getGateTarget()` once dialling has settled, or use
`StargatePlayerTravelEvent`, which carries both ends and where somebody is going.

**A shutdown is only raised for a gate that was actually open.** Shutting a gate that was
already closed raises nothing, so these can be counted against each other.

`getReason()` is one of:

| Reason | When |
| --- | --- |
| `TIMEOUT` | the shutdown clock ran out, including `timeout-shutdown: 0`, where a gate closes as soon as somebody has travelled |
| `MANUAL` | somebody worked the switch, or an admin ran a command |
| `FAR_END` | the gate at the other end closed, or never opened |
| `REMOVAL` | the gate is being removed and its wormhole closes on the way out |
| `PLUGIN_DISABLE` | the plugin is unloading, usually because the server is stopping |

There is no `IRIS`: raising the iris into an open gate fills the portal and does not close
the wormhole.

### Gate travel

`StargatePlayerTravelEvent` fires once every check this plugin makes has passed — permission,
iris code, cooldown, one-way, same-world — and before anything has moved. `getStargate()` is the
gate being entered, `getDestination()` is where it leads, and `getArrival()` is the exact spot
the player would land.

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

It fires for a player on foot and for one riding anything — a horse, a minecart, a boat. It does
not fire for the vehicle itself, nor for anything travelling on its own, so cancelling stops the
player rather than the world around them.

A cancelled traveller is held, not moved. If they were walking in they are kept out; if they were
already standing in the portal they stay free to walk away, since refusing every move of someone
already inside would leave them unable to leave the ring at all. A listener that throws does not
stop travel: another plugin failing is not a decision to strand somebody halfway into a wormhole.

### Minecarts

A minecart does not survive a gate: it is removed and a fresh one spawned at the far end, so
anything holding a reference to the old cart needs telling. `StargateMinecartTeleportEvent`
carries `getOldMinecart()` and `getNewMinecart()`. It fires after the swap and is not cancellable
— by then the trip has happened.

### Rings

`RingTravelEvent` fires once per travelling player, after both ends of the pair have been read
and before either has been written — so a listener always sees the whole trip as it was before
any of it happened, never a half-finished one with the people from one end already standing in
the other.

Cancelling takes that player out of the trip and leaves everybody else in it: the rings still
fire, and they stay put while the others go. There is no way to cancel a whole cycle, because by
that point the rings are up and coming down again regardless. It fires only for players; mobs,
items and vehicles ride along as cargo and raise nothing.

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
