package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateTestSupport;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * With {@code same-world-only} on, nothing crosses between worlds by gate, not only players on foot.
 *
 * <p>The research facility's stage 6 found the gap: walking into a gate to the Nether was refused,
 * while a minecart carried the same player through, and only the walking path asked the setting at
 * all. The dial now refuses another world up front; these pin the other ways across, which still
 * matter for a wormhole that was already open when the setting was turned on.
 */
class SameWorldOnlyTest
{
    private static final int BX = 10, BY = 64, BZ = 20;

    private World here;
    private World nether;
    private Stargate origin;
    private Stargate destination;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(), any(Runnable.class), anyLong()))
            .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return 1; });
        PluginTestSupport.scheduler(scheduler);

        here = mock(World.class);
        when(here.getName()).thenReturn("world");
        nether = mock(World.class);
        when(nether.getName()).thenReturn("world_nether");

        destination = new Stargate();
        destination.setGateName("destination");
        destination.setGateWorld(nether);
        destination.setGateFacing(BlockFace.EAST);
        destination.setGateActive(true);
        destination.setGatePortalOpen(true);
        destination.setGatePlayerTeleportLocation(new Location(nether, 99.5, 70, 99.5));

        origin = new Stargate();
        origin.setGateName("origin");
        origin.setGateWorld(here);
        origin.setGateFacing(BlockFace.NORTH);
        origin.setGateActive(true);
        origin.setGatePortalOpen(true);
        origin.setGatePlayerTeleportLocation(new Location(here, BX + 0.5, BY, BZ + 0.5));
        origin.getGatePortalBlocks().add(new Location(here, BX, BY, BZ));
        StargateTestSupport.target(origin, destination);
        StargateManager.registerStargate(origin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        StargateManager.removeStargate(origin);
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private static void sameWorldOnly(final boolean on)
    {
        ConfigTestSupport.set(ConfigKeys.SAME_WORLD_ONLY, on);
    }

    /** A zombie standing in the origin gate's opening, walking north into it. */
    private Zombie zombieInTheOpening()
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.getLocation()).thenReturn(new Location(here, BX + 0.5, BY, BZ + 0.5));
        when(zombie.getWorld()).thenReturn(here);
        when(zombie.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getVelocity()).thenReturn(new Vector(0, 0, -1.5));
        when(zombie.teleport(any(Location.class))).thenReturn(true);
        when(here.getNearbyEntities(any(BoundingBox.class)))
            .thenReturn(Collections.<Entity>singletonList(zombie));
        return zombie;
    }

    /** An item thrown into the opening. */
    private Item itemInTheOpening()
    {
        final Item item = mock(Item.class);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        when(item.getLocation()).thenReturn(new Location(here, BX + 0.5, BY, BZ + 0.5));
        when(item.getWorld()).thenReturn(here);
        when(item.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(item.isValid()).thenReturn(true);
        when(item.getVelocity()).thenReturn(new Vector(0, 0, -0.5));
        when(item.teleport(any(Location.class))).thenReturn(true);
        return item;
    }

    /** An arrow at the opening, and the one the far world would fire in its place. */
    private Arrow arrowAtTheOpening()
    {
        final Arrow arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getLocation()).thenReturn(new Location(here, BX + 0.5, BY, BZ + 0.5));
        when(arrow.getWorld()).thenReturn(here);
        when(arrow.getPassengers()).thenReturn(Collections.<Entity>emptyList());
        when(arrow.isValid()).thenReturn(true);
        when(arrow.getVelocity()).thenReturn(new Vector(0, 0, -2.4));
        when(arrow.getType()).thenReturn(EntityType.ARROW);
        when(arrow.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        final Arrow fired = mock(Arrow.class);
        when(fired.getUniqueId()).thenReturn(UUID.randomUUID());
        when(fired.isValid()).thenReturn(true);
        when(nether.spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class)))
            .thenReturn(fired);
        return arrow;
    }

    /** A mob in a wormhole to another world stays where it is. */
    @Test
    void theSweepDoesNotCarryAMobIntoAnotherWorld()
    {
        final Zombie zombie = zombieInTheOpening();
        sameWorldOnly(true);

        GateEntityScanner.create().run();

        verify(zombie, never()).teleport(any(Location.class));
    }

    /** The same mob, on a server that allows it, goes through: what makes the test above mean anything. */
    @Test
    void theSweepCarriesAMobIntoAnotherWorldWhenTheServerAllowsIt()
    {
        final Zombie zombie = zombieInTheOpening();
        sameWorldOnly(false);

        GateEntityScanner.create().run();

        verify(zombie, atLeastOnce()).teleport(any(Location.class));
    }

    /** A thrown item is left on this side, neither sent nor destroyed. */
    @Test
    void anItemIsNotThrownIntoAnotherWorld()
    {
        final Item item = itemInTheOpening();
        sameWorldOnly(true);

        assertFalse(GateEntityScanner.sendItemThrough(item, origin), "nothing was done with it");

        verify(item, never()).teleport(any(Location.class));
        verify(item, never()).remove();
    }

    /** The same item, on a server that allows it, is sent. */
    @Test
    void anItemIsThrownIntoAnotherWorldWhenTheServerAllowsIt()
    {
        final Item item = itemInTheOpening();
        sameWorldOnly(false);

        assertTrue(GateEntityScanner.sendItemThrough(item, origin));

        verify(item).teleport(any(Location.class));
    }

    /**
     * An arrow is not fired out of another world. An ender pearl is one too, and one thrown through
     * would carry its owner across without the walking path ever being asked.
     */
    @Test
    void anArrowIsNotFiredIntoAnotherWorld()
    {
        final Arrow arrow = arrowAtTheOpening();
        sameWorldOnly(true);

        assertFalse(GateEntityScanner.sendProjectileThrough(arrow, origin));

        verify(arrow, never()).remove();
        verify(nether, never()).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(),
            any(Class.class));
    }

    /** The same arrow, on a server that allows it, is fired out of the far gate. */
    @Test
    void anArrowIsFiredIntoAnotherWorldWhenTheServerAllowsIt()
    {
        final Arrow arrow = arrowAtTheOpening();
        sameWorldOnly(false);

        assertTrue(GateEntityScanner.sendProjectileThrough(arrow, origin));

        verify(nether).spawnArrow(any(Location.class), any(Vector.class), anyFloat(), anyFloat(), any(Class.class));
    }

    /** The rule is about crossing worlds; a trip within one is never refused by it. */
    @Test
    void aTripWithinOneWorldIsNeverRefused()
    {
        sameWorldOnly(true);

        assertFalse(StargateRestrictions.isCrossWorldRefused(here, new Location(here, 0, 64, 0)));
        assertTrue(StargateRestrictions.isCrossWorldRefused(here, new Location(nether, 0, 64, 0)),
            "and one into another world is");
    }

    /** A world nobody can name is not grounds to refuse: an unknown is not a crossing. */
    @Test
    void anUnknownWorldIsNotRefused()
    {
        sameWorldOnly(true);

        assertFalse(StargateRestrictions.isCrossWorldRefused(null, nether));
        assertFalse(StargateRestrictions.isCrossWorldRefused(here, (World) null));
        assertFalse(StargateRestrictions.isCrossWorldRefused(here, (Location) null));
    }
}
