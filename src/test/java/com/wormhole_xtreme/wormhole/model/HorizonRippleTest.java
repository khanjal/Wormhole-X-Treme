package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.PrivateStatics;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * A ripple across an open gate's horizon, step by step, as each viewer is sent it (#579).
 *
 * <p>Every block change each player is sent is recorded in order, so these say exactly which cells
 * were drawn as what at each step, and which were put back: a ring drawn and never put back is a
 * block of ice or lava left standing in a wormhole on somebody's screen. The scheduler holds each
 * booked step until the test runs it, and drops one that is called off.
 */
class HorizonRippleTest
{
    private World world;
    private Player front;
    private Stargate gate;
    private WormholeXTreme plugin;
    private MockedStatic<MaterialUtils> materials;
    private MockedStatic<StargateManager> manager;
    private final BlockData water = named("water");
    private final BlockData ice = named("ice");
    private final BlockData lava = named("lava");
    private final BlockData air = named("air");
    /** What the world really holds in an empty cell. */
    private final BlockData truth = named("truth");
    private final Map<Player, List<String>> sent = new HashMap<>();
    private final Map<Integer, Runnable> pending = new LinkedHashMap<>();
    private final List<Long> delays = new ArrayList<>();
    private final List<Entity> standing = new ArrayList<>();
    private long now;
    /** Set to make every send of ice throw, as a closing connection does. */
    private boolean refuseIce;

