package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.logging.Level;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;
import com.wormhole_xtreme.wormhole.utils.RecordingCreation;

/**
 * What a gate build preview puts in the world, who sees it, and when it goes.
 *
 * <p>Every display here is a real entity on the server. One that is shown to somebody else, saved
 * with the chunk, or left behind when its owner leaves is a block only one player can see the
 * reason for, so each way a preview ends is checked to take its displays with it.
 */
class GatePreviewsTest
{
    /** Standard's frame and chevrons, and the button on its DHD. */
    private static final int STANDARD_BLOCKS = 19;

    /** And its opening, which the server's block limit counts too. */
    private static final int STANDARD_OPENING = 21;

    private WormholeXTreme plugin;
    private World world;
    private Player owner;
    private final List<BlockDisplay> spawned = new ArrayList<>();
    private final List<Interaction> buttons = new ArrayList<>();
    private final Map<Material, BlockData> data = new EnumMap<>(Material.class);
    private Runnable dialStep;
    /**
     * Every iris step booked and not cancelled, in the order they were booked.
     *
     * <p>A map rather than one slot. Holding only the latest hides a sweep that was never
     * called off: the second sweep's booking simply overwrites the first's, and a test can no
     * longer tell one running sweep from two.
     */
    private final Map<Integer, Runnable> irisPending = new LinkedHashMap<>();
    private int nextIrisTask = 1;
    private BukkitTask dialTask;
    private final List<Long> dialDelays = new ArrayList<>();
    /**
     * Holds an iris sweep's next step, with a cancel that really drops it.
     *
     * <p>A task mock that accepts {@code cancel()} and does nothing makes a called-off sweep
     * look exactly like a running one, so an iris toggled twice would run the first sweep's
     * leftovers over the second's and the test would never notice.
     *
     * @param step
     *            the step the sweep booked
     * @return the task standing for it
     */
    private BukkitTask bookIrisStep(final Runnable step)
    {
        final int id = nextIrisTask++;
        irisPending.put(id, step);
        final BukkitTask task = mock(BukkitTask.class);
        doAnswer(invocation ->
        {
            irisPending.remove(id);
            return null;
        }).when(task).cancel();
        return task;
    }

    /**
     * Runs an iris sweep to its end.
     *
     * <p>The iris arrives a ring at a time now, booked through the same {@code later} seam the
     * dial uses, so a test that wants the finished iris has to run the sweep out first -- the
     * same way {@link #dialStep} is run for a dial.
     */
    private void finishIrisSweep()
    {
        for (int guard = 0; !irisPending.isEmpty() && (guard < 60); guard++)
        {
            final Integer id = irisPending.keySet().iterator().next();
            irisPending.remove(id).run();
        }
    }

