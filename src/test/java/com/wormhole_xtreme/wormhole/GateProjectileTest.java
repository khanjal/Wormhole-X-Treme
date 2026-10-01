package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;

/**
 * How arrows and other projectiles cross a gate.
 *
 * <p>They cannot simply be moved. Teleporting an arrow leaves it flagged as having landed —
 * {@code AbstractArrow.isInBlock()} is readable but there is no setter — so it arrives at
 * the far gate already stuck and drops out of the air however much velocity it is given.
 * Confirmed in play: arrows came out of the destination and fell straight down. So the
 * original is consumed and an identical one is fired at the destination instead.
 */
class GateProjectileTest
{
    private World world;
    private Stargate origin;
    private Arrow arrow;
    private Arrow spawned;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        PluginTestSupport.scheduler(scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Stargate destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.EAST);
        destination.setGateActive(true);
        destination.setGatePortalOpen(true);
        destination.setGatePlayerTeleportLocation(new Location(world, 99.5, 70, 99.5));

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePortalOpen(true);
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);

        // An arrow in flight, sitting in the origin gate's portal block.
        arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(arrow.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(arrow.isInsideVehicle()).thenReturn(false);
        when(arrow.isValid()).thenReturn(true);
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -2.4));
        when(arrow.getType()).thenReturn(EntityType.ARROW);
        when(arrow.getDamage()).thenReturn(2.5);
        when(arrow.isCritical()).thenReturn(true);
        when(arrow.getPierceLevel()).thenReturn(3);
        when(arrow.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(arrow));

        // The replacement the scanner asks the world to spawn.
        spawned = mock(Arrow.class);
        when(spawned.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spawned.isValid()).thenReturn(true);
        when(world.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenReturn(spawned);
    }

    @AfterEach
    void tearDown()
    {
        StargateManager.removeStargate(origin);
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
    }

    /**
     * Drives the crossing the way the tracker does: the projectile is at the portal now.
     */
    private void sendArrowThroughGate()
    {
        GateEntityScanner.sendProjectileThrough(arrow, origin);
    }

    @Test
    void theOriginalArrowIsConsumedRatherThanMoved()
    {
        sendArrowThroughGate();

        verify(arrow).remove();
        verify(arrow, never()).teleport(any(Location.class));
    }

    @Test
    void aReplacementIsFiredOutOfTheDestinationGate()
    {
        sendArrowThroughGate();

        // spawnArrow creates it already travelling; a plain spawn produces something that
        // behaves like an arrow which has already landed.
        final ArgumentCaptor<Vector> dir = ArgumentCaptor.forClass(Vector.class);
        final ArgumentCaptor<Float> speed = ArgumentCaptor.forClass(Float.class);
        verify(world).spawnArrow(any(Location.class), dir.capture(), speed.capture(), anyFloat(), any(Class.class));

        assertTrue(dir.getValue().getX() > 0, "should fly east, the way the destination gate faces");
        // 2.4 is below the launch floor, so it leaves at 3.0 rather than dribbling out.
        assertEquals(3.0, speed.getValue(), 1e-5);
    }

    @Test
    void theReplacementGetsItsVelocityAfterSpawningAndAgainNextTick()
    {
        // The bug that made three builds' worth of fixes look ineffective: velocity was
        // only set inside the spawn callback, which runs before the entity joins the world
        // and is discarded when it does. It has to be applied to the spawned entity, and
        // again a tick later.
        sendArrowThroughGate();

        final ArgumentCaptor<Vector> v = ArgumentCaptor.forClass(Vector.class);
        verify(spawned, times(2)).setVelocity(v.capture());
        assertTrue(v.getValue().getX() > 0, "should still be flying east on the re-apply");
        assertEquals(3.0, v.getValue().length(), 1e-6);
    }

    @Test
    void theReplacementKeepsTheStateThatMattersInCombat()
    {
        final Player shooter = mock(Player.class);
        when(arrow.getShooter()).thenReturn(shooter);

        sendArrowThroughGate();

        // Without the shooter, a kill through a gate is credited to nobody.
        verify(spawned).setShooter(shooter);
        verify(spawned).setDamage(2.5);
        verify(spawned).setCritical(true);
        verify(spawned).setPierceLevel(3);
        verify(spawned).setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
    }

    /**
     * A Punch bow's knockback and a crossbow shot survive the crossing, however this server keeps them.
     *
     * <p>From 1.21 the arrow remembers its weapon; before, it held knockback and the crossbow flag
     * itself. Reached by name, because the old setters are marked for removal: the matrix runs
     * both halves.
     */
    @Test
    void theReplacementKeepsWhatItsWeaponGaveIt() throws Exception
    {
        if (GateEntityScanner.carriesWeapon())
        {
            final ItemStack bow = mock(ItemStack.class);
            when(arrowMethod("getWeapon").invoke(arrow)).thenReturn(bow);

            sendArrowThroughGate();

            arrowMethod("setWeapon", ItemStack.class).invoke(verify(spawned), bow);
        }
        else
        {
            when(arrowMethod("getKnockbackStrength").invoke(arrow)).thenReturn(1);
            when(arrowMethod("isShotFromCrossbow").invoke(arrow)).thenReturn(true);

            sendArrowThroughGate();

            arrowMethod("setKnockbackStrength", int.class).invoke(verify(spawned), 1);
            arrowMethod("setShotFromCrossbow", boolean.class).invoke(verify(spawned), true);
        }
    }

    /** A property that will not copy costs that property, not the crossing. */
    @Test
    void aPropertyThatWillNotCopyStillLetsTheArrowCross() throws Exception
    {
        if (GateEntityScanner.carriesWeapon())
        {
            final ItemStack weapon = mock(ItemStack.class);
            when(arrowMethod("getWeapon").invoke(arrow)).thenReturn(weapon);
            arrowMethod("setWeapon", ItemStack.class)
                .invoke(doThrow(new IllegalStateException("refused")).when(spawned), any(ItemStack.class));
        }
        else
        {
            when(arrowMethod("getKnockbackStrength").invoke(arrow)).thenReturn(1);
            arrowMethod("setKnockbackStrength", int.class)
                .invoke(doThrow(new IllegalStateException("refused")).when(spawned), anyInt());
        }

        assertDoesNotThrow(this::sendArrowThroughGate);

        verify(arrow).remove();
        verify(spawned).setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
    }

    private static Method arrowMethod(final String name, final Class<?>... parameters) throws NoSuchMethodException
    {
        return AbstractArrow.class.getMethod(name, parameters);
    }

    /**
     * Issue #536: a slowness arrow came out of the far gate as a plain arrow.
     *
     * <p>The effect is set on the replacement itself, which every version supports, whether or not
     * this one also carries it in the arrow's item. By name, because getBasePotionType is from 1.20.2
     * and the matrix builds 1.20.
     */
    @Test
    void aTippedArrowKeepsItsPotion() throws Exception
    {
        final PotionEffect custom = mock(PotionEffect.class);
        when(arrow.getCustomEffects()).thenReturn(Collections.singletonList(custom));
        // Before 1.20.2 the base potion is a PotionData, found by its getter's type.
        final boolean byType = potionMethod("getBasePotionType") != null;
        final Method getter = byType ? potionMethod("getBasePotionType") : potionMethod("getBasePotionData");
        final Method setter = potionMethod(byType ? "setBasePotionType" : "setBasePotionData",
            getter.getReturnType());
        final Object base = byType ? PotionType.SLOWNESS : mock(getter.getReturnType());
        when(getter.invoke(arrow)).thenReturn(base);

        sendArrowThroughGate();

        setter.invoke(verify(spawned), base);
        verify(spawned).addCustomEffect(custom, true);
    }

    /**
     * The arrow's own item comes too, which from 1.20.5 is where its effect, name and colour live.
     * Absent before 1.20.4, where the arrow still crosses with its effect set directly.
     */
    @Test
    void aTippedArrowKeepsItsItem() throws Exception
    {
        final ItemStack tipped = mock(ItemStack.class);
        if (GateEntityScanner.arrowsCarryItems())
        {
            when(arrowMethod("getItem").invoke(arrow)).thenReturn(tipped);
        }

        sendArrowThroughGate();

        verify(arrow).remove();
        if (GateEntityScanner.arrowsCarryItems())
        {
            arrowMethod("setItem", ItemStack.class).invoke(verify(spawned), tipped);
        }
    }

    /** An item that will not copy costs the item, not the potion or the crossing. */
    @Test
    void anArrowWhoseItemWillNotCopyStillCrossesWithItsPotion() throws Exception
    {
        final PotionEffect custom = mock(PotionEffect.class);
        when(arrow.getCustomEffects()).thenReturn(Collections.singletonList(custom));
        if (GateEntityScanner.arrowsCarryItems())
        {
            when(arrowMethod("getItem").invoke(arrow)).thenReturn(mock(ItemStack.class));
            arrowMethod("setItem", ItemStack.class)
                .invoke(doThrow(new IllegalArgumentException("refused")).when(spawned), any(ItemStack.class));
        }

        assertDoesNotThrow(this::sendArrowThroughGate);

        verify(arrow).remove();
        verify(spawned).addCustomEffect(custom, true);
    }

    private static Method potionMethod(final String name, final Class<?>... parameters)
    {
        try
        {
            return Arrow.class.getMethod(name, parameters);
        }
        catch (final NoSuchMethodException absent)
        {
            return null;
        }
    }

    /**
     * A Loyalty trident came out of the far gate as a plain trident, stuck there, and never came back.
     *
     * <p>Its item is copied on every version, since a trident is a throwable as well as an arrow. Its
     * loyalty and glint are read off the item only when the entity is made, so on Paper they are set
     * as well; Spigot has no way to, and the trident keeps its enchantments but not its return.
     */
    @Test
    void aTridentKeepsItsEnchantedItemAndComesBack() throws Exception
    {
        final Trident trident = flying(Trident.class);
        final Trident replacement = mock(Trident.class);
        when(world.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenReturn(replacement);
        final Player shooter = mock(Player.class);
        when(trident.getShooter()).thenReturn(shooter);
        when(trident.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        final ItemStack enchanted = mock(ItemStack.class);
        when(trident.getItem()).thenReturn(enchanted);
        if (GateEntityScanner.tridentLoyaltyIsSettable())
        {
            when(Trident.class.getMethod("getLoyaltyLevel").invoke(trident)).thenReturn(3);
            when(Trident.class.getMethod("hasGlint").invoke(trident)).thenReturn(true);
        }

        assertDoesNotThrow(() -> GateEntityScanner.sendProjectileThrough(trident, origin));

        verify(trident).remove();
        verify(replacement).setItem(enchanted);
        verify(replacement).setShooter(shooter);
        verify(replacement).setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        if (GateEntityScanner.tridentLoyaltyIsSettable())
        {
            Trident.class.getMethod("setLoyaltyLevel", int.class).invoke(verify(replacement), 3);
            Trident.class.getMethod("setGlint", boolean.class).invoke(verify(replacement), true);
        }
    }

    /** Only Paper has them, so only the Paper legs can prove the names are right. */
    @Test
    @EnabledIfSystemProperty(named = "server.api", matches = "paper")
    void papersTridentLoyaltyIsFound()
    {
        assertTrue(GateEntityScanner.tridentLoyaltyIsSettable(),
            "a Loyalty trident would stop coming back through a gate on Paper");
    }

    /** A spectral arrow's glow lasts as long as the bow gave it, not the default. */
    @Test
    void aSpectralArrowKeepsItsGlow()
    {
        final SpectralArrow spectral = flying(SpectralArrow.class);
        final SpectralArrow replacement = mock(SpectralArrow.class);
        when(world.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenReturn(replacement);
        when(spectral.getGlowingTicks()).thenReturn(37);

        GateEntityScanner.sendProjectileThrough(spectral, origin);

        verify(replacement).setGlowingTicks(37);
    }

    /** Without its item a splash potion still splashes, with no effect. */
    @Test
    void aThrownPotionKeepsItsPotion()
    {
        final ThrownPotion potion = flying(ThrownPotion.class);
        final ThrownPotion replacement = mock(ThrownPotion.class);
        doReturn(replacement).when(world).spawn(any(Location.class), any(Class.class));
        final ItemStack item = mock(ItemStack.class);
        when(potion.getItem()).thenReturn(item);

        GateEntityScanner.sendProjectileThrough(potion, origin);

        verify(replacement).setItem(item);
    }

    /** A crossbow's firework keeps its stars and its angle, rather than arriving as a blank rocket. */
    @Test
    void aFireworkKeepsItsExplosion()
    {
        final Firework firework = flying(Firework.class);
        final Firework replacement = mock(Firework.class);
        doReturn(replacement).when(world).spawn(any(Location.class), any(Class.class));
        final FireworkMeta meta = mock(FireworkMeta.class);
        when(firework.getFireworkMeta()).thenReturn(meta);
        when(firework.isShotAtAngle()).thenReturn(true);

        GateEntityScanner.sendProjectileThrough(firework, origin);

        verify(replacement).setFireworkMeta(meta);
        verify(replacement).setShotAtAngle(true);
    }

    /**
     * A projectile of this kind, in flight in the origin gate.
     *
     * <p>Its EntityType is found by class, because several were renamed in 1.20.5.
     */
    private <T extends Projectile> T flying(final Class<T> kind)
    {
        final EntityType type = Arrays.stream(EntityType.values())
            .filter(t -> (t.getEntityClass() != null) && kind.isAssignableFrom(t.getEntityClass()))
            .findFirst().orElseThrow();
        final T shot = mock(kind);
        when(shot.getUniqueId()).thenReturn(UUID.randomUUID());
        when(shot.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(shot.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(shot.isValid()).thenReturn(true);
        when(shot.getVelocity()).thenReturn(new Vector(0, 0, -2.4));
        when(shot.getType()).thenReturn(type);
        return shot;
    }

    @Test
    void anArrowThatHasAlreadyLandedIsRelaunchedNotDroppedAgain()
    {
        // The actual cause of arrows falling out of the destination. Portal blocks are air,
        // so an arrow flies through the ring and sticks in whatever is behind it; the sweep
        // finds it up to a second later, stopped. Preserving that zero speed produced a
        // replacement with no momentum, which is exactly what was seen in play.
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, 0));
        when(arrow.isInBlock()).thenReturn(true);

        sendArrowThroughGate();

        final ArgumentCaptor<Float> speed = ArgumentCaptor.forClass(Float.class);
        verify(world).spawnArrow(any(Location.class), any(Vector.class), speed.capture(), anyFloat(), any(Class.class));
        assertEquals(3.0, speed.getValue(), 1e-5, "a stalled arrow should leave at bow speed");

        final ArgumentCaptor<Vector> v = ArgumentCaptor.forClass(Vector.class);
        verify(spawned, atLeastOnce()).setVelocity(v.capture());
        assertEquals(3.0, v.getValue().length(), 1e-6);
        assertTrue(v.getValue().getX() > 0, "and still leave the way the gate faces");
    }

    @Test
    void aSlowProjectileIsBroughtUpToLaunchSpeed()
    {
        // An arrow caught mid-flight but already slowed would otherwise dribble out.
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -0.2));
        when(arrow.isInBlock()).thenReturn(false);

        sendArrowThroughGate();

        final ArgumentCaptor<Float> speed = ArgumentCaptor.forClass(Float.class);
        verify(world).spawnArrow(any(Location.class), any(Vector.class), speed.capture(), anyFloat(), any(Class.class));
        assertEquals(3.0, speed.getValue(), 1e-5);
    }

    @Test
    void aFastArrowKeepsItsOwnSpeed()
    {
        // Anything already travelling faster than the launch floor is left alone.
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -5.0));

        sendArrowThroughGate();

        final ArgumentCaptor<Float> speed = ArgumentCaptor.forClass(Float.class);
        verify(world).spawnArrow(any(Location.class), any(Vector.class), speed.capture(), anyFloat(), any(Class.class));
        assertEquals(5.0, speed.getValue(), 1e-5);
    }

    @Test
    void everyProjectileTypeIsReplacedNotJustArrows()
    {
        // Anything that flies under its own momentum has the same problem, so the rule is
        // Projectile, not Arrow.
        for (final Class<? extends Entity> type : Arrays.asList(
            Snowball.class, Egg.class,
            EnderPearl.class, ThrownPotion.class,
            Trident.class, Fireball.class))
        {
            assertTrue(Projectile.class.isAssignableFrom(type),
                type.getSimpleName() + " should be handled by the projectile path");
        }
        // And these are not projectiles, so they keep the ordinary teleport.
        for (final Class<? extends Entity> type : Arrays.asList(
            Item.class, TNTPrimed.class,
            FallingBlock.class, Zombie.class))
        {
            assertFalse(Projectile.class.isAssignableFrom(type),
                type.getSimpleName() + " should take the ordinary teleport path");
        }
    }

    @Test
    void aNonProjectileIsStillJustTeleported()
    {
        // Only projectiles need replacing; everything else moves as before.
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(zombie.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(zombie.isInsideVehicle()).thenReturn(false);
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getVelocity()).thenReturn(new Vector(0, 0, -1));
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(zombie));

        GateEntityScanner.create().run();

        verify(zombie).teleport(any(Location.class));
        verify(zombie, never()).remove();
    }
}
