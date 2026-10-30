package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PetTestSupport;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What closing an iris does to whoever is standing in the opening: who is moved, who is not, how
 * often the safe spot is looked for, and that it all happens before the blocks go in.
 *
 * <p>Written ahead of moving this code out of {@code StargateBlockSetup} (#430, #734): each test
 * fails when the one guard or call it is named for is taken out. {@link IrisDoesNotEntombTest}
 * pins the head-and-feet decision and {@link IrisClearsRiddenMountTest} the ridden horse; these
 * are the cases neither reached.
 */
class IrisClearanceTest
{
    private static final int BX = 10;
    private static final int BY = 64;
    private static final int BZ = 20;

    private World world;
    private Stargate gate;
    private Location exit;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        gate = new Stargate();
        gate.setGateName("IrisGate");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        gate.getGatePortalBlocks().add(new Location(world, BX, BY + 1, BZ));
        // No world on the exit: the safe-location search hands it straight back.
        exit = new Location(null, BX + 0.5, BY, BZ + 3.5);
        gate.setGatePlayerTeleportLocation(exit);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    private Zombie zombieAt(final int y)
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.isValid()).thenReturn(true);
        when(zombie.getLocation()).thenReturn(new Location(world, BX + 0.5, y, BZ + 0.5));
        when(zombie.teleport(any(Location.class))).thenReturn(true);
        return zombie;
    }

    private static long teleports(final Entity entity)
    {
        return mockingDetails(entity).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("teleport")).count();
    }

    @Test
    void aDroppedItemInTheOpeningIsLeftWhereItLies()
    {
        final Item item = mock(Item.class);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        when(item.getLocation()).thenReturn(new Location(world, BX + 0.5, BY, BZ + 0.5));
        final Zombie zombie = zombieAt(BY);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(item, zombie));

        StargateBlockSetup.clearIrisPath(gate);

        assertEquals(1, teleports(zombie), "a living thing in the opening is moved");
        assertEquals(0, teleports(item), "a dropped item does not suffocate and is not carried to the exit");
    }

    @Test
    void aPlayerMovedClearGetsFiveTicksOfDamageImmunity()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isValid()).thenReturn(true);
        PetTestSupport.standsWhereTeleported(player, new Location(world, BX + 0.5, BY, BZ + 0.5));
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(player));

        StargateBlockSetup.clearIrisPath(gate);

        verify(player).setNoDamageTicks(5);
    }

    @Test
    void theSafeSpotIsLookedForOnceWhateverTheNumberInTheWayAndNotAtAllWhenNobodyIs()
    {
        final Zombie feet = zombieAt(BY);
        final Zombie head = zombieAt(BY + 1);
        final Zombie outside = zombieAt(BY + 5);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(feet, head));
        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            utils.when(() -> WorldUtils.findSafePlayerLocation(any(Location.class))).thenReturn(exit);

            StargateBlockSetup.clearIrisPath(gate);

            utils.verify(() -> WorldUtils.findSafePlayerLocation(any(Location.class)), times(1));
        }
        assertEquals(1, teleports(feet), "the one at the feet");
        assertEquals(1, teleports(head), "the one at the head");

        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(outside));
        try (MockedStatic<WorldUtils> utils = mockStatic(WorldUtils.class))
        {
            StargateBlockSetup.clearIrisPath(gate);

            utils.verify(() -> WorldUtils.findSafePlayerLocation(any(Location.class)), never());
        }
        assertEquals(0, teleports(outside), "and nobody outside the opening is touched");
    }

    @Test
    void twoRidersOfOneMountMoveItOnce()
    {
        final Horse mount = mock(Horse.class);
        when(mount.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mount.isValid()).thenReturn(true);
        when(mount.teleport(any(Location.class))).thenReturn(true);
        final Zombie one = zombieAt(BY);
        final Zombie two = zombieAt(BY + 1);
        when(one.getVehicle()).thenReturn(mount);
        when(two.getVehicle()).thenReturn(mount);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(one, two));

        StargateBlockSetup.clearIrisPath(gate);

        assertEquals(1, teleports(mount), "the stack goes once, not once per rider");
    }

    @Test
    void aStackIsFollowedDownToItsBottomButOnlySixteenSeatsFar()
    {
        // Twenty seats deep, each riding the next: the walk gives up after sixteen.
        final Zombie[] seats = new Zombie[20];
        for (int i = 0; i < seats.length; i++)
        {
            seats[i] = zombieAt(BY);
        }
        for (int i = 0; i < seats.length - 1; i++)
        {
            when(seats[i].getVehicle()).thenReturn(seats[i + 1]);
        }
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(seats[0]));

        StargateBlockSetup.clearIrisPath(gate);

        assertEquals(1, teleports(seats[16]), "sixteen seats down is where it stops");
        assertEquals(0, teleports(seats[0]), "not the rider who was found");
        assertEquals(0, teleports(seats[19]), "and not the bottom of a stack that deep");
    }

    @Test
    void oneEntityThatCannotBeMovedDoesNotStopTheRest()
    {
        final Zombie broken = mock(Zombie.class);
        when(broken.getUniqueId()).thenReturn(UUID.randomUUID());
        when(broken.getLocation()).thenThrow(new IllegalStateException("not in a world"));
        final Zombie fine = zombieAt(BY);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(broken, fine));

        assertDoesNotThrow(() -> StargateBlockSetup.clearIrisPath(gate));

        assertEquals(1, teleports(fine), "the one after the failure is still moved");
    }

    @Test
    void aGateWithoutAWorldABoxOrAnExitIsLeftAlone()
    {
        final Zombie zombie = zombieAt(BY);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(zombie));

        final Stargate noWorld = new Stargate();
        noWorld.setGateName("NoWorld");
        noWorld.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        noWorld.setGatePlayerTeleportLocation(exit);
        assertDoesNotThrow(() -> StargateBlockSetup.clearIrisPath(noWorld), "no world to ask");

        final Stargate noBox = new Stargate();
        noBox.setGateName("NoBox");
        noBox.setGateWorld(world);
        noBox.setGatePlayerTeleportLocation(exit);
        StargateBlockSetup.clearIrisPath(noBox);

        final Stargate noExit = new Stargate();
        noExit.setGateName("NoExit");
        noExit.setGateWorld(world);
        noExit.getGatePortalBlocks().add(new Location(world, BX, BY, BZ));
        StargateBlockSetup.clearIrisPath(noExit);

        verify(world, never()).getNearbyEntities(any(BoundingBox.class));
        assertEquals(0, teleports(zombie), "nobody is moved for a gate that cannot say where to");
    }

    @Test
    void aHorizontalIrisClearsTheOpeningBeforeItsBlocksAreSet()
    {
        gate.setGateFacing(BlockFace.UP);
        final Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        final Zombie zombie = zombieAt(BY);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.<Entity>of(zombie));

        StargateBlockSetup.fillGateIris(gate, Material.GLASS);

        final InOrder order = inOrder(zombie, block);
        order.verify(zombie).teleport(any(Location.class));
        order.verify(block, times(2)).setType(Material.GLASS);
        assertFalse(StargateBlockSetup.irisIsDrawn(gate), "this one is built out of real blocks");
        assertEquals(1, teleports(zombie), "and whoever stood in it went once");
    }
}
