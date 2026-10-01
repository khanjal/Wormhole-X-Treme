package com.wormhole_xtreme.wormhole;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.SizedFireball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.PassengerReattach;

/**
 * Sweeps active gates for loose entities — dropped items, wandering mobs — standing in an
 * open wormhole, and sends them through.
 *
 * <p>Players and vehicles are handled by their own events and are skipped here; this
 * exists only for entities that generate no event when they drift into a portal.
 *
 * <p>The sweep is deliberately shaped around the "many gates, many players" case. It walks
 * the open gates rather than every gate, so its cost tracks how many wormholes are in use
 * rather than how many gates a server has built. Per tick interval it does one entity query
 * per open gate, not one per portal block: a Standard gate has 21 portal blocks, so the
 * naive version issued 21 spatial queries per gate and 1,000+ across a server with 50 open
 * wormholes. Everything that does not depend on the entity — the destination, the arrival
 * location — is computed once per gate rather than once per entity.
 */
public final class GateEntityScanner implements Runnable
{
    private GateEntityScanner() {}

    /**
     * Creates the sweep task.
     *
     * @return a runnable suitable for a repeating scheduler task
     */
    public static Runnable create()
    {
        return new GateEntityScanner();
    }

    @Override
    public void run()
    {
        try
        {
            // Only the gates actually showing a portal, not every gate on the server. A
            // gate that is not open has nothing to sweep, and walking the whole list to
            // find that out scaled this task with how many gates a server has built rather
            // than with how many are in use — on a world with three thousand gates and two
            // wormholes open, that was three thousand checks a second to do two gates'
            // worth of work. StargateManager keeps the open set as gates open and close, so
            // reading it costs nothing to maintain.
            for (final Stargate gate : StargateManager.getOpenGates())
            {
                sweepGateQuietly(gate);
            }
            // An idle gate's drawn iris is air to the server, so it gets swept too.
            for (final Stargate gate : StargateManager.getIrisGates())
            {
                splatAtIdleIrisQuietly(gate);
            }
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Entity scan aborted", t);
        }
    }

    /**
     * Sweeps one gate, and keeps going if that one gate cannot be swept.
     *
     * <p>Its own method rather than a try inside run's try: one bad gate must not end the
     * tick for the gates after it in the loop.
     *
     * @param gate
     *            the gate to sweep
     */
    private void sweepGateQuietly(final Stargate gate)
    {
        try
        {
            sweepGate(gate);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Entity scan failed for gate " + (gate != null ? gate.getGateName() : "null"), t);
        }
    }

    /**
     * Sends any loose entity standing in this gate's open wormhole through it.
     *
     * @param gate
     *            the gate to sweep
     */
    private static void sweepGate(final Stargate gate)
    {
        // The caller only offers open gates, but the checks stay. The set is read live, so a
        // gate can shut down between being handed over and being swept -- and the set follows
        // the active flag alone, so it can also hold a gate the registry has never heard of:
        // one still being detected, or one built in a test. Sweeping filtered the registry
        // before and must go on excluding those, or an entity gets sent through a gate that
        // is not on the server.
        if (gate == null || !gate.isGatePortalOpen() || gate.getGateTarget() == null
            || !StargateManager.isRegistered(gate))
        {
            return;
        }
        final World world = gate.getGateWorld();
        final BoundingBox bounds = gate.getGatePortalBounds();
        if (world == null || bounds == null)
        {
            return;
        }

        // One query for the whole gate. The box encloses the ring, so candidates still
        // have to be confirmed against the actual portal blocks below.
        final Collection<Entity> candidates = world.getNearbyEntities(bounds);
        if (candidates.isEmpty())
        {
            return;
        }

        // Per-gate, not per-entity: these do not vary across the entities found.
        final Stargate target = gate.getGateTarget();
        final Location arrival = WormholeXTremeVehicleListener.forwardAndUp(
            target.getGatePlayerTeleportLocation(), target.getGateFacing(), 1.0, 1.0);
        if (arrival == null)
        {
            return;
        }

        // Either iris being shut stops the trip. The near one used to stop it by being solid
        // enough that nothing could stand in the opening to be swept; the far one was never
        // asked at all, which is how items and mobs went on arriving at a gate that had shut
        // its iris after the wormhole opened.
        final boolean irisShut = gate.isGateIrisActive() || target.isGateIrisActive();

        for (final Entity entity : candidates)
        {
            sendOneThroughQuietly(entity, gate, arrival, target.getGateFacing(), irisShut);
        }
    }

