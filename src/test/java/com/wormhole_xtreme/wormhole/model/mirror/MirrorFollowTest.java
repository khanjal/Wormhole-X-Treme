package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;

/**
 * A banner that follows where its mirror opens first (#613).
 *
 * <p>Opt-in on purpose: an earlier mode re-stamped banners on approach and was removed because
 * "we shouldn't update the banner automatically". So what is pinned here is as much what does
 * not happen -- a mirror left at the default is never touched, a room with no capture yet leaves
 * the banner as it is -- as what does: the look comes from the followed room's stored capture,
 * its start's when it has one, and a new capture of that room stamps it again.
 */
class MirrorFollowTest
{
    @TempDir
    File dataFolder;

    @TempDir
    File presets;

    /** Captures by key, handed to MirrorFollow in place of the real store. */
    private final Map<String, Capture> stored = new HashMap<>();

    private World bannerWorld;
    private Banner banner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        MirrorManager.clear();
        MirrorPresetRegistry.load(presets);
        MirrorFollow.captures = stored::get;
        MirrorFollow.biomes = place -> "";

        banner = mock(Banner.class);
        final Block block = mock(Block.class);
        when(block.getState()).thenReturn(banner);
        bannerWorld = mock(World.class);
        when(bannerWorld.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorFollow.clear();
        MirrorManager.clear();
        PluginTestSupport.remove();
    }

    /** A mirror whose banner is in "world", its room in front of it. */
    private static QuantumMirror mirror(final String name, final int x, final String start, final boolean follows)
    {
        return new QuantumMirror(name, new BlockPlace("world", x, 66, 10), new Place("world", x + 0.5, 64, 11.5, 0f, 0f),
            null, start, 1, follows);
    }

    private static BlockData block(final Material material)
    {
        final BlockData data = mock(BlockData.class);
        when(data.getAsString()).thenReturn("minecraft:" + material.name().toLowerCase());
        when(data.getMaterial()).thenReturn(material);
        when(data.isOccluding()).thenReturn(true);
        return data;
    }

    /** A capture round a room, filled with one block up to and including a height. */
    private static Capture filled(final int roomX, final Material material, final int upTo)
    {
        return filling(roomX, material, upTo).build();
    }

    /** The same, not yet built, so a test can prune it as a real capture is. */
    private static Capture.Builder filling(final int roomX, final Material material, final int upTo)
    {
        final BlockData air = mock(BlockData.class);
        when(air.getAsString()).thenReturn("minecraft:air");
        when(air.getMaterial()).thenReturn(Material.AIR);
        final Capture.Builder builder = new Capture.Builder("world", true,
            new Capture.Box(roomX - 10, 58, 0, 21, 16, 24), air);
        final BlockData data = block(material);
        for (int x = roomX - 10; x <= (roomX + 10); x++)
        {
            for (int y = 58; y <= upTo; y++)
            {
                for (int z = 0; z < 24; z++)
                {
                    builder.put(x, y, z, data);
                }
            }
        }
        return builder;
    }

    private void store(final QuantumMirror room, final Capture capture)
    {
        stored.put(Captures.keyFor(room), capture);
    }