    private static BlockData named(final String name)
    {
        final BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn(name);
        return data;
    }

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        when(plugin.isEnabled()).thenReturn(true);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncDelayedTask(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call ->
        {
            final int id = delays.size();
            delays.add(call.getArgument(2));
            pending.put(id, call.getArgument(1));
            return id;
        });
        doAnswer(call -> pending.remove((Integer) call.getArgument(0))).when(scheduler).cancelTask(anyInt());
        PluginTestSupport.scheduler(scheduler);
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, true);
        GateViews.clear();
        HorizonRipple.cancelAll();
        now = 100_000L;
        HorizonRipple.clock = () -> now;
        HorizonRipple.nextWait = () -> 7_000L;

        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        final Block empty = mock(Block.class);
        when(empty.getType()).thenReturn(Material.AIR);
        when(empty.getBlockData()).thenReturn(truth);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(empty);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(standing);
        // The gate faces south, its opening in the plane z = 20: in front is z = 21.
        front = playerAt(25.0);
        when(world.getPlayers()).thenReturn(List.of(front));

        gate = gateNamed("Abydos");
        materials = mockStatic(MaterialUtils.class);
        materials.when(() -> MaterialUtils.drawnAcross(Material.WATER, BlockFace.SOUTH)).thenReturn(water);
        materials.when(() -> MaterialUtils.drawnAcross(HorizonRipple.FLAT_RING, BlockFace.SOUTH)).thenReturn(ice);
        materials.when(() -> MaterialUtils.drawnAcross(Material.LAVA, BlockFace.SOUTH)).thenReturn(lava);
        materials.when(() -> MaterialUtils.drawnAcross(Material.AIR, BlockFace.SOUTH)).thenReturn(air);
        materials.when(() -> MaterialUtils.isAirMaterial(Material.AIR)).thenReturn(true);
        manager = mockStatic(StargateManager.class);
        manager.when(StargateManager::getOpenGates).thenReturn(Set.of(gate));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        HorizonRipple.cancelAll();
        HorizonRipple.clock = System::currentTimeMillis;
        HorizonRipple.nextWait = DEFAULT_WAIT;
        manager.close();
        materials.close();
        GateViews.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.scheduler(null);
        PluginTestSupport.remove();
    }

    /** The random wait as shipped, put back after each test. */
    private static final LongSupplier DEFAULT_WAIT = HorizonRipple.nextWait;

    private Player playerAt(final double z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 12.0, 64.0, z));
        final List<String> log = new ArrayList<>();
        sent.put(player, log);
        doAnswer(call ->
        {
            final Location at = call.getArgument(0);
            final BlockData data = call.getArgument(1);
            if (refuseIce && (data == ice))
            {
                throw new IllegalStateException("connection closing");
            }
            log.add(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " " + data.getAsString());
            return null;
        }).when(player).sendBlockChange(any(Location.class), any(BlockData.class));
        return player;
    }

    /** An open water gate, five by five from x = 10, y = 64, in the plane z = 20, framed in that plane only. */
    private Stargate gateNamed(final String name)
    {
        final Stargate one = mock(Stargate.class);
        when(one.getGateName()).thenReturn(name);
        when(one.isGateActive()).thenReturn(true);
        when(one.isGatePortalOpen()).thenReturn(true);
        when(one.getGateWorld()).thenReturn(world);
        when(one.getGateFacing()).thenReturn(BlockFace.SOUTH);
        when(one.getEffectivePortalMaterial()).thenReturn(Material.WATER);
        when(one.getGatePortalBounds()).thenReturn(new BoundingBox(10, 64, 20, 15, 69, 21));
        opening(one, 5, 5, false);
        return one;
    }

    /**
     * Gives a gate an opening this size from x = 10, y = 64 in the plane z = 20, with a frame round it,
     * and with {@code deep} another frame round the plane in front.
     */
    private void opening(final Stargate which, final int wide, final int tall, final boolean deep)
    {
        final List<Location> portal = new ArrayList<>();
        final List<Location> frame = new ArrayList<>();
        for (int z = 20; z <= (deep ? 21 : 20); z++)
        {
            for (int x = 9; x <= (10 + wide); x++)
            {
                for (int y = 63; y <= (64 + tall); y++)
                {
                    final boolean inside = (x >= 10) && (x < (10 + wide)) && (y >= 64) && (y < (64 + tall));
                    if (!inside)
                    {
                        frame.add(new Location(world, x, y, z));
                    }
                    else if (z == 20)
                    {
                        portal.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        when(which.getGatePortalBlocks()).thenReturn(portal);
        when(which.getGateStructureBlocks()).thenReturn(frame);
    }

    /** Runs the one step booked, as the scheduler would two ticks on. */
    private void step()
    {
        assertEquals(1, pending.size(), "one step booked at a time");
        final Map.Entry<Integer, Runnable> next = pending.entrySet().iterator().next();
        pending.remove(next.getKey());
        next.getValue().run();
    }

    /** What a player was sent since last asked, as a set of "x,y,z what". */
    private Set<String> sentTo(final Player player)
    {
        final List<String> log = sent.get(player);
        final Set<String> since = new TreeSet<>(log);
        log.clear();
        return since;
    }

    /** Cells of the plane z = 20 or 21, each drawn as one thing. */
    private static Set<String> cells(final String what, final int z, final int... xy)
    {
        final Set<String> out = new TreeSet<>();
        for (int i = 0; i < xy.length; i += 2)
        {
            out.add(xy[i] + "," + xy[i + 1] + "," + z + " " + what);
        }
        return out;
    }

    /** The eight cells round the middle of the five by five, (12, 66). */
    private static final int[] FIRST_RING = { 11, 65, 12, 65, 13, 65, 11, 66, 13, 66, 11, 67, 12, 67, 13, 67 };

    private static Set<String> union(final Set<String> one, final Set<String> two)
    {
        final Set<String> both = new TreeSet<>(one);
        both.addAll(two);
        return both;
    }

    @Test
    void aSmallRippleShowsEachRingLongerAndABigOneNoSlowerThanTheFloor()
    {
        assertEquals(8L, HorizonRipple.stepTicks(1));
        assertEquals(6L, HorizonRipple.stepTicks(2));
        assertEquals(3L, HorizonRipple.stepTicks(4));
        assertEquals(2L, HorizonRipple.stepTicks(6));
        assertEquals(2L, HorizonRipple.stepTicks(40));
        assertEquals(2L, HorizonRipple.stepTicks(0));
    }

    @Test
    void aFlatWaterGateRipplesInIceFromTheCentreOutEachRingPutBackAsTheNextIsDrawn()
    {
        assertTrue(HorizonRipple.start(gate));

        assertEquals(cells("ice", 20, 12, 66), sentTo(front), "the first step is the one cell at the middle");
        step();
        assertEquals(union(cells("water", 20, 12, 66), cells("ice", 20, FIRST_RING)), sentTo(front),
            "the middle back to water, the eight round it to ice");
        step();
        final Set<String> second = sentTo(front);
        assertEquals(cells("water", 20, FIRST_RING), filter(second, "water"), "the first ring back to water");
        assertEquals(12, filter(second, "ice").size(), "the twelve cells two out");
        step();
        final Set<String> corners = cells("ice", 20, 10, 64, 14, 64, 10, 68, 14, 68);
        assertEquals(corners, filter(sentTo(front), "ice"), "the four corners last");
        step();
        assertEquals(cells("water", 20, 10, 64, 14, 64, 10, 68, 14, 68), sentTo(front),
            "the ripple over: the corners back to water, and nothing left as ice");
        assertTrue(pending.isEmpty(), "nothing more booked");
        assertFalse(HorizonRipple.isRippling(gate));
        assertEquals(List.of(3L, 3L, 3L, 3L), delays, "four rings, so a ring every three ticks");
    }

    private static Set<String> filter(final Set<String> sends, final String what)
    {
        final Set<String> out = new TreeSet<>();
        for (final String one : sends)
        {
            if (one.endsWith(" " + what))
            {
                out.add(one);
            }
        }
        return out;
    }

    /** Ice is solid to the client: a cell somebody's box reaches into is never drawn. */
    @Test
    void nothingIsDrawnWhereSomebodyStands()
    {
        final Entity traveller = mock(Entity.class);
        when(traveller.getBoundingBox()).thenReturn(new BoundingBox(12.2, 66.0, 20.2, 12.8, 67.8, 20.8));
        standing.add(traveller);

        assertTrue(HorizonRipple.start(gate));
        assertTrue(sentTo(front).isEmpty(), "the middle cell is taken, so the first step draws nothing");
        step();
        final Set<String> first = sentTo(front);
        assertFalse(first.contains("12,67,20 ice"), "their head is in the cell above the middle");
        assertEquals(7, first.size(), "the rest of the first ring is drawn");
    }

    /** Grand and Massive have a ring in front of their horizon: the portal material ripples there, whatever it is. */
    @Test
    void aGateWithARingInFrontRipplesItsOwnPortalMaterialABlockInFront()
    {
        opening(gate, 5, 5, true);
        when(gate.getEffectivePortalMaterial()).thenReturn(Material.LAVA);
        final Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(11, 66, 21)).thenReturn(stone);

        assertTrue(HorizonRipple.start(gate));
        assertEquals(cells("lava", 21, 12, 66), sentTo(front), "in front of the middle, not in the horizon");
        step();
        final Set<String> first = sentTo(front);
        assertTrue(first.contains("12,66,21 truth"), "the cell in front of the middle put back to what is there");
        assertFalse(first.contains("11,66,21 lava"), "a cell in front that is not air is left alone");
        assertEquals(7, filter(first, "lava").size(), "the rest of the first ring, a block in front");
        assertTrue(first.stream().noneMatch(one -> one.contains(",20 ")), "nothing is sent in the horizon's own plane");
    }

    @Test
    void aFlatLavaGateDoesNotRipple()
    {
        when(gate.getEffectivePortalMaterial()).thenReturn(Material.LAVA);

        assertFalse(HorizonRipple.start(gate));
        assertTrue(pending.isEmpty());
        assertTrue(sentTo(front).isEmpty());
    }

    @Test
    void aFlatWaterGateTwoByTwoDoesNotRipple()
    {
        opening(gate, 2, 2, false);

        assertFalse(HorizonRipple.start(gate));
        opening(gate, 2, 3, false);
        assertTrue(HorizonRipple.start(gate), "two by three is bigger than two by two");
    }

    @Test
    void aGateNotShowingItsWormholeDoesNotRipple() throws ReflectiveOperationException
    {
        when(gate.isGatePortalOpen()).thenReturn(false);
        assertFalse(HorizonRipple.start(gate), "its kawoosh is still running");
        when(gate.isGatePortalOpen()).thenReturn(true);

        when(gate.isGateIrisActive()).thenReturn(true);
        assertFalse(HorizonRipple.start(gate), "its iris is shut");
        when(gate.isGateIrisActive()).thenReturn(false);

        when(gate.getGateFacing()).thenReturn(BlockFace.UP);
        assertFalse(HorizonRipple.start(gate), "a horizontal gate");
        when(gate.getGateFacing()).thenReturn(BlockFace.SOUTH);

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(HorizonRipple.start(gate), "its chunk is not loaded");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, false);
        assertFalse(HorizonRipple.start(gate), "gate-ripple is off");
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, true);

        final Map<String, Object> sweeping = PrivateStatics.of(StargateIrisAnimator.class, "running");
        sweeping.put("Abydos", mock(IrisSweepDriver.class));
        try
        {
            assertFalse(HorizonRipple.start(gate), "its iris is crossing");
        }
        finally
        {
            sweeping.remove("Abydos");
        }

        assertTrue(HorizonRipple.start(gate), "and with none of those, it ripples");
    }

    @Test
    void nobodyNearIsNoRipple()
    {
        when(front.getLocation()).thenReturn(new Location(world, 12.0, 64.0, 200.0));

        assertFalse(HorizonRipple.start(gate));
    }

    @Test
    void oneRippleAtATimeAndNoneWithinTwoSecondsOfTheLast()
    {
        assertTrue(HorizonRipple.start(gate));
        now += HorizonRipple.MIN_GAP_MILLIS;
        assertFalse(HorizonRipple.start(gate), "one is running, however long since it began");
        finish();
        assertTrue(HorizonRipple.start(gate), "that one over, and two seconds since it began");
        finish();

        now += HorizonRipple.MIN_GAP_MILLIS - 1;
        assertFalse(HorizonRipple.start(gate), "too soon after the last one started");
        now += 1;
        assertTrue(HorizonRipple.start(gate), "two seconds on");
    }

    /** Runs a five by five's ripple to its end. */
    private void finish()
    {
        step();
        step();
        step();
        step();
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** An iris that shuts mid ripple paints its own opening: the ring is not put back over it as the horizon. */
    @Test
    void anIrisShutMidRippleIsNotPaintedOverWithTheHorizon()
    {
        assertTrue(HorizonRipple.start(gate));
        assertEquals(cells("ice", 20, 12, 66), sentTo(front));
        when(gate.isGateIrisActive()).thenReturn(true);

        step();

        assertTrue(sentTo(front).isEmpty(), "the iris is the opening's to draw now");
        assertTrue(pending.isEmpty(), "and the ripple is over");
    }

    /** A gate that stops showing between two steps puts back what it drew rather than leaving it. */
    @Test
    void aRippleWhoseGateStopsShowingPutsItsRingBackAndStops()
    {
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);
        when(world.getPlayers()).thenReturn(List.of(front));
        ConfigTestSupport.set(ConfigKeys.GATE_RIPPLE, false);

        step();

        assertEquals(cells("water", 20, 12, 66), sentTo(front), "turned off mid ripple: the middle back to water");
        assertTrue(pending.isEmpty());
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** What the close and the iris call: the booked step is dropped and the ring showing goes back. */
    @Test
    void callingARippleOffPutsItsRingBack()
    {
        assertTrue(HorizonRipple.start(gate));
        step();
        sentTo(front);

        HorizonRipple.cancel(gate);

        assertEquals(cells("water", 20, FIRST_RING), sentTo(front));
        assertTrue(pending.isEmpty(), "its next step dropped");
        assertFalse(HorizonRipple.isRippling(gate));
    }

    @Test
    void stoppingThePluginCallsEveryRippleOff()
    {
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);

        HorizonRipple.cancelAll();

        assertEquals(cells("water", 20, 12, 66), sentTo(front));
        assertTrue(pending.isEmpty());
    }

    /** A viewer who walks out of the horizon's reach mid ripple still has their ring put back, and is drawn no more. */
    @Test
    void aViewerWhoWalksOutOfReachStillHasTheirRingPutBack()
    {
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);
        when(front.getLocation()).thenReturn(new Location(world, 12.0, 64.0, 200.0));

        step();

        assertEquals(cells("water", 20, 12, 66), sentTo(front));
    }

    @Test
    void aCrossingRipplesBothEnds()
    {
        final Stargate far = gateNamed("Chulak");

        HorizonRipple.crossed(gate, far);

        assertTrue(HorizonRipple.isRippling(gate), "the gate gone into");
        assertTrue(HorizonRipple.isRippling(far), "the gate come out of");
    }

    @Test
    void aCrossingThatCannotRippleTravelsOnRegardless()
    {
        when(gate.getGatePortalBlocks()).thenThrow(new IllegalStateException("gate half unloaded"));

        HorizonRipple.crossed(gate, null);

        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** An open gate somebody is near ripples of its own after its wait, and waits again from there. */
    @Test
    void anOpenGateSomebodyIsNearRipplesOfItsOwnAfterItsWait()
    {
        HorizonRipple.tick();
        assertFalse(HorizonRipple.isRippling(gate), "the first look only starts the wait");
        now += 6_999L;
        HorizonRipple.tick();
        assertFalse(HorizonRipple.isRippling(gate), "not yet");
        now += 1L;
        HorizonRipple.tick();
        assertTrue(HorizonRipple.isRippling(gate), "seven seconds on, it ripples");
    }

    /** One gate that throws on its turn does not stop the gates after it, nor end the sweep's task. */
    @Test
    void aGateThatThrowsOnItsTurnDoesNotStopTheNext()
    {
        final Stargate broken = gateNamed("Broken");
        when(broken.getGateStructureBlocks()).thenThrow(new IllegalStateException("half unloaded"));
        manager.when(StargateManager::getOpenGates).thenReturn(new LinkedHashSet<>(List.of(broken, gate)));
        HorizonRipple.tick();
        now += 7_000L;

        HorizonRipple.tick();

        assertFalse(HorizonRipple.isRippling(broken));
        assertTrue(HorizonRipple.isRippling(gate), "the next gate still has its turn");
    }

    /** Nobody near, nothing waits: coming back starts the wait again rather than rippling at once. */
    @Test
    void walkingAwayFromAGateForgetsItsWait()
    {
        HorizonRipple.tick();
        when(front.getLocation()).thenReturn(new Location(world, 12.0, 64.0, 200.0));
        now += 10_000L;
        HorizonRipple.tick();
        when(front.getLocation()).thenReturn(new Location(world, 12.0, 64.0, 25.0));
        HorizonRipple.tick();

        assertFalse(HorizonRipple.isRippling(gate), "back near: a fresh wait, not the one from before");
        now += 7_000L;
        HorizonRipple.tick();
        assertTrue(HorizonRipple.isRippling(gate));
    }

    @Test
    void theWaitIsSixToTwentySeconds()
    {
        for (int i = 0; i < 2_000; i++)
        {
            final long wait = DEFAULT_WAIT.getAsLong();
            assertTrue((wait >= 3_000L) && (wait <= 6_000L), wait + " is outside three to six seconds");
        }
    }

    @Test
    void aGateFacingEastIsRingedAcrossItsOwnPlane()
    {
        final List<Location> portal = new ArrayList<>();
        for (int z = 10; z <= 12; z++)
        {
            for (int y = 64; y <= 66; y++)
            {
                portal.add(new Location(world, 5, y, z));
            }
        }
        when(gate.getGateFacing()).thenReturn(BlockFace.EAST);
        when(gate.getGatePortalBlocks()).thenReturn(portal);

        final List<List<Location>> rings = HorizonRipple.ringsOf(gate);

        assertEquals(2, rings.size());
        assertEquals(List.of(new Location(world, 5, 65, 11)), rings.get(0), "the middle of a plane along z");
    }

    /** Clears the gate's horizon for its view, as the sweep at {@code gate-view: open} does, and draws it for these players. */
    private void drawnTheView(final Player... viewers) throws ReflectiveOperationException
    {
        final Set<String> cleared = PrivateStatics.of(GateViews.class, "CLEARED");
        cleared.add("Abydos");
        final Map<UUID, Set<String>> through = PrivateStatics.of(GateViews.class, "SEES_THROUGH");
        for (final Player viewer : viewers)
        {
            through.put(viewer.getUniqueId(), new HashSet<>(Set.of("Abydos")));
        }
    }

    /** At {@code gate-view: open}, whoever sees through the gate sees its own portal material cross the clear opening. */
    @Test
    void aViewerDrawnTheViewSeesThePortalMaterialCrossTheClearOpening() throws ReflectiveOperationException
    {
        final Player beside = playerAt(26.0);
        when(world.getPlayers()).thenReturn(List.of(front, beside));
        drawnTheView(front);

        assertTrue(HorizonRipple.start(gate));
        assertEquals(cells("water", 20, 12, 66), sentTo(front), "drawn the view: the horizon's own water");
        assertEquals(cells("ice", 20, 12, 66), sentTo(beside), "not drawn it: the ice a flat water gate ripples in");
        step();
        assertEquals(union(cells("air", 20, 12, 66), cells("water", 20, FIRST_RING)), sentTo(front),
            "the middle put back to the clear opening they see, the first ring drawn");
        assertEquals(union(cells("water", 20, 12, 66), cells("ice", 20, FIRST_RING)), sentTo(beside),
            "the middle put back to the horizon they see");
    }

    /** A flat lava gate ripples for nobody but a viewer of its cleared opening, who sees its lava cross it. */
    @Test
    void aFlatLavaGateRipplesOnlyForWhoeverSeesThroughIt() throws ReflectiveOperationException
    {
        when(gate.getEffectivePortalMaterial()).thenReturn(Material.LAVA);
        final Player beside = playerAt(26.0);
        when(world.getPlayers()).thenReturn(List.of(front, beside));
        drawnTheView(front);

        assertTrue(HorizonRipple.start(gate), "its horizon is cleared for a view");
        assertEquals(cells("lava", 20, 12, 66), sentTo(front));
        step();
        assertEquals(union(cells("air", 20, 12, 66), cells("lava", 20, FIRST_RING)), sentTo(front));
        assertTrue(sentTo(beside).isEmpty(), "a flat lava gate draws nobody else anything");
    }

    /** Somebody who stops being drawn the view mid ripple is put back to the horizon, not to a clear opening. */
    @Test
    void aViewerWhoStopsSeeingThroughMidRippleIsPutBackToTheHorizon() throws ReflectiveOperationException
    {
        drawnTheView(front);
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);
        final Map<UUID, Set<String>> through = PrivateStatics.of(GateViews.class, "SEES_THROUGH");
        through.remove(front.getUniqueId());

        step();

        assertEquals(union(cells("water", 20, 12, 66), cells("ice", 20, FIRST_RING)), sentTo(front),
            "what horizonFor says now: the water, and the next ring as anybody else sees it");
    }

    /** The horizon stopping being cleared calls the ripple off, its ring put back to the horizon drawn next. */
    @Test
    void theHorizonNoLongerClearedCallsTheRippleOff() throws ReflectiveOperationException
    {
        drawnTheView(front);
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);

        GateViews.irisOpening(gate);

        assertEquals(cells("water", 20, 12, 66), sentTo(front));
        assertTrue(pending.isEmpty(), "its next step dropped");
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** A viewer who leaves, or changes world, has dropped the chunk: nothing is put back for them. */
    @Test
    void aViewerWhoLeavesIsForgotten()
    {
        manager.when(() -> StargateManager.forgetPortalVisuals(any(UUID.class))).thenCallRealMethod();
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);

        StargateManager.forgetPortalVisuals(front.getUniqueId());
        step();

        assertEquals(cells("ice", 20, FIRST_RING), sentTo(front), "the next ring, and no water put back for the last");
    }

    @Test
    void aViewerWhoChangesWorldIsForgotten()
    {
        manager.when(() -> StargateManager.forgetSweptLayers(any(UUID.class))).thenCallRealMethod();
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);

        StargateManager.forgetSweptLayers(front.getUniqueId());
        step();

        assertEquals(cells("ice", 20, FIRST_RING), sentTo(front));
    }

    /** A throw while a ring is drawn calls the ripple off: what was sent goes back, and nothing is left registered. */
    @Test
    void aThrowWhileDrawingCallsTheRippleOffAndPutsItsRingBack()
    {
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);
        when(world.getNearbyEntities(any(BoundingBox.class))).thenThrow(new IllegalStateException("chunk unloading"));

        step();

        assertEquals(cells("water", 20, 12, 66), sentTo(front), "the middle back to water, and no ice drawn");
        assertTrue(pending.isEmpty(), "nothing more booked");
        assertFalse(HorizonRipple.isRippling(gate), "not left registered, so the gate can ripple again");
        when(world.getNearbyEntities(any(BoundingBox.class))).thenReturn(standing);
        now += HorizonRipple.MIN_GAP_MILLIS;
        assertTrue(HorizonRipple.start(gate));
    }

    /** A send that throws part way through a ring still has that ring put back, cells sent before it included. */
    @Test
    void aSendThatThrowsPartWayStillHasItsRingPutBack()
    {
        assertTrue(HorizonRipple.start(gate));
        sentTo(front);
        refuseIce = true;

        step();

        assertEquals(union(cells("water", 20, 12, 66), cells("water", 20, FIRST_RING)), sentTo(front),
            "the middle back, and the first ring put back to water rather than left half drawn");
        assertTrue(pending.isEmpty());
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** A throw on the very first ring leaves nothing registered or booked. */
    @Test
    void aThrowOnTheFirstRingLeavesNothingBehind()
    {
        when(world.getNearbyEntities(any(BoundingBox.class))).thenThrow(new IllegalStateException("chunk unloading"));

        assertFalse(HorizonRipple.start(gate));

        assertTrue(pending.isEmpty());
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** A booked step that throws outside the drawing is caught too, and the ripple dropped. */
    @Test
    void aBookedStepThatThrowsIsCaughtAndTheRippleDropped()
    {
        assertTrue(HorizonRipple.start(gate));
        when(gate.isGateIrisActive()).thenThrow(new IllegalStateException("gate half unloaded"));

        step();

        assertTrue(pending.isEmpty());
        assertFalse(HorizonRipple.isRippling(gate));
    }

    /** Turned off and on again, every wait starts afresh rather than firing at once on every gate. */
    @Test
    void forgettingEveryRippleForgetsTheWaitsToo()
    {
        HorizonRipple.tick();
        now += 60_000L;

        HorizonRipple.cancelAll();
        HorizonRipple.tick();

        assertFalse(HorizonRipple.isRippling(gate), "a fresh wait, not the one left from before");
        now += 7_000L;
        HorizonRipple.tick();
        assertTrue(HorizonRipple.isRippling(gate));
    }
}