    /**
     * Sends one entity through, if it is standing somewhere that counts.
     *
     * <p>The candidates come from a bounding box, which is bigger than the wormhole itself,
     * so being in the box is not yet a reason to be sent anywhere.
     *
     * @param entity
     *            the entity to consider
     * @param gate
     *            the gate it is standing in
     * @param arrival
     *            where it comes out
     * @param facing
     *            the way the far gate faces, for the direction it arrives travelling
     * @param irisShut
     *            whether an iris at either end is covering the way through
     */
    private static void sendOneThroughQuietly(final Entity entity, final Stargate gate,
        final Location arrival, final BlockFace facing, final boolean irisShut)
    {
        try
        {
            if (!shouldSendThrough(entity))
            {
                return;
            }
            final Location at = entity.getLocation();
            if (!gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
            {
                return; // inside the bounding box but not in the wormhole itself
            }
            if (irisShut)
            {
                splatOnIris(entity);
                return;
            }
            sendThrough(entity, arrival, gate.getGateFacing(), facing, null);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Failed to send entity through gate", t);
        }
    }

    /** The idle-iris sweep for one gate, which must not end the tick for the gates after it. */
    private static void splatAtIdleIrisQuietly(final Stargate gate)
    {
        try
        {
            splatAtIdleIris(gate);
        }
        catch (final RuntimeException t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Iris sweep failed for gate " + gate.getGateName(), t);
        }
    }

    /**
     * Destroys the loose items standing in an idle gate's opening while its drawn iris is shut.
     *
     * @param gate
     *            the gate to sweep
     */
    static void splatAtIdleIris(final Stargate gate)
    {
        // A gate sending somewhere is the other sweep's; this one takes every other drawn iris,
        // the far end of a wormhole included, which has no target and so no sweep of its own.
        if ((gate == null) || (gate.isGateActive() && (gate.getGateTarget() != null))
            || !gate.isGateIrisActive() || !gate.isGateIrisDrawn()
            || !StargateManager.isRegistered(gate))
        {
            return;
        }
        final World world = gate.getGateWorld();
        final BoundingBox bounds = gate.getGatePortalBounds();
        if ((world == null) || (bounds == null))
        {
            return;
        }
        for (final Entity entity : world.getNearbyEntities(bounds))
        {
            final Location at = entity.getLocation();
            if (gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()))
            {
                splatOnIris(entity);
            }
        }
    }

    /**
     * What becomes of something that reaches a shut iris.
     *
     * <p>Loose items are destroyed, which is what an iris is for and what keeps a gate that
     * somebody is tipping hoppers into from filling its opening with a drift of items nobody
     * can see behind the drawing.
     *
     * <p>Anything alive is left where it is. It simply does not travel: a cow that wandered
     * into a shut gate is a cow standing in a gate, not a dead cow.
     *
     * @param entity
     *            the entity that reached the iris
     */
    private static void splatOnIris(final Entity entity)
    {
        if (entity instanceof Item)
        {
            entity.remove();
        }
    }

