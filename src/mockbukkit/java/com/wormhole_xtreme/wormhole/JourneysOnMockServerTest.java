package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;

/**
 * A player's whole trip through each way of travelling, on a simulated server: set it up by
 * command, use it, arrive, and leave nothing scheduled behind.
 *
 * <p>The Mockito tests take each step on its own. These run them in one go through the real
 * command, listener and scheduler, so a step that no longer hands on to the next shows up here.
 * Each ends by settling the server and checking the trip left nothing new running: a task that
 * is never cancelled costs every tick for the life of the server.
 */
@OnMockServer
class JourneysOnMockServerTest
{
    private static ServerMock server;

    @BeforeAll
    static void startServer()
    {
        server = MockServerSupport.start();
    }

    @AfterAll
    static void stopServer()
    {
        MockServerSupport.stop();
    }

    /** Settles the server, and says which repeating tasks it is left running. */
    private static java.util.Set<Integer> settledTasks()
    {
        MockServerSupport.settle(server);
        assertEquals(0, MockServerSupport.oneOffTasks(server), () -> "still rescheduling after 30 minutes: "
            + MockServerSupport.describeOneOffTasks(server));
        return MockServerSupport.repeatingTasks(server);
    }

    /** Every repeating task running once the trip has settled was already running before it. */
    private static void assertNothingNewRunning(final java.util.Set<Integer> before, final String trip)
    {
        final java.util.Set<Integer> started = new java.util.TreeSet<>(settledTasks());
        started.removeAll(before);
        assertTrue(started.isEmpty(), "tasks still running after " + trip + ": " + started);
    }

    private static void ticks(final int n)
    {
        server.getScheduler().performTicks(n);
    }

    private static void click(final MockServerSupport.Player p, final Action action, final Block block,
        final BlockFace face)
    {
        server.getPluginManager().callEvent(new PlayerInteractEvent(p, action, null, block, face, EquipmentSlot.HAND));
    }

    private static void assertAt(final Location expected, final Location actual)
    {
        assertSame(expected.getWorld(), actual.getWorld(), "world");
        assertEquals(expected.getBlockX(), actual.getBlockX(), "x");
        assertEquals(expected.getBlockY(), actual.getBlockY(), "y");
        assertEquals(expected.getBlockZ(), actual.getBlockZ(), "z");
    }

    private static Stargate buildGate(final MockServerSupport.Player p, final MockServerSupport.World world,
        final double x, final String name)
    {
        p.teleport(new Location(world, x, 64, 0.5, 0f, 0f));
        p.performCommand("wormhole gate build Standard");
        p.performCommand("wormhole gate preview place");
        p.performCommand("wormhole gate complete " + name);
        final Stargate gate = StargateManager.getStargate(name);
        assertNotNull(gate, name + " was not built: " + p.messages());
        return gate;
    }

    /** Built, dialled from its DHD, walked through, and shut again when it times out. */
    @Test
    void aPlayerDialsAGateWalksThroughAndItShutsBehindThem()
    {
        final MockServerSupport.World world = new MockServerSupport.World("gates", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Dialler");
        final Stargate alpha = buildGate(p, world, 0.5, "Alpha");
        final Stargate beta = buildGate(p, world, 40.5, "Beta");
        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        click(p, Action.RIGHT_CLICK_BLOCK, alpha.getGateDialLeverBlock(), BlockFace.SOUTH);
        p.performCommand("dial Beta");
        ticks(200);
        assertTrue(alpha.isGateActive(), "Alpha did not open: " + p.messages());
        assertSame(beta, alpha.getGateTarget());

        final List<Location> portal = alpha.getGatePortalBlocks();
        p.simulatePlayerMove(portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5));
        ticks(20);
        assertAt(beta.getGatePlayerTeleportLocation(), p.getLocation());

        ticks(20 * 320);
        assertFalse(alpha.isGateActive(), "Alpha never timed out");
        assertNothingNewRunning(before, "the gate shut");
    }