    /** What stands in the world, by x, y and z; air everywhere else. */
    private final Map<List<Integer>, Material> standing = new HashMap<>();
    /** Players online besides the owner. */
    private final Map<UUID, Player> others = new HashMap<>();
    /** Where a block was written, in order. */
    private final List<List<Integer>> written = new ArrayList<>();
    private final long[] now = { 1_000_000L };
    private Stargate3DShape standard;
    private final RecordingCreation creation = new RecordingCreation(type ->
    {
        if (type == Interaction.class)
        {
            final Interaction button = mock(Interaction.class);
            when(button.isValid()).thenReturn(true);
            buttons.add(button);
            return button;
        }
        final BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        spawned.add(display);
        return display;
    });

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        // These tests pin the chevrons' and the woosh's timing; the ring's turn has its own test.
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, false);
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));

        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
        {
            final org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
            when(block.getBlockData()).thenAnswer(read -> GatePreviews.blockData.apply(
                (standing.get(List.of(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))) == null) ? Material.AIR
                    : standing.get(List.of(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
            final List<Integer> at = List.of(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            // AIR rather than null where nothing stands: that is what a real block answers, and
            // the layering asks whether a cell is air before drawing into it.
            when(block.getType()).thenReturn(standing.getOrDefault(at, Material.AIR));
            Mockito.doAnswer(set ->
            {
                written.add(at);
                data.forEach((material, shown) ->
                {
                    if (shown == set.getArgument(0))
                    {
                        standing.put(at, material);
                    }
                });
                return null;
            }).when(block).setBlockData(any(BlockData.class), Mockito.anyBoolean());
            return block;
        });
        final org.bukkit.WorldBorder border = mock(org.bukkit.WorldBorder.class);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        GatePreviews.occupied = (w, x, y, z) -> false;
        HiddenEntities.creationWith(creation);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getWorld()).thenReturn(world);
        standAt(0.5, 0.5, 180f);

        GatePreviews.clock = () -> now[0];
        GatePreviews.online = id -> id.equals(owner.getUniqueId()) ? owner : others.get(id);
        // Buttons and levers share Switch data, as on a server.
        GatePreviews.blockData = material -> data.computeIfAbsent(material,
            m -> ((m == Material.STONE_BUTTON) || (m == Material.LEVER)) ? buttonData() : mock(BlockData.class));
        GatePreviews.later = (ticks, step) ->
        {
            dialStep = step;
            dialDelays.add(ticks);
            dialTask = mock(BukkitTask.class);
            return dialTask;
        };
        GatePreviews.irisLater = (ticks, step) -> bookIrisStep(step);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GatePreviews.clear();
        com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry.load(null);
        HiddenEntities.creationWith(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Directional buttonData()
    {
        final org.bukkit.block.data.type.Switch data = mock(org.bukkit.block.data.type.Switch.class);
        when(data.getFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        return data;
    }

    /** Puts the owner's feet at a spot at y 64, looking along a yaw, level. */
    private void standAt(final double x, final double z, final float yaw)
    {
        when(owner.getLocation()).thenReturn(new Location(world, x, 64.0, z, yaw, 0f));
        when(owner.getEyeLocation()).thenReturn(new Location(world, x, 65.62, z, yaw, 0f));
    }

    /** Standard, north of whoever looks north from the origin: its DHD in the block in front. */
    private List<Cell> standardLookingNorth()
    {
        return GateBlueprint.of(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
    }

    /**
     * A preview is one display a block, not saved, hidden from everybody, and shown to its owner.
     *
     * <p>The hiding has to happen before the display is added to the world, or every player in
     * range is sent it first.
     */
    @Test
    void aPreviewIsOneHiddenUnsavedDisplayABlockShownOnlyToItsOwner()
    {
        final Player bystander = mock(Player.class);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        assertEquals(1, buttons.size(), "and the box a click on its button lands on");
        assertEquals(Collections.nCopies(STANDARD_BLOCKS + 1, true), creation.hiddenWhenAdded,
            "each unsaved and hidden by the time it was added");
        for (final BlockDisplay display : spawned)
        {
            final InOrder order = inOrder(display, owner);
            order.verify(display).setBlock(any(BlockData.class));
            order.verify(owner).showEntity(plugin, display);
        }
        verifyNoInteractions(bystander);
    }

    /** Each display stands on a cell of the blueprint, and the button is turned to face the builder. */
    @Test
    void theDisplaysStandOnTheBlueprintWithTheButtonFacingTheBuilder()
    {
        final List<Location> places = creation.places;
        final Directional button = buttonData();
        GatePreviews.blockData = material -> (material == Material.STONE_BUTTON) ? button : mock(BlockData.class);

        GatePreviews.show(owner, standard, null);

        final List<Cell> cells = standardLookingNorth();
        assertEquals(cells.size() + 1, places.size(), "the displays, and the button's box last");
        for (int i = 0; i < cells.size(); i++)
        {
            assertEquals(new Location(world, cells.get(i).x(), cells.get(i).y(), cells.get(i).z()), places.get(i));
        }
        verify(button).setFacing(BlockFace.SOUTH);
    }

    /** A second preview stands beside the first, so every shape can be shown side by side. */
    @Test
    void aSecondPreviewStandsBesideTheFirstRatherThanReplacingIt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.countOf(owner.getUniqueId()));
        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display, never()).remove());
    }

    /** Clearing takes away the preview looked at, and leaves the others standing. */
    @Test
    void clearingTakesAwayOnlyThePreviewLookedAt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> first = new ArrayList<>(spawned.subList(0, STANDARD_BLOCKS));
        final List<BlockDisplay> second = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));

        standAt(40.5, 1.5, 0f); // a step back, out of the preview, looking away
        assertFalse(GatePreviews.clearLookedAt(owner), "looking south, away from both");
        standAt(40.5, 0.5, 180f);
        assertTrue(GatePreviews.clearLookedAt(owner));

        second.forEach(display -> verify(display).remove());
        first.forEach(display -> verify(display, never()).remove());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** clear all takes every preview a player has. */
    @Test
    void clearAllTakesEveryPreview()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.clearAll(owner));

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** Quitting or changing world takes a player's previews with them. */
    @Test
    void forgettingAPlayerTakesTheirPreviews()
    {
        GatePreviews.show(owner, standard, null);

        GatePreviews.forget(owner.getUniqueId());

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }

    /**
     * A preview times out once its owner stops using build commands, and not while they keep
     * using them.
     */
    @Test
    void aPreviewTimesOutUnlessItsOwnerKeepsUsingBuildCommands()
    {
        GatePreviews.show(owner, standard, null);

        now[0] += 9 * 60_000L;
        standAt(0.5, 1.5, 0f); // a step back, out of the preview, looking away
        GatePreviews.clearLookedAt(owner);
        now[0] += 6 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display, never()).remove());

        now[0] += 5 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** A gate found where a preview's button stood takes the preview away; one found elsewhere does not. */
    @Test
    void aGateBuiltWhereThePreviewStoodTakesThePreviewAway()
    {
        GatePreviews.show(owner, standard, null);
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();

        GatePreviews.builtAt(world, button.x() + 1, button.y(), button.z());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));

        GatePreviews.builtAt(world, button.x(), button.y(), button.z());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
        spawned.forEach(display -> verify(display).remove());
    }

    /** A preview that would take the server past its block limit is refused, and nothing is spawned. */
    @Test
    void aPreviewPastTheServersBlockLimitIsRefused()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, STANDARD_BLOCKS + STANDARD_OPENING);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));
        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /**
     * Asking for a preview the limit refuses still counts as using build commands, so the ones
     * already standing do not time out on a player who is busy trying.
     */
    @Test
    void aRefusedPreviewStillKeepsTheOthersFromTimingOut()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, STANDARD_BLOCKS + STANDARD_OPENING);
        GatePreviews.show(owner, standard, null);

        now[0] += 9 * 60_000L;
        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));
        now[0] += 9 * 60_000L;
        GatePreviews.tick();

        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()), "renewed nine minutes in, so still up at eighteen");
        spawned.forEach(display -> verify(display, never()).remove());
    }

    /**
     * A display the server dropped with its chunk comes back once the chunk is loaded again, and
     * not before: a preview never loads a chunk.
     */
    @Test
    void aDisplayDroppedWithItsChunkComesBackOnlyOnceTheChunkIsLoaded()
    {
        GatePreviews.show(owner, standard, null);
        final BlockDisplay dropped = spawned.get(0);
        when(dropped.isValid()).thenReturn(false);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS, spawned.size(), "nothing spawned into an unloaded chunk");

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS + 1, spawned.size(), "the one dropped display, and only that one");
    }

    /** Disabling the plugin takes every preview on the server. */
    @Test
    void disablingTakesEveryPreviewOnTheServer()
    {
        final Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getLocation()).thenReturn(new Location(world, 80.5, 64.0, 0.5, 180f, 0f));
        GatePreviews.show(owner, standard, null);
        GatePreviews.show(other, standard, null);

        GatePreviews.restoreAll();

        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }

    private List<BlockDisplay> ringDisplaysOfWave(final int wave)
    {
        final List<Cell> cells = standardLookingNorth();
        final List<BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).wave() == wave)
            {
                out.add(spawned.get(i));
            }
        }
        return out;
    }

    private List<BlockDisplay> dhdDisplays()
    {
        final List<Cell> cells = standardLookingNorth();
        final List<BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).dhd())
            {
                out.add(spawned.get(i));
            }
        }
        return out;
    }

    /**
     * -activate lights the chevrons a wave at a time, sends the kawoosh out and back, and leaves the
     * opening filled, as a gate dials.
     *
     * <p>Standard has no chevron material, so a lit chevron is its light material, glowstone. The
     * wormhole is water, which no block display draws, so it is sent to the owner as fake blocks
     * the way a real gate draws it: three woosh steps of 21, 13 and 5 cells out, the same back, then
     * the 21-cell opening.
     */
    @Test
    void activatingLightsTheChevronsThenSendsTheKawooshOutAndBackAndFillsTheOpening()
    {
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Control.DIALLING, GatePreviews.activate(owner));
        assertNotNull(dialStep, "a dial runs on a timer");

        dialStep.run();
        ringDisplaysOfWave(1).forEach(d -> verify(d).setBlock(data.get(Material.GLOWSTONE)));
        ringDisplaysOfWave(2).forEach(d -> verify(d, never()).setBlock(data.get(Material.GLOWSTONE)));
        for (int wave = 2; wave <= 7; wave++)
        {
            dialStep.run();
        }
        ringDisplaysOfWave(7).forEach(d -> verify(d).setBlock(data.get(Material.GLOWSTONE)));
        verify(owner, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        for (int step = 1; step <= 3; step++)
        {
            dialStep.run();
        }
        verify(owner, times(21 + 13 + 5)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        dialStep.run();
        dialStep.run();
        dialStep.run();

        verify(owner, times(21 + 13 + 5)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        verify(owner, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
        final long light = standard.getShapeLightTicks();
        final long woosh = standard.getShapeWooshTicks();
        final long hold = com.wormhole_xtreme.wormhole.model.Stargate.LAST_CHEVRON_PAUSE_TICKS;
        assertEquals(List.of(light, light, light, light, light, light, light, hold, woosh, woosh, woosh, woosh, woosh),
            dialDelays, "chevrons the shape's light ticks apart, the last held before the woosh, and the woosh its"
                + " ticks apart, as a real gate times them, then nothing more");
        assertEquals(STANDARD_BLOCKS, spawned.size(), "the wormhole is fake blocks, not displays");
    }

    /**
     * A preview's wormhole is laid in the preview's own plane, the way a real gate lays its own.
     *
     * <p>The same bug a real gate had: a {@code NETHER_PORTAL} portal material carries an axis,
     * and the default is right for half the gates and edge-on for the other half. Pinned here as
     * well as on the gate because the two draw down different paths, and the whole point of
     * {@code DrawnHorizon} was that a rule living in one of them eventually stops matching the
     * other.
     */
    @Test
    void aPreviewsWormholeIsLaidInThePreviewsPlane()
    {
        final org.bukkit.block.data.Orientable portal = mock(org.bukkit.block.data.Orientable.class);
        when(portal.getAxes()).thenReturn(java.util.EnumSet.of(org.bukkit.Axis.X, org.bukkit.Axis.Z));
        data.put(Material.WATER, portal);

        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 10; step++)
        {
            dialStep.run();
        }

        // The builder looks north, so the preview faces south and its opening runs across X.
        verify(portal, atLeastOnce()).setAxis(org.bukkit.Axis.X);
        verify(portal, never()).setAxis(org.bukkit.Axis.Z);
    }

    /**
     * And so is a preview's shut iris, which stands in displays rather than in sent blocks.
     *
     * <p>A separate path from the wormhole above, and the one a nether-portal iris would come
     * down. The blueprint's own cells are not covered because they cannot be: a blueprint is
     * frame, chevron, button and dial sign, and the opening is a list of its own.
     */
    @Test
    void aPreviewsShutIrisIsLaidInThePreviewsPlane()
    {
        final org.bukkit.block.data.Orientable iris = mock(org.bukkit.block.data.Orientable.class);
        when(iris.getAxes()).thenReturn(java.util.EnumSet.of(org.bukkit.Axis.X, org.bukkit.Axis.Z));
        data.put(Material.STONE, iris);

        GatePreviews.show(owner, standard, null);
        GatePreviews.iris(owner);
        finishIrisSweep();

        verify(iris, atLeastOnce()).setAxis(org.bukkit.Axis.X);
        verify(iris, never()).setAxis(org.bukkit.Axis.Z);
    }

    /** -activate on a gate that is open shuts it down: the chevrons go out and the opening is taken back. */
    @Test
    void activatingAnOpenGateShutsItDown()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }

        assertEquals(GatePreviews.Control.SHUT_DOWN, GatePreviews.activate(owner));

        verify(owner, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        ringDisplaysOfWave(1).forEach(d -> verify(d, org.mockito.Mockito.atLeast(2)).setBlock(data.get(Material.OBSIDIAN)));
    }

    /** A gate found where an open preview stood takes back the wormhole it sent its owner. */
    @Test
    void aGateBuiltWhereAnOpenPreviewStoodTakesBackItsWormhole()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();

        GatePreviews.builtAt(world, button.x(), button.y(), button.z());

        verify(owner, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }

    /** Clearing an open preview whose chunk has unloaded sends nothing back, and loads nothing to do it. */
    @Test
    void clearingAnOpenPreviewInAnUnloadedChunkLeavesTheChunkAlone()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final int sentBack = (21 + 13 + 5);
        verify(owner, times(sentBack)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        final long reads = readsOfTheWorld();

        assertEquals(1, GatePreviews.clearAll(owner));

        verify(owner, times(sentBack)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        assertEquals(reads, readsOfTheWorld(), "nothing read, so nothing loaded");
    }

    private long readsOfTheWorld()
    {
        return Mockito.mockingDetails(world).getInvocations().stream()
            .filter(call -> "getBlockAt".equals(call.getMethod().getName())).count();
    }

    private void place(final Cell cell, final Material material)
    {
        standing.put(List.of(cell.x(), cell.y(), cell.z()), material);
    }

    private static float scaleOf(final BlockDisplay display)
    {
        final ArgumentCaptor<Transformation> drawn = ArgumentCaptor.forClass(Transformation.class);
        verify(display, Mockito.atLeastOnce()).setTransformation(drawn.capture());
        return drawn.getValue().getScale().x();
    }

    /**
     * The guide takes a correctly placed block's display away, draws a block still to place small, and
     * outlines a wrong one in red; turned off, it draws the whole gate again.
     */
    @Test
    void theGuideDrawsWhatIsLeftSmallWhatIsWrongInRedAndNothingOverWhatIsDone()
    {
        final List<Cell> cells = standardLookingNorth();
        GatePreviews.show(owner, standard, null);
        final BlockDisplay done = spawned.get(0);
        final BlockDisplay wrong = spawned.get(1);
        final BlockDisplay left = spawned.get(2);
        place(cells.get(0), Material.OBSIDIAN);
        place(cells.get(1), Material.DIRT);

        assertEquals(GatePreviews.Control.GUIDE_ON, GatePreviews.guide(owner));

        verify(done).remove();
        assertEquals(1.02f, scaleOf(wrong), 1e-6f);
        verify(wrong).setGlowing(true);
        verify(wrong).setGlowColorOverride(Color.RED);
        assertEquals(0.5f, scaleOf(left), 1e-6f);
        verify(left, never()).setGlowing(true);
        assertEquals(STANDARD_BLOCKS, spawned.size(), "nothing new drawn");

        assertEquals(GatePreviews.Control.GUIDE_OFF, GatePreviews.guide(owner));

        assertEquals(STANDARD_BLOCKS + 1, spawned.size(), "the placed block's display back");
        assertEquals(1.0f, scaleOf(wrong), 1e-6f);
        final InOrder glow = inOrder(wrong);
        glow.verify(wrong).setGlowing(true);
        glow.verify(wrong).setGlowing(false);
        assertEquals(1.0f, scaleOf(left), 1e-6f);
    }

    /** With the guide on, a block in the opening is marked in red glass, and the mark goes once it is cleared. */
    @Test
    void theGuideMarksABlockInTheOpeningUntilItIsCleared()
    {
        GatePreviews.show(owner, standard, null);
        final Cell inTheWay = GateBlueprint.openingOf(standard,
            GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH)).get(0);
        place(inTheWay, Material.COBBLESTONE);

        GatePreviews.guide(owner);

        assertEquals(new Location(world, inTheWay.x(), inTheWay.y(), inTheWay.z()),
            creation.places.get(creation.places.size() - 1));
        final BlockDisplay mark = spawned.get(spawned.size() - 1);
        verify(mark).setBlock(data.get(Material.RED_STAINED_GLASS));
        verify(mark).setGlowing(true);

        standing.clear();
        GatePreviews.tick();

        verify(mark).remove();
    }

    /** Once every block is in place and the opening clear, the builder is told, once. */
    @Test
    void theBuilderIsToldOnceWhenEveryBlockIsInPlace()
    {
        final List<Cell> cells = standardLookingNorth();
        GatePreviews.show(owner, standard, null);
        GatePreviews.guide(owner);
        for (final Cell cell : cells)
        {
            place(cell, (cell.part() == Part.BUTTON) ? Material.OAK_BUTTON : Material.OBSIDIAN);
        }
        final Cell last = cells.get(0);
        standing.remove(List.of(last.x(), last.y(), last.z()));
        GatePreviews.tick();
        verify(owner, never()).sendMessage(contains("is built!"));

        final Cell inTheWay = GateBlueprint.openingOf(standard,
            GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH)).get(5);
        place(inTheWay, Material.DIRT);
        place(last, Material.OBSIDIAN);
        GatePreviews.tick();
        verify(owner, never()).sendMessage(contains("is built!"));

        standing.remove(List.of(inTheWay.x(), inTheWay.y(), inTheWay.z()));
        GatePreviews.tick();
        GatePreviews.tick();

        verify(owner, times(1)).sendMessage(contains("Standard is built!"));
        assertTrue(spawned.stream().allMatch(display -> Mockito.mockingDetails(display).getInvocations().stream()
            .anyMatch(call -> "remove".equals(call.getMethod().getName()))), "and nothing is drawn over the gate");
    }

    /** A dial sign is not needed for the gate to be found, so the build is finished without one. */
    @Test
    void aSignDialGateIsFinishedWithoutItsSign() throws Exception
    {
        final Stargate3DShape signDial = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/StandardSignDial.shape")).toArray(new String[0]));
        final List<Cell> cells = GateBlueprint.of(signDial, GateBlueprint.inFrontOf(signDial, 0, 64, 0, BlockFace.NORTH));
        assertTrue(cells.stream().anyMatch(cell -> cell.part() == Part.DIAL_SIGN));
        GatePreviews.show(owner, signDial, null);
        for (final Cell cell : cells)
        {
            if (cell.part() != Part.DIAL_SIGN)
            {
                place(cell, (cell.part() == Part.BUTTON) ? Material.LEVER : Material.OBSIDIAN);
            }
        }

        GatePreviews.guide(owner);

        verify(owner).sendMessage(contains("StandardSignDial is built!"));
    }

    /**
     * A block placed or broken inside a preview redraws it on the next tick, once however many change
     * in that tick; one outside it redraws nothing.
     */
    @Test
    void aChangedBlockRedrawsThePreviewItStandsInOnTheNextTick()
    {
        final Cell first = standardLookingNorth().get(0);
        GatePreviews.show(owner, standard, null);
        GatePreviews.guide(owner);
        dialDelays.clear();

        GatePreviews.blockChanged(world, first.x() + 50, first.y(), first.z());
        assertTrue(dialDelays.isEmpty(), "outside the preview");

        place(first, Material.OBSIDIAN);
        GatePreviews.blockChanged(world, first.x(), first.y(), first.z());
        GatePreviews.blockChanged(world, first.x(), first.y(), first.z());
        assertEquals(List.of(1L), dialDelays);
        verify(spawned.get(0), never()).remove();

        dialStep.run();

        verify(spawned.get(0)).remove();
        GatePreviews.blockChanged(world, first.x(), first.y(), first.z());
        assertEquals(List.of(1L, 1L), dialDelays, "and the next change is seen");
    }

    /** A redraw queued for a preview cleared before it runs draws nothing back. */
    @Test
    void aRedrawForAClearedPreviewDrawsNothing()
    {
        final Cell first = standardLookingNorth().get(0);
        GatePreviews.show(owner, standard, null);
        GatePreviews.blockChanged(world, first.x(), first.y(), first.z());
        GatePreviews.clearAll(owner);
        final int drawn = spawned.size();

        dialStep.run();

        assertEquals(drawn, spawned.size());
    }

    /** A real button on the preview's takes clicks: the preview's own box goes, guide or not. */
    @Test
    void aRealButtonOnThePreviewsTakesTheClicks()
    {
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();
        GatePreviews.show(owner, standard, null);
        assertEquals(1, buttons.size());

        place(button, Material.STONE_BUTTON);
        GatePreviews.blockChanged(world, button.x(), button.y(), button.z());
        dialStep.run();

        verify(buttons.get(0)).remove();
        assertEquals(1, buttons.size(), "and none put back");
    }

    /** -materials counts what the preview takes, what is left to place, and what is in its opening. */
    @Test
    void theMaterialsListCountsWhatIsLeft()
    {
        final List<Cell> cells = standardLookingNorth();
        GatePreviews.show(owner, standard, null);
        place(cells.get(0), Material.OBSIDIAN);
        place(cells.get(1), Material.OBSIDIAN);
        place(GateBlueprint.openingOf(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH)).get(3),
            Material.DIRT);

        final GatePreviews.Materials list = GatePreviews.materials(owner);

        assertEquals("Standard", list.shape());
        assertEquals(List.of(new BuildGuide.Need("obsidian", STANDARD_BLOCKS - 1, STANDARD_BLOCKS - 3),
            new BuildGuide.Need("button or lever", 1, 1)), list.needs());
        assertEquals(1, list.blocked());
        assertEquals(Material.OBSIDIAN, list.frame());

        standAt(0.5, 1.5, 0f);
        assertNull(GatePreviews.materials(owner), "looking away");
    }

    /** Right-clicking the preview's button dials it; a second click inside the same moment is the same click. */
    @Test
    void rightClickingThePreviewsButtonDialsItOnce()
    {
        GatePreviews.show(owner, standard, null);
        final Interaction button = buttons.get(0);

        assertTrue(GatePreviews.pressed(owner, button));
        assertNotNull(dialStep, "the click dialled it");
        final Runnable first = dialStep;

        now[0] += 100L;
        assertTrue(GatePreviews.pressed(owner, button));
        verify(dialTask, never()).cancel();

        now[0] += 1_000L;
        assertTrue(GatePreviews.pressed(owner, button));
        verify(dialTask).cancel();
        assertEquals(first, dialStep, "shut down, not dialled again");

        assertFalse(GatePreviews.pressed(owner, mock(Interaction.class)), "somebody else's entity is not a button");
    }

    /** -iris closes an iris of the iris material over the opening, and opens it again. */
    @Test
    void theIrisClosesOverTheOpeningAndOpensAgain()
    {
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Control.IRIS_CLOSED, GatePreviews.iris(owner));
        finishIrisSweep();
        final List<BlockDisplay> iris = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));
        assertEquals(STANDARD_OPENING, iris.size(), "every cell of the opening, once the sweep is out");
        iris.forEach(d -> verify(d).setBlock(data.get(Material.STONE)));

        assertEquals(GatePreviews.Control.IRIS_OPENED, GatePreviews.iris(owner));
        finishIrisSweep();
        iris.forEach(d -> verify(d).remove());
    }

    /** -material redresses the preview in a group, or changes one role; a non-block changes nothing. */
    @Test
    void materialsChangeByGroupOrByRole()
    {
        GatePreviews.show(owner, standard, null);
        final BlockDisplay frame = spawned.get(0);
        GatePreviews.blockData = material ->
        {
            if (material == Material.DIAMOND)
            {
                throw new IllegalArgumentException("not a block");
            }
            return data.computeIfAbsent(material, m -> mock(BlockData.class));
        };

        assertEquals(GatePreviews.Control.CHANGED, GatePreviews.material(owner,
            new com.wormhole_xtreme.wormhole.model.MaterialGroup("Atlantis", Material.LAPIS_BLOCK, Material.WATER,
                Material.STONE, Material.SEA_LANTERN, Material.OAK_WALL_SIGN)));
        verify(frame).setBlock(data.get(Material.LAPIS_BLOCK));

        assertEquals(GatePreviews.Control.CHANGED,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.GOLD_BLOCK));
        verify(frame).setBlock(data.get(Material.GOLD_BLOCK));

        assertEquals(GatePreviews.Control.NOT_A_BLOCK,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.DIAMOND));
        assertEquals(Material.GOLD_BLOCK, GatePreviews.of(owner.getUniqueId()).get(0).palette().structure());
    }

    /** Changing the portal material of an open preview sends its owner the wormhole in the new one. */
    @Test
    void anOpenPreviewShowsANewPortalMaterialAtOnce()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }

        assertEquals(GatePreviews.Control.CHANGED,
            GatePreviews.material(owner, GateBlueprint.Role.PORTAL, Material.LAVA));

        verify(owner, times(STANDARD_OPENING)).sendBlockChange(any(Location.class), eq(data.get(Material.LAVA)));
    }

    /**
     * -layer shows the layers up to one: the next each time, all again after the last, or a number; a
     * number past the last changes nothing.
     */
    @Test
    void layersShowUpToOneAtATimeThenAll()
    {
        final List<Cell> cells = standardLookingNorth();
        final List<Integer> built = cells.stream().map(Cell::layer).distinct().sorted().toList();
        assertEquals(2, built.size(), "Standard: its ring, and its DHD in front");
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> first = new ArrayList<>(spawned);

        assertEquals(new GatePreviews.Layers(1, 2, true), GatePreviews.layers(owner, GatePreviews.NEXT_LAYER));

        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).layer() == built.get(0))
            {
                verify(first.get(i), never()).remove();
            }
            else
            {
                verify(first.get(i)).remove();
            }
        }
        assertTrue(cells.stream().anyMatch(cell -> cell.layer() != built.get(0)), "something was hidden");
        verify(buttons.get(0)).remove();

        assertEquals(new GatePreviews.Layers(2, 2, true), GatePreviews.layers(owner, GatePreviews.NEXT_LAYER));
        final int back = spawned.size();
        assertTrue(back > first.size(), "the second layer drawn again");

        assertEquals(new GatePreviews.Layers(2, 2, false), GatePreviews.layers(owner, 3));
        assertEquals(back, spawned.size());

        assertEquals(new GatePreviews.Layers(0, 2, true), GatePreviews.layers(owner, GatePreviews.NEXT_LAYER));
        assertEquals(new GatePreviews.Layers(1, 2, true), GatePreviews.layers(owner, 1));
        assertEquals(new GatePreviews.Layers(0, 2, true), GatePreviews.layers(owner, GatePreviews.ALL_LAYERS));

        standAt(0.5, 1.5, 0f);
        assertNull(GatePreviews.layers(owner, 1), "looking away");
    }

    /**
     * A preview stood on a button already placed lands where a gate detected from that button would:
     * the same cells as one stood in front of a player whose DHD it is.
     */
    @Test
    void aPreviewOnAPlacedButtonStandsWhereTheGateWouldBeFound()
    {
        final List<Cell> expected = GateBlueprint.of(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.WEST));
        final Cell buttonCell = expected.stream().filter(c -> c.part() == Part.BUTTON).findFirst().orElseThrow();
        final org.bukkit.block.Block button = mock(org.bukkit.block.Block.class);
        when(button.getWorld()).thenReturn(world);
        when(button.getX()).thenReturn(buttonCell.x() + 30);
        when(button.getY()).thenReturn(buttonCell.y());
        when(button.getZ()).thenReturn(buttonCell.z());

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.showOn(owner, standard, null, button, BlockFace.EAST));

        for (int i = 0; i < expected.size(); i++)
        {
            final Cell cell = expected.get(i);
            assertEquals(new Location(world, cell.x() + 30, cell.y(), cell.z()), creation.places.get(i));
        }
    }

    /** -dhd hides the DHD and its button for a picture of the ring alone, and shows them again. */
    @Test
    void theDhdHidesForAPictureOfTheRingAndComesBack()
    {
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> dhd = dhdDisplays();
        final List<BlockDisplay> ring = ringDisplaysOfWave(1);
        assertEquals(3, dhd.size(), "Standard's DHD: its block, the iris lever's block, and the button");

        assertEquals(GatePreviews.Control.DHD_HIDDEN, GatePreviews.toggleDhd(owner));
        dhd.forEach(d -> verify(d).remove());
        ring.forEach(d -> verify(d, never()).remove());
        verify(buttons.get(0)).remove();

        assertEquals(GatePreviews.Control.DHD_SHOWN, GatePreviews.toggleDhd(owner));
        assertEquals(STANDARD_BLOCKS + dhd.size(), spawned.size(), "the DHD's displays back");
        assertEquals(2, buttons.size(), "and its button's box");
    }

    /** Every control answers that nothing is looked at when the player looks away from their previews. */
    @Test
    void theControlsNeedAPreviewLookedAt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(0.5, 1.5, 0f); // a step back, out of the preview, looking away

        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.activate(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.iris(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.toggleDhd(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.GOLD_BLOCK));
        assertEquals(STANDARD_BLOCKS, spawned.size());
    }

    /** Clearing a preview part way through dialling stops the dial and takes the button's box too. */
    @Test
    void clearingADiallingPreviewStopsTheDial()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        dialStep.run();

        assertEquals(1, GatePreviews.clearAll(owner));

        verify(dialTask).cancel();
        verify(buttons.get(0)).remove();
    }

    /**
     * A Standard gate is shown the classic way, its chevrons drawn as frame, and -chevrons shows the
     * group's chevron blocks and hides them again. Lit, a plain chevron shows the light material, as
     * a gate built without chevron blocks does.
     */
    @Test
    void aStandardGateStartsWithPlainChevronsAndChevronsShowsThem()
    {
        final com.wormhole_xtreme.wormhole.model.MaterialGroup lamps = new com.wormhole_xtreme.wormhole.model.MaterialGroup(
            "Standard", Material.OBSIDIAN, Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN,
            Material.REDSTONE_LAMP);
        GatePreviews.show(owner, standard, lamps);
        final BlockDisplay chevron = ringDisplaysOfWave(1).get(0);
        verify(chevron).setBlock(data.get(Material.OBSIDIAN));
        verify(chevron, never()).setBlock(data.get(Material.REDSTONE_LAMP));

        assertEquals(GatePreviews.Control.CHEVRONS_SHOWN, GatePreviews.toggleChevrons(owner));
        verify(chevron).setBlock(data.get(Material.REDSTONE_LAMP));

        assertEquals(GatePreviews.Control.CHEVRONS_PLAIN, GatePreviews.toggleChevrons(owner));
        GatePreviews.activate(owner);
        dialStep.run();
        verify(chevron).setBlock(data.get(Material.GLOWSTONE));
    }

    /** Any other group shows its chevron blocks from the start: they are its look. */
    @Test
    void anotherGroupShowsItsChevronsFromTheStart()
    {
        final com.wormhole_xtreme.wormhole.model.MaterialGroup atlantis = new com.wormhole_xtreme.wormhole.model.MaterialGroup(
            "Atlantis", Material.OBSIDIAN, Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN,
            Material.REDSTONE_LAMP);
        GatePreviews.show(owner, standard, atlantis);

        verify(ringDisplaysOfWave(1).get(0)).setBlock(data.get(Material.REDSTONE_LAMP));
        assertEquals(Material.REDSTONE_LAMP, GatePreviews.of(owner.getUniqueId()).get(0).drawnPalette().chevron());
    }

    /** The limit counts a preview's opening too, since dialling or closing the iris fills it. */
    @Test
    void theBlockLimitCountsTheOpening()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, (STANDARD_BLOCKS + STANDARD_OPENING) - 1);

        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));
        assertTrue(spawned.isEmpty());
    }

    /**
     * A closing iris covers the wormhole rather than taking it back; clearing takes it back.
     *
     * <p>It used to take it back, from when the iris stood in the horizon's place instead of in
     * front of it. Reported from a server: the water was replaced with air a beat before the
     * first ring of the sweep arrived, so the wormhole read as having closed rather than been
     * covered -- and a sweep over empty air is a sweep over nothing.
     *
     * <p>Since the preview stacks its layers, "covered" means the wormhole moves a block behind
     * the ring for a viewer in front, exactly as a real gate's does -- not that it is taken
     * away. Through a glass iris it reads the same either way; what must never happen is the
     * opening going empty.
     */
    @Test
    void aClosingIrisCoversTheWormholeAndClearingTakesItBack()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final int takenBackByTheWoosh = 21 + 13 + 5;

        GatePreviews.iris(owner);
        finishIrisSweep();
        assertTrue(wormholeSendsAlong(owner, -1) > 0,
            "the wormhole moved a block behind the ring rather than being taken away");

        GatePreviews.iris(owner);
        finishIrisSweep();
        assertTrue(wormholeSendsAlong(owner, 0) > 0, "and comes back to the ring when the iris opens");

        assertEquals(1, GatePreviews.clearAll(owner));
        verify(owner, atLeast(takenBackByTheWoosh + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }

    /** The preview's opening cells, as the blueprint lays them out for the fixture's preview. */
    private List<Cell> openingCells()
    {
        return GateBlueprint.openingOf(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
    }

    /** The facing of the fixture's preview: the way its DHD's button faces, back at the owner. */
    private BlockFace previewFacing()
    {
        return GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH).facing();
    }

    /** Opens the fixture's preview, with its wormhole showing. */
    private void openThePreview()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
    }

    /** A shared viewer standing the given number of blocks along the facing from the opening. */
    private Player viewerAlong(final String name, final int steps)
    {
        final Player viewer = onlineHere(name);
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();
        final Location at = new Location(world, cell.x() + (steps * facing.getModX()) + 0.5,
            cell.y() + (steps * facing.getModY()), cell.z() + (steps * facing.getModZ()) + 0.5);
        when(viewer.getLocation()).thenReturn(at);
        when(viewer.getEyeLocation()).thenReturn(at);
        GatePreviews.share(owner, viewer);
        return viewer;
    }

    /**
     * The same, out to one side of the gate as well as along its facing.
     *
     * @param name
     *            the viewer's name
     * @param along
     *            blocks along the facing, negative for behind
     * @param aside
     *            blocks across it, along whichever horizontal axis the facing does not use
     * @return the viewer, already shared the preview
     */
    private Player viewerAside(final String name, final int along, final int aside)
    {
        final Player viewer = onlineHere(name);
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();
        final Location at = new Location(world,
            cell.x() + (along * facing.getModX()) + (facing.getModX() == 0 ? aside : 0) + 0.5,
            cell.y() + (along * facing.getModY()),
            cell.z() + (along * facing.getModZ()) + (facing.getModZ() == 0 ? aside : 0) + 0.5);
        when(viewer.getLocation()).thenReturn(at);
        when(viewer.getEyeLocation()).thenReturn(at);
        GatePreviews.share(owner, viewer);
        return viewer;
    }

    /**
     * The display spawned at a cell, offset along the facing.
     *
     * @return the display, or null if nothing was spawned there
     */
    private BlockDisplay displayAt(final Cell cell, final int steps)
    {
        final BlockFace facing = previewFacing();
        final int x = cell.x() + (steps * facing.getModX());
        final int y = cell.y() + (steps * facing.getModY());
        final int z = cell.z() + (steps * facing.getModZ());
        for (int i = 0; i < creation.places.size(); i++)
        {
            final Location at = creation.places.get(i);
            if ((at.getBlockX() == x) && (at.getBlockY() == y) && (at.getBlockZ() == z)
                && (creation.created.get(i) instanceof BlockDisplay display))
            {
                return display;
            }
        }
        return null;
    }

    /**
     * How many of a viewer's hand-backs -- block changes to what really stands there, which in
     * this fixture is air -- landed on the cells a given number of steps along the facing.
     */
    private long handBacksAlong(final Player viewer, final int steps)
    {
        final BlockFace facing = previewFacing();
        final Set<List<Integer>> wanted = new HashSet<>();
        for (final Cell cell : openingCells())
        {
            wanted.add(List.of(cell.x() + (steps * facing.getModX()), cell.y() + (steps * facing.getModY()),
                cell.z() + (steps * facing.getModZ())));
        }
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(viewer, atLeastOnce()).sendBlockChange(where.capture(), eq(data.get(Material.AIR)));
        return where.getAllValues().stream()
            .filter(at -> wanted.contains(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()))).count();
    }

    /** Whether a display was ever spawned at a cell, offset along the facing. */
    private boolean spawnedAt(final Cell cell, final int steps)
    {
        final BlockFace facing = previewFacing();
        final int x = cell.x() + (steps * facing.getModX());
        final int y = cell.y() + (steps * facing.getModY());
        final int z = cell.z() + (steps * facing.getModZ());
        return creation.places.stream().anyMatch(
            at -> (at.getBlockX() == x) && (at.getBlockY() == y) && (at.getBlockZ() == z));
    }

    /**
     * How many of the wormhole's block changes to a viewer landed on the opening's own cells,
     * or on the cells a given number of steps along the facing from them.
     *
     * <p>Counted over the whole ring rather than one cell of it: a cell whose far side is not
     * free keeps the iris in the ring and shows no wormhole at all, which is the intended
     * fallback and not something an assertion about one cell should trip over.
     */
    private long wormholeSendsAlong(final Player viewer, final int steps)
    {
        final BlockFace facing = previewFacing();
        final Set<List<Integer>> wanted = new HashSet<>();
        for (final Cell cell : openingCells())
        {
            wanted.add(List.of(cell.x() + (steps * facing.getModX()), cell.y() + (steps * facing.getModY()),
                cell.z() + (steps * facing.getModZ())));
        }
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(viewer, atLeastOnce()).sendBlockChange(where.capture(), eq(data.get(Material.WATER)));
        return where.getAllValues().stream()
            .filter(at -> wanted.contains(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()))).count();
    }

    /**
     * The last thing a viewer was sent in a cell offset from the ring, which is what they are
     * still looking at.
     *
     * <p>A count, or an at-least-once, says only that the right block passed through at some
     * point. A sweep sends one picture and the settled draw after it sends another, so a test
     * that asks whether a block was ever sent is answered by the sweep and says nothing about
     * what is left standing when it ends.
     *
     * @param viewer
     *            the viewer
     * @param steps
     *            how far along the preview's facing, so -1 is a block behind the ring
     * @return the block data last sent there, or null if nothing was
     */
    private BlockData lastSentAlong(final Player viewer, final int steps)
    {
        final BlockFace facing = previewFacing();
        final Set<List<Integer>> wanted = new HashSet<>();
        for (final Cell cell : openingCells())
        {
            wanted.add(List.of(cell.x() + (steps * facing.getModX()), cell.y() + (steps * facing.getModY()),
                cell.z() + (steps * facing.getModZ())));
        }
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(viewer, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        BlockData last = null;
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            if (wanted.contains(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ())))
            {
                last = what.getAllValues().get(i);
            }
        }
        return last;
    }

    /**
     * A viewer behind a shut iris sees the wormhole in the ring, with the iris beyond it.
     *
     * <p>The real gate has read this way since #424; the preview showed everyone the front's
     * picture, which from behind is the wormhole a block the wrong side of the iris. A preview
     * that rehearses the gate has to rehearse this too.
     */
    @Test
    void aViewerBehindSeesTheWormholeInTheRingAndTheIrisBeyondIt()
    {
        openThePreview();
        final Player behind = viewerAlong("Bea", -4);
        clearInvocations(behind);

        GatePreviews.iris(owner);
        finishIrisSweep();

        assertTrue(spawnedAt(openingCells().get(0), 1),
            "an iris display stands a block along the facing, which is what a viewer behind sees");
        assertTrue(wormholeSendsAlong(behind, 0) > 0, "and the wormhole is in the ring for them");
        assertEquals(0, wormholeSendsAlong(behind, -1),
            "not a block behind it, which from where they stand is in front of the iris");
    }

    /**
     * A viewer behind is shown the iris beyond the ring, and not the one in it.
     *
     * <p>Both sets stand while a preview is stacked, so which one a viewer sees is the whole of
     * what makes the two sides different. Shown both, somebody behind the gate would see two
     * irises; shown the ring's alone, they would see the front's picture.
     */
    @Test
    void aViewerBehindIsShownTheIrisBeyondTheRingAndNotTheOneInIt()
    {
        openThePreview();
        final Player behind = viewerAlong("Bea", -4);

        GatePreviews.iris(owner);
        finishIrisSweep();

        final Cell cell = openingCells().get(0);
        verify(behind, atLeastOnce()).showEntity(plugin, displayAt(cell, 1));
        verify(behind, atLeastOnce()).hideEntity(plugin, displayAt(cell, 0));
    }

    /**
     * The owner, in front, keeps the iris in the ring and the wormhole behind it.
     */
    @Test
    void aViewerInFrontKeepsTheIrisInTheRing()
    {
        openThePreview();
        clearInvocations(owner);

        GatePreviews.iris(owner);
        finishIrisSweep();

        assertTrue(wormholeSendsAlong(owner, -1) > 0, "the wormhole is a block behind the ring for them");
    }

    /**
     * Walking round a stacked preview restacks it for that viewer.
     *
     * <p>A preview redraws itself every hundred ticks, which cannot follow somebody walking
     * round one: without the move hook they would keep the other side's picture for up to five
     * seconds, or until something else redrew.
     */
    @Test
    void walkingRoundAStackedPreviewRestacksItForThatViewer()
    {
        openThePreview();
        final Player walker = viewerAlong("Wes", -4);
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(walker);

        // Round to the front, where the wormhole belongs a block behind the ring instead.
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();
        final Location front = new Location(world, cell.x() + (4 * facing.getModX()) + 0.5,
            cell.y() + (4 * facing.getModY()), cell.z() + (4 * facing.getModZ()) + 0.5);
        // Where the step ends, which is what the move event carries: a player is still reported
        // at the step they are leaving while it is being handled, so their own location would
        // say they are still behind the gate.
        GatePreviews.moved(walker, front);

        assertTrue(wormholeSendsAlong(walker, -1) > 0,
            "from the front the wormhole belongs a block behind the ring, and the step is what says so");
    }

    /**
     * Re-opening the iris takes back the wormhole a stacked preview drew off the ring.
     *
     * <p>Everything that hands a preview's blocks back works from the opening's own cells, and a
     * stacked wormhole is not in them: it is a block behind the ring for a viewer in front. The
     * sent set recorded the ring cell rather than the one actually written, so opening the iris
     * again left a water block hanging a block behind the gate until the chunk reloaded.
     */
    @Test
    void openingTheIrisAgainTakesBackTheWormholeDrawnOffTheRing()
    {
        openThePreview();
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(owner);

        GatePreviews.iris(owner);
        finishIrisSweep();

        assertTrue(handBacksAlong(owner, -1) > 0,
            "the cell the stacked wormhole was drawn in is handed back, not just the ring");
    }

    /**
     * Clearing a stacked preview takes back the wormhole it drew off the ring.
     */
    @Test
    void clearingAStackedPreviewTakesBackTheWormholeOffTheRing()
    {
        openThePreview();
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(owner);

        assertEquals(1, GatePreviews.clearAll(owner));

        assertTrue(handBacksAlong(owner, -1) > 0, "the block behind the ring goes back with the rest");
    }

    /**
     * Somebody shared a stacked preview gets their own side of it, not both irises.
     *
     * <p>Sharing shows a new viewer every display standing and catches them up on the blocks
     * sent, neither of which knows about sides: they were handed both iris sets -- one of them
     * a stray block right in front of them -- and the wormhole in the ring, which is the wrong
     * cell for anybody in front.
     */
    @Test
    void sharingAStackedPreviewGivesTheNewViewerTheirOwnSide()
    {
        openThePreview();
        GatePreviews.iris(owner);
        finishIrisSweep();

        // In front, where the owner is: the iris beyond the ring is not theirs to see.
        final Player late = onlineHere("Lee");
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();
        final Location front = new Location(world, cell.x() + (4 * facing.getModX()) + 0.5,
            cell.y() + (4 * facing.getModY()), cell.z() + (4 * facing.getModZ()) + 0.5);
        when(late.getLocation()).thenReturn(front);
        when(late.getEyeLocation()).thenReturn(front);
        GatePreviews.share(owner, late);

        verify(late, atLeastOnce()).hideEntity(plugin, displayAt(cell, 1));
        assertTrue(wormholeSendsAlong(late, -1) > 0, "and their wormhole is behind the ring, where they stand to see it");
    }

    /**
     * Somebody who is not shown a preview is not drawn one by walking past it.
     *
     * <p>The restack ran for any player in the world. A passer-by was shown one of the two iris
     * sets -- entities hidden from everybody until somebody is told to see them -- and sent the
     * wormhole, and nothing would ever take either back: every teardown path reaches only the
     * people watching. Somebody else's unshared build site would appear as a floating iris.
     */
    @Test
    void walkingPastAPreviewNobodyHasSharedShowsItToNobody()
    {
        openThePreview();
        GatePreviews.iris(owner);
        finishIrisSweep();
        final Player passerBy = onlineHere("Pat");
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();

        GatePreviews.moved(passerBy, new Location(world, cell.x() + (4 * facing.getModX()) + 0.5,
            cell.y() + (4 * facing.getModY()), cell.z() + (4 * facing.getModZ()) + 0.5));

        verify(passerBy, never()).sendBlockChange(any(Location.class), any(BlockData.class));
        verify(passerBy, never()).showEntity(eq(plugin), any(BlockDisplay.class));
    }

    /**
     * From round the side of a stacked preview, the iris keeps the ring.
     *
     * <p>A preview is a sheet of displays with nothing either side to hide a second one behind.
     * Seen from the side, the iris beyond the ring was a slab standing a block clear of the
     * gate with daylight around it, and the wormhole a block the other way was another -- the
     * two layers read as two slabs rather than one gate. The preview collapses to a single
     * layer from there, as the real gate does.
     *
     * @see com.wormhole_xtreme.wormhole.model.IrisLayeringTest
     */
    @Test
    void fromOffToTheSideOfAStackedPreviewTheWormholeKeepsTheRing()
    {
        openThePreview();
        final Player side = viewerAside("Sid", -1, 12);
        clearInvocations(side);

        GatePreviews.iris(owner);
        finishIrisSweep();

        final Cell cell = openingCells().get(0);
        // Neither iris is theirs: the one beyond the ring would stand clear of the gate, and
        // the one in the ring is where the wormhole goes for somebody behind it.
        verify(side, atLeastOnce()).hideEntity(plugin, displayAt(cell, 1));
        verify(side, atLeastOnce()).hideEntity(plugin, displayAt(cell, 0));
        assertTrue(wormholeSendsAlong(side, 0) > 0, "and the wormhole is in the ring for them");
    }

    /**
     * A stacked draw says in the log what it decided, once the log is asking for it.
     *
     * <p>A preview takes a different path from a built gate -- its iris is a display entity and
     * its wormhole a block change -- so the gate's own line never appears for one. Testing a
     * preview and reading the gate's log is how an evening went missing.
     */
    @Test
    void aStackedDrawSaysInTheLogWhereItPutTheLayers()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.TRUE);
        openThePreview();

        GatePreviews.iris(owner);
        finishIrisSweep();

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(plugin, atLeastOnce()).prettyLog(eq(Level.FINE), said.capture());
        final String line = said.getAllValues().stream()
            .filter(s -> s.startsWith("Preview layers:")).findFirst().orElse(null);
        assertNotNull(line, "a stacked preview logs its own line: " + said.getAllValues());
        assertTrue(line.contains("Cells=" + openingCells().size()), line);
        assertTrue(line.contains("WithHorizon="), line);
        assertTrue(line.contains("FirstHorizon="), line);
    }

    /**
     * A see-through iris sweeping shut moves the wormhole behind as it covers, not at the end.
     *
     * <p>A preview's iris is a display entity standing in the same cell as the wormhole rather
     * than a block replacing it, so an opaque one hides the water and needs nothing. A
     * see-through one does not hide it -- the game declines to draw a liquid behind a
     * translucent block -- so the cell read as empty and the gate appeared to erase its own
     * wormhole a ring at a time, then produce it again when the sweep ended and the layers
     * were finally stacked.
     *
     * <p>Asserted with the sweep still running, which is the whole point: at the end it always
     * looked right.
     */
    @Test
    void aGlassIrisSweepMovesTheWormholeBehindAsItCovers()
    {
        openThePreview();
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        final Player front = viewerAlong("Fran", 4);
        clearInvocations(front);

        GatePreviews.iris(owner);

        assertFalse(irisPending.isEmpty(),
            "the sweep is still running -- without this the assertion below holds at the end anyway");
        verify(front, atLeastOnce()).sendBlockChange(any(Location.class),
            argThat(d -> (d == data.get(Material.BLUE_ICE)) || (d == data.get(Material.PACKED_ICE))));
    }

    /**
     * And the wormhole it moves behind is laid in the preview's plane, like everything else.
     *
     * <p>The horizon is built down a third path again, beside the wormhole and the iris, so it
     * could have kept the game's default on its own. The two ices are only the carrier here --
     * neither really has an axis -- but they are what this fixture's horizon draws, and a palette
     * is free to name a portal material that does.
     */
    @Test
    void theWormholeMovedBehindAGlassIrisIsLaidInThePreviewsPlane()
    {
        final org.bukkit.block.data.Orientable ice = mock(org.bukkit.block.data.Orientable.class);
        when(ice.getAxes()).thenReturn(java.util.EnumSet.of(org.bukkit.Axis.X, org.bukkit.Axis.Z));
        data.put(Material.BLUE_ICE, ice);
        data.put(Material.PACKED_ICE, ice);
        openThePreview();
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        viewerAlong("Fran", 4);

        GatePreviews.iris(owner);

        verify(ice, atLeastOnce()).setAxis(org.bukkit.Axis.X);
        verify(ice, never()).setAxis(org.bukkit.Axis.Z);
    }

    /**
     * A see-through iris sweeping open gives the wormhole back a ring at a time, not all at once.
     *
     * <p>The mirror of the closing sweep, and it went wrong the same way. An opening iris is
     * unstacked from its very first ring, and unstacking handed every cell's far layer back
     * together -- so the ice vanished from the whole opening while most of it was still covered
     * by glass, and the gate opened onto nothing until each ring's water caught up.
     *
     * <p>Asserted mid-sweep, and against the count: some cells have their wormhole back and
     * some do not, which is the whole claim. All of them at once is the bug.
     */
    @Test
    void aGlassIrisSweepGivesTheWormholeBackARingAtATime()
    {
        openThePreview();
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        final Player front = viewerAlong("Fran", 4);
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(front);

        GatePreviews.iris(owner);

        assertFalse(irisPending.isEmpty(),
            "the opening sweep is still running -- at the end everything is handed back anyway");
        final long given = handBacksAlong(front, -1);
        assertTrue(given > 0, "some of the far layer has come back already");
        assertTrue(given < openingCells().size(),
            "but not all of it: " + given + " of " + openingCells().size()
                + " handed back while most of the opening is still covered");
    }

    /**
     * An opaque iris sweeping shut leaves the wormhole in the ring, where its display covers it.
     *
     * <p>Nothing to move: the display stands in the cell the water is in and hides it, which is
     * what a preview has always done. Moving it would be work for a picture nobody can tell
     * apart, and the sweep runs on every cell of every ring.
     */
    @Test
    void anOpaqueIrisSweepLeavesTheWormholeInTheRing()
    {
        openThePreview();
        final Player front = viewerAlong("Fran", 4);
        clearInvocations(front);

        GatePreviews.iris(owner);

        assertFalse(irisPending.isEmpty(), "the sweep is still running");
        assertEquals(0, wormholeSendsAlong(front, -1),
            "no wormhole is moved off the ring while an opaque iris sweeps");
    }

    /**
     * A preview behind a see-through iris draws the wormhole as a look-alike, as a gate does.
     *
     * <p>This was exempted at first, on the grounds that a preview's iris is a display entity
     * and an entity takes no part in block face culling. True, and not the whole rule: a
     * translucent entity hides translucent water behind it just the same, and the preview went
     * on showing nothing long after the gate had been fixed. The decision is shared now, so
     * the two cannot drift apart again.
     *
     * <p>Asserted on what is left standing rather than on what went past. An at-least-once here
     * was answered by the ice the sweep sends on its way across, so the settled draw that follows
     * could have gone back to sending water -- invisible behind the glass -- and this still
     * passed.
     */
    @Test
    void aPreviewBehindAGlassIrisDrawsTheWormholeAsALookAlike()
    {
        openThePreview();
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        final Player front = viewerAlong("Fran", 4);
        clearInvocations(front);

        GatePreviews.iris(owner);
        finishIrisSweep();

        final BlockData settled = lastSentAlong(front, -1);
        assertTrue((settled == data.get(Material.BLUE_ICE)) || (settled == data.get(Material.PACKED_ICE)),
            "the cell behind the ring is still showing a look-alike once the sweep has ended: " + settled);
    }

    /**
     * A cell with nothing beyond the ring keeps the wormhole in the ring, for a viewer behind.
     *
     * <p>Without room for two layers there is no stacking to do at that cell. What belongs in
     * the plane is drawn first and the other layer follows when there is somewhere for it, so
     * from behind that is the wormhole, with no iris over it.
     *
     * <p>This inverts what the test here used to assert -- that the iris kept the ring, so a
     * shut gate could never read as an open one. Walking along the back of a gate swapping the
     * wormhole out for bare iris and back again looked more broken than it looked safe, and
     * the barrier itself never moved: a preview has nothing to walk through at all, and a real
     * gate refuses on its state rather than its picture.
     */
    @Test
    void aCellWithNoRoomBeyondKeepsTheWormholeInTheRingFromBehind()
    {
        openThePreview();
        final Cell cell = openingCells().get(0);
        final BlockFace facing = previewFacing();
        // Something built where that cell's iris would go for a viewer behind.
        standing.put(List.of(cell.x() + facing.getModX(), cell.y() + facing.getModY(),
            cell.z() + facing.getModZ()), Material.STONE);
        final Player behind = viewerAlong("Bea", -4);

        GatePreviews.iris(owner);
        finishIrisSweep();

        assertFalse(spawnedAt(cell, 1), "nothing to stand in beyond the ring at that cell");
        verify(behind, atLeastOnce()).hideEntity(plugin, displayAt(cell, 0));
        assertTrue(wormholeSendsAlong(behind, 0) > 0, "and the wormhole is in the ring for them");
    }

    /**
     * Stopping sharing a stacked preview takes back the wormhole drawn off the ring.
     *
     * <p>What is handed back on unsharing is the cells the preview sent, which are the opening's
     * own. A viewer drawn from the front had theirs a block behind the ring, so it stayed on
     * their screen after the preview was gone from it.
     */
    @Test
    void unsharingAStackedPreviewTakesBackTheWormholeOffTheRing()
    {
        openThePreview();
        final Player lee = viewerAlong("Lee", 4);
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(lee);

        // Sharing again is how sharing is stopped.
        GatePreviews.share(owner, lee);

        assertTrue(handBacksAlong(lee, -1) > 0, "the cell their wormhole stood in goes back with the rest");
    }

    /**
     * Restyling a stacked preview dresses the iris beyond the ring too.
     *
     * <p>Both sets are the same iris, and only one of them was restyled: a viewer behind the
     * gate went on looking at the block the preview wore before.
     */
    @Test
    void restylingAStackedPreviewDressesTheIrisBeyondTheRing()
    {
        openThePreview();
        viewerAlong("Bea", -4);
        GatePreviews.iris(owner);
        finishIrisSweep();
        final BlockDisplay beyond = displayAt(openingCells().get(0), 1);
        clearInvocations(beyond);

        assertEquals(GatePreviews.Control.CHANGED,
            GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.IRON_BLOCK));

        verify(beyond, atLeastOnce()).setBlock(data.get(Material.IRON_BLOCK));
    }

    /**
     * A preview is not stacked while its iris is sweeping across.
     *
     * <p>The sweep covers the wormhole ring by ring, so the wormhole has to stay in the ring for
     * it to be seen covering anything. Moving it behind the gate at the first ring empties the
     * opening for the length of the sweep, which is what the report above looked like.
     */
    @Test
    void aPreviewIsNotStackedUntilItsSweepHasCrossed()
    {
        openThePreview();
        viewerAlong("Sam", -4);

        GatePreviews.iris(owner);

        assertFalse(spawnedAt(openingCells().get(0), 1), "no iris beyond the ring while the sweep is crossing");

        finishIrisSweep();

        assertTrue(spawnedAt(openingCells().get(0), 1), "and it stands once the sweep is over");
    }

    /**
     * Dialling behind a closed iris sends no kawoosh.
     *
     * <p>Every woosh step lands on or past the iris, so a real gate draws none of them with it
     * shut (#404). The preview drew all three out and back through its own closed iris.
     *
     * <p>The wormhole is still sent -- opening behind a shut iris is not the same as not opening
     * -- but since the preview stacks its layers it goes a block behind the ring for the owner,
     * who is in front, and the ring is handed back. Counting the water sends is what says no
     * woosh wave was drawn: exactly the opening's worth, all of them in the same plane.
     */
    @Test
    void dialingBehindAClosedIrisSendsNoKawoosh()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.iris(owner);
        finishIrisSweep();
        clearInvocations(owner);

        GatePreviews.activate(owner);
        // Seven chevrons, then one woosh stage: behind a shut iris the woosh ends there and books
        // nothing after it.
        for (int step = 0; step < 7; step++)
        {
            dialStep.run();
        }
        final int booked = dialDelays.size();
        dialStep.run();

        verify(owner, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
        assertEquals(21, wormholeSendsAlong(owner, -1),
            "all of them a block behind the ring, where a shut iris puts the wormhole for somebody in front,"
                + " and not one of them a woosh wave");
        assertEquals(booked, dialDelays.size(), "and nothing more is booked after it");
    }

    /**
     * Closing the iris partway through the kawoosh takes back what of it is already out.
     *
     * <p>The steps still to come are skipped behind the closed iris, but the first had already
     * been sent, and it stood in front of the iris until the woosh drew back a second later. It
     * goes on the next woosh step now, as it does on a real gate: the two play one sequence.
     */
    @Test
    void closingTheIrisMidKawooshTakesBackWhatIsOut()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 8; step++)
        {
            dialStep.run();
        }
        verify(owner, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        GatePreviews.iris(owner);
        dialStep.run();

        verify(owner, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        verify(owner, times(21 + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
    }

    /**
     * An iris closed while the chevrons are still locking hides the kawoosh that follows.
     *
     * <p>What decides is the iris at the moment the woosh would play, not when dialling began.
     */
    @Test
    void anIrisClosedDuringDiallingHidesTheKawoosh()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 3; step++)
        {
            dialStep.run();
        }
        GatePreviews.iris(owner);
        finishIrisSweep();
        for (int step = 3; step < 8; step++)
        {
            dialStep.run();
        }

        verify(owner, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
    }

    /**
     * An iris opened while the chevrons are still locking lets the kawoosh through.
     *
     * <p>The other way round from the test above: closed when dialling began, open by the time
     * the woosh plays, so the woosh is drawn.
     */
    @Test
    void anIrisOpenedDuringDiallingLetsTheKawooshThrough()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.iris(owner);
        finishIrisSweep();
        GatePreviews.activate(owner);
        for (int step = 0; step < 3; step++)
        {
            dialStep.run();
        }
        GatePreviews.iris(owner);
        finishIrisSweep();
        for (int step = 3; step < 13; step++)
        {
            dialStep.run();
        }

        verify(owner, atLeast((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
    }

    /**
     * The horizon is still being sent while the iris sweeps over it.
     *
     * <p>The assertion the one above cannot make by counting: that the water is there *during*
     * the sweep, which is the whole of what was reported. Taken at the moment the first ring
     * lands, before the sweep has finished and before anything else has had a chance to redraw.
     */
    @Test
    void theWormholeIsStillDrawnWhileTheIrisSweepsAcrossIt()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        clearInvocations(owner);

        GatePreviews.iris(owner);

        verify(owner, atLeastOnce()).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
        verify(owner, never()).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }

    /** Obsidian frames are one a gate can be found by, as a server's Standard group makes them. */
    private static void obsidianFramesAreFindable()
    {
        com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry.load(Map.of("Standard", Map.of("structure", "OBSIDIAN")));
    }

    private final List<Object[]> detected = new ArrayList<>();

    private com.wormhole_xtreme.wormhole.model.Stargate detectsAGate()
    {
        final com.wormhole_xtreme.wormhole.model.Stargate gate = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        GatePreviews.detector = (button, facing, shape) ->
        {
            detected.add(new Object[] { button, facing, shape });
            return gate;
        };
        return gate;
    }

    /**
     * -place builds the frame and then the button, turned to face the builder and hung on the wall,
     * leaves the opening empty, and finds the gate from that button facing that way.
     */
    @Test
    void placingBuildsTheFrameThenTheButtonAndFindsTheGateFromIt()
    {
        obsidianFramesAreFindable();
        final com.wormhole_xtreme.wormhole.model.Stargate gate = detectsAGate();
        final List<Cell> cells = standardLookingNorth();
        GatePreviews.show(owner, standard, null);

        final GatePreviews.Placed placed = GatePreviews.place(owner);

        assertEquals(GatePreviews.Outcome.PLACED, placed.outcome());
        assertEquals(cells.stream().map(c -> List.of(c.x(), c.y(), c.z())).toList(), written,
            "every block, frame first and the button last");
        for (final Cell cell : cells)
        {
            assertEquals((cell.part() == Part.BUTTON) ? Material.STONE_BUTTON : Material.OBSIDIAN,
                standing.get(List.of(cell.x(), cell.y(), cell.z())), cell.toString());
        }
        final org.bukkit.block.data.type.Switch button = (org.bukkit.block.data.type.Switch) data.get(Material.STONE_BUTTON);
        verify(button).setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        verify(button, Mockito.atLeastOnce()).setFacing(BlockFace.SOUTH);
        assertEquals(1, detected.size());
        final org.bukkit.block.Block pressed = (org.bukkit.block.Block) detected.get(0)[0];
        assertEquals(placed.button(), pressed);
        assertEquals(BlockFace.SOUTH, detected.get(0)[1]);
        assertEquals(standard, detected.get(0)[2]);
        assertEquals(gate, placed.gate());
        verify(pressed, Mockito.atLeastOnce()).setBlockData(data.get(Material.STONE_BUTTON), false);
    }

    /** A block already right is left standing, so a half-built gate is finished rather than rebuilt. */
    @Test
    void placingKeepsBlocksAlreadyRight()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final List<Cell> cells = standardLookingNorth();
        place(cells.get(0), Material.OBSIDIAN);
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Outcome.PLACED, GatePreviews.place(owner).outcome());

        final List<Integer> kept = List.of(cells.get(0).x(), cells.get(0).y(), cells.get(0).z());
        assertFalse(written.contains(kept));
        assertEquals(cells.size() - 1, written.size());
    }

    /**
     * A button or lever already on the wall facing the builder stays; one facing away or on the floor is
     * replaced by the wall button a placed gate has.
     */
    @Test
    void placingKeepsOnlyASwitchHungTheWayAButtonWouldHang()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final Cell buttonCell = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();
        final List<Integer> at = List.of(buttonCell.x(), buttonCell.y(), buttonCell.z());
        final org.bukkit.block.data.type.Switch hung = (org.bukkit.block.data.type.Switch) buttonData();
        when(hung.getAttachedFace()).thenReturn(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        when(hung.getFacing()).thenReturn(BlockFace.SOUTH);
        data.put(Material.OAK_BUTTON, hung);
        place(buttonCell, Material.OAK_BUTTON);
        GatePreviews.show(owner, standard, null);

        GatePreviews.place(owner);
        assertFalse(written.contains(at), "a wall button facing the builder stays");

        written.clear();
        when(hung.getFacing()).thenReturn(BlockFace.NORTH);
        GatePreviews.clearAll(owner);
        place(buttonCell, Material.OAK_BUTTON);
        GatePreviews.show(owner, standard, null);
        GatePreviews.place(owner);
        assertTrue(written.contains(at), "one facing away is replaced");

        written.clear();
        final org.bukkit.block.data.type.Switch lever = (org.bukkit.block.data.type.Switch) GatePreviews.blockData
            .apply(Material.LEVER);
        when(lever.getAttachedFace()).thenReturn(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
        when(lever.getFacing()).thenReturn(BlockFace.SOUTH);
        GatePreviews.clearAll(owner);
        place(buttonCell, Material.LEVER);
        GatePreviews.show(owner, standard, null);
        GatePreviews.place(owner);
        assertFalse(written.contains(at), "a lever on the wall facing the builder stays too");

        when(lever.getAttachedFace()).thenReturn(org.bukkit.block.data.FaceAttachable.AttachedFace.FLOOR);
        GatePreviews.clearAll(owner);
        place(buttonCell, Material.LEVER);
        GatePreviews.show(owner, standard, null);
        GatePreviews.place(owner);
        assertTrue(written.contains(at), "one on the floor is replaced");
        assertEquals(Material.STONE_BUTTON, standing.get(at));
    }

    /** A sign-dial shape's sign is left for the builder to write the gate's name on. */
    @Test
    void placingLeavesTheDialSignToTheBuilder() throws Exception
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final Stargate3DShape signDial = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/StandardSignDial.shape")).toArray(new String[0]));
        final Cell sign = GateBlueprint.of(signDial, GateBlueprint.inFrontOf(signDial, 0, 64, 0, BlockFace.NORTH))
            .stream().filter(c -> c.part() == Part.DIAL_SIGN).findFirst().orElseThrow();
        GatePreviews.show(owner, signDial, null);

        assertEquals(GatePreviews.Outcome.PLACED, GatePreviews.place(owner).outcome());

        assertFalse(written.isEmpty());
        assertFalse(written.contains(List.of(sign.x(), sign.y(), sign.z())));
    }

    /**
     * Anything in the way, a wrong block in the frame or anything in the opening, refuses the whole
     * gate before a block is placed, naming five and counting the rest.
     */
    @Test
    void anythingInTheWayRefusesItAllAndNamesWhat()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final List<Cell> cells = standardLookingNorth();
        final List<Cell> opening = GateBlueprint.openingOf(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0,
            BlockFace.NORTH));
        GatePreviews.show(owner, standard, null);
        place(cells.get(0), Material.STONE);
        for (int i = 0; i < 6; i++)
        {
            place(opening.get(i), Material.DIRT);
        }

        final GatePreviews.Placed placed = GatePreviews.place(owner);

        assertEquals(GatePreviews.Outcome.IN_THE_WAY, placed.outcome());
        assertEquals(6, placed.inTheWay().size());
        assertEquals("stone at " + cells.get(0).x() + " " + cells.get(0).y() + " " + cells.get(0).z(),
            placed.inTheWay().get(0));
        assertTrue(placed.inTheWay().get(1).startsWith("dirt at "));
        assertEquals("2 more", placed.inTheWay().get(5));
        assertTrue(written.isEmpty(), "nothing placed");
        assertTrue(detected.isEmpty());
    }

    /** A block a gate or ring already owns is in the way, even where it is air. */
    @Test
    void aBlockAGateOrRingOwnsIsInTheWay()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final Cell owned = GateBlueprint.openingOf(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0,
            BlockFace.NORTH)).get(2);
        GatePreviews.occupied = (w, x, y, z) -> (x == owned.x()) && (y == owned.y()) && (z == owned.z());
        GatePreviews.show(owner, standard, null);

        final GatePreviews.Placed placed = GatePreviews.place(owner);

        assertEquals(GatePreviews.Outcome.IN_THE_WAY, placed.outcome());
        assertEquals(List.of("a gate or ring at " + owned.x() + " " + owned.y() + " " + owned.z()), placed.inTheWay());
        assertTrue(written.isEmpty());
    }

    /**
     * Nothing is placed for a frame no group would find, a gate reaching into an unloaded chunk or past
     * the world border, or a preview nobody is looking at; a gate placed but not found says so.
     */
    @Test
    void placingIsRefusedBeforeAnythingIsWrittenWhenItCannotWork()
    {
        detectsAGate();
        obsidianFramesAreFindable();
        GatePreviews.show(owner, standard, null);
        GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.GOLD_BLOCK);
        assertEquals(GatePreviews.Outcome.NOT_FINDABLE, GatePreviews.place(owner).outcome());
        GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.OBSIDIAN);

        final org.bukkit.WorldBorder border = world.getWorldBorder();
        when(border.isInside(any(Location.class))).thenReturn(false);
        assertEquals(GatePreviews.Outcome.OUTSIDE_BORDER, GatePreviews.place(owner).outcome());
        when(border.isInside(any(Location.class))).thenReturn(true);

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertEquals(GatePreviews.Outcome.NOT_LOADED, GatePreviews.place(owner).outcome());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        standAt(0.5, 1.5, 0f);
        assertEquals(GatePreviews.Outcome.NOT_LOOKING, GatePreviews.place(owner).outcome());
        assertTrue(written.isEmpty());
        assertTrue(detected.isEmpty());

        standAt(0.5, 0.5, 180f);
        GatePreviews.detector = (button, facing, shape) -> null;
        assertEquals(GatePreviews.Outcome.NOT_FOUND, GatePreviews.place(owner).outcome());
        assertFalse(written.isEmpty(), "placed, then not found");
    }

    /** A player online in the preview's world, besides its owner. */
    private Player onlineHere(final String name)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.getWorld()).thenReturn(world);
        // Online, as somebody standing in the world is: the move hook asks before it redraws.
        when(player.isOnline()).thenReturn(true);
        others.put(player.getUniqueId(), player);
        return player;
    }

    /**
     * Sharing shows every display to that player, but not the button, which only the owner presses;
     * sharing again hides them all.
     */
    @Test
    void sharingShowsThePreviewToThatPlayerAndSharingAgainHidesIt()
    {
        final Player alex = onlineHere("Alex");
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Shared.SHARED, GatePreviews.share(owner, alex));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(alex).showEntity(plugin, display));
        verify(alex, never()).showEntity(plugin, buttons.get(0));
        assertEquals(new GatePreviews.Audience(false, List.of("Alex")), GatePreviews.audience(owner));

        assertEquals(GatePreviews.Shared.UNSHARED, GatePreviews.share(owner, alex));

        spawned.forEach(display -> verify(alex).hideEntity(plugin, display));
        assertEquals(new GatePreviews.Audience(false, List.of()), GatePreviews.audience(owner));
        assertEquals(GatePreviews.Shared.SELF, GatePreviews.share(owner, owner));
        standAt(0.5, 1.5, 0f);
        assertEquals(GatePreviews.Shared.NOT_LOOKING, GatePreviews.share(owner, alex));
        assertNull(GatePreviews.audience(owner));
    }

    /**
     * The preview's iris arrives a ring at a time, not all at once.
     *
     * <p>The whole reason the preview exists is to rehearse a gate before building it, and the
     * iris animation is one of the things worth rehearsing. It was instant here while a real
     * gate swept, so the preview was answering a question about a gate it did not match.
     */
    @Test
    void theIrisSweepsOntoThePreviewARingAtATime()
    {
        GatePreviews.show(owner, standard, null);

        GatePreviews.iris(owner);

        final int afterFirstRing = spawned.size() - STANDARD_BLOCKS;
        assertTrue(afterFirstRing > 0, "the first ring is drawn at once");
        assertTrue(afterFirstRing < STANDARD_OPENING,
            "but not the whole opening: " + afterFirstRing + " of " + STANDARD_OPENING);
        assertEquals(1, irisPending.size(), "and the rest is booked");

        finishIrisSweep();

        assertEquals(STANDARD_OPENING, spawned.size() - STANDARD_BLOCKS, "every cell by the end");
    }

    /**
     * Toggling the iris again mid-sweep calls the first sweep off.
     *
     * <p>Two sweeps running over one preview would add and remove the same cells in whatever
     * order their steps happened to fire, and whichever finished last would decide what the
     * preview showed -- which is not necessarily the state the iris is actually in. Somebody
     * flipping the iris back and forth to look at it is exactly the person who would find that.
     */
    @Test
    void togglingTheIrisAgainMidSweepCallsOffTheFirst()
    {
        GatePreviews.show(owner, standard, null);

        GatePreviews.iris(owner);
        assertEquals(1, irisPending.size(), "the closing sweep is part way through");

        // Straight back open, without letting the close finish.
        assertEquals(GatePreviews.Control.IRIS_OPENED, GatePreviews.iris(owner));

        assertEquals(1, irisPending.size(),
            "the closing sweep's step was dropped, not left waiting beside the opening one");
        finishIrisSweep();
        assertEquals(0, countStanding(), "and the opening sweep finished");
    }

    /** How many of the opening's displays are still standing rather than removed. */
    private long countStanding()
    {
        return spawned.subList(STANDARD_BLOCKS, spawned.size()).stream()
            .filter(display -> org.mockito.Mockito.mockingDetails(display).getInvocations().stream()
                .noneMatch(invocation -> "remove".equals(invocation.getMethod().getName())))
            .count();
    }

    /** A display drawn after sharing, here the closed iris, is shown to the viewer as well. */
    @Test
    void aDisplayDrawnLaterIsShownToViewersToo()
    {
        final Player alex = onlineHere("Alex");
        GatePreviews.show(owner, standard, null);
        GatePreviews.share(owner, alex);
        final int before = spawned.size();

        GatePreviews.iris(owner);
        finishIrisSweep();

        assertEquals(STANDARD_BLOCKS + STANDARD_OPENING, spawned.size());
        spawned.subList(before, spawned.size()).forEach(display -> verify(alex).showEntity(plugin, display));
    }

    /**
     * A viewer is sent the kawoosh and the wormhole as its owner is, is sent the open wormhole on
     * joining late, and has it taken back when the owner shuts it down or stops sharing.
     */
    @Test
    void viewersAreSentTheWormholeAndHaveItTakenBack()
    {
        final Player alex = onlineHere("Alex");
        final Player sam = onlineHere("Sam");
        GatePreviews.show(owner, standard, null);
        GatePreviews.share(owner, alex);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        verify(alex, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        GatePreviews.share(owner, sam);
        verify(sam, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        GatePreviews.share(owner, sam);
        verify(sam, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));

        GatePreviews.activate(owner);
        verify(alex, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        verify(sam, times(21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }

    /**
     * Shared with everyone, a preview is shown to whoever is in its world, including anyone arriving
     * later, and stops being counted for anyone who leaves; sharing again hides it from them.
     */
    @Test
    void sharedWithEveryoneItReachesWhoeverIsInTheWorld()
    {
        final Player alex = onlineHere("Alex");
        final Player sam = onlineHere("Sam");
        when(world.getPlayers()).thenReturn(List.of(owner, alex));
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Shared.SHARED_ALL, GatePreviews.shareAll(owner));
        spawned.forEach(display -> verify(alex).showEntity(plugin, display));
        verify(sam, never()).showEntity(any(), any());
        assertEquals(new GatePreviews.Audience(true, List.of()), GatePreviews.audience(owner));

        when(world.getPlayers()).thenReturn(List.of(owner, alex, sam));
        GatePreviews.tick();
        spawned.forEach(display -> verify(sam).showEntity(plugin, display));

        final World elsewhere = mock(World.class);
        when(sam.getWorld()).thenReturn(elsewhere);
        when(world.getPlayers()).thenReturn(List.of(owner, alex));
        GatePreviews.tick();
        when(sam.getWorld()).thenReturn(world);
        when(world.getPlayers()).thenReturn(List.of(owner, alex, sam));
        GatePreviews.tick();
        spawned.forEach(display -> verify(sam, times(2)).showEntity(plugin, display));

        assertEquals(GatePreviews.Shared.UNSHARED_ALL, GatePreviews.shareAll(owner));
        spawned.forEach(display -> verify(alex).hideEntity(plugin, display));
        spawned.forEach(display -> verify(sam).hideEntity(plugin, display));
        verify(owner, never()).hideEntity(any(), any());
        spawned.forEach(display -> verify(owner, times(1)).showEntity(plugin, display));
    }

    /** A player shared with while in another world is shown the preview once they arrive in its world. */
    @Test
    void aViewerInAnotherWorldIsShownItOnArriving()
    {
        final Player alex = onlineHere("Alex");
        final World elsewhere = mock(World.class);
        when(alex.getWorld()).thenReturn(elsewhere);
        GatePreviews.show(owner, standard, null);

        GatePreviews.share(owner, alex);
        verify(alex, never()).showEntity(any(), any());

        when(alex.getWorld()).thenReturn(world);
        GatePreviews.tick();

        spawned.forEach(display -> verify(alex).showEntity(plugin, display));
    }

    /** A viewer who relogs has forgotten what they were shown, so the next tick shows it again. */
    @Test
    void aViewerWhoRelogsIsShownItAgain()
    {
        final Player alex = onlineHere("Alex");
        GatePreviews.show(owner, standard, null);
        GatePreviews.share(owner, alex);

        GatePreviews.forget(alex.getUniqueId());
        GatePreviews.tick();

        spawned.forEach(display -> verify(alex, times(2)).showEntity(plugin, display));
    }

    /** An owner who leaves takes their open wormhole back from viewers as well. */
    @Test
    void anOwnerLeavingTakesTheWormholeBackFromViewers()
    {
        final Player alex = onlineHere("Alex");
        GatePreviews.show(owner, standard, null);
        GatePreviews.share(owner, alex);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }

        GatePreviews.forget(owner.getUniqueId());

        verify(alex, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }

    /**
     * With the ring turning (#357), a light travels round the frame before each chevron: it starts
     * half the ring from the chevron, lands on it at the end of the turn, and the chevron then locks,
     * the turn taking the chevron's own interval.
     */
    @Test
    void theRingsLightTravelsToTheChevronBeforeItLocks()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, "CHEVRON");
        final List<Cell> cells = standardLookingNorth();
        final com.wormhole_xtreme.wormhole.logic.DialSpin spin = com.wormhole_xtreme.wormhole.logic.DialSpin.of(cells,
            GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
        final Cell start = spin.path(1).get(0);
        GatePreviews.show(owner, standard, null);
        final BlockDisplay startDisplay = spawned.get(cells.indexOf(start));
        GatePreviews.activate(owner);

        dialStep.run();

        verify(startDisplay).setBlock(data.get(Material.GLOWSTONE));
        ringDisplaysOfWave(1).forEach(d -> verify(d, never()).setBlock(data.get(Material.GLOWSTONE)));

        final int ticks = standard.getShapeLightTicks();
        for (int step = 1; step < ticks - 1; step++)
        {
            dialStep.run();
        }
        // Each check sees only its own tick: the light's last, then the lock.
        final List<BlockDisplay> chevron = ringDisplaysOfWave(1);
        chevron.forEach(org.mockito.Mockito::clearInvocations);
        dialStep.run();
        assertTrue(chevron.stream().anyMatch(d -> mockingDetails(d).getInvocations()
            .stream().anyMatch(i -> i.getMethod().getName().equals("setBlock")
                && data.get(Material.GLOWSTONE).equals(i.getArgument(0)))), "the light's last tick lands on chevron 1");

        chevron.forEach(org.mockito.Mockito::clearInvocations);
        dialStep.run();

        chevron.forEach(d -> verify(d).setBlock(data.get(Material.GLOWSTONE)));
        assertTrue(dialDelays.subList(1, ticks + 1).stream().allMatch(d -> d == 1L), "the light moves a cell a tick");
    }

    /**
     * The default TOP turn rests on the top as a chevron locks: the top is still lit once the lock
     * is drawn, and the next chevron locks the rest later, as a real gate's does.
     */
    @Test
    void theTopTurnRestsOnTheTopAfterALock()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, "TOP");
        final List<Cell> cells = standardLookingNorth();
        final com.wormhole_xtreme.wormhole.logic.DialSpin spin = com.wormhole_xtreme.wormhole.logic.DialSpin.of(cells,
            GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
        final List<Cell> path = spin.path(com.wormhole_xtreme.wormhole.logic.DialSpinPattern.TOP, 1);
        GatePreviews.show(owner, standard, null);
        final BlockDisplay top = spawned.get(cells.indexOf(path.get(path.size() - 1)));
        final GatePreview preview = GatePreviews.of(owner.getUniqueId()).get(0);
        GatePreviews.activate(owner);
        final int ticks = standard.getShapeLightTicks();
        for (int step = 0; step < ticks; step++)
        {
            dialStep.run();
        }
        org.mockito.Mockito.clearInvocations(top);

        dialStep.run();

        assertEquals(1, preview.litWaves(), "the first chevron locked");
        final List<BlockData> shown = mockingDetails(top).getInvocations().stream()
            .filter(i -> i.getMethod().getName().equals("setBlock")).map(i -> i.<BlockData>getArgument(0)).toList();
        assertEquals(data.get(Material.GLOWSTONE), shown.get(shown.size() - 1), "the top still lit as it locks");
        for (int step = 0; step < (com.wormhole_xtreme.wormhole.logic.DialSpin.TOP_HOLD_TICKS + ticks); step++)
        {
            dialStep.run();
            assertEquals(1, preview.litWaves(), "resting, then turning, at step " + step);
        }
        dialStep.run();
        assertEquals(2, preview.litWaves());
    }

    /**
     * Under UNIVERSE a locked chevron rides round with the ring rather than also lighting in its own
     * place, as a real gate's does; lit in both, half the gate stood lit.
     */
    @Test
    void aUniverseChevronDoesNotLightInPlace()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, "UNIVERSE");
        GatePreviews.show(owner, standard, null);
        final GatePreview preview = GatePreviews.of(owner.getUniqueId()).get(0);
        GatePreviews.activate(owner);
        final List<BlockDisplay> chevron = ringDisplaysOfWave(1);
        // UNIVERSE's turn outlasts the chevron's interval, so step to the lock itself, not by the interval.
        for (int step = 0; (step < 200) && (preview.litWaves() < 1); step++)
        {
            dialStep.run();
        }
        assertEquals(1, preview.litWaves(), "chevron 1 locked");

        for (final BlockDisplay d : chevron)
        {
            final List<BlockData> shown = mockingDetails(d).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("setBlock")).map(i -> i.<BlockData>getArgument(0)).toList();
            assertFalse(shown.isEmpty(), "chevron 1 was redrawn as it locked");
            assertNotEquals(data.get(Material.GLOWSTONE), shown.get(shown.size() - 1), "and not as lit");
        }
    }

    /**
     * Under UNIVERSE only the ring's front layer rides, so the last lock lights every chevron in
     * place: a Grand preview's back layer, which never rides, is lit once the dial is done.
     */
    @Test
    void aUniversePreviewLightsEveryLayerAtTheLastLock() throws Exception
    {
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, "UNIVERSE");
        final Stargate3DShape grand = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Grand.shape")).toArray(new String[0]));
        final List<Cell> cells = GateBlueprint.of(grand, GateBlueprint.inFrontOf(grand, 0, 64, 0, BlockFace.NORTH));
        GatePreviews.show(owner, grand, null);
        final GatePreview preview = GatePreviews.of(owner.getUniqueId()).get(0);
        assertNotNull(preview.spin(), "a Grand preview turns");
        GatePreviews.activate(owner);
        for (int step = 0; (step < 2000) && (preview.litWaves() < preview.lastWave()); step++)
        {
            dialStep.run();
        }
        assertEquals(preview.lastWave(), preview.litWaves(), "the dial finished");

        final List<Cell> behind = cells.stream()
            .filter(c -> (c.wave() > 0) && (c.wave() <= preview.lastWave()) && !preview.spin().ring().contains(c)).toList();
        assertFalse(behind.isEmpty(), "Grand's chevrons have a layer behind the ring");
        for (final Cell cell : behind)
        {
            final List<BlockData> shown = mockingDetails(spawned.get(cells.indexOf(cell))).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("setBlock")).map(i -> i.<BlockData>getArgument(0)).toList();
            assertEquals(data.get(Material.GLOWSTONE), shown.isEmpty() ? null : shown.get(shown.size() - 1),
                "chevron " + cell.wave() + " lit behind the ring");
        }
    }

    /**
     * Shutting a preview down at any point in its woosh, out or back, takes every woosh block back:
     * the owner's last sight of each block the preview sent is the real one. Found in-game on the
     * big gates, whose woosh is long enough to catch.
     */
    @Test
    void shuttingAPreviewMidWooshTakesTheWooshBack() throws Exception
    {
        final Stargate3DShape massive = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Massive.shape")).toArray(new String[0]));
        GatePreviews.show(owner, massive, null);
        final int stages = 2 * GatePreviews.of(owner.getUniqueId()).get(0).lastWoosh();
        assertTrue(stages > 6, "Massive has a long woosh");
        for (int stop = 1; stop <= stages; stop++)
        {
            final GatePreview preview = GatePreviews.of(owner.getUniqueId()).get(0);
            GatePreviews.activate(owner);
            for (int step = 0; (step < 2000) && (preview.wooshStage() < stop) && !preview.open(); step++)
            {
                dialStep.run();
            }
            GatePreviews.activate(owner);

            final java.util.Map<List<Integer>, Object> last = new java.util.HashMap<>();
            for (final org.mockito.invocation.Invocation i : mockingDetails(owner).getInvocations())
            {
                if (i.getMethod().getName().equals("sendBlockChange"))
                {
                    final Location at = i.getArgument(0);
                    last.put(List.of(at.getBlockX(), at.getBlockY(), at.getBlockZ()), i.getArgument(1));
                }
            }
            assertFalse(last.isEmpty(), "the woosh was sent");
            final List<List<Integer>> left = last.entrySet().stream()
                .filter(e -> !data.get(Material.AIR).equals(e.getValue())).map(java.util.Map.Entry::getKey).toList();
            assertEquals(List.of(), left, "woosh blocks still showing after a shut down at stage " + stop);
        }
    }

    /** Stands a Standard frame north of the owner with one frame block missing, belonging to the gate given. */
    private Cell standAGateShortOfOneBlock(final com.wormhole_xtreme.wormhole.model.Stargate gate)
    {
        final List<Cell> cells = standardLookingNorth();
        final Cell hole = cells.get(0);
        for (final Cell cell : cells)
        {
            if ((cell != hole) && (cell.part() != Part.BUTTON) && (cell.part() != Part.DIAL_SIGN))
            {
                place(cell, Material.OBSIDIAN);
            }
        }
        GatePreviews.occupied = (w, x, y, z) -> standing.containsKey(List.of(x, y, z));
        GatePreviews.gateAt = (w, x, y, z) -> standing.containsKey(List.of(x, y, z)) ? gate : null;
        return hole;
    }

    /**
     * A preview over one gate already there fills only what that gate is missing, and hands that gate
     * back to be regenerated, rather than finding a second gate in the same blocks.
     *
     * <p>Lithium, a {@code Massive} gate brought in by the importer, stood a frame block short, and a
     * preview could not be placed over it: every other block it needed was the gate's.
     */
    @Test
    void placingOverOneGateFillsWhatItIsMissingAndHandsThatGateBack()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final com.wormhole_xtreme.wormhole.model.Stargate existing = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        final Cell hole = standAGateShortOfOneBlock(existing);
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst().orElseThrow();
        GatePreviews.show(owner, standard, null);

        final GatePreviews.Placed placed = GatePreviews.place(owner, true);

        assertEquals(GatePreviews.Outcome.REPAIRED, placed.outcome());
        assertSame(existing, placed.gate());
        assertEquals(List.of(List.of(hole.x(), hole.y(), hole.z()), List.of(button.x(), button.y(), button.z())),
            written, "the missing frame block, then the button");
        assertEquals(Material.OBSIDIAN, standing.get(List.of(hole.x(), hole.y(), hole.z())));
        assertTrue(detected.isEmpty(), "no second gate is looked for");
    }

    /** Without leave to change a gate that is there, its blocks are in the way as before. */
    @Test
    void placingOverAGateWithoutLeaveIsRefused()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        standAGateShortOfOneBlock(mock(com.wormhole_xtreme.wormhole.model.Stargate.class));
        GatePreviews.show(owner, standard, null);

        final GatePreviews.Placed placed = GatePreviews.place(owner, false);

        assertEquals(GatePreviews.Outcome.IN_THE_WAY, placed.outcome());
        assertTrue(placed.inTheWay().get(0).startsWith("a gate or ring at "), placed.inTheWay().toString());
        assertTrue(written.isEmpty());
    }

    /** Over two gates, or a gate and a ring, nothing is placed: there is no one gate to fill in. */
    @Test
    void placingOverTwoGatesOrAGateAndARingIsRefused()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final com.wormhole_xtreme.wormhole.model.Stargate one = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        final com.wormhole_xtreme.wormhole.model.Stargate two = mock(com.wormhole_xtreme.wormhole.model.Stargate.class);
        final Cell hole = standAGateShortOfOneBlock(one);
        final Cell other = standardLookingNorth().get(1);
        GatePreviews.gateAt = (w, x, y, z) -> !standing.containsKey(List.of(x, y, z)) ? null
            : ((x == other.x()) && (y == other.y()) && (z == other.z())) ? two : one;
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Outcome.IN_THE_WAY, GatePreviews.place(owner, true).outcome());

        GatePreviews.gateAt = (w, x, y, z) -> !standing.containsKey(List.of(x, y, z)) ? null
            : ((x == other.x()) && (y == other.y()) && (z == other.z())) ? null : one;
        assertEquals(GatePreviews.Outcome.IN_THE_WAY, GatePreviews.place(owner, true).outcome(),
            "a block owned but by no gate is a ring's");
        assertTrue(written.isEmpty());
        assertEquals(null, standing.get(List.of(hole.x(), hole.y(), hole.z())));
    }

    /** A wrong block in a gate it stands over is still in the way: filling in never replaces anything. */
    @Test
    void placingOverAGateLeavesAWrongBlockInTheWay()
    {
        obsidianFramesAreFindable();
        detectsAGate();
        final Cell hole = standAGateShortOfOneBlock(mock(com.wormhole_xtreme.wormhole.model.Stargate.class));
        final Cell stone = standardLookingNorth().get(1);
        place(stone, Material.STONE);
        GatePreviews.show(owner, standard, null);

        final GatePreviews.Placed placed = GatePreviews.place(owner, true);

        assertEquals(GatePreviews.Outcome.IN_THE_WAY, placed.outcome());
        assertEquals(List.of("stone at " + stone.x() + " " + stone.y() + " " + stone.z()), placed.inTheWay());
        assertTrue(written.isEmpty());
        assertEquals(null, standing.get(List.of(hole.x(), hole.y(), hole.z())));
    }

    /**
     * A preview dials with its material group's ring pattern (#366), as a gate of that group
     * would: here the server turns no ring, and a group set to chevron turns one anyway.
     */
    @Test
    void aPreviewTurnsItsGroupsPatternWhereTheServerTurnsNone()
    {
        final List<Cell> cells = standardLookingNorth();
        final com.wormhole_xtreme.wormhole.logic.DialSpin spin = com.wormhole_xtreme.wormhole.logic.DialSpin.of(cells,
            GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
        final Cell start = spin.path(1).get(0);
        GatePreviews.show(owner, standard, new com.wormhole_xtreme.wormhole.model.MaterialGroup("Turning",
            Material.OBSIDIAN, Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN)
            .withDialSpin(com.wormhole_xtreme.wormhole.logic.DialSpinPattern.CHEVRON));
        final BlockDisplay startDisplay = spawned.get(cells.indexOf(start));
        GatePreviews.activate(owner);

        dialStep.run();

        verify(startDisplay).setBlock(data.get(Material.GLOWSTONE));
        ringDisplaysOfWave(1).forEach(d -> verify(d, never()).setBlock(data.get(Material.GLOWSTONE)));
    }
}
