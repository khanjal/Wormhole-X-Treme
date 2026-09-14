package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

/**
 * Replays a window from two captures taken on a real server: the far side's own, and this side
 * from {@code mirror debug save}.
 *
 * <p>Skipped unless {@code -Dwormhole.replay=<dir>} names a folder holding {@code here.view},
 * {@code far.view} and {@code replay.properties} (banner x,y,z and facing; destination x,y,z and
 * yaw; eye x,y,z). It draws the view for a player at that eye and prints a slice through the
 * middle of it, so a view that shows the wrong thing in game can be read block by block here.
 */
class MirrorReplayTest
{
    @TempDir
    File dataFolder;

    private final Map<String, BlockData> byName = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        PluginTestSupport.scheduler(null);
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorProximity.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        MirrorProximity.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void replayARecordedView() throws Exception
    {
        final String dir = System.getProperty("wormhole.replay", "");
        assumeTrue(!dir.isEmpty() && new File(dir, "here.view").isFile()
            && new File(dir, "far.view").isFile() && new File(dir, "replay.properties").isFile(),
            "no recording to replay");
        final java.util.Properties p = new java.util.Properties();
        try (java.io.FileInputStream in = new java.io.FileInputStream(new File(dir, "replay.properties")))
        {
            p.load(in);
        }
        final MirrorCapture here = MirrorCapture.load(new File(dir, "here.view"));
        final MirrorCapture far = MirrorCapture.load(new File(dir, "far.view"));
        final int bx = Integer.parseInt(p.getProperty("banner.x"));
        final int by = Integer.parseInt(p.getProperty("banner.y"));
        final int bz = Integer.parseInt(p.getProperty("banner.z"));
        final BlockFace facing = BlockFace.valueOf(p.getProperty("banner.facing"));
        final MirrorPoint destination = new MirrorPoint(far.worldName(),
            Double.parseDouble(p.getProperty("dest.x")), Double.parseDouble(p.getProperty("dest.y")),
            Double.parseDouble(p.getProperty("dest.z")), Float.parseFloat(p.getProperty("dest.yaw")), 0f);
        final double ex = Double.parseDouble(p.getProperty("eye.x"));
        final double ey = Double.parseDouble(p.getProperty("eye.y"));
        final double ez = Double.parseDouble(p.getProperty("eye.z"));
        final int radius = Integer.parseInt(p.getProperty("radius", "16"));
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, radius);

