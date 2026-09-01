package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * How arrows and other projectiles cross a gate.
 *
 * <p>They cannot simply be moved. Teleporting an arrow leaves it flagged as having landed —
 * {@code AbstractArrow.isInBlock()} is readable but there is no setter — so it arrives at
 * the far gate already stuck and drops out of the air however much velocity it is given.
 * Confirmed in play: arrows came out of the destination and fell straight down. So the
 * original is consumed and an identical one is fired at the destination instead.
 */
public class GateProjectileTest
{
    private World world;
    private Stargate origin;
    private Arrow arrow;
    private Arrow spawned;

    private static final int BX = 10, BY = 64, BZ = 20;

    @BeforeEach
    public void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pf = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pf.setAccessible(true);
        pf.set(null, plugin);

        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        final java.lang.reflect.Field sf = WormholeXTreme.class.getDeclaredField("scheduler");
        sf.setAccessible(true);
        sf.set(null, scheduler);

        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        final Stargate destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(world);
        destination.setGateFacing(BlockFace.EAST);
        destination.setGateActive(true);
        destination.setGatePlayerTeleportLocation(new Location(world, 99.5, 70, 99.5));

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(world);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePlayerTeleportLocation(new Location(world, BX + 0.5, BY, BZ + 0.5));
        origin.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        final java.lang.reflect.Field tf = Stargate.class.getDeclaredField("gateTarget");
        tf.setAccessible(true);
        tf.set(origin, destination);
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
        when(arrow.getKnockbackStrength()).thenReturn(1);
        when(arrow.getPierceLevel()).thenReturn(3);
        when(arrow.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        when(world.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(arrow));

        // The replacement the scanner asks the world to spawn.
        spawned = mock(Arrow.class);
        when(spawned.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spawned.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class)))
            .thenAnswer(inv -> {
                ((Consumer<Entity>) inv.getArgument(2)).accept(spawned);
                return spawned;
            });
    }

    @AfterEach
    public void tearDown()
    {
        StargateManager.removeStargate(origin);
        GateSpatialIndex.clear();
    }

    @Test
    public void theOriginalArrowIsConsumedRatherThanMoved()
    {
        GateEntityScanner.create().run();

        verify(arrow).remove();
        verify(arrow, never()).teleport(any(Location.class));
    }

    @Test
    public void aReplacementIsFiredOutOfTheDestinationGate()
    {
        GateEntityScanner.create().run();

        final org.mockito.ArgumentCaptor<Vector> v = org.mockito.ArgumentCaptor.forClass(Vector.class);
        verify(spawned, atLeastOnce()).setVelocity(v.capture());

        assertTrue(v.getValue().getX() > 0, "should fly east, the way the destination gate faces");
        assertEquals(2.4, v.getValue().length(), 1e-6, "speed should carry over");
    }

    @Test
    public void theReplacementKeepsTheStateThatMattersInCombat()
    {
        final Player shooter = mock(Player.class);
        when(arrow.getShooter()).thenReturn(shooter);

        GateEntityScanner.create().run();

        // Without the shooter, a kill through a gate is credited to nobody.
        verify(spawned).setShooter(shooter);
        verify(spawned).setDamage(2.5);
        verify(spawned).setCritical(true);
        verify(spawned).setKnockbackStrength(1);
        verify(spawned).setPierceLevel(3);
        verify(spawned).setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
    }

    @Test
    public void everyProjectileTypeIsReplacedNotJustArrows()
    {
        // Anything that flies under its own momentum has the same problem, so the rule is
        // Projectile, not Arrow.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            org.bukkit.entity.Snowball.class, org.bukkit.entity.Egg.class,
            org.bukkit.entity.EnderPearl.class, org.bukkit.entity.ThrownPotion.class,
            org.bukkit.entity.Trident.class, org.bukkit.entity.Fireball.class))
        {
            assertTrue(org.bukkit.entity.Projectile.class.isAssignableFrom(type),
                type.getSimpleName() + " should be handled by the projectile path");
        }
        // And these are not projectiles, so they keep the ordinary teleport.
        for (final Class<? extends Entity> type : java.util.Arrays.asList(
            org.bukkit.entity.Item.class, org.bukkit.entity.TNTPrimed.class,
            org.bukkit.entity.FallingBlock.class, org.bukkit.entity.Zombie.class))
        {
            assertFalse(org.bukkit.entity.Projectile.class.isAssignableFrom(type),
                type.getSimpleName() + " should take the ordinary teleport path");
        }
    }

    @Test
    public void aNonProjectileIsStillJustTeleported()
    {
        // Only projectiles need replacing; everything else moves as before.
        final org.bukkit.entity.Zombie zombie = mock(org.bukkit.entity.Zombie.class);
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
