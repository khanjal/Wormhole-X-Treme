package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.HeldScheduler;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * A ring pattern drawn where a player stands, before any slab is laid, and the layout guide that
 * goes with it (#332, step 4).
 *
 * <p>The drawing is the real {@link PreviewSurroundings} over mocks of the world and the players,
 * and what a client is showing is rebuilt from the block changes in the order they were sent, so
 * "the guide is gone" means the real block was the last thing sent, not that a clear-up was
 * called. The pattern squares are written out here rather than taken from {@link RingPattern}, so
 * a pattern that drifted would fail a test instead of moving it along.
 */
class RingPatternPreviewTest
{
    private static final String WORLD_NAME = "world";
    private static final int X = 100, Y = 64, Z = 200;

    /** The odd pattern's sixteen slab squares, as offsets from where the player stands. */
    private static final int[][] ODD = { { -1, -3 }, { 0, -3 }, { 1, -3 }, { -2, -2 }, { 2, -2 }, { -3, -1 },
        { 3, -1 }, { -3, 0 }, { 3, 0 }, { -3, 1 }, { 3, 1 }, { -2, 2 }, { 2, 2 }, { -1, 3 }, { 0, 3 }, { 1, 3 } };

    /** The even pattern's twelve, with the player on the low corner of the 2x2 centre. */
    private static final int[][] EVEN = { { 0, -2 }, { 1, -2 }, { -1, -1 }, { 2, -1 }, { -2, 0 }, { 3, 0 },
        { -2, 1 }, { 3, 1 }, { -1, 2 }, { 2, 2 }, { 0, 3 }, { 1, 3 } };

    private final HeldScheduler held = new HeldScheduler();
    private final Map<Material, BlockData> data = new EnumMap<>(Material.class);
    private int asked = 0;
    private final Map<BlockData, String> described = new IdentityHashMap<>();
    private final Map<String, Slab> laid = new HashMap<>();
    private final Map<String, Material> laidAs = new HashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConfigManager> config;
    private World world;
    private Player viewer;
    private Player bystander;
    private BlockData real;

    /** What the world holds: the slabs a test has laid, and nothing else. */
    private final RingTemplate.BlockProbe probe = new RingTemplate.BlockProbe()
    {
        @Override
        public Material materialAt(final int x, final int y, final int z)
        {
            asked++;
            return laidAs.getOrDefault(key(x, y, z), Material.AIR);
        }

        @Override
        public RingTemplate.SlabHalf halfAt(final int x, final int y, final int z)
        {
            asked++;
            return laid.containsKey(key(x, y, z)) ? half.get(key(x, y, z)) : null;
        }
    };

    private final Map<String, RingTemplate.SlabHalf> half = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        held.install();
        RingManager.clear();
        RingPreviews.clearAll();

        real = mock(BlockData.class);
        bukkit = mockStatic(Bukkit.class);
        // A slab is made afresh each time and remembers the half it was set to, so what a client
        // shows at a square says which half of which slab it is.
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call ->
        {
            final Material made = call.getArgument(0, Material.class);
            if (!made.name().endsWith("_SLAB"))
            {
                return dataFor(made);
            }
            final Slab slab = mock(Slab.class);
            described.put(slab, made.name() + "/?");
            doAnswer(set ->
            {
                described.put(slab, made.name() + "/" + set.getArgument(0, Slab.Type.class));
                return null;
            }).when(slab).setType(any(Slab.Type.class));
            return slab;
        });
        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingDeployTicks).thenReturn(Integer.valueOf(3));
        config.when(ConfigManager::getRingFlashTicks).thenReturn(Integer.valueOf(2));
        config.when(ConfigManager::getRingSettleTicks).thenReturn(Integer.valueOf(7));
        config.when(ConfigManager::getRingHoldTicks).thenReturn(Integer.valueOf(11));
        config.when(ConfigManager::getRingLightsLingerTicks).thenReturn(Integer.valueOf(13));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(16));
        config.when(ConfigManager::getRingSoundClose).thenReturn("");
        config.when(ConfigManager::getRingSoundRing).thenReturn("");
        config.when(ConfigManager::getRingSoundOpen).thenReturn("");
        config.when(ConfigManager::getRingSoundFlash).thenReturn("");
        // Not the shipped defaults, so a ring that read the wrong setting shows.
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.SEA_LANTERN);
        config.when(ConfigManager::getRingDefaultFlash).thenReturn(Material.SHROOMLIGHT);
        config.when(ConfigManager::getRingDefaultStyle).thenReturn(RingStyle.SEQUENTIAL);

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD_NAME);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
        {
            final Block block = mock(Block.class);
            when(block.isPassable()).thenReturn(Boolean.valueOf(call.getArgument(1, Integer.class) >= Y));
            when(block.getBlockData()).thenReturn(real);
            return block;
        });

        viewer = playerAt(X, Y, Z);
        bystander = playerAt(X, Y, Z);
        when(world.getPlayers()).thenReturn(List.of(viewer, bystander));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        RingPreviews.clearAll();
        RingManager.clear();
        config.close();
        bukkit.close();
        held.remove();
        PluginTestSupport.remove();
    }

    private Player playerAt(final int x, final int y, final int z)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(Boolean.TRUE);
        when(player.getLocation()).thenReturn(new Location(world, x + 0.5, y, z + 0.5));
        return player;
    }

    private static String key(final int x, final int y, final int z)
    {
        return x + "," + y + "," + z;
    }

    private BlockData dataFor(final Material material)
    {
        return data.computeIfAbsent(material, made -> mock(BlockData.class));
    }

    /** A slab on screen as {@code MATERIAL/HALF}, or null for anything else. */
    private String describe(final BlockData shown)
    {
        return described.get(shown);
    }

    /** Lays a slab of a material in the half a floor ring takes, at an offset from the player. */
    private void lay(final int[] offset, final Material material, final RingTemplate.SlabHalf in)
    {
        final String at = key(X + offset[0], Y, Z + offset[1]);
        laid.put(at, mock(Slab.class));
        laidAs.put(at, material);
        half.put(at, in);
    }

    private void layAll(final int[][] squares)
    {
        for (final int[] square : squares)
        {
            lay(square, Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        }
    }

    /** What the viewer's client shows at each position, from the block changes in the order sent. */
    private Map<String, BlockData> onScreen(final Player who)
    {
        final ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        final ArgumentCaptor<BlockData> what = ArgumentCaptor.forClass(BlockData.class);
        verify(who, atLeastOnce()).sendBlockChange(where.capture(), what.capture());
        final Map<String, BlockData> screen = new HashMap<>();
        for (int i = 0; i < where.getAllValues().size(); i++)
        {
            final Location at = where.getAllValues().get(i);
            screen.put(key(at.getBlockX(), at.getBlockY(), at.getBlockZ()), what.getAllValues().get(i));
        }
        return screen;
    }

    /** The positions in a layer of the viewer's screen where something other than the real block shows. */
    private Set<String> shownIn(final int y)
    {
        final Set<String> shown = new HashSet<>();
        for (final Map.Entry<String, BlockData> entry : onScreen(viewer).entrySet())
        {
            if ((Integer.parseInt(entry.getKey().split(",")[1]) == y) && (entry.getValue() != real))
            {
                shown.add(entry.getKey());
            }
        }
        return shown;
    }

    private Set<String> squares(final int[][] offsets, final int y)
    {
        final Set<String> out = new HashSet<>();
        for (final int[] offset : offsets)
        {
            out.add(key(X + offset[0], y, Z + offset[1]));
        }
        return out;
    }

    private void assertNothingOfTheDrawingRemains()
    {
        for (final Map.Entry<String, BlockData> entry : onScreen(viewer).entrySet())
        {
            assertSame(real, entry.getValue(), "the last thing sent at " + entry.getKey() + " is the real block");
        }
    }

    private void settle(final RingPreview.State wanted)
    {
        for (int guard = 0; (RingPreviews.of(viewer).getState() != wanted) && (guard < 1000); guard++)
        {
            held.runNextOtherThan(RingPreviews.TIMEOUT_TICKS);
        }
        assertEquals(wanted, RingPreviews.of(viewer).getState());
    }

    /** The odd pattern: the pad lit under it, the floor opened, and every slab square marked. */
    @Test
    void theOddPatternIsDrawnAtItsSixteenSquaresInTheDefaultMaterials()
    {
        assertEquals(RingPreview.Result.DONE, RingPreviews.showPattern(viewer, RingPattern.ODD, probe));

        final Map<String, BlockData> screen = onScreen(viewer);
        assertEquals(squares(ODD, Y - 2), shownIn(Y - 2), "the lights go two layers under the slabs");
        assertEquals(squares(ODD, Y - 1), shownIn(Y - 1), "the floor opens one layer under them");
        assertEquals(squares(ODD, Y), shownIn(Y), "and the slabs go in the layer the player stands in");
        for (final String at : squares(ODD, Y - 2))
        {
            assertSame(dataFor(Material.SEA_LANTERN), screen.get(at), "the default light");
        }
        for (final String at : squares(ODD, Y - 1))
        {
            assertSame(dataFor(Material.BARRIER), screen.get(at));
        }
        for (final String at : squares(ODD, Y))
        {
            assertEquals("SMOOTH_STONE_SLAB/BOTTOM", describe(screen.get(at)), "the fallback slab, in a floor ring's half");
        }
    }

    /** The even pattern is twelve squares with the player on the low corner of its 2x2 centre. */
    @Test
    void theEvenPatternIsDrawnAtItsTwelveSquares()
    {
        assertEquals(RingPreview.Result.DONE, RingPreviews.showPattern(viewer, RingPattern.EVEN, probe));

        assertEquals(squares(EVEN, Y - 2), shownIn(Y - 2));
        assertEquals(squares(EVEN, Y - 1), shownIn(Y - 1));
        assertEquals(squares(EVEN, Y), shownIn(Y));
        assertEquals(12, RingPreviews.of(viewer).getGuideTotal());
    }

    /** The ring is what a real build would make, with the server's defaults and no pair. */
    @Test
    void thePreviewIsAnUnsavedFloorRingInTheServersDefaults()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        final RingPreview preview = RingPreviews.of(viewer);
        assertTrue(preview.isPattern());
        final Ring ring = preview.getRing();
        assertEquals(RingOrientation.FLOOR, ring.getOrientation());
        assertEquals(X, ring.getAnchorX());
        assertEquals(Y, ring.getAnchorY());
        assertEquals(Z, ring.getAnchorZ());
        assertEquals(Material.SEA_LANTERN, ring.getLightMaterial());
        assertEquals(Material.SHROOMLIGHT, ring.getFlashMaterial());
        assertEquals(RingStyle.SEQUENTIAL, ring.getStyle());
        assertEquals(RingYamlManager.FALLBACK_MATERIAL, ring.getRingMaterial());
        assertNull(RingManager.getPair(preview.getPair().getId()), "it was never stored");
    }

    /** Nothing laid yet: every square is still to place. */
    @Test
    void withNoSlabsLaidNoneAreInPlace()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        assertEquals(0, RingPreviews.of(viewer).getGuideInPlace());
        assertEquals(16, RingPreviews.of(viewer).getGuideTotal());
    }

    /** Some laid: those squares are counted and not marked, the rest are marked. */
    @Test
    void slabsAlreadyLaidAreCountedAndNotMarked()
    {
        lay(ODD[0], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        lay(ODD[5], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        lay(ODD[9], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        assertEquals(3, RingPreviews.of(viewer).getGuideInPlace());
        final Set<String> marked = shownIn(Y);
        assertEquals(13, marked.size());
        assertFalse(marked.contains(key(X + ODD[0][0], Y, Z + ODD[0][1])), "a square with its slab needs no mark");
        assertTrue(marked.contains(key(X + ODD[1][0], Y, Z + ODD[1][1])));
    }

    /** All laid: sixteen of sixteen, and nothing is marked at the slab layer. */
    @Test
    void aCompleteCircleCountsAllAndMarksNothing()
    {
        layAll(ODD);

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        assertEquals(16, RingPreviews.of(viewer).getGuideInPlace());
        assertTrue(shownIn(Y).isEmpty());
    }

    /** A slab of another kind, or laid in the other half, is not the right slab. */
    @Test
    void aWrongKindOrHalfOfSlabDoesNotCount()
    {
        lay(ODD[0], Material.QUARTZ_SLAB, RingTemplate.SlabHalf.BOTTOM);
        lay(ODD[1], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.TOP);
        lay(ODD[2], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        assertEquals(1, RingPreviews.of(viewer).getGuideInPlace());
    }

    /** Run again where the player stands, it counts what has been laid since and keeps the preview. */
    @Test
    void runAgainWhereTheyStandItRefreshesTheGuide()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview first = RingPreviews.of(viewer);
        lay(ODD[0], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        assertEquals(RingPreview.Result.DONE, RingPreviews.showPattern(viewer, RingPattern.ODD, probe));

        assertSame(first, RingPreviews.of(viewer), "the same preview, not a new one");
        assertEquals(1, first.getGuideInPlace());
        assertSame(real, onScreen(viewer).get(key(X + ODD[0][0], Y, Z + ODD[0][1])),
            "the mark on the square that was filled is taken away");
        assertEquals(15, shownIn(Y).size());
    }

    /** Run again from another square, or the other pattern, it moves. */
    @Test
    void runAgainElsewhereOrAsTheOtherPatternReplacesIt()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview first = RingPreviews.of(viewer);

        RingPreviews.showPattern(viewer, RingPattern.EVEN, probe);

        final RingPreview second = RingPreviews.of(viewer);
        assertNotSame(first, second);
        assertEquals(RingPattern.EVEN, second.getRing().getPattern());
        assertSame(real, onScreen(viewer).get(key(X - 3, Y, Z)), "the odd pattern's far square is gone");
        assertEquals(squares(EVEN, Y), shownIn(Y));

        when(viewer.getLocation()).thenReturn(new Location(world, X + 20.5, Y, Z + 0.5));
        RingPreviews.showPattern(viewer, RingPattern.EVEN, probe);

        assertEquals(X + 20, RingPreviews.of(viewer).getRing().getAnchorX());
        assertSame(real, onScreen(viewer).get(key(X, Y, Z - 2)), "the first spot is taken back");
    }

    /** One square along z, or one up, is another place: the preview moves with the player. */
    @Test
    void runAgainOneSquareAlongZOrOneUpMovesIt()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        when(viewer.getLocation()).thenReturn(new Location(world, X + 0.5, Y, Z + 1.5));
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        assertEquals(Z + 1, RingPreviews.of(viewer).getRing().getAnchorZ());

        when(viewer.getLocation()).thenReturn(new Location(world, X + 0.5, Y + 1, Z + 1.5));
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        assertEquals(Y + 1, RingPreviews.of(viewer).getRing().getAnchorY());
    }

    /** From out of range a trial value must not load the pattern's chunks to recount: the last count stands. */
    @Test
    void aTrialWithThePatternUnloadedAsksTheWorldForNoBlocksAndKeepsTheCount()
    {
        lay(ODD[0], Material.SMOOTH_STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        assertEquals(1, RingPreviews.of(viewer).getGuideInPlace());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.FALSE);
        final int askedBefore = asked;

        RingPreviews.of(viewer).tryRingMaterial(Material.QUARTZ_SLAB);

        assertEquals(askedBefore, asked, "no block was read from an unloaded chunk");
        assertEquals(1, RingPreviews.of(viewer).getGuideInPlace(), "the last count stands");
        assertEquals(Material.QUARTZ_SLAB, RingPreviews.of(viewer).getTrialRing(), "the trial is kept");
    }

    /** A trial slab judges the squares against that slab, and the guide follows it. */
    @Test
    void aTrialSlabIsWhatTheGuideJudgesAgainst()
    {
        for (final int[] square : ODD)
        {
            lay(square, Material.QUARTZ_SLAB, RingTemplate.SlabHalf.BOTTOM);
        }
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        assertEquals(0, RingPreviews.of(viewer).getGuideInPlace());

        RingPreviews.of(viewer).tryRingMaterial(Material.QUARTZ_SLAB);

        assertEquals(16, RingPreviews.of(viewer).getGuideInPlace());
    }

    /** Deploy, light, collapse and cycle all work on a pattern, and the guide survives them. */
    @Test
    void theStepsWorkOnAPatternAndTheGuideComesBack()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        layAll(new int[][] { ODD[0] });
        final RingPreview preview = RingPreviews.of(viewer);

        assertEquals(RingPreview.Result.DONE, RingPreviews.deploy(viewer, preview.getPair(), preview.getRing()));
        settle(RingPreview.State.DEPLOYED);
        assertEquals(RingPreview.Result.DONE, RingPreviews.light(viewer));
        settle(RingPreview.State.LIGHTING);
        settle(RingPreview.State.DEPLOYED);
        assertEquals(RingPreview.Result.DONE, RingPreviews.collapse(viewer));
        settle(RingPreview.State.LIT);

        assertEquals(squares(ODD, Y), shownIn(Y), "the guide is back under the rings that went home");
        assertEquals(RingPreview.Result.DONE, RingPreviews.cycle(viewer, preview.getPair(), preview.getRing()));
        for (int guard = 0; preview.isCycling() && (guard < 1000); guard++)
        {
            held.runNextOtherThan(RingPreviews.TIMEOUT_TICKS);
        }
        assertFalse(preview.isCycling());
        assertEquals(RingPreview.State.LIT, preview.getState());
        assertEquals(squares(ODD, Y), shownIn(Y));
    }

    /** The slabs the stack is drawn in are the defaults, and the sweep uses the default flash. */
    @Test
    void theStackIsDrawnInTheDefaultSlabAndTheLightInTheDefaultFlash()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview preview = RingPreviews.of(viewer);
        RingPreviews.deploy(viewer, preview.getPair(), preview.getRing());
        settle(RingPreview.State.DEPLOYED);

        assertFalse(shownIn(Y + 1).isEmpty(), "rings stand above the layer the slabs are laid in");
        assertTrue(describe(onScreen(viewer).get(shownIn(Y + 1).iterator().next())).startsWith("SMOOTH_STONE_SLAB/"));
        RingPreviews.light(viewer);
        settle(RingPreview.State.DEPLOYED);
        final ArgumentCaptor<BlockData> sent = ArgumentCaptor.forClass(BlockData.class);
        verify(viewer, atLeastOnce()).sendBlockChange(any(Location.class), sent.capture());
        assertTrue(sent.getAllValues().contains(dataFor(Material.SHROOMLIGHT)), "the flash was drawn");
    }

    /** The guide stands under a settled stack, where the rings do not cover it. */
    @Test
    void theGuideStillMarksTheSquaresWhileTheStackStands()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview preview = RingPreviews.of(viewer);
        RingPreviews.deploy(viewer, preview.getPair(), preview.getRing());
        settle(RingPreview.State.DEPLOYED);

        final Map<String, BlockData> screen = onScreen(viewer);
        for (final String at : squares(ODD, Y))
        {
            assertNotSame(real, screen.get(at), "something still marks " + at);
        }
    }

    /** Asked again with the stack standing, the preview shows the same as before. */
    @Test
    void runAgainWithTheStackStandingLeavesTheStackDrawn()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview preview = RingPreviews.of(viewer);
        RingPreviews.deploy(viewer, preview.getPair(), preview.getRing());
        settle(RingPreview.State.DEPLOYED);
        final Map<String, String> before = new HashMap<>();
        onScreen(viewer).forEach((at, shown) -> before.put(at, describe(shown)));

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        final Map<String, String> after = new HashMap<>();
        onScreen(viewer).forEach((at, shown) -> after.put(at, describe(shown)));
        assertEquals(before, after);
    }

    /** The guide's marks are bottom-half slabs, the half a floor ring is laid in. */
    @Test
    void theGuideIsDrawnInTheBottomHalf()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        final Map<String, BlockData> screen = onScreen(viewer);
        for (final String at : squares(ODD, Y))
        {
            assertEquals("SMOOTH_STONE_SLAB/BOTTOM", describe(screen.get(at)));
        }
    }

    /** Drawn for the viewer alone. */
    @Test
    void nobodyElseIsSentAnything()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        final RingPreview preview = RingPreviews.of(viewer);
        RingPreviews.deploy(viewer, preview.getPair(), preview.getRing());
        settle(RingPreview.State.DEPLOYED);

        verify(bystander, never()).sendBlockChange(any(Location.class), any(BlockData.class));
    }

    /** -clear takes the lights, the floor, the slabs and the guide back. */
    @Test
    void clearingTakesEverythingBack()
    {
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        assertTrue(RingPreviews.clear(viewer));

        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains();
    }

    /** Quitting, changing world, the timeout and plugin disable each take it back. */
    @Test
    void quitWorldChangeTimeoutAndDisableTakeItBack()
    {
        final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(viewer);
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        new RingPreviewListener().onQuit(quit);
        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains();

        final PlayerChangedWorldEvent moved = mock(PlayerChangedWorldEvent.class);
        when(moved.getPlayer()).thenReturn(viewer);
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        new RingPreviewListener().onChangedWorld(moved);
        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains();

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        held.runNext();
        assertNull(RingPreviews.of(viewer), "the timeout");
        assertNothingOfTheDrawingRemains();

        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        RingPreviews.clearAll();
        assertNull(RingPreviews.of(viewer));
        assertNothingOfTheDrawingRemains();
    }

    /** It belongs to no pair, so the triggers that go by pair do not reach it. */
    @Test
    void aRealPairBeingRemovedOrCycledLeavesItStanding()
    {
        final Ring far = new Ring(500, Y, 500, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        // Named as the pattern's own throwaway pair is, so only identity tells them apart.
        final RingPair other = new RingPair("pattern", WORLD_NAME, far, far);
        RingManager.addPair(other, 4);
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);
        // Something a refresh of this preview would redraw, so a refresh that reached it shows.
        RingPreviews.of(viewer).getRing().setLightMaterial(Material.GLOWSTONE);

        RingPreviews.clearFor(other);
        RingPreviews.refresh(other);

        assertNotNull(RingPreviews.of(viewer));
        assertEquals(squares(ODD, Y), shownIn(Y));
        for (final String at : squares(ODD, Y - 2))
        {
            assertSame(dataFor(Material.SEA_LANTERN), onScreen(viewer).get(at), "not redrawn by another pair's refresh");
        }
    }

    /** Replacing it with a preview of a built ring clears the pattern first. */
    @Test
    void aBuiltRingPreviewReplacesAPatternAndClearsItFirst()
    {
        final Ring built = new Ring(300, Y, 300, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final RingPair pair = new RingPair("aaaa0002", WORLD_NAME, built, built);
        RingManager.addPair(pair, 4);
        RingPreviews.showPattern(viewer, RingPattern.ODD, probe);

        RingPreviews.show(viewer, pair, built);

        assertFalse(RingPreviews.of(viewer).isPattern());
        assertSame(real, onScreen(viewer).get(key(X - 3, Y, Z)), "the pattern's squares were given back");
    }

    /** A preview of a built ring has no guide to count. */
    @Test
    void aBuiltRingPreviewIsNotAPattern()
    {
        final Ring built = new Ring(300, Y, 300, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final RingPair pair = new RingPair("aaaa0002", WORLD_NAME, built, built);
        RingManager.addPair(pair, 4);

        RingPreviews.show(viewer, pair, built);

        assertFalse(RingPreviews.of(viewer).isPattern());
        assertEquals(0, RingPreviews.of(viewer).getGuideTotal());
    }
}
