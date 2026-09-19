package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
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
    private BukkitTask dialTask;
    private final List<Long> dialDelays = new ArrayList<>();
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
            when(block.getType()).thenReturn(standing.get(at));
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
        final List<BlockDisplay> iris = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));
        assertEquals(STANDARD_OPENING, iris.size());
        iris.forEach(d -> verify(d).setBlock(data.get(Material.STONE)));

        assertEquals(GatePreviews.Control.IRIS_OPENED, GatePreviews.iris(owner));
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

    /** Closing the iris over an open wormhole takes the wormhole back, and clearing the preview takes back the rest. */
    @Test
    void theIrisAndClearingTakeTheWormholeBack()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final int takenBackByTheWoosh = 21 + 13 + 5;

        GatePreviews.iris(owner);
        verify(owner, times(takenBackByTheWoosh + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));

        GatePreviews.iris(owner);
        verify(owner, times((takenBackByTheWoosh + 21) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        assertEquals(1, GatePreviews.clearAll(owner));
        verify(owner, times(takenBackByTheWoosh + 21 + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
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

    /** A display drawn after sharing, here the closed iris, is shown to the viewer as well. */
    @Test
    void aDisplayDrawnLaterIsShownToViewersToo()
    {
        final Player alex = onlineHere("Alex");
        GatePreviews.show(owner, standard, null);
        GatePreviews.share(owner, alex);
        final int before = spawned.size();

        GatePreviews.iris(owner);

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
}
