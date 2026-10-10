package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChunkTickets;

/**
 * The chunks held in front of a watched window's far side, so its creatures show on the first look
 * (#296).
 *
 * <p>A ticket left behind keeps its chunk loaded for as long as the server runs, so most of these are
 * about letting go: after the grace, at once on a reset or a world unloading, and only when no other
 * window still holds the same chunk. Through a loader seam, never MockBukkit.
 */
class FarChunkHoldsTest
{
    /** Arrival at chunk (6, -2), facing south: ahead is +z. */
    private static final Place SOUTH = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);

    private WormholeXTreme plugin;
    private World far;
    private MockedStatic<Bukkit> bukkit;
    private final List<FarChunkHolds.Area> asked = new ArrayList<>();
    private final Map<FarChunkHolds.Area, Consumer<Chunk>> waiting = new LinkedHashMap<>();
    private final Map<FarChunkHolds.Area, Chunk> chunks = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = PluginTestSupport.install();
        ConfigTestSupport.clear();
        FarChunkHolds.releaseAll();
        ChunkTickets.clear();
        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        when(far.getUID()).thenReturn(UUID.randomUUID());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(far);
        FarChunkHolds.loaderWith((world, x, z, loaded, failed) ->
        {
            final FarChunkHolds.Area area = new FarChunkHolds.Area(world.getName(), x, z);
            asked.add(area);
            waiting.put(area, loaded);
        });
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FarChunkHolds.releaseAll();
        FarChunkHolds.loaderWith(null);
        FarChunkHolds.asyncWith(null);
        Windows.clock = System::currentTimeMillis;
        ChunkTickets.clear();
        bukkit.close();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * The area is the arrival's chunk, two ahead and two either side: fifteen chunks, none behind,
     * for each of the four ways a far side faces.
     */
    @Test
    void theAreaIsInFrontOfTheArrivalForEachFacing()
    {
        assertEquals(square(6, -2, 4, -2, 8, 0), set(FarChunkHolds.ahead(SOUTH, 2)), "facing south: z -2 to 0, x 4 to 8");
        assertEquals(square(6, -2, 4, -4, 8, -2), set(FarChunkHolds.ahead(facing(180.0f), 2)), "facing north: z -4 to -2");
        assertEquals(square(6, -2, 4, -4, 6, 0), set(FarChunkHolds.ahead(facing(90.0f), 2)), "facing west: x 4 to 6, z -4 to 0");
        assertEquals(square(6, -2, 6, -4, 8, 0), set(FarChunkHolds.ahead(facing(-90.0f), 2)), "facing east: x 6 to 8");
        assertEquals(15, FarChunkHolds.ahead(SOUTH, 2).size(), "fifteen chunks at two, not twenty-five");
        assertEquals(new FarChunkHolds.Area("far", 6, -2), FarChunkHolds.ahead(SOUTH, 2).get(0), "the arrival's own first");
        assertTrue(FarChunkHolds.ahead(SOUTH, 0).isEmpty(), "nothing at radius 0");
    }

    /** The chunks are asked for two a tick, the arrival's own first, and held once loaded. */
    @Test
    void chunksAreAskedForTwoATickNearestFirstAndHeldOnceLoaded()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        assertTrue(asked.isEmpty(), "nothing asked for until the pacing runs");

        FarChunkHolds.step();
        assertEquals(2, asked.size(), "two a tick");
        assertEquals(new FarChunkHolds.Area("far", 6, -2), asked.get(0), "the arrival's chunk first");
        for (int tick = 0; tick < 7; tick++)
        {
            FarChunkHolds.step();
        }
        assertEquals(15, asked.size(), "all fifteen in eight ticks");

        loadAll();

        assertEquals(15, FarChunkHolds.heldCount());
        verify(chunks.get(new FarChunkHolds.Area("far", 8, 0))).addPluginChunkTicket(plugin);
        assertArrayEquals(new int[] { 15, 15 }, FarChunkHolds.heldFor("museum"));
    }

    /** A chunk behind the arrival, or outside the radius, is never asked for. */
    @Test
    void aChunkBehindTheArrivalOrOutsideTheRadiusIsNeverTouched()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();

        assertFalse(asked.contains(new FarChunkHolds.Area("far", 6, -3)), "behind the arrival");
        assertFalse(asked.contains(new FarChunkHolds.Area("far", 9, -1)), "past the side");
        assertFalse(asked.contains(new FarChunkHolds.Area("far", 6, 1)), "past the front");
    }

    /**
     * After the last viewer goes, the area is kept for the grace and let go after it.
     *
     * <p>A viewer stepping in and out of a mirror's range would otherwise load and unload fifteen
     * chunks each time.
     */
    @Test
    void anAreaIsKeptForTheGraceAfterItsLastViewerAndLetGoAfter()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        loadAll();
        final Chunk arrival = chunks.get(new FarChunkHolds.Area("far", 6, -2));

        FarChunkHolds.settle(Map.of(), 1_000L);
        FarChunkHolds.settle(Map.of(), 1_000L + FarChunkHolds.GRACE_MILLIS - 1);
        verify(arrival, never()).removePluginChunkTicket(plugin);
        assertEquals(15, FarChunkHolds.heldCount(), "kept through the grace");

        FarChunkHolds.settle(Map.of(), 1_000L + FarChunkHolds.GRACE_MILLIS);

        verify(arrival).removePluginChunkTicket(plugin);
        assertEquals(0, FarChunkHolds.heldCount(), "let go after it");
    }

    /** Two windows onto the same place share one ticket a chunk, which goes when the second lets go. */
    @Test
    void twoWindowsOverOneAreaShareItsTicketsUntilTheLastLetsGo()
    {
        final Map<String, List<FarChunkHolds.Area>> both = new LinkedHashMap<>();
        both.put("museum", FarChunkHolds.ahead(SOUTH, 2));
        both.put("gate:Abydos", FarChunkHolds.ahead(SOUTH, 2));
        FarChunkHolds.settle(both, 0L);
        stepAll();
        loadAll();
        final Chunk arrival = chunks.get(new FarChunkHolds.Area("far", 6, -2));
        verify(arrival, times(1)).addPluginChunkTicket(plugin);
        assertEquals(15, asked.size(), "each chunk asked for once");

        FarChunkHolds.settle(watching("gate:Abydos", SOUTH), 0L);
        FarChunkHolds.settle(watching("gate:Abydos", SOUTH), FarChunkHolds.GRACE_MILLIS * 2);
        verify(arrival, never()).removePluginChunkTicket(plugin);

        FarChunkHolds.settle(Map.of(), FarChunkHolds.GRACE_MILLIS * 3);
        FarChunkHolds.settle(Map.of(), FarChunkHolds.GRACE_MILLIS * 5);
        verify(arrival).removePluginChunkTicket(plugin);
    }

    /** A lower radius lets go of the chunks it no longer covers at once. */
    @Test
    void aLowerRadiusLetsGoOfTheOuterChunksAtOnce()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        loadAll();

        FarChunkHolds.settle(Map.of("museum", FarChunkHolds.ahead(SOUTH, 1)), 100L);

        verify(chunks.get(new FarChunkHolds.Area("far", 8, 0))).removePluginChunkTicket(plugin);
        verify(chunks.get(new FarChunkHolds.Area("far", 7, -1)), never()).removePluginChunkTicket(plugin);
        assertEquals(6, FarChunkHolds.heldCount(), "three across, two deep");
    }

    /** A chunk loaded after its window was let go is not held; one never generated is not asked again. */
    @Test
    void aLateChunkIsNotHeldAndAnUnmadeOneIsNotAskedAgain()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        final FarChunkHolds.Area unmade = new FarChunkHolds.Area("far", 6, -2);
        waiting.remove(unmade).accept(null);
        FarChunkHolds.releaseAll();
        loadAll();
        assertEquals(0, FarChunkHolds.heldCount(), "nothing arriving after the release is held");

        asked.clear();
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        FarChunkHolds.loaded(unmade, null);
        FarChunkHolds.settle(watching("museum", SOUTH), 1L);
        stepAll();
        assertEquals(1, asked.stream().filter(unmade::equals).count(), "an unmade chunk is asked for once while watched");
    }

    /** A reset and a world unloading let go of everything held at once. */
    @Test
    void aResetOrAWorldUnloadingLetsGoOfEverythingAtOnce()
    {
        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        loadAll();
        FarChunkHolds.forgetWorld("far");
        verify(chunks.get(new FarChunkHolds.Area("far", 6, -2))).removePluginChunkTicket(plugin);
        assertEquals(0, FarChunkHolds.heldCount());
        FarChunkHolds.settle(Map.of(), 0L);
        stepAll();
        assertEquals(15, asked.size(), "and not asked for again for a window no longer watched");

        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();
        loadAll();
        FarChunkHolds.releaseAll();
        // Once by the world unloading, once by the reset.
        verify(chunks.get(new FarChunkHolds.Area("far", 7, 0)), times(2)).removePluginChunkTicket(plugin);
        assertEquals(0, FarChunkHolds.heldCount());
    }

    /** No more than the cap is ever held, the nearest windows' first, and it is said once. */
    @Test
    void noMoreThanTheCapIsHeldTheNearestWindowsFirst()
    {
        final Map<String, List<FarChunkHolds.Area>> many = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++)
        {
            many.put("window" + i, FarChunkHolds.ahead(new Place("far", 100.5 + (i * 200), 70.0, -20.5, 0.0f, 0.0f), 2));
        }
        FarChunkHolds.settle(many, 0L);
        FarChunkHolds.settle(many, 1L);
        stepAll();

        assertEquals(FarChunkHolds.MOST_HELD, asked.size(), "the cap, of 450 wanted");
        assertTrue(asked.contains(new FarChunkHolds.Area("far", 6, -2)), "the nearest window's area is among them");
        assertFalse(asked.contains(new FarChunkHolds.Area("far", ((int) Math.floor(100.5 + (29 * 200))) >> 4, -2)),
            "the furthest window's is not");
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), anyString());
    }

    /** The radius is 2 when the setting is missing, as on an upgraded config.yml, and read from 0 to 4. */
    @Test
    void theRadiusDefaultsToTwoAndIsReadFromZeroToFour()
    {
        assertEquals(2, ConfigManager.getMirrorEntityLoadRadius(), "missing from config.yml");
        ConfigTestSupport.set(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 9);
        assertEquals(4, ConfigManager.getMirrorEntityLoadRadius(), "nine is read as four");
        ConfigTestSupport.set(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, -1);
        assertEquals(0, ConfigManager.getMirrorEntityLoadRadius(), "below nothing is nothing");
        ConfigTestSupport.set(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 3);
        assertEquals(3, ConfigManager.getMirrorEntityLoadRadius());
    }

    /** Paper's world, as far as this needs it: the compile path has no asynchronous load. */
    interface PaperWorld extends World
    {
        CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate);
    }

    /**
     * A load that throws, says it failed, or never answers is given up on: nothing is held, it is not
     * asked again until the back-off is over, and it is said once.
     *
     * <p>Left on its way, a chunk was never asked for again while its window was watched.
     */
    @Test
    void aLoadThatFailsOrNeverAnswersIsGivenUpOnAndAskedAgainOnlyAfterABackOff()
    {
        final long[] now = { 1_000L };
        Windows.clock = () -> now[0];
        final Map<FarChunkHolds.Area, Consumer<Throwable>> failing = new LinkedHashMap<>();
        FarChunkHolds.loaderWith((world, x, z, loaded, failed) ->
        {
            final FarChunkHolds.Area area = new FarChunkHolds.Area(world.getName(), x, z);
            asked.add(area);
            if ((x == 6) && (z == -2))
            {
                throw new IllegalStateException("the region file is unreadable");
            }
            if ((x == 6) && (z == -1))
            {
                failing.put(area, failed);
            }
            // Every other one never answers.
        });
        FarChunkHolds.settle(watching("museum", SOUTH), now[0]);
        stepAll();
        failing.values().forEach(failed -> failed.accept(new IllegalStateException("load cancelled")));

        assertEquals(13, FarChunkHolds.loadingCount(), "the thrown and the failed are no longer on their way");
        assertEquals(0, FarChunkHolds.heldCount());
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), anyString(), any(Throwable.class));

        now[0] += FarChunkHolds.LOAD_TIMEOUT_MILLIS;
        FarChunkHolds.settle(watching("museum", SOUTH), now[0]);
        assertEquals(0, FarChunkHolds.loadingCount(), "the ones that never answered are given up on");

        asked.clear();
        now[0] += 1_000L;
        FarChunkHolds.settle(watching("museum", SOUTH), now[0]);
        stepAll();
        assertTrue(asked.isEmpty(), "not asked again during the back-off");

        now[0] += FarChunkHolds.RETRY_MILLIS;
        FarChunkHolds.settle(watching("museum", SOUTH), now[0]);
        stepAll();
        assertEquals(15, asked.size(), "asked again once it is over");
    }

    /**
     * Through the server's own loader on Spigot: a chunk never generated is never loaded or ticketed,
     * so nothing is generated for this; a generated one is loaded and held.
     */
    @Test
    void onSpigotAChunkNeverGeneratedIsNeverLoadedAndAGeneratedOneIsHeld()
    {
        FarChunkHolds.loaderWith(null);
        FarChunkHolds.asyncWith(null);
        assertFalse(FarChunkHolds.loadsAsync(), "the compile path is Spigot's, with no asynchronous load");
        final Chunk arrival = chunk(new FarChunkHolds.Area("far", 6, -2));
        when(far.isChunkGenerated(6, -2)).thenReturn(true);
        when(far.getChunkAt(6, -2)).thenReturn(arrival);

        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();

        verify(far).isChunkGenerated(8, 0);
        verify(far, never()).getChunkAt(8, 0);
        verify(far, never()).addPluginChunkTicket(eq(8), eq(0), any(Plugin.class));
        verify(arrival).addPluginChunkTicket(plugin);
        assertEquals(1, FarChunkHolds.heldCount(), "the one generated chunk, and none of the fourteen not");
    }

    /** A load that throws on Spigot is caught and given up on, not thrown out of the pacing task. */
    @Test
    void aSpigotLoadThatThrowsIsCaught()
    {
        FarChunkHolds.loaderWith(null);
        FarChunkHolds.asyncWith(null);
        when(far.isChunkGenerated(anyInt(), anyInt())).thenThrow(new IllegalStateException("region file"));

        FarChunkHolds.settle(watching("museum", SOUTH), 0L);
        stepAll();

        assertEquals(0, FarChunkHolds.loadingCount(), "nothing left on its way");
        assertEquals(0, FarChunkHolds.heldCount());
    }

    /** On Paper the chunk is asked for asynchronously and without generating: generate is false. */
    @Test
    void onPaperTheLoadIsAsynchronousAndNeverGenerates() throws NoSuchMethodException
    {
        final PaperWorld paper = mock(PaperWorld.class);
        when(paper.getName()).thenReturn("far");
        when(paper.getUID()).thenReturn(UUID.randomUUID());
        bukkit.when(() -> Bukkit.getWorld("far")).thenReturn(paper);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        final Chunk arrival = mock(Chunk.class);
        when(arrival.getWorld()).thenReturn(paper);
        when(arrival.getX()).thenReturn(6);
        when(arrival.getZ()).thenReturn(-2);
        when(paper.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(null));
        when(paper.getChunkAtAsync(6, -2, false)).thenReturn(CompletableFuture.completedFuture(arrival));
        final CompletableFuture<Chunk> broken = new CompletableFuture<>();
        broken.completeExceptionally(new IllegalStateException("load failed"));
        when(paper.getChunkAtAsync(6, -1, false)).thenReturn(broken);
        FarChunkHolds.loaderWith(null);
        FarChunkHolds.asyncWith(PaperWorld.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class));
        try
        {
            FarChunkHolds.settle(watching("museum", SOUTH), 0L);
            stepAll();

            verify(paper).getChunkAtAsync(6, -2, false);
            verify(paper, never()).getChunkAtAsync(anyInt(), anyInt(), eq(true));
            verify(paper, never()).getChunkAt(anyInt(), anyInt());
            verify(arrival).addPluginChunkTicket(plugin);
            assertEquals(1, FarChunkHolds.heldCount(), "the one there, none of those never generated, not the one that failed");
            assertEquals(0, FarChunkHolds.loadingCount(), "the failed one is not left on its way");
        }
        finally
        {
            FarChunkHolds.asyncWith(null);
        }
    }

    /**
     * Through the server's own loader at radius 0 nothing is asked of the world at all, where at
     * radius 1 the same world is asked.
     */
    @Test
    void atRadiusZeroTheWorldIsNeverAskedThroughTheRealLoader()
    {
        FarChunkHolds.loaderWith(null);
        FarChunkHolds.asyncWith(null);

        FarChunkHolds.settle(Map.of("museum", FarChunkHolds.ahead(SOUTH, 0)), 0L);
        stepAll();
        verify(far, never()).isChunkGenerated(anyInt(), anyInt());
        verify(far, never()).getChunkAt(anyInt(), anyInt());
        verify(far, never()).addPluginChunkTicket(anyInt(), anyInt(), any(Plugin.class));

        FarChunkHolds.settle(Map.of("museum", FarChunkHolds.ahead(SOUTH, 1)), 0L);
        stepAll();
        verify(far).isChunkGenerated(6, -2);
    }

    /** A watched window onto a world that is not loaded asks for nothing and starts no pacing task. */
    @Test
    void aWindowOntoAWorldNotLoadedStartsNoPacing() throws Exception
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(5);
        PluginTestSupport.scheduler(scheduler);
        try
        {
            final Place elsewhere = new Place("elsewhere", 100.5, 70.0, -20.5, 0.0f, 0.0f);
            for (int sweep = 0; sweep < 5; sweep++)
            {
                FarChunkHolds.settle(Map.of("gate:Abydos", FarChunkHolds.ahead(elsewhere, 2)), sweep * 1_000L);
            }
            verify(scheduler, never()).scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());

            FarChunkHolds.settle(watching("museum", SOUTH), 6_000L);
            verify(scheduler, times(1)).scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /** The pacing task starts once, and stops when nothing is left to ask for. */
    @Test
    void thePacingTaskStartsOnceAndStopsWhenNothingIsQueued() throws Exception
    {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Runnable[] task = new Runnable[1];
        when(scheduler.scheduleSyncRepeatingTask(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call ->
        {
            task[0] = call.getArgument(1);
            return 5;
        });
        PluginTestSupport.scheduler(scheduler);
        try
        {
            FarChunkHolds.settle(watching("museum", SOUTH), 0L);
            FarChunkHolds.settle(watching("museum", SOUTH), 1L);
            verify(scheduler, times(1)).scheduleSyncRepeatingTask(eq(plugin), any(Runnable.class), eq(1L), eq(1L));
            assertTrue(FarChunkHolds.pacing());

            for (int tick = 0; tick < 8; tick++)
            {
                task[0].run();
            }

            assertEquals(15, asked.size(), "fifteen asked for, two a tick");
            verify(scheduler).cancelTask(5);
            assertFalse(FarChunkHolds.pacing());
        }
        finally
        {
            PluginTestSupport.scheduler(null);
        }
    }

    /**
     * When the cap bites, the windows already held keep their chunks however the viewers move; one
     * that stops being watched gives its chunks up after the grace to the next window waiting.
     *
     * <p>Ordered only by nearness, a viewer walking between two windows swapped which one was cut on
     * every sweep, loading and unloading fifteen chunks each time.
     */
    @Test
    void underTheCapHeldWindowsKeepTheirChunksAndOneLetGoGivesWayToTheNext()
    {
        final Map<String, List<FarChunkHolds.Area>> many = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++)
        {
            many.put("window" + i, FarChunkHolds.ahead(new Place("far", 100.5 + (i * 200), 70.0, -20.5, 0.0f, 0.0f), 2));
        }
        FarChunkHolds.settle(many, 0L);
        stepAll();
        loadAll();
        assertEquals(FarChunkHolds.MOST_HELD, FarChunkHolds.heldCount(), "the cap, and not one chunk past it");
        final FarChunkHolds.Area last = many.get("window29").get(0);
        assertFalse(chunks.containsKey(last), "the furthest window's chunks were never loaded");

        final Map<String, List<FarChunkHolds.Area>> reversed = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--)
        {
            reversed.put("window" + i, many.get("window" + i));
        }
        FarChunkHolds.settle(reversed, 1L);
        stepAll();
        for (final Chunk held : new ArrayList<>(chunks.values()))
        {
            verify(held, never()).removePluginChunkTicket(plugin);
        }
        assertFalse(asked.contains(last), "window 29, nearest now, does not displace those already held");

        reversed.remove("window0");
        FarChunkHolds.settle(reversed, 2L);
        FarChunkHolds.settle(reversed, 2L + FarChunkHolds.GRACE_MILLIS);
        stepAll();
        verify(chunks.get(many.get("window0").get(0))).removePluginChunkTicket(plugin);
        assertTrue(asked.contains(last), "its place goes to a window that was waiting");
    }

    /** A radius that is not a whole number in config.yml reads as the default 2, said once. */
    @Test
    void aRadiusThatIsNotAWholeNumberReadsAsTwo()
    {
        ConfigTestSupport.set(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, "two");
        assertEquals(2, ConfigManager.getMirrorEntityLoadRadius(), "not a number: the default");
        assertEquals(2, ConfigManager.getMirrorEntityLoadRadius());
        ConfigTestSupport.set(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, " 3 ");
        assertEquals(3, ConfigManager.getMirrorEntityLoadRadius(), "a number written as text is still read");
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), anyString());
    }

    private void stepAll()
    {
        for (int tick = 0; tick < 300; tick++)
        {
            FarChunkHolds.step();
        }
    }

    private void loadAll()
    {
        for (final Map.Entry<FarChunkHolds.Area, Consumer<Chunk>> one : new ArrayList<>(waiting.entrySet()))
        {
            waiting.remove(one.getKey());
            one.getValue().accept(chunk(one.getKey()));
        }
    }

    private Chunk chunk(final FarChunkHolds.Area area)
    {
        return chunks.computeIfAbsent(area, at ->
        {
            final Chunk chunk = mock(Chunk.class);
            when(chunk.getWorld()).thenReturn(far);
            when(chunk.getX()).thenReturn(at.x());
            when(chunk.getZ()).thenReturn(at.z());
            return chunk;
        });
    }

    private static Map<String, List<FarChunkHolds.Area>> watching(final String name, final Place destination)
    {
        return Map.of(name, FarChunkHolds.ahead(destination, 2));
    }

    private static Place facing(final float yaw)
    {
        return new Place("far", 100.5, 70.0, -20.5, yaw, 0.0f);
    }

    private static Set<FarChunkHolds.Area> set(final List<FarChunkHolds.Area> areas)
    {
        return Set.copyOf(areas);
    }

    /** Every chunk from one corner to the other, inclusive; the first two numbers are unused labels for the arrival. */
    private static Set<FarChunkHolds.Area> square(final int arrivalX, final int arrivalZ, final int x1, final int z1,
        final int x2, final int z2)
    {
        final Set<FarChunkHolds.Area> square = new java.util.HashSet<>();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
        {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
            {
                square.add(new FarChunkHolds.Area("far", x, z));
            }
        }
        assertTrue(square.contains(new FarChunkHolds.Area("far", arrivalX, arrivalZ)), "the arrival is in the area");
        return square;
    }
}