        final World world = mock(World.class);
        when(world.getName()).thenReturn(here.worldName());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockOf(world, here, invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), bx, by, bz, facing));
        MirrorCaptures.install(destination, far);
        MirrorManager.add(new QuantumMirror("replay", new MirrorBlock(here.worldName(), bx, by, bz),
            destination));

        final Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.getWorld()).thenReturn(world);
        when(viewer.getEyeHeight()).thenReturn(1.62);
        when(viewer.getLocation()).thenReturn(new Location(world, ex, ey - 1.62, ez));
        when(viewer.getEyeLocation()).thenReturn(new Location(world, ex, ey, ez));
        when(world.getPlayers()).thenReturn(List.of(viewer));

        final int intoXProbe = -facing.getModX();
        final int intoZProbe = -facing.getModZ();
        final Map<MirrorWindow.Spot, String> verdicts = new HashMap<>();
        MirrorWindows.probe = (x, y, z, verdict) -> verdicts.put(new MirrorWindow.Spot(x, y, z), verdict);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(here.worldName())).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData(org.mockito.ArgumentMatchers.any(Material.class)))
                .thenAnswer(invocation -> data("minecraft:"
                    + ((Material) invocation.getArgument(0)).name().toLowerCase(java.util.Locale.ROOT)));
            bukkit.when(() -> Bukkit.createBlockData(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> data(invocation.getArgument(0)));
            bukkit.when(() -> Bukkit.getPlayer(viewer.getUniqueId())).thenReturn(viewer);
            MirrorProximity.tick();
        }

        final Map<MirrorWindow.Spot, String> drawn = new HashMap<>();
        for (final Invocation call : mockingDetails(viewer).getInvocations())
        {
            if ("sendBlockChanges".equals(call.getMethod().getName()))
            {
                for (final BlockState state : (Collection<BlockState>) call.getArgument(0))
                {
                    final String as = mockingDetails(state).getInvocations().stream()
                        .filter(c -> "setBlockData".equals(c.getMethod().getName()))
                        .map(c -> ((BlockData) c.getArgument(0)).getAsString()).findFirst().orElse("truth");
                    drawn.put(new MirrorWindow.Spot(state.getX(), state.getY(), state.getZ()), as);
                }
            }
        }
        final StringBuilder out = new StringBuilder();
        MirrorWindows.describe(viewer).forEach(line -> out.append(line).append('\n'));
        out.append(drawn.size()).append(" blocks in the batch\n");
        // A slice through the opening's middle: the axis the banner faces along, by height.
        final int intoX = -facing.getModX();
        final int intoZ = -facing.getModZ();
        out.append("slice through the middle (depth behind the face 1..").append(radius + 2)
            .append(" left to right; rows y ").append(by + 8).append(" down to ").append(by - 8)
            .append("): '.' not drawn, 'a' air, '#' solid, 's' sky, '~' other\n");
        for (int y = by + 8; y >= by - 8; y--)
        {
            out.append(String.format("%4d ", y));
            for (int depth = 1; depth <= radius + 2; depth++)
            {
                final int x = bx + (intoX * depth);
                final int z = bz + (intoZ * depth);
                final String as = drawn.get(new MirrorWindow.Spot(x, y, z));
                out.append(glyph(as));
            }
            out.append("   real: ");
            for (int depth = 1; depth <= radius + 2; depth++)
            {
                out.append(here.isAir(bx + (intoX * depth), y, bz + (intoZ * depth)) ? '.' : '#');
            }
            out.append("   far: ");
            final MirrorWindow shape = MirrorWindow.of(new MirrorBlock(here.worldName(), bx, by, bz),
                facing, false, destination);
            for (int depth = 1; depth <= radius + 2; depth++)
            {
                final MirrorWindow.Spot at = shape.farOf(bx + (intoX * depth), y, bz + (intoZ * depth));
                out.append(far.isAir(at.x(), at.y(), at.z()) ? '.' : '#');
            }
            out.append('\n');
        }
        MirrorWindows.probe = null;
        // One grid per layer behind the opening: across (left to right as the viewer sees it)
        // by height. Verdict glyph, then the far side's content, then this world's.
        final int rightX = -intoZProbe;
        final int rightZ = intoXProbe;
        final MirrorWindow shape = MirrorWindow.of(new MirrorBlock(here.worldName(), bx, by, bz),
            facing, false, destination);
        out.append("per layer, across -4..4 (viewer's left to right) by y ").append(by + 4)
            .append(" down to ").append(by - 4)
            .append(": d drawn, a air over air, h hidden, n not through opening, c not covered, "
                + "b beyond, . never walked | far # solid . air | here # solid . air\n");
        for (int layer = 1; layer <= 9; layer++)
        {
            out.append("layer ").append(layer).append('\n');
            for (int y = by + 4; y >= by - 4; y--)
            {
                final StringBuilder v = new StringBuilder();
                final StringBuilder f = new StringBuilder();
                final StringBuilder h = new StringBuilder();
                for (int across = -4; across <= 4; across++)
                {
                    final int x = bx + (intoXProbe * (layer + 1)) + (rightX * across);
                    final int z = bz + (intoZProbe * (layer + 1)) + (rightZ * across);
                    final String verdict = verdicts.get(new MirrorWindow.Spot(x, y, z));
                    v.append((verdict == null) ? '.' : verdict.startsWith("drawn") ? 'd'
                        : verdict.startsWith("air") ? 'a' : verdict.startsWith("hidden") ? 'h'
                        : verdict.startsWith("not through") ? 'n' : verdict.startsWith("not covered") ? 'c'
                        : verdict.startsWith("beyond") ? 'b' : '?');
                    final MirrorWindow.Spot at = shape.farOf(x, y, z);
                    f.append(far.isAir(at.x(), at.y(), at.z()) ? '.' : '#');
                    h.append(here.isAir(x, y, z) ? '.' : '#');
                }
                out.append(String.format("  y %d  %s | far %s | here %s%n", y, v, f, h));
            }
        }
        System.out.println(out);
        java.nio.file.Files.writeString(new File(dir, "replay-out.txt").toPath(), out.toString());
    }

    private static char glyph(final String as)
    {
        if (as == null)
        {
            return '.';
        }
        if (as.endsWith(":air"))
        {
            return 'a';
        }
        if (as.contains("concrete"))
        {
            return 's';
        }
        if (as.endsWith(":barrier"))
        {
            return 'B';
        }
        return '#';
    }

    private BlockData data(final String name)
    {
        return byName.computeIfAbsent(name, n ->
        {
            final BlockData d = mock(BlockData.class);
            when(d.getAsString()).thenReturn(n);
            final String bare = n.contains("[") ? n.substring(0, n.indexOf('[')) : n;
            final Material material = Material.matchMaterial(bare);
            when(d.getMaterial()).thenReturn(material);
            when(d.isOccluding()).thenReturn((material != null) && material.isOccluding());
            return d;
        });
    }

    private Block blockOf(final World world, final MirrorCapture here, final int x, final int y,
        final int z, final int bx, final int by, final int bz, final BlockFace facing)
    {
        final Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        final boolean air = here.isAir(x, y, z);
        final String name = air ? "minecraft:air" : here.nameAt(x, y, z);
        final BlockData d = data(name);
        // Asked before the stubbing begins: a mock called inside thenReturn is an unfinished stub.
        final boolean passable = air || !d.isOccluding();
        when(block.isEmpty()).thenReturn(air);
        when(block.isPassable()).thenReturn(passable);
        if ((x == bx) && (y == by) && (z == bz))
        {
            final Directional banner = mock(Directional.class);
            when(banner.getFacing()).thenReturn(facing);
            when(block.getBlockData()).thenReturn(banner);
            when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
            when(block.getState()).thenAnswer(i -> stateAt(Banner.class, x, y, z));
        }
        else
        {
            when(block.getBlockData()).thenReturn(d);
            when(block.getState()).thenAnswer(i -> stateAt(BlockState.class, x, y, z));
        }
        return block;
    }

    private static <T extends BlockState> T stateAt(final Class<T> type, final int x, final int y,
        final int z)
    {
        final T state = mock(type);
        when(state.getX()).thenReturn(x);
        when(state.getY()).thenReturn(y);
        when(state.getZ()).thenReturn(z);
        return state;
    }
}
