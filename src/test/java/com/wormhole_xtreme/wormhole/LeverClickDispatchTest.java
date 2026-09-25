package com.wormhole_xtreme.wormhole;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Which lever a click was aimed at, when a gate has two of them next to each other.
 *
 * <p>The dial lever opens the gate and the iris lever closes the iris, and on the Standard
 * layout they sit one block apart. So a click is matched exactly first and only then by
 * adjacency, and when both are adjacent the dial wins -- otherwise reaching for the dial on
 * a gate with an iris would sometimes shut the iris instead.
 *
 * <p>That ordering was not covered. {@link ButtonLeverHitTest} pins the pending-command
 * branches at the head of the method and {@link GateActivationSwitchTest} the helpers at its
 * tail; the dispatch between them had nothing.
 */
class LeverClickDispatchTest
{
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";

    private World world;
    private Player player;
    /** The iris lever saves the gate, and the real save writes into the working directory. */
    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        db = mockStatic(StargateDBManager.class);

        // Toggling a lever schedules the block update that follows it.
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(Runnable.class),
            org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        PluginTestSupport.scheduler(scheduler);

        GateSpatialIndex.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("w");

        player = mock(Player.class);
        when(player.getName()).thenReturn("clicker");
        when(player.isOp()).thenReturn(false);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
    }

    @AfterEach
    void tearDown()
    {
        db.close();
        GateSpatialIndex.clear();
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
        PluginTestSupport.forgetAllGates();
    }

    private Block blockAt(final int x, final int y, final int z)
    {
        final Block b = mock(Block.class);
        when(b.getType()).thenReturn(Material.LEVER);
        when(b.getWorld()).thenReturn(world);
        when(b.getX()).thenReturn(Integer.valueOf(x));
        when(b.getY()).thenReturn(Integer.valueOf(y));
        when(b.getZ()).thenReturn(Integer.valueOf(z));
        when(b.getLocation()).thenReturn(new Location(world, x, y, z));
        // A lever reports Switch data, which is what the iris toggle reads and sets.
        final org.bukkit.block.data.type.Switch leverData = mock(org.bukkit.block.data.type.Switch.class);
        when(b.getBlockData()).thenReturn(leverData);
        return b;
    }

    /**
     * A gate whose dial and iris levers are one block apart, registered against {@code at}.
     *
     * <p>The click block is what the index resolves, so each test registers the block it is
     * about to click and the gate carries its own two lever blocks separately.
     */
    private Stargate gateWithLevers(final Block dial, final Block iris, final Block at)
    {
        final Stargate gate = new Stargate();
        gate.setGateName("twin");
        gate.setGateOwner(OWNER);
        gate.setGateFacing(BlockFace.NORTH);
        gate.setGateDialLeverBlock(dial);
        gate.setGateIrisLeverBlock(iris);
        // A real iris lever is only ever placed for a gate with a code.
        gate.setGateIrisDeactivationCode("1234");
        StargateManager.registerStargate(gate);
        StargateManager.addBlockIndex(at, gate);
        return gate;
    }

    private boolean click(final Block b)
    {
        return GateInteractionHandler.handlePlayerInteractEvent(
            new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, b, BlockFace.NORTH));
    }

    /** Clicking the dial lever itself opens the gate and leaves the iris alone. */
    @Test
    void clickingTheDialLeverExactlyActivatesTheGate()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Stargate gate = gateWithLevers(dial, iris, dial);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(dial);

        org.junit.jupiter.api.Assertions.assertSame(gate, StargateManager.removeActivatedStargate(player),
            "the dial lever lights the gate for dialling");
        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisActive(),
            "the click was on the dial lever, so the iris must not have been touched");
    }

    /** Clicking the iris lever itself toggles the iris rather than opening the gate. */
    @Test
    void clickingTheIrisLeverExactlyTogglesTheIris()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Stargate gate = gateWithLevers(dial, iris, iris);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(iris);

        org.junit.jupiter.api.Assertions.assertTrue(gate.isGateIrisActive(),
            "an exact click on the iris lever is an iris toggle");
    }

    /**
     * The iris lever changes the gate's default, and the gate is saved there and then.
     *
     * <p>Left to the next save, a crash lost it: the gate came back with the iris it had before
     * somebody pulled the lever.
     */
    @Test
    void theIrisLeverSavesTheDefaultItSets()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Stargate gate = gateWithLevers(dial, iris, iris);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(iris);

        org.junit.jupiter.api.Assertions.assertTrue(gate.isGateIrisDefaultActive(), "the lever sets the default");
        db.verify(() -> StargateDBManager.saveStargate(gate));
    }

    /** A click next to the iris lever alone is the same toggle, and saves the same way. */
    @Test
    void aClickBesideOnlyTheIrisLeverSavesTheDefaultToo()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 7);
        final Block beside = blockAt(5, 64, 8);
        final Stargate gate = gateWithLevers(dial, iris, beside);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(beside);

        org.junit.jupiter.api.Assertions.assertTrue(gate.isGateIrisDefaultActive(),
            "adjacent to the iris lever and not the dial is an iris toggle");
        db.verify(() -> StargateDBManager.saveStargate(gate));
    }

    /**
     * A gate with no code still keeps its iris spot, and a lever a player puts there must not
     * shut the iris: with no code and no lever of its own, nothing could open it again.
     */
    @Test
    void aLeverOnTheIrisSpotOfAGateWithNoCodeTogglesNothing()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 7);
        final Stargate gate = gateWithLevers(dial, iris, iris);
        gate.setGateIrisDeactivationCode("");
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        final boolean handled = click(iris);

        // Asserted with the click handled, so "the iris stayed open" is not just a click
        // that never reached the dispatch.
        org.junit.jupiter.api.Assertions.assertTrue(handled, "the click on an indexed block was not handled");
        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisActive(),
            "a stray lever shut an iris with no code");
        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisDefaultActive());
    }

    /**
     * When a click is next to both levers, the dial wins.
     *
     * <p>This is the Standard layout: the iris lever is placed right beside the dial lever,
     * so a click adjacent to one is usually adjacent to the other too. Preferring the iris
     * there would mean reaching for the dial and shutting the iris instead.
     */
    @Test
    void aClickAdjacentToBothLeversIsTakenAsTheDial()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Block between = blockAt(5, 65, 5);
        final Stargate gate = gateWithLevers(dial, iris, between);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(between);

        // Asserted positively: "the iris did not move" is also true when nothing happened at
        // all, so it cannot tell a dial activation from a refusal.
        org.junit.jupiter.api.Assertions.assertSame(gate, StargateManager.removeActivatedStargate(player),
            "adjacent to both means the dial, not the iris and not nothing");
        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisActive());
    }

    /**
     * Without permission the player is told so, and nothing moves.
     *
     * <p>The gate is owned by somebody else and the player holds no node, so neither branch
     * of the dispatch may fire -- but the click was still on a lever, so it is answered
     * rather than silently ignored.
     */
    @Test
    void aClickWithoutPermissionIsRefusedRatherThanIgnored()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Stargate gate = gateWithLevers(dial, iris, iris);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        click(iris);

        verify(player).sendMessage(contains("ermission"));
        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisActive(),
            "a refused click must not toggle the iris anyway");
        db.verifyNoInteractions();
    }

    /** A click on a gate block that is neither lever does not toggle anything. */
    @Test
    void aClickOnSomeOtherGateBlockTogglesNothing()
    {
        final Block dial = blockAt(5, 64, 5);
        final Block iris = blockAt(5, 64, 6);
        final Block elsewhere = blockAt(50, 64, 50);
        final Stargate gate = gateWithLevers(dial, iris, elsewhere);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        click(elsewhere);

        org.junit.jupiter.api.Assertions.assertFalse(gate.isGateIrisActive());
        verify(player, never()).sendMessage(contains("ermission"));
    }
}
