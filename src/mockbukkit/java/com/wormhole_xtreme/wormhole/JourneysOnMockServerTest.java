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
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

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

    // PlayerMock's own simulatePlayerMove is deprecated for removal.
    private static void walk(final MockServerSupport.Player p, final Location to)
    {
        new PlayerSimulation(p).simulatePlayerMove(to);
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
        return buildGate(p, world, x, name, "");
    }

    /**
     * Builds a gate, completing it with options such as {@code idc=} (which gives it an iris
     * lever) or {@code net=} (which keeps it off the other journeys' network).
     */
    private static Stargate buildGate(final MockServerSupport.Player p, final MockServerSupport.World world,
        final double x, final String name, final String options)
    {
        p.teleport(new Location(world, x, 64, 0.5, 0f, 0f));
        p.performCommand("wormhole gate build Standard");
        p.performCommand("wormhole gate preview place");
        p.performCommand("wormhole gate complete " + name + (options.isEmpty() ? "" : (" " + options)));
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
        walk(p, portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5));
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

        walk(p, new Location(world, 0.6, 64, 0.6));
        ticks(200);

        assertAt(new Location(world, 30.5, 64, 0.5), p.getLocation());
        assertNothingNewRunning(before, "the rings came home");
        assertEquals(0, world.ticketedChunks(), "a ring kept its chunks loaded after the trip");
    }

    /** A pet of the given kind tamed to {@code owner}, sitting or not, where it is put. */
    private static <T extends Tameable & Sittable> T pet(final Location at, final Class<T> kind,
        final AnimalTamer owner, final boolean sitting)
    {
        final T pet = at.getWorld().spawn(at, kind);
        pet.setTamed(true);
        pet.setOwner(owner);
        pet.setSitting(sitting);
        return pet;
    }

    /**
     * A wolf following its owner comes through the gate with them; one told to sit, and one
     * following somebody else, stay where they are.
     */
    @Test
    void aFollowingPetComesThroughAGateWithItsOwner()
    {
        final MockServerSupport.World world = new MockServerSupport.World("petgates", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Walker");
        final MockServerSupport.Player stranger = new MockServerSupport.Player(server, "Stranger");
        final Stargate home = buildGate(p, world, 0.5, "Kennel");
        final Stargate away = buildGate(p, world, 40.5, "Park");
        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        final Location waiting = new Location(world, -1.5, 64, 1.5);
        final Wolf follower = pet(new Location(world, 1.5, 64, 1.5), Wolf.class, p, false);
        final Cat sitter = pet(waiting, Cat.class, p, true);
        final Wolf strangers = pet(new Location(world, 1.5, 64, -0.5), Wolf.class, stranger, false);
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        click(p, Action.RIGHT_CLICK_BLOCK, home.getGateDialLeverBlock(), BlockFace.SOUTH);
        p.performCommand("dial Park");
        ticks(200);
        assertTrue(home.isGateActive(), "Kennel did not open: " + p.messages());
        final List<Location> portal = home.getGatePortalBlocks();
        final Location near = strangers.getLocation();
        walk(p, portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5));
        ticks(40);

        assertAt(away.getGatePlayerTeleportLocation(), p.getLocation());
        assertAt(p.getLocation(), follower.getLocation());
        assertAt(waiting, sitter.getLocation());
        assertAt(near, strangers.getLocation());
        ticks(20 * 320);
        assertNothingNewRunning(before, "the pet came through");
    }

    /** A cat following its owner is beamed after them into another world. */
    @Test
    void aFollowingPetIsBeamedIntoAnotherWorldAfterItsOwner()
    {
        final MockServerSupport.World here = new MockServerSupport.World("pethome", 3);
        final MockServerSupport.World there = new MockServerSupport.World("petaway", 3);
        server.addWorld(here);
        server.addWorld(there);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Traveller");
        final Location den = new Location(there, 5.5, 64, 5.5, 0f, 0f);
        p.teleport(den);
        p.performCommand("wormhole beam admin set Den");
        p.teleport(new Location(here, 0.5, 64, 0.5, 0f, 0f));
        final Cat follower = pet(new Location(here, 2.5, 64, 0.5), Cat.class, p, false);
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        p.performCommand("wormhole beam to Den");
        ticks(120);

        assertAt(den, p.getLocation());
        assertAt(p.getLocation(), follower.getLocation());
        assertNothingNewRunning(before, "the pet was beamed");
    }

    /** Presses a gate's DHD and dials, with a code for the far iris when one is given. */
    private static List<String> dial(final MockServerSupport.Player p, final Stargate from, final String to,
        final String code)
    {
        p.messages();
        click(p, Action.RIGHT_CLICK_BLOCK, from.getGateDialLeverBlock(), BlockFace.SOUTH);
        p.performCommand("dial " + to + ((code == null) ? "" : (" " + code)));
        ticks(200);
        return p.messages();
    }

    /** Pulls a gate's iris lever, and waits for the iris to finish crossing. */
    private static void pullIrisLever(final MockServerSupport.Player p, final Stargate gate)
    {
        final Block lever = gate.getGateIrisLeverBlock();
        assertNotNull(lever, gate.getGateName() + " has no iris lever");
        click(p, Action.RIGHT_CLICK_BLOCK, lever, BlockFace.SOUTH);
        ticks(60);
    }

    /**
     * An iris shut over the far gate refuses a dial with no code and with the wrong one; the
     * right code opens it, and the traveller comes through.
     */
    @Test
    void aClosedIrisRefusesADialUntilItsCodeIsGiven()
    {
        final MockServerSupport.World world = new MockServerSupport.World("irisgates", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Caller");
        final Stargate home = buildGate(p, world, 0.5, "Abydos");
        final Stargate far = buildGate(p, world, 40.5, "Chulak", "idc=4321");
        pullIrisLever(p, far);
        assertTrue(far.isGateIrisActive(), "Chulak's iris did not shut");
        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        final java.util.Set<Integer> before = settledTasks();

        final List<String> bare = dial(p, home, "Chulak", null);
        assertFalse(home.isGateActive(), "dialled through a shut iris with no code: " + bare);
        assertTrue(bare.stream().anyMatch(m -> m.contains("provide the IDC")), "no refusal: " + bare);
        final List<String> wrong = dial(p, home, "Chulak", "1111");
        assertFalse(home.isGateActive(), "dialled through a shut iris with the wrong code: " + wrong);
        assertTrue(wrong.stream().anyMatch(m -> m.contains("provide the IDC")), "wrong code not judged: " + wrong);
        assertTrue(far.isGateIrisActive(), "the wrong code opened the iris");

        final List<String> right = dial(p, home, "Chulak", "4321");
        assertTrue(right.stream().anyMatch(m -> m.contains("IDC accepted")), "code not accepted: " + right);
        assertFalse(far.isGateIrisActive(), "the right code left the iris shut");
        assertTrue(home.isGateActive(), "Abydos did not open: " + right);
        final List<Location> portal = home.getGatePortalBlocks();
        walk(p, portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5));
        ticks(20);
        assertAt(far.getGatePlayerTeleportLocation(), p.getLocation());

        ticks(20 * 320);
        assertNothingNewRunning(before, "the iris opened and the gate shut");
    }

    /** An iris shut over the far end after the dial bounces the traveller, who stays behind. */
    @Test
    void anIrisShutAfterTheDialBouncesTheTraveller()
    {
        final MockServerSupport.World world = new MockServerSupport.World("irisbounce", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Visitor");
        final MockServerSupport.Player keeper = new MockServerSupport.Player(server, "Keeper");
        final Stargate home = buildGate(p, world, 0.5, "Tollan");
        final Stargate far = buildGate(p, world, 40.5, "Dakara", "idc=8888");
        keeper.teleport(new Location(world, 40.5, 64, 0.5, 0f, 0f));
        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        final java.util.Set<Integer> before = settledTasks();

        final List<String> dialled = dial(p, home, "Dakara", null);
        assertTrue(home.isGateActive(), "Tollan did not open: " + dialled);
        pullIrisLever(keeper, far);
        assertTrue(far.isGateIrisActive(), "Dakara's iris did not shut");
        final List<Location> portal = home.getGatePortalBlocks();
        final Location into = portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5);
        walk(p, into);
        ticks(20);

        assertTrue(p.messages().stream().anyMatch(m -> m.contains("Remote Iris is locked")), "not told of the iris");
        assertSame(world, p.getWorld());
        assertTrue(p.getLocation().distance(far.getGatePlayerTeleportLocation()) > 20,
            "went through a shut iris to " + p.getLocation());

        ticks(20 * 320);
        assertNothingNewRunning(before, "the traveller was bounced and the gate shut");
    }

    /**
     * A lever a player puts on the unused iris spot of a gate with no code does not shut an
     * iris nobody could open again, and the player can break it again.
     */
    @Test
    void aLeverOnTheIrisSpotOfAGateWithNoCodeLeavesTheIrisAlone()
    {
        final MockServerSupport.World world = new MockServerSupport.World("nocode", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Builder");
        final Stargate gate = buildGate(p, world, 0.5, "Edora");
        final Block spot = gate.getGateIrisLeverBlock();
        assertNotNull(spot, "Edora has no iris spot, so nothing here reaches the bug");
        assertSame(gate, StargateManager.getGateFromBlock(spot), "the iris spot is not indexed");
        spot.setType(Material.LEVER);

        click(p, Action.RIGHT_CLICK_BLOCK, spot, BlockFace.SOUTH);
        ticks(60);

        assertFalse(gate.isGateIrisActive(), "a stray lever shut an iris with no code");
        assertFalse(gate.isGateIrisDefaultActive(), "a stray lever made a codeless iris shut by default");
        final BlockBreakEvent breaking = new BlockBreakEvent(spot, p);
        server.getPluginManager().callEvent(breaking);
        assertFalse(breaking.isCancelled(), "the player cannot take back their own lever: " + p.messages());
    }

    /** A redstone pulse into the block, rising from off. */
    private static void pulse(final Block block)
    {
        server.getPluginManager().callEvent(new BlockRedstoneEvent(block, 0, 15));
    }

    /**
     * A sign gate dialled by redstone: the player hangs a dial sign and names the gate on it,
     * steps the sign past the first gate on its network to the far one, and a pulse into the
     * [RD] cell dials the one the sign shows, switching the [RA] lever on while it is open.
     *
     * <p>Not covered: the guard that stops the plugin's own lever writes counting as a trigger.
     * MockBukkit never fires a redstone event itself, so nothing here can set one off.
     */
    @Test
    void aRedstonePulseDialsTheGateTheSignShows()
    {
        final MockServerSupport.World world = new MockServerSupport.World("redstone", 4);
        server.addWorld(world);
        final MockServerSupport.Player p = new MockServerSupport.Player(server, "Wirer");
        // A network of their own, away from the other journeys' gates. Relay sorts first, so the
        // sign has to be stepped past it: dialling the network's first gate would be wrong.
        final Stargate first = buildGate(p, world, -40.5, "Relay", "net=Wires");
        final Stargate far = buildGate(p, world, 40.5, "Target", "net=Wires");

        p.teleport(new Location(world, 0.5, 64, 0.5, 0f, 0f));
        p.performCommand("wormhole gate build StandardSignDial");
        p.performCommand("wormhole gate preview place");
        // Placing reads the design at once, before any sign is there, so it is set aside and
        // the gate completed the way a player building by hand does: sign first, then button.
        p.performCommand("wormhole gate complete -cancel");
        // The dial sign hangs on the [D] block beside the DHD's activation block, facing the
        // same way as the button: here, one block west of it.
        final Block button = world.getBlockAt(0, 65, 0);
        assertTrue(org.bukkit.Tag.BUTTONS.isTagged(button.getType()), "no DHD button where expected: " + button.getType());
        final Block sign = button.getRelative(BlockFace.WEST);
        sign.setType(Material.OAK_WALL_SIGN);
        final BlockData facing = sign.getBlockData();
        ((Directional) facing).setFacing(((Directional) button.getBlockData()).getFacing());
        sign.setBlockData(facing);
        final Sign written = (Sign) sign.getState();
        // Paper's text component, since the tests compile only against Paper and setLine is deprecated there.
        written.getSide(Side.FRONT).line(0, net.kyori.adventure.text.Component.text("Signal"));
        written.update(true);
        p.performCommand("wormhole gate complete Signal net=Wires");
        click(p, Action.RIGHT_CLICK_BLOCK, button, BlockFace.NORTH);
        final Stargate home = StargateManager.getStargate("Signal");
        assertNotNull(home, "Signal was not built: " + p.messages());
        assertTrue(home.isGateSignPowered(), "the dial sign was not taken up");
        assertTrue(home.isGateRedstonePowered(), "a SignDial gate should take redstone");

        p.messages();
        click(p, Action.RIGHT_CLICK_BLOCK, home.getGateDialSignBlock(), BlockFace.NORTH);
        ticks(5);
        assertSame(first, home.getGateDialSignTarget(), "the first step is not Relay: " + p.messages());
        click(p, Action.RIGHT_CLICK_BLOCK, home.getGateDialSignBlock(), BlockFace.NORTH);
        ticks(5);
        final List<String> told = p.messages();
        assertSame(far, home.getGateDialSignTarget(), "the second step is not Target: " + told);
        assertTrue(told.stream().anyMatch(m -> m.contains("Dialer set to: Target")), "not told: " + told);
        final String shown = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(((Sign) home.getGateDialSignBlock().getState()).getSide(Side.FRONT).line(2));
        assertTrue(shown.contains("Target"), "the sign's selected line reads \"" + shown + "\"");
        final Block output = home.getGateRedstoneGateActivatedBlock();
        assertFalse(((Switch) output.getBlockData()).isPowered(), "the [RA] lever is on before any dial");
        p.messages();
        final java.util.Set<Integer> before = settledTasks();

        // Dust on the [RD] cell, as a player's circuit leaves it.
        final Block trigger = home.getGateRedstoneDialActivationBlock();
        trigger.setType(Material.REDSTONE_WIRE);
        pulse(trigger);
        ticks(200);
        assertTrue(home.isGateActive(), "the pulse did not dial: " + p.messages());
        assertSame(far, home.getGateTarget());
        assertTrue(((Switch) output.getBlockData()).isPowered(), "the [RA] lever did not switch on");

        final List<Location> portal = home.getGatePortalBlocks();
        walk(p, portal.get(portal.size() / 2).clone().add(0.5, 0, 0.5));
        ticks(20);
        assertAt(far.getGatePlayerTeleportLocation(), p.getLocation());

        ticks(20 * 320);
        assertFalse(home.isGateActive(), "Signal never timed out");
        assertFalse(((Switch) output.getBlockData()).isPowered(), "the [RA] lever stayed on after the gate shut");
        assertNothingNewRunning(before, "the redstone dial");
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