    /**
     * Replaces a projectile with an identical one at the destination, flying outward.
     *
     * <p>Everything that makes the projectile behave and score correctly is carried over:
     * its shooter, so kills are still credited and an ender pearl still teleports the
     * player who threw it, plus the arrow properties that affect damage and pickup, and the
     * item it carries, which is where a tipped arrow's effect and a trident's enchantments live.
     *
     * @param projectile
     *            the projectile arriving at the gate
     * @param arrival
     *            where it should reappear
     * @param exit
     *            the velocity the replacement should leave with
     * @return the replacement, or null to fall back to a plain teleport
     */
    private static Entity respawnProjectile(final Projectile projectile, final Location arrival, final Vector exit,
        final Stargate exitGate)
    {
        try
        {
            final Class<? extends Entity> type = projectile.getType().getEntityClass();
            if (type == null || !Projectile.class.isAssignableFrom(type))
            {
                return null;
            }

            final Entity spawned;
            if (AbstractArrow.class.isAssignableFrom(type))
            {
                // spawnArrow creates an arrow already travelling, which a plain spawn does
                // not — a spawned-then-nudged arrow behaves like one that has landed.
                final double speed = exit.length();
                final Vector direction = (speed > 0) ? exit.clone().normalize() : exit.clone();
                spawned = arrival.getWorld().spawnArrow(arrival, direction, (float) speed, 0f,
                    type.asSubclass(AbstractArrow.class));
            }
            else
            {
                spawned = arrival.getWorld().spawn(arrival, type);
            }

            copyProjectileState(projectile, spawned);
            projectile.remove();
            if (spawned instanceof Projectile shot)
            {
                ProjectileGateTracker.track(shot, projectile, exitGate);
            }
            return spawned;
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Could not respawn projectile through gate, falling back to teleport", e);
            return null;
        }
    }

    /**
     * Copies the state that makes a replacement projectile behave like the original.
     *
     * @param from
     *            the projectile arriving at the gate
     * @param to
     *            its replacement at the destination
     */
    private static void copyProjectileState(final Projectile from, final Entity to)
    {
        to.setFireTicks(from.getFireTicks());
        if (to instanceof Projectile shot)
        {
            // Kill credit, and for an ender pearl, who gets teleported when it lands.
            shot.setShooter(from.getShooter());
        }
        copyItem(from, to);
        if ((from instanceof AbstractArrow a) && (to instanceof AbstractArrow b))
        {
            copyArrowState(a, b);
        }
        if ((from instanceof Arrow a) && (to instanceof Arrow b))
        {
            copyPotion(a, b);
        }
        if ((from instanceof SpectralArrow a) && (to instanceof SpectralArrow b))
        {
            b.setGlowingTicks(a.getGlowingTicks());
        }
        if ((from instanceof Trident a) && (to instanceof Trident b))
        {
            // The entity reads these off its item only when it is made, so the item alone is not enough.
            copy(GET_LOYALTY, SET_LOYALTY, a, b);
            copy(HAS_GLINT, SET_GLINT, a, b);
        }
        if ((from instanceof Firework a) && (to instanceof Firework b))
        {
            b.setFireworkMeta(a.getFireworkMeta());
            b.setShotAtAngle(a.isShotAtAngle());
        }
    }

    /**
     * Copies the item a projectile carries: a potion's contents, a trident's enchantments, a
     * tipped arrow's effect, a pearl's or snowball's look.
     */
    private static void copyItem(final Projectile from, final Entity to)
    {
        if ((from instanceof ThrowableProjectile thrown) && (to instanceof ThrowableProjectile replacement))
        {
            // Tridents included: they are throwable as well as arrows, on every version.
            replacement.setItem(thrown.getItem());
        }
        else if ((from instanceof AbstractArrow a) && (to instanceof AbstractArrow b))
        {
            copy(GET_ARROW_ITEM, SET_ARROW_ITEM, a, b);
        }
        else if ((from instanceof SizedFireball a) && (to instanceof SizedFireball b))
        {
            b.setDisplayItem(a.getDisplayItem());
        }
    }

