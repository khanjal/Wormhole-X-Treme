package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateGrid;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;
import com.wormhole_xtreme.wormhole.utils.RecordingCreation;

/**
 * What a preview's iris sweep sends each viewer's wormhole, step by step (#432).
 *
 * <p>A preview's wormhole is fake blocks sent to each viewer; behind a see-through iris it stands
 * a block off the ring as ice, on the viewer's far side. Every step of a sweep redrew the whole
 * preview, and that redraw sent the wormhole into every ring cell for every watcher: the rings
 * already covered got their water back under the glass, the sweep's own picture lasted one step,
 * and an opening sweep put water under the cells it had not reached yet. Each viewer's client is
 * modelled here as the last block it was sent at each position, which is what they see.
 */
class PreviewIrisSweepRedrawTest
{
    private World world;
    private Player owner;
    private Stargate3DShape standard;
    private final Map<Material, BlockData> data = new EnumMap<>(Material.class);
    private final Map<List<Integer>, Material> standing = new HashMap<>();
    private final Map<UUID, Player> others = new HashMap<>();
    /** What each viewer's client holds: the last block sent to each position. */
    private final Map<Player, Map<List<Integer>, BlockData>> pictures = new IdentityHashMap<>();
    /** Every block change sent to each viewer, in order. */
    private final Map<Player, List<Map.Entry<List<Integer>, BlockData>>> sends = new IdentityHashMap<>();
    /** Iris steps booked and not called off; a cancel really drops one. */
    private final Map<Integer, Runnable> irisPending = new LinkedHashMap<>();
    private int nextIrisTask = 1;
    private Runnable dialStep;
    private final RecordingCreation creation = new RecordingCreation(type ->
    {
        if (type == Interaction.class)
        {
            final Interaction button = mock(Interaction.class);
            when(button.isValid()).thenReturn(true);
            return button;
        }
        final BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        return display;
    });

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.GATE_DIAL_SPIN, false);
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
        {
            final List<Integer> at = List.of(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
            final Material real = standing.getOrDefault(at, Material.AIR);
            final Block block = mock(Block.class);
            when(block.getType()).thenReturn(real);
            when(block.getBlockData()).thenAnswer(read -> GatePreviews.blockData.apply(real));
            return block;
        });
        final WorldBorder border = mock(WorldBorder.class);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getWorldBorder()).thenReturn(border);
        GatePreviews.occupied = (w, x, y, z) -> false;
        HiddenEntities.creationWith(creation);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5, 180f, 0f));
        when(owner.getEyeLocation()).thenReturn(new Location(world, 0.5, 65.62, 0.5, 180f, 0f));

        GatePreviews.clock = () -> 1_000_000L;
        GatePreviews.online = id -> id.equals(owner.getUniqueId()) ? owner : others.get(id);
        GatePreviews.blockData = material -> data.computeIfAbsent(material, m -> mock(BlockData.class));
        GatePreviews.later = (ticks, step) ->
        {
            dialStep = step;
            return mock(BukkitTask.class);
        };
        GatePreviews.irisLater = (ticks, step) ->
        {
            final int id = nextIrisTask++;
            irisPending.put(id, step);
            final BukkitTask task = mock(BukkitTask.class);
            doAnswer(invocation -> irisPending.remove(id)).when(task).cancel();
            return task;
        };
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GatePreviews.clear();
        MaterialGroupRegistry.load(null);
        HiddenEntities.creationWith(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** Shows the preview, dials it open, and dresses its iris in glass, which hides water behind it. */
    private void openAGlassIrisPreview()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
    }

    private List<Cell> opening()
    {
        return GateBlueprint.openingOf(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
    }

    private BlockFace facing()
    {
        return GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH).facing();
    }

    private GatePreview preview()
    {
        return GatePreviews.of(owner.getUniqueId()).get(0);
    }

    /** An opening cell's position, moved so many blocks along the facing: -1 is behind the ring. */
    private List<Integer> along(final int index, final int steps)
    {
        final Cell cell = opening().get(index);
        final BlockFace facing = facing();
        return List.of(cell.x() + (steps * facing.getModX()), cell.y() + (steps * facing.getModY()),
            cell.z() + (steps * facing.getModZ()));
    }

    private Location standingAlong(final int steps)
    {
        final List<Integer> at = along(0, steps);
        return new Location(world, at.get(0) + 0.5, at.get(1), at.get(2) + 0.5);
    }

    /** A viewer shared the preview, standing so many blocks along its facing, whose client is recorded. */
    private Player viewerAlong(final String name, final int steps)
    {
        return viewerAt(name, standingAlong(steps));
    }

    /** A viewer shared the preview, standing at a place, whose client is recorded. */
    private Player viewerAt(final String name, final Location at)
    {
        final Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getName()).thenReturn(name);
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getLocation()).thenReturn(at);
        when(viewer.getEyeLocation()).thenReturn(at);
        others.put(viewer.getUniqueId(), viewer);
        pictures.put(viewer, new HashMap<>());
        sends.put(viewer, new ArrayList<>());
        doAnswer(invocation ->
        {
            final Location where = invocation.getArgument(0);
            final List<Integer> key = List.of(where.getBlockX(), where.getBlockY(), where.getBlockZ());
            pictures.get(viewer).put(key, invocation.getArgument(1));
            sends.get(viewer).add(Map.entry(key, invocation.getArgument(1)));
            return null;
        }).when(viewer).sendBlockChange(any(Location.class), any(BlockData.class));
        GatePreviews.share(owner, viewer);
        return viewer;
    }

    private void walkTo(final Player viewer, final int steps)
    {
        final Location at = standingAlong(steps);
        when(viewer.getLocation()).thenReturn(at);
        when(viewer.getEyeLocation()).thenReturn(at);
        GatePreviews.moved(viewer, at);
    }

    /** Every opening cell the iris has covered since the preview was shown. */
    private final Set<Integer> everCovered = new HashSet<>();

    /** Turns the iris, as its owner does, and notes what it covers. */
    private void toggleIris()
    {
        everCovered.addAll(preview().irisShown());
        GatePreviews.iris(owner);
        everCovered.addAll(preview().irisShown());
    }

    /** Runs one booked sweep step, and says which cells it covered or uncovered. */
    private Set<Integer> stepTheSweep()
    {
        assertFalse(irisPending.isEmpty(), "a sweep step is booked");
        final Set<Integer> before = new HashSet<>(preview().irisShown());
        final Integer id = irisPending.keySet().iterator().next();
        irisPending.remove(id).run();
        everCovered.addAll(preview().irisShown());
        final Set<Integer> changed = new HashSet<>(preview().irisShown());
        changed.removeAll(before);
        final Set<Integer> uncovered = new HashSet<>(before);
        uncovered.removeAll(preview().irisShown());
        changed.addAll(uncovered);
        return changed;
    }

    private void finishTheSweep()
    {
        for (int guard = 0; !irisPending.isEmpty() && (guard < 60); guard++)
        {
            stepTheSweep();
        }
    }

    /** What a block reads as on a client, for a message a person can follow. */
    private String shown(final BlockData sent)
    {
        if (sent == null)
        {
            return "nothing sent";
        }
        if (sent == data.get(Material.WATER))
        {
            return "wormhole";
        }
        if ((sent == data.get(Material.BLUE_ICE)) || (sent == data.get(Material.PACKED_ICE)))
        {
            return "ice";
        }
        return (sent == data.get(Material.AIR)) ? "air" : "other";
    }

    private String shownAt(final Player viewer, final int index, final int steps)
    {
        return shown(pictures.get(viewer).get(along(index, steps)));
    }

    /**
     * The picture for a viewer in front: covered cells have the wormhole handed back out of the ring
     * and the ice behind it; uncovered ones the wormhole in the ring, and the ice handed back from
     * behind wherever the iris has been.
     */
    private void assertFrontPicture(final Player viewer, final String when)
    {
        assertFrontPicture(viewer, when, false);
    }

    /**
     * The same.
     *
     * @param ringNeverSent
     *            true where a covered ring may never have been sent anything, which shows the
     *            same: a client that lost its blocks, or a preview opened behind the iris
     */
    private void assertFrontPicture(final Player viewer, final String when, final boolean ringNeverSent)
    {
        for (int i = 0; i < opening().size(); i++)
        {
            final boolean covered = preview().irisShown().contains(i);
            final String ring = shownAt(viewer, i, 0);
            final String behind = shownAt(viewer, i, -1);
            if (covered)
            {
                assertTrue(ring.equals("air") || (ringNeverSent && ring.equals("nothing sent")),
                    when + ": covered cell " + i + " has the wormhole handed back out of the ring, not " + ring);
                assertEquals("ice", behind, when + ": covered cell " + i + " has its stand-in behind the ring");
            }
            else
            {
                assertEquals("wormhole", ring, when + ": uncovered cell " + i + " shows the wormhole in the ring");
                if (everCovered.contains(i))
                {
                    assertEquals("air", behind, when + ": uncovered cell " + i + " has its stand-in handed back");
                }
                else
                {
                    assertTrue(behind.equals("air") || behind.equals("nothing sent"),
                        when + ": never-covered cell " + i + " keeps nothing behind the ring, not " + behind);
                }
            }
        }
    }

    /** The picture for a viewer behind: the wormhole in every ring cell, nothing a block either side. */
    private void assertBehindPicture(final Player viewer, final String when)
    {
        for (int i = 0; i < opening().size(); i++)
        {
            assertEquals("wormhole", shownAt(viewer, i, 0), when + ": cell " + i + " keeps the wormhole in the ring");
            for (final int off : new int[] {-1, 1})
            {
                final String there = shownAt(viewer, i, off);
                assertTrue(there.equals("air") || there.equals("nothing sent"),
                    when + ": cell " + i + " has " + there + " a block off the ring, where nothing belongs");
            }
        }
    }

    /**
     * The step after the first touches only the ring it covers.
     *
     * <p>The redraw that comes with each step sent the wormhole into every ring cell, so the rings
     * the sweep had already covered got their water back under the glass and the ring the step
     * covered was the only one drawn as the sweep means it. Asserted exactly: the step's ring
     * handed back and stood behind, once each, and nothing else sent to this viewer.
     */
    @Test
    void aClosingStepSendsAFrontViewerOnlyTheRingItCovers()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        sends.get(front).clear();

        final Set<Integer> covered = stepTheSweep();

        assertFalse(irisPending.isEmpty(), "still sweeping");
        final Map<List<Integer>, String> expected = new HashMap<>();
        for (final int i : covered)
        {
            expected.put(along(i, 0), "air");
            expected.put(along(i, -1), "ice");
        }
        final Map<List<Integer>, String> got = new HashMap<>();
        sends.get(front).forEach(sent -> got.put(sent.getKey(), shown(sent.getValue())));
        assertEquals(expected, got, "the step's ring moves behind the gate and nothing else is sent");
        assertEquals(2 * covered.size(), sends.get(front).size(),
            "one change per position, not the whole opening again: " + sends.get(front).size());
        assertFrontPicture(front, "after the second step");
    }

    /**
     * Two viewers on opposite sides of a closing sweep each keep their own picture at every step.
     *
     * <p>The viewer behind holds the wormhole in the ring throughout, so a step has nothing to send
     * them at all; it was sending them the whole opening.
     */
    @Test
    void viewersOnOppositeSidesKeepTheirOwnPicturesThroughAClosingSweep()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        final Player back = viewerAlong("Bea", -4);
        toggleIris();

        int steps = 0;
        while (irisPending.size() == 1 && (preview().irisShown().size() < opening().size()))
        {
            sends.get(back).clear();
            stepTheSweep();
            steps++;
            assertFrontPicture(front, "covered " + preview().irisShown().size());
            assertBehindPicture(back, "covered " + preview().irisShown().size());
            assertEquals(List.of(), sends.get(back), "a step sends a viewer behind nothing");
        }

        assertTrue(steps > 1, "the sweep took several steps to cover the opening, not " + steps);
        assertEquals(opening().size(), preview().irisShown().size(), "and it got all the way across");
    }

    /**
     * An opening sweep leaves the cells it has not reached yet as the closed iris drew them.
     *
     * <p>The first redraw put the wormhole back into every ring cell at once, under glass that
     * hides it, so the cells still covered read as empty for the length of the sweep.
     */
    @Test
    void anOpeningSweepLeavesTheCellsStillCoveredAsTheClosedIrisDrewThem()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        finishTheSweep();
        assertFrontPicture(front, "settled shut");

        toggleIris();

        assertFalse(irisPending.isEmpty(), "still sweeping open");
        assertFalse(preview().irisShown().isEmpty(), "with some of the iris still covering the opening");
        assertFrontPicture(front, "after the first ring opens");
        stepTheSweep();
        assertFrontPicture(front, "after the second");
        finishTheSweep();
        assertFrontPicture(front, "settled open");
    }

    /**
     * A viewer who crosses mid-sweep and crosses back is drawn for where they stand each time, and
     * the next step keeps it.
     *
     * <p>Crossing already moved the covered rings with them (#442); the next step's redraw then
     * put the water back into every ring cell.
     */
    @Test
    void aViewerWhoCrossesAndCrossesBackKeepsTheirPictureAcrossTheNextStep()
    {
        openAGlassIrisPreview();
        final Player walker = viewerAlong("Fran", 4);
        toggleIris();
        stepTheSweep();

        walkTo(walker, -4);
        assertBehindPicture(walker, "walked round behind");
        walkTo(walker, 4);
        assertFrontPicture(walker, "walked back in front");
        stepTheSweep();

        assertFalse(irisPending.isEmpty(), "still sweeping");
        assertFrontPicture(walker, "after the next step");
    }

    /**
     * The same, the other way: a viewer behind an opening sweep who walks round to the front gets
     * the still-covered cells' wormhole behind the ring, and the next step leaves it there.
     */
    @Test
    void aViewerWhoCrossesToTheFrontMidOpeningKeepsThatPictureAcrossTheNextStep()
    {
        openAGlassIrisPreview();
        final Player walker = viewerAlong("Bea", -4);
        toggleIris();
        finishTheSweep();
        toggleIris();

        walkTo(walker, 4);
        assertFrontPicture(walker, "walked round to the front");
        stepTheSweep();

        assertFalse(preview().irisShown().isEmpty(), "still covering part of the opening");
        assertFrontPicture(walker, "after the next step");
    }

    /**
     * A sweep called off by the iris turning back keeps every viewer's picture right from the
     * first step of the next.
     *
     * <p>The opening sweep starts with the whole iris standing, over cells the called-off closing
     * sweep never reached. Those kept their water in the ring, under glass that hides it.
     */
    @Test
    void aSweepCalledOffPartWayHandsTheNextOneTheRightPicture()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        final Player back = viewerAlong("Bea", -4);
        toggleIris();
        stepTheSweep();

        toggleIris();

        assertEquals(1, irisPending.size(), "one sweep running, the opening one");
        assertFrontPicture(front, "first step of the sweep that took over");
        assertBehindPicture(back, "first step of the sweep that took over");
        finishTheSweep();
        assertFrontPicture(front, "settled open");
    }

    /**
     * A viewer whose client lost the preview's blocks mid-sweep -- a chunk reloaded, or out of range
     * when they were sent -- gets their whole picture back at the next periodic redraw.
     *
     * <p>A step sends only what moved, so nothing else would put those cells right until the sweep
     * settled; the redraw every few seconds used to, by sending everybody the whole ring.
     */
    @Test
    void aViewerWhoLostTheBlocksGetsThemBackAtThePeriodicRedraw()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        stepTheSweep();
        pictures.get(front).clear();
        sends.get(front).clear();

        GatePreviews.tick();

        assertFalse(irisPending.isEmpty(), "still sweeping");
        assertFrontPicture(front, "after the redraw", true);
    }

    /**
     * A new portal material mid-sweep is sent at once, to every cell holding the wormhole, rather
     * than waiting for the cells to move.
     */
    @Test
    void aPortalMaterialChangedMidSweepIsSentAtOnce()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        stepTheSweep();

        GatePreviews.material(owner, GateBlueprint.Role.PORTAL, Material.NETHER_PORTAL);

        assertFalse(irisPending.isEmpty(), "still sweeping");
        final BlockData portal = data.get(Material.NETHER_PORTAL);
        for (int i = 0; i < opening().size(); i++)
        {
            if (!preview().irisShown().contains(i))
            {
                assertSame(portal, pictures.get(front).get(along(i, 0)),
                    "uncovered cell " + i + " shows the new portal material: " + shownAt(front, i, 0));
            }
        }
    }

    /**
     * A viewer who leaves the preview's world mid-sweep and comes back is drawn the sweep as it
     * stands, from what the return sent them; their client kept none of what it had.
     */
    @Test
    void aViewerReturningFromAnotherWorldMidSweepIsDrawnAsItStands()
    {
        openAGlassIrisPreview();
        final Player traveller = viewerAlong("Tia", 4);
        toggleIris();
        stepTheSweep();
        final World elsewhere = mock(World.class);
        when(traveller.getWorld()).thenReturn(elsewhere);
        GatePreviews.tick();
        stepTheSweep();
        when(traveller.getWorld()).thenReturn(world);
        pictures.get(traveller).clear();

        GatePreviews.tick();

        assertFalse(irisPending.isEmpty(), "still sweeping");
        assertFrontPicture(traveller, "back in the world");
    }

    /**
     * A viewer unshared and shared again mid-sweep is drawn at their first step, not left with the
     * wormhole in the covered rings until the next ring: their layers from before compared equal.
     */
    @Test
    void aViewerSharedAgainMidSweepIsDrawnAtTheirFirstStep()
    {
        openAGlassIrisPreview();
        final Player back = viewerAlong("Rae", 4);
        toggleIris();
        stepTheSweep();
        GatePreviews.share(owner, back);
        GatePreviews.share(owner, back);

        walkTo(back, 5);

        assertFrontPicture(back, "a step after being shared again");
    }

    /**
     * Behind an opaque iris the wormhole goes back into the ring at the first ring of an opening
     * sweep, not at its end.
     *
     * <p>A front viewer of a shut preview holds the wormhole a block behind the ring, however opaque
     * the iris. Nobody sees the difference -- the iris covers the ring and hides what is behind it
     * -- but it is the one change for an opaque iris, so it is pinned.
     */
    @Test
    void anOpaqueIrisOpeningPutsTheWormholeBackInTheRingAtTheFirstRing()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        finishTheSweep();
        assertEquals("wormhole", shownAt(front, 0, -1), "shut, the wormhole stands behind the opaque iris");

        toggleIris();

        assertFalse(preview().irisShown().isEmpty(), "still sweeping open");
        for (int i = 0; i < opening().size(); i++)
        {
            assertEquals("wormhole", shownAt(front, i, 0), "cell " + i + " has the wormhole in the ring");
            assertEquals("air", shownAt(front, i, -1), "and nothing behind it any more");
        }
    }

    /**
     * A preview standing on a button that faces up or down never stacks its layers, so its sweep
     * stands nothing off the ring either: nothing after the sweep would hand it back.
     *
     * <p>No command makes one today -- a build preview takes a wall button's facing or the
     * player's -- but {@code showOn} accepts any facing, and the leak would be solid ice beside
     * the preview for good.
     */
    @Test
    void aPreviewFacingUpLeavesNoIceAfterItsSweep()
    {
        final Block dhd = mock(Block.class);
        when(dhd.getX()).thenReturn(0);
        when(dhd.getY()).thenReturn(64);
        when(dhd.getZ()).thenReturn(0);
        when(dhd.getWorld()).thenReturn(world);
        final List<Cell> cells = GateBlueprint.openingOf(standard, GateGrid.fromActivationHolder(standard, 0, 64, 0,
            BlockFace.UP));
        final Cell first = cells.get(0);
        final Location above = new Location(world, first.x() + 0.5, first.y() + 4.0, first.z() + 0.5, 0f, 90f);
        when(owner.getEyeLocation()).thenReturn(above);
        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.showOn(owner, standard, null, dhd, BlockFace.UP));
        assertEquals(BlockFace.UP, preview().grid().facing(), "the fixture stands a preview facing up");
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        assertTrue(preview().open(), "the preview opened");
        final Player viewer = viewerAt("Uma", above);
        toggleIris();
        stepTheSweep();
        finishTheSweep();

        final Set<List<Integer>> ring = new HashSet<>();
        cells.forEach(cell -> ring.add(List.of(cell.x(), cell.y(), cell.z())));
        for (final List<Integer> at : ring)
        {
            assertEquals("wormhole", shown(pictures.get(viewer).get(at)), "settled, the ring at " + at + " shows it");
        }
        pictures.get(viewer).forEach((at, sent) -> assertTrue(ring.contains(at) || !"ice".equals(shown(sent)),
            "ice left at " + at + ", off a preview that never stacks"));
        GatePreviews.activate(owner);
        pictures.get(viewer).forEach((at, sent) -> assertTrue(Set.of("air", "other").contains(shown(sent)),
            "shut, and still showing " + shown(sent) + " at " + at));
    }

    /**
     * A sweep called off by an instant one, as a reload of {@code gate-iris-animation} can make the
     * next, hands back the ice it stood: the instant draw only knows the ring.
     */
    @Test
    void aSweepCalledOffByAnInstantOneLeavesNoIceBehind()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        stepTheSweep();
        assertFrontPicture(front, "mid-sweep");

        ConfigTestSupport.set(ConfigKeys.GATE_IRIS_ANIMATION, "instant");
        toggleIris();

        assertTrue(irisPending.isEmpty(), "the iris opened at once");
        assertTrue(preview().irisShown().isEmpty(), "all of it");
        assertFrontPicture(front, "opened at once");
    }

    /**
     * A preview that opens while its iris is still sweeping shut is drawn there and then.
     *
     * <p>The kawoosh settles behind an iris that is part way across: the cells it covers take the
     * wormhole behind the ring and the rest show it in the ring, without waiting for the sweep.
     */
    @Test
    void aPreviewOpeningMidSweepIsDrawnForTheIrisAsItStands()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.material(owner, GateBlueprint.Role.IRIS, Material.YELLOW_STAINED_GLASS);
        final Player front = viewerAlong("Fran", 4);
        GatePreviews.activate(owner);
        toggleIris();
        assertFalse(preview().irisShown().isEmpty(), "the first ring is in");

        for (int step = 0; (step < 13) && !preview().open(); step++)
        {
            dialStep.run();
        }

        assertTrue(preview().open(), "the preview opened");
        assertFalse(irisPending.isEmpty(), "with the sweep still crossing");
        assertFrontPicture(front, "opened mid-sweep", true);

        // And shutting it again takes all of that back, the ring cells included.
        GatePreviews.activate(owner);
        for (int i = 0; i < opening().size(); i++)
        {
            for (final int off : new int[] {-1, 0})
            {
                final String there = shownAt(front, i, off);
                assertTrue(there.equals("air") || there.equals("nothing sent"),
                    "shut again: cell " + i + " still shows " + there + " " + off + " along the facing");
            }
        }
    }

    /**
     * A viewer shared the preview mid-sweep is drawn by the next step like everybody else.
     *
     * <p>They are caught up with the wormhole in every ring cell, and the step after takes it back
     * out of the rings already covered.
     */
    @Test
    void aViewerSharedMidSweepIsDrawnByTheNextStep()
    {
        openAGlassIrisPreview();
        toggleIris();
        stepTheSweep();
        final Player late = viewerAlong("Lou", 4);

        stepTheSweep();

        assertFalse(irisPending.isEmpty(), "still sweeping");
        assertFrontPicture(late, "the step after they arrived");
    }

    /**
     * A sweep starts from where each viewer stands now, not where the last sweep left them.
     *
     * <p>A viewer who walks round behind a settled iris has the wormhole in the ring; the opening
     * sweep has nothing of theirs to move, and sends them nothing.
     */
    @Test
    void aViewerWhoWalkedRoundAfterASweepIsDrawnFromThereByTheNext()
    {
        openAGlassIrisPreview();
        final Player walker = viewerAlong("Fran", 4);
        toggleIris();
        finishTheSweep();
        walkTo(walker, -4);
        sends.get(walker).clear();

        toggleIris();
        stepTheSweep();

        assertFalse(preview().irisShown().isEmpty(), "still sweeping open");
        assertEquals(List.of(), sends.get(walker), "nothing of theirs to move");
        assertBehindPicture(walker, "two steps into the opening sweep");
    }

    /**
     * A viewer who leaves the preview's world mid-sweep is sent nothing more, and one who stays is
     * still drawn.
     */
    @Test
    void aViewerWhoLeavesTheWorldMidSweepIsSentNothingMore()
    {
        openAGlassIrisPreview();
        final Player front = viewerAlong("Fran", 4);
        final Player gone = viewerAlong("Gus", 4);
        toggleIris();
        final World faraway = mock(World.class);
        when(gone.getWorld()).thenReturn(faraway);
        sends.get(gone).clear();

        stepTheSweep();

        assertEquals(List.of(), sends.get(gone), "nothing reaches a viewer in another world");
        assertFrontPicture(front, "the viewer who stayed");
    }

    /**
     * A preview shut mid-sweep, and one unshared mid-sweep, leave no ice and no wormhole behind on
     * the viewer it was drawn for.
     */
    @Test
    void shuttingOrUnsharingMidSweepTakesEveryFakeBlockBack()
    {
        openAGlassIrisPreview();
        final Player shut = viewerAlong("Fran", 4);
        final Player unshared = viewerAlong("Una", 4);
        toggleIris();
        stepTheSweep();
        assertFrontPicture(shut, "mid-sweep");
        assertFrontPicture(unshared, "mid-sweep, before they were unshared");

        GatePreviews.share(owner, unshared);
        GatePreviews.activate(owner);

        for (final Player viewer : List.of(shut, unshared))
        {
            for (int i = 0; i < opening().size(); i++)
            {
                for (final int off : new int[] {-1, 0, 1})
                {
                    final String there = shownAt(viewer, i, off);
                    assertTrue(there.equals("air") || there.equals("nothing sent"),
                        viewer.getName() + ": cell " + i + " still shows " + there + " " + off + " along the facing");
                }
            }
        }
    }

    /**
     * A viewer a sweep never drew anything off the ring for has only the ring taken back when the
     * preview goes: behind an opaque iris the wormhole never leaves it, and handing back both cells
     * either side of every ring cell was two openings of sends for nothing.
     */
    @Test
    void clearingMidSweepTakesOnlyTheRingFromAViewerWhoHeldNothingOffIt()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final Player front = viewerAlong("Fran", 4);
        toggleIris();
        stepTheSweep();
        assertFalse(irisPending.isEmpty(), "still sweeping");
        sends.get(front).clear();

        GatePreviews.clearAll(owner);

        final Set<List<Integer>> ring = new HashSet<>();
        for (int i = 0; i < opening().size(); i++)
        {
            ring.add(along(i, 0));
        }
        final Set<List<Integer>> sent = new HashSet<>();
        sends.get(front).forEach(change -> sent.add(change.getKey()));
        assertEquals(ring, sent, "the ring cells are taken back, and nothing either side of them");
        assertEquals(ring.size(), sends.get(front).size(), "once each");
        ring.forEach(at -> assertEquals("air", shown(pictures.get(front).get(at)), "the ring at " + at + " is air"));
    }
}