    /** Saved as a destination, beamed to from elsewhere, and arrived at facing the saved way. */
    @Test
    void aPlayerBeamsToASavedDestination()
    {
        final MockServerSupport.World world = new MockServerSupport.World("beams", 3);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Beamer");
        final Location home = new Location(world, 10.5, 64, 10.5, 90f, 0f);
        p.teleport(home);
        p.performCommand("wormhole beam admin set Home");
        p.teleport(new Location(world, -20.5, 64, -20.5, 0f, 0f));
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        p.performCommand("wormhole beam to Home");
        ticks(100);

        assertAt(home, p.getLocation());
        assertEquals(90f, p.getLocation().getYaw(), 0.01f, "yaw");
        assertNothingNewRunning(before, "the beam landed");
    }

    /** Two rings laid in slabs and paired, then walked into: the far end, and both chunks let go. */
    @Test
    void aPlayerStepsIntoARingAndArrivesAtItsPartner()
    {
        final MockServerSupport.World world = new MockServerSupport.World("rings", 3);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Ringer");
        for (final int x : new int[] { 0, 30 })
        {
            for (final RingPattern.Offset o : RingPattern.ODD.getPerimeter())
            {
                world.getBlockAt(x + o.getDx(), 64, o.getDz()).setType(Material.STONE_SLAB);
            }
            p.teleport(new Location(world, x + 0.5, 64, 0.5, 0f, 0f));
            p.performCommand("wormhole ring create");
        }
        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        final List<String> made = p.messages();
        assertTrue(made.stream().anyMatch(m -> m.contains("is live")), "pair not made: " + made);
        final java.util.Set<Integer> before = settledTasks();

        p.simulatePlayerMove(new Location(world, 0.6, 64, 0.6));
        ticks(200);

        assertAt(new Location(world, 30.5, 64, 0.5), p.getLocation());
        assertNothingNewRunning(before, "the rings came home");
        assertEquals(0, world.ticketedChunks(), "a ring kept its chunks loaded after the trip");
    }

    private static Block hangMirror(final MockServerSupport.World world)
    {
        for (int x = -4; x <= 4; x++)
        {
            for (int y = 64; y <= 70; y++)
            {
                world.getBlockAt(x, y, 5).setType(Material.STONE);
            }
        }
        final Block banner = world.getBlockAt(0, 66, 4);
        banner.setType(Material.WHITE_WALL_BANNER);
        final BlockData data = banner.getBlockData();
        ((Directional) data).setFacing(BlockFace.NORTH);
        banner.setBlockData(data);
        return banner;
    }

    /** Two mirrors in two worlds: a right-click shows the other, and a punch goes through it. */
    @Test
    void aPlayerChoosesAnotherMirrorAndPunchesThroughIt()
    {
        final MockServerSupport.World library = new MockServerSupport.World("library", 2);
        final MockServerSupport.World tower = new MockServerSupport.World("tower", 2);
        server.addWorld(library);
        server.addWorld(tower);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Mirrorer");
        for (final MockServerSupport.World w : List.of(library, tower))
        {
            final Block banner = hangMirror(w);
            p.teleport(new Location(w, 0.5, 64, 1.5, 0f, 0f));
            p.lookAt(banner);
            p.performCommand("wormhole mirror create " + w.getName());
            assertNotNull(com.wormhole_xtreme.wormhole.model.mirror.MirrorManager.byName(w.getName()),
                w.getName() + " was not made a mirror: " + p.messages());
        }
        final Block libraryBanner = library.getBlockAt(0, 66, 4);
        final Block towerBanner = tower.getBlockAt(0, 66, 4);
        p.teleport(new Location(library, 0.5, 64, 2.5, 0f, 0f));
        p.lookAt(libraryBanner);
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        click(p, Action.RIGHT_CLICK_BLOCK, libraryBanner, BlockFace.NORTH);
        final List<String> chose = p.messages();
        assertTrue(chose.stream().anyMatch(m -> m.contains("opens onto 'tower'")), "did not choose tower: " + chose);
        click(p, Action.LEFT_CLICK_BLOCK, libraryBanner, BlockFace.NORTH);
        ticks(100);

        assertSame(tower, p.getWorld(), "punch did not reach the tower");
        assertEquals(towerBanner.getX(), p.getLocation().getBlockX(), "x");
        assertEquals(towerBanner.getZ(), p.getLocation().getBlockZ(), "z");
        p.teleport(new Location(library, 0.5, 64, -30.5));
        p.lookAt(null);
        assertNothingNewRunning(before, "the mirror trip");
    }
}