    /** Damage, pickup, and what the bow or crossbow gave the arrow. */
    private static void copyArrowState(final AbstractArrow a, final AbstractArrow b)
    {
        b.setDamage(a.getDamage());
        b.setCritical(a.isCritical());
        b.setPierceLevel(a.getPierceLevel());
        b.setPickupStatus(a.getPickupStatus());
        // A Punch bow's knockback and a crossbow shot's identity: from 1.21 both come from the
        // weapon the arrow remembers, and the old setters are marked for removal.
        if (!copy(GET_WEAPON, SET_WEAPON, a, b))
        {
            copy(GET_KNOCKBACK, SET_KNOCKBACK, a, b);
            copy(IS_CROSSBOW, SET_CROSSBOW, a, b);
        }
    }

    /**
     * A tipped arrow's effect, set on the arrow itself: before 1.20.4 there is no item to copy, on
     * 1.20.4 the arrow keeps its effect apart from its item, and on Paper setting the item leaves
     * the arrow's colour behind.
     */
    private static void copyPotion(final Arrow a, final Arrow b)
    {
        if (!copy(GET_POTION_TYPE, SET_POTION_TYPE, a, b))
        {
            copy(GET_POTION_DATA, SET_POTION_DATA, a, b);
        }
        for (final PotionEffect effect : a.getCustomEffects())
        {
            b.addCustomEffect(effect, true);
        }
    }

    /** {@code AbstractArrow.getItem()}, from 1.20.4, or null. */
    private static final Method GET_ARROW_ITEM = method(AbstractArrow.class, "getItem");

    /** {@code AbstractArrow.setItem(ItemStack)}, from 1.20.4, or null. */
    private static final Method SET_ARROW_ITEM = method(AbstractArrow.class, "setItem", ItemStack.class);

    /** {@code Arrow.getBasePotionType()}, from 1.20.2, or null. */
    private static final Method GET_POTION_TYPE = method(Arrow.class, "getBasePotionType");

    /** {@code Arrow.setBasePotionType(PotionType)}, from 1.20.2, or null. */
    private static final Method SET_POTION_TYPE = method(Arrow.class, "setBasePotionType", PotionType.class);

    /** {@code Arrow.getBasePotionData()}, deprecated, for 1.20 and 1.20.1. */
    private static final Method GET_POTION_DATA = method(Arrow.class, "getBasePotionData");

    /** {@code Arrow.setBasePotionData(PotionData)}, deprecated, for 1.20 and 1.20.1; found by its getter's type. */
    private static final Method SET_POTION_DATA = (GET_POTION_DATA == null) ? null
        : method(Arrow.class, "setBasePotionData", GET_POTION_DATA.getReturnType());

    /** Paper's {@code Trident.getLoyaltyLevel()}, or null on Spigot. */
    private static final Method GET_LOYALTY = method(Trident.class, "getLoyaltyLevel");

    /** Paper's {@code Trident.setLoyaltyLevel(int)}, or null on Spigot. */
    private static final Method SET_LOYALTY = method(Trident.class, "setLoyaltyLevel", int.class);

    /** Paper's {@code Trident.hasGlint()}, or null on Spigot. */
    private static final Method HAS_GLINT = method(Trident.class, "hasGlint");

    /** Paper's {@code Trident.setGlint(boolean)}, or null on Spigot. */
    private static final Method SET_GLINT = method(Trident.class, "setGlint", boolean.class);

    /** {@code AbstractArrow.getWeapon()}, from 1.21, or null. */
    private static final Method GET_WEAPON = method(AbstractArrow.class, "getWeapon");

    /** {@code AbstractArrow.setWeapon(ItemStack)}, from 1.21, or null. */
    private static final Method SET_WEAPON = method(AbstractArrow.class, "setWeapon", ItemStack.class);

    /** {@code AbstractArrow.getKnockbackStrength()}, marked for removal from 1.21, or null once gone. */
    private static final Method GET_KNOCKBACK = method(AbstractArrow.class, "getKnockbackStrength");