    private MirrorFollow.Result restamp(final QuantumMirror mirror)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            return MirrorFollow.restamp(mirror);
        }
    }

    private int captured(final QuantumMirror room, final boolean taken)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(bannerWorld);
            return MirrorFollow.captured(Captures.keyFor(room), taken);
        }
    }

    /** Sand underfoot and open air above reads as somewhere outdoors, mostly yellow. */
    @Test
    void aCaptureOfOpenGroundReadsAsOutdoorsInItsGroundsColour()
    {
        final QuantumMirror room = mirror("hub", 40, null, false);

        final MirrorView view = MirrorView.fromCapture(filled(40, Material.SAND, 63), room.destination(), "DESERT");

        assertEquals("DESERT", view.biome());
        assertEquals(DyeColor.YELLOW, view.dominant());
        assertFalse(view.enclosed(), "one sampled layer of four is ground; the rest is sky");
    }

    /** A room full of shelves reads as indoors, so the stamp makes the cloth their brown. */
    @Test
    void aCaptureOfAFullRoomReadsAsIndoors()
    {
        final QuantumMirror room = mirror("library", 40, null, false);

        final MirrorView view = MirrorView.fromCapture(filled(40, Material.BOOKSHELF, 73), room.destination(), "");

        assertEquals(DyeColor.BROWN, view.dominant());
        assertTrue(view.enclosed());
    }

    /**
     * Rock nobody at the opening could see still counts as solid.
     *
     * <p>A real capture drops what is buried, keeping no colour for it; counted as air, a mirror
     * cut into a hillside would read as open sky.
     */
    @Test
    void blocksTheCaptureDroppedAsUnseenStillCountAsSolid()
    {
        final QuantumMirror room = mirror("vault", 40, null, false);
        final Capture.Builder builder = filling(40, Material.STONE, 73);
        builder.prune();
        final Capture capture = builder.build();
        assertTrue(capture.isBuried(40, 64, 11), "the fixture's middle is buried, or this proves nothing");

        assertTrue(MirrorView.fromCapture(capture, room.destination(), "").enclosed());
    }

    /** With a start, the banner reads the start's room, not its own. */
    @Test
    void followsItsStartsRoomAndRemembersTheLook()
    {
        final QuantumMirror hub = mirror("hub", 40, null, false);
        final QuantumMirror museum = mirror("museum", 0, "hub", true);
        MirrorManager.add(hub);
        MirrorManager.add(museum);
        store(museum, filled(0, Material.SAND, 63));
        store(hub, filled(40, Material.BOOKSHELF, 73));

        final MirrorFollow.Result result = restamp(museum);

        assertEquals(MirrorFollow.Outcome.STAMPED, result.outcome());
        verify(banner).setBaseColor(DyeColor.BROWN);
        assertEquals(DyeColor.BROWN, MirrorManager.byName("museum").look().view().dominant(),
            "the look is written down beside the banner, as a hand stamp's is");
    }

    /** No start, or a start that has gone: its own room. */
    @Test
    void withoutAStartItFollowsItsOwnRoom()
    {
        final QuantumMirror museum = mirror("museum", 0, "gone", true);
        MirrorManager.add(museum);

        assertSame(museum, MirrorFollow.followed(museum));
    }

    /** No capture yet, or one still being read: the banner is left exactly as it is. */
    @Test
    void leavesTheBannerAloneWhileTheRoomHasNoCapture()
    {
        final QuantumMirror museum = mirror("museum", 0, null, true);
        MirrorManager.add(museum);

        assertEquals(MirrorFollow.Outcome.WAITING, restamp(museum).outcome());

        verify(banner, never()).setBaseColor(any());
        verify(banner, never()).update(true);
        assertNull(MirrorManager.byName("museum").look());
    }

    /**
     * A new capture of a room stamps every banner following it, and nothing else.
     *
     * <p>A mirror at the default must keep the look somebody gave it by hand, whatever is
     * captured: that is the whole reason this is opt-in.
     */
    @Test
    void aNewCaptureStampsOnlyTheBannersThatFollowThatRoom()
    {
        final QuantumMirror hub = mirror("hub", 40, null, false);
        MirrorManager.add(hub);
        MirrorManager.add(mirror("museum", 0, "hub", true));
        MirrorManager.add(mirror("archive", 80, "hub", false));
        MirrorManager.add(mirror("garden", 120, null, true));
        store(hub, filled(40, Material.SAND, 63));

        assertEquals(1, captured(hub, true), "only museum follows hub's room");
        assertEquals(List.of(DyeColor.YELLOW), MirrorManager.byName("museum").look().view().colours());
        assertNull(MirrorManager.byName("archive").look(), "archive does not follow");
        assertNull(MirrorManager.byName("garden").look(), "garden follows its own room, not hub's");
    }

    /**
     * A capture read off the disk stamps only a banner that asked while it was being read.
     *
     * <p>Every capture is read again after a restart or five minutes unwatched; re-stamping every
     * follower each time would write the same banners over and over.
     */
    @Test
    void aCaptureReadOffTheDiskStampsOnlyABannerStillWaitingForIt()
    {
        final QuantumMirror hub = mirror("hub", 40, null, false);
        MirrorManager.add(hub);
        MirrorManager.add(mirror("museum", 0, "hub", true));

        store(hub, filled(40, Material.SAND, 63));
        assertEquals(0, captured(hub, false), "nobody was waiting for it");

        stored.clear();
        assertEquals(MirrorFollow.Outcome.WAITING, restamp(MirrorManager.byName("museum")).outcome());
        store(hub, filled(40, Material.SAND, 63));

        assertEquals(1, captured(hub, false), "museum asked while it was being read");
        assertEquals(0, captured(hub, false), "and asked once");
    }

    /** A world not loaded here gives no biome; one whose chunk is not loaded gives its kind's. */
    @Test
    void aBiomeIsNeverLoadedForButTheWorldsKindStandsIn()
    {
        final World nether = mock(World.class);
        when(nether.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);
        final World end = mock(World.class);
        when(end.getEnvironment()).thenReturn(World.Environment.THE_END);
        final World normal = mock(World.class);
        when(normal.getEnvironment()).thenReturn(World.Environment.NORMAL);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("nether")).thenReturn(nether);
            bukkit.when(() -> Bukkit.getWorld("end")).thenReturn(end);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(normal);

            assertEquals("NETHER_WASTES", MirrorView.biomeOf(new Place("nether", 0, 64, 0, 0f, 0f)));
            assertEquals("THE_END", MirrorView.biomeOf(new Place("end", 0, 64, 0, 0f, 0f)));
            assertEquals("", MirrorView.biomeOf(new Place("world", 0, 64, 0, 0f, 0f)));
            assertEquals("", MirrorView.biomeOf(new Place("unloaded", 0, 64, 0, 0f, 0f)));
        }
        verify(nether, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    /** The setting is written only when on, so an older file reads as off and a new one round-trips. */
    @Test
    void theSettingIsSavedOnlyWhenOnAndAnOldFileReadsAsOff()
    {
        final Map<String, Object> plain = MirrorYamlManager.writeMirror(mirror("museum", 0, null, false));
        assertFalse(plain.containsKey("Follow"), "an ordinary mirror's entry reads as it did before");
        assertFalse(MirrorYamlManager.readMirror("museum", plain).follows());

        final Map<String, Object> following = MirrorYamlManager.writeMirror(mirror("museum", 0, null, true));
        assertTrue(MirrorYamlManager.readMirror("museum", following).follows());
    }
}