    /** {@code AbstractArrow.setKnockbackStrength(int)}, marked for removal from 1.21, or null once gone. */
    private static final Method SET_KNOCKBACK = method(AbstractArrow.class, "setKnockbackStrength", int.class);

    /** {@code AbstractArrow.isShotFromCrossbow()}, or null once gone. */
    private static final Method IS_CROSSBOW = method(AbstractArrow.class, "isShotFromCrossbow");

    /** {@code AbstractArrow.setShotFromCrossbow(boolean)}, marked for removal from 1.21, or null once gone. */
    private static final Method SET_CROSSBOW = method(AbstractArrow.class, "setShotFromCrossbow", boolean.class);

    /** @return true if arrows on this server carry the weapon that fired them */
    static boolean carriesWeapon()
    {
        return (GET_WEAPON != null) && (SET_WEAPON != null);
    }

    /** @return true if arrows on this server carry the item they are picked up as */
    static boolean arrowsCarryItems()
    {
        return (GET_ARROW_ITEM != null) && (SET_ARROW_ITEM != null);
    }

    /** @return true if this server lets a trident's loyalty be set, which only Paper does */
    static boolean tridentLoyaltyIsSettable()
    {
        return (GET_LOYALTY != null) && (SET_LOYALTY != null) && (HAS_GLINT != null) && (SET_GLINT != null);
    }

    /** Looks a method up once, by name, so none is linked against directly. */
    private static Method method(final Class<?> owner, final String name, final Class<?>... parameters)
    {
        try
        {
            return owner.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }

    /**
     * Copies one property from projectile to projectile through a getter and setter found by name.
     *
     * @return true if this server has both, whether or not there was anything to copy
     */
    private static boolean copy(final Method getter, final Method setter, final Projectile from,
        final Projectile to)
    {
        if ((getter == null) || (setter == null))
        {
            return false;
        }
        try
        {
            final Object value = getter.invoke(from);
            if (value != null)
            {
                setter.invoke(to, value);
            }
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Could not copy " + getter.getName() + " to a projectile's replacement", e);
        }
        return true;
    }

    /**
     * Sends a projectile through a gate, consuming it and firing a replacement.
     *
     * <p>Called by {@link ProjectileGateTracker} the tick a projectile reaches a portal,
     * which is the only moment it is actually there.
     *
     * @param projectile
     *            the projectile crossing the gate
     * @param gate
     *            the gate it is entering
     * @return true if it was sent through
     */
    static boolean sendProjectileThrough(final Projectile projectile, final Stargate gate)
    {
        final Stargate target = gate.getGateTarget();
        // An arrow that reaches a shut iris stops there, at whichever end the iris is: the
        // near one it was fired at, or the far one it would otherwise have come out of. It is
        // consumed rather than dropped, because an iris that leaves a pile of arrows on the
        // floor in front of it is not much of an iris.
        if (gate.isGateIrisActive() || target.isGateIrisActive())
        {
            projectile.remove();
            return true;
        }
        final Location arrival = WormholeXTremeVehicleListener.forwardAndUp(
            target.getGatePlayerTeleportLocation(), target.getGateFacing(), 1.0, 1.0);
        if (arrival == null)
        {
            return false;
        }
        sendThrough(projectile, arrival, gate.getGateFacing(), target.getGateFacing(), target);
        return true;
    }

    /**
     * Speed given to a projectile that reaches a gate with no momentum left. Roughly a
     * fully drawn bow.
     */
    private static final double PROJECTILE_LAUNCH_SPEED = 3.0;

    /**
     * Works out how fast something should leave the destination gate.
     *
     * <p>Preserving the arrival speed is right for anything still moving, but a projectile
     * usually is not. Portal blocks are air, so an arrow flies straight through the ring
     * and sticks in whatever is behind it; the sweep only comes round up to a second later
     * and finds it stopped. Carrying that zero across is what made arrows drop out of the
     * destination no matter how the velocity was applied.
     *
     * <p>So a projectile that has landed, or is barely moving, is relaunched at a sensible
     * speed rather than at the speed it happens to have. Anything else keeps only the speed it
     * had through the gate: the opening is air, so an item dropped into it is falling when the
     * sweep finds it, and that fall came out of the far gate as a sideways throw.
     *
     * @param entity
     *            the entity crossing the gate
     * @param incoming
     *            its velocity on arrival
     * @param entryFacing
     *            the way the gate it entered faces, or null if unknown
     * @return the velocity to derive the exit speed from
     */
    private static Vector launchSpeed(final Entity entity, final Vector incoming, final BlockFace entryFacing)
    {
        if (!(entity instanceof Projectile))
        {
            return throughGate(incoming, entryFacing);
        }
        final boolean stopped = (entity instanceof AbstractArrow arrow) && arrow.isInBlock();
        if (stopped || incoming.lengthSquared() < (PROJECTILE_LAUNCH_SPEED * PROJECTILE_LAUNCH_SPEED))
        {
            return new Vector(PROJECTILE_LAUNCH_SPEED, 0, 0);
        }
        return incoming;
    }

    /**
     * The part of a velocity that carries an entity through a gate facing this way.
     *
     * @param incoming
     *            the entity's velocity
     * @param facing
     *            the way the gate faces, or null, which keeps the whole velocity
     * @return the velocity along the gate's axis
     */
    static Vector throughGate(final Vector incoming, final BlockFace facing)
    {
        if (facing == null)
        {
            return incoming;
        }
        final Vector axis = new Vector(facing.getModX(), facing.getModY(), facing.getModZ());
        if (axis.lengthSquared() == 0)
        {
            return incoming;
        }
        axis.normalize();
        return axis.multiply(incoming.dot(axis));
    }

    /**
     * Squared speed above which an entity counts as travelling under its own momentum,
     * rather than sitting in the portal. Chosen well below a walking pace.
     */
    private static final double MOVING_THRESHOLD_SQUARED = 0.01;

    /**
     * Sets an entity's velocity, tolerating an entity that has since been removed.
     *
     * @param velocity
     *            the velocity to apply
     */
    private static void applyVelocity(final Entity entity, final Vector velocity)
    {
        try
        {
            entity.setVelocity(velocity);
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Could not set exit velocity after gate sweep", e);
        }
    }

    /**
     * Decides whether an entity found in a wormhole is this sweep's responsibility.
     *
     * @param entity
     *            the candidate
     * @return true if it should be sent through
     */
    static boolean shouldSendThrough(final Entity entity)
    {
        if (entity == null)
        {
            return false;
        }
        // Players move on their own move events, minecarts and boats on vehicle-move
        // events, and a passenger travels with whatever is carrying it. Everything else is
        // this sweep's job — including a riderless horse or camel, which raises no event of
        // its own and was previously excluded here for being a Bukkit Vehicle.
        if (entity instanceof Player
            || WormholeXTremeVehicleListener.handlesMovementOf(entity)
            || entity.isInsideVehicle())
        {
            return false;
        }
        // A mount with a player aboard is the player listener's, which asks permission,
        // cooldown and fare; one it turned away would otherwise be carried through from here.
        if (carriesAPlayer(entity))
        {
            return false;
        }
        // Projectiles belong to ProjectileGateTracker, which follows each one and catches
        // it the tick it reaches a portal. This sweep is far too slow to see one crossing.
        if (entity instanceof Projectile)
        {
            return false;
        }
        // Item frames and paintings hang on a block rather than travelling through the
        // world. Sending one through a gate tears it off its wall and leaves it orphaned at
        // the far end, so a decorated gate frame would slowly strip itself every time the
        // gate opened.
        // Display and interaction entities are scenery another plugin put somewhere on purpose,
        // holograms and build previews among them, and have no business travelling.
        if ((entity instanceof Hanging) || (entity instanceof Display)
            || (entity instanceof Interaction))
        {
            return false;
        }
        // An entity that just arrived here is standing in the destination wormhole; without
        // this it would be bounced straight back on the next sweep.
        return !WormholeXTremeVehicleListener.isVehicleRecentlyTeleported(entity.getUniqueId());
    }

    /** Whether a player rides anywhere in this entity's passenger stack. */
    private static boolean carriesAPlayer(final Entity entity)
    {
        final List<Entity> parents = new ArrayList<>();
        final List<Entity> children = new ArrayList<>();
        WormholeXTremeVehicleListener.collectPassengerPairs(entity, parents, children);
        for (final Entity child : children)
        {
            if (child instanceof Player)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Teleports an entity, points it out of the destination gate, and re-seats anything
     * riding it.
     *
     * <p>Redirecting matters most for things that arrive under their own momentum. An
     * arrow shot north into a gate used to come out of the far end still travelling north,
     * whichever way that gate faced — often straight back into its own frame. Speed through the
     * gate is preserved and only the direction changes, so an item that rolled in at walking pace
     * still leaves at walking pace rather than being launched.
     *
     * @param entity
     *            the entity to move
     * @param arrival
     *            the destination
     * @param entryFacing
     *            the direction the gate it enters faces, or null if unknown
     * @param exitFacing
     *            the direction the destination gate faces
     * @param exitGate
     *            the gate it comes out of, or null where that does not matter
     */
    private static void sendThrough(final Entity entity, final Location arrival, final BlockFace entryFacing,
        final BlockFace exitFacing, final Stargate exitGate)
    {
        WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(entity.getUniqueId());
        final Vector incoming = entity.getVelocity();
        final Vector exit = WormholeXTremeVehicleListener.computeExitVelocity(exitFacing,
            launchSpeed(entity, incoming, entryFacing), 1.0);

        // A projectile cannot simply be moved. Teleporting an arrow leaves it flagged as
        // having landed — AbstractArrow.isInBlock() is readable but not settable — so it
        // arrives at the far gate already stuck and drops out of the air. The original is
        // consumed and a replacement fired instead.
        final Entity arrived = (entity instanceof Projectile shot)
            ? respawnProjectile(shot, arrival, exit, exitGate)
            : null;

        final List<Entity> parents = new ArrayList<>();
        final List<Entity> children = new ArrayList<>();
        final Entity moved;
        if (arrived != null)
        {
            moved = arrived;
        }
        else
        {
            WormholeXTremeVehicleListener.collectPassengerPairs(entity, parents, children);
            if (!RiddenTeleport.move(entity, arrival, parents, children))
            {
                return;
            }
            moved = entity;
        }

        // Velocity is applied here rather than inside the spawn callback, and again on the
        // next tick. Both matter: a spawn callback runs before the entity joins the world
        // and its velocity is discarded when it does, and a teleport clears motion so a
        // same-tick velocity is lost to that. Getting either wrong drops the arrow.
        applyVelocity(moved, exit);
        if (exit.lengthSquared() > MOVING_THRESHOLD_SQUARED)
        {
            WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), () ->
            {
                // Not to one that has hit something since: that would push a bounced arrow on again.
                if (moved.isValid() && !ProjectileGateTracker.hasHit(moved))
                {
                    applyVelocity(moved, exit);
                }
            }, 1L);
        }

        if (children.isEmpty())
        {
            return;
        }
        // Marked too, or a rider waiting in the far portal for its seat is swept straight back.
        for (final Entity child : children)
        {
            WormholeXTremeVehicleListener.markVehicleRecentlyTeleported(child.getUniqueId());
        }
        // The shared re-seat, whose retries fetch a passenger that did not land beside its mount.
        PassengerReattach.schedule(entity, parents, children, exit, 1L);
    }
}
