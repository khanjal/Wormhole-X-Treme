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
        // Plane spacing of the rays cast through the opening; finer finds smaller holes.
        final double rayStep = Double.parseDouble(p.getProperty("ray.step", "0.04"));
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, radius);

        final World world = mock(World.class);
        when(world.getName()).thenReturn(here.worldName());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), org.mockito.ArgumentMatchers.any(org.bukkit.HeightMap.class)))
            .thenAnswer(invocation -> here.top(invocation.getArgument(0), invocation.getArgument(1)));
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
            .append("): '.' not drawn, 'a' air, '#' solid, '~' other\n");
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
                final String realName = here.nameAt(bx + (intoX * depth), y, bz + (intoZ * depth));
                out.append(here.isAir(bx + (intoX * depth), y, bz + (intoZ * depth)) ? '.'
                    : realName.startsWith("minecraft:water") ? 'w' : '#');
            }
            out.append("   far: ");
            final MirrorWindow shape = MirrorWindow.of(new MirrorBlock(here.worldName(), bx, by, bz),
                facing, false, destination);
            for (int depth = 1; depth <= radius + 2; depth++)
            {
                final MirrorWindow.Spot at = shape.farOf(bx + (intoX * depth), y, bz + (intoZ * depth));
                out.append(far.isAir(at.x(), at.y(), at.z()) ? '.'
                    : far.nameAt(at.x(), at.y(), at.z()).startsWith("minecraft:water") ? 'w' : '#');
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
        // Ground truth: rays from the eye through the opening, followed the way the client
        // shows them. A ray that meets a real block nobody drew, before a drawn solid and within
        // the depth, is a hole with the real world in it. Past the depth nothing is drawn.
        final java.util.Map<String, Integer> holes = new java.util.TreeMap<>();
        final java.util.Map<String, MirrorWindow.Spot> holeAt = new HashMap<>();
        int rays = 0;
        int holed = 0;
        final java.util.Map<String, Integer> shows = new java.util.TreeMap<>();
        final int faceAlong = (intoZProbe != 0) ? (bz + intoZProbe) : (bx + intoXProbe);
        final double facePlane = faceAlong + ((intoXProbe + intoZProbe) > 0 ? 0.0 : 1.0);
        for (double across = rayStep / 2.0; across < 1.0; across += rayStep)
        {
            for (double up = rayStep / 2.0; up < 2.0; up += rayStep)
            {
                final double px = (intoZProbe != 0) ? (bx + across) : facePlane;
                final double pz = (intoZProbe != 0) ? facePlane : (bz + across);
                final double py = (by - 1) + up;
                final double dx = px - ex;
                final double dy = py - ey;
                final double dz = pz - ez;
                final double len = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
                rays++;
                long last = Long.MIN_VALUE;
                String outcome = "escaped";
                MirrorWindow.Spot where = null;
                final StringBuilder trail = new StringBuilder();
                // A voxel walk from the point on the face plane: every block the ray passes
                // through, however briefly. A fixed step skipped the corner of a block a ray
                // clipped for an eighth of a block, and called what lay behind it a hole.
                final double[] o = { px, py, pz };
                final double[] d = { dx / len, dy / len, dz / len };
                final int[] c = { (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz) };
                final int[] step = new int[3];
                final double[] tMax = new double[3];
                final double[] tDelta = new double[3];
                for (int axis = 0; axis < 3; axis++)
                {
                    step[axis] = (d[axis] > 0) ? 1 : (d[axis] < 0) ? -1 : 0;
                    tDelta[axis] = (step[axis] == 0) ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d[axis]);
                    final double edge = (step[axis] > 0) ? (c[axis] + 1) : c[axis];
                    tMax[axis] = (step[axis] == 0) ? Double.POSITIVE_INFINITY : ((edge - o[axis]) / d[axis]);
                }
                final double furthest = radius + 2.0 + 40.0;
                for (double t = 0.0; t < furthest;)
                {
                    final int cx = c[0];
                    final int cy = c[1];
                    final int cz = c[2];
                    final int next = (tMax[0] < tMax[1]) ? ((tMax[0] < tMax[2]) ? 0 : 2) : ((tMax[1] < tMax[2]) ? 1 : 2);
                    t = tMax[next];
                    c[next] += step[next];
                    tMax[next] += tDelta[next];
                    final MirrorWindow.Spot spot = new MirrorWindow.Spot(cx, cy, cz);
                    final double ox = (cx + 0.5) - ex;
                    final double oy = (cy + 0.5) - ey;
                    final double oz = (cz + 0.5) - ez;
                    if (Math.sqrt((ox * ox) + (oy * oy) + (oz * oz)) >= radius)
                    {
                        outcome = "ok";
                        shows.merge("past the depth", 1, Integer::sum);
                        break;
                    }
                    final String as = drawn.get(spot);
                    trail.append(' ').append(cx).append(',').append(cy).append(',').append(cz).append('=')
                        .append((as == null) ? (here.isAir(cx, cy, cz) ? "real-air" : "REAL-" + here.nameAt(cx, cy, cz)) : as)
                        .append('/').append(verdicts.getOrDefault(spot, "-").replaceAll(", rect.*", ""));
                    if (as != null)
                    {
                        if (as.endsWith(":air") || as.endsWith(":barrier"))
                        {
                            continue;
                        }
                        outcome = "ok";
                        shows.merge("drawn " + as, 1, Integer::sum);
                        break;
                    }
                    if (here.isAir(cx, cy, cz))
                    {
                        continue;
                    }
                    final int layerOf = ((cx - bx) * intoXProbe) + ((cz - bz) * intoZProbe) - 1;
                    if (layerOf <= 0)
                    {
                        // The wall's own sill and jambs, seen at a grazing angle: real, and
                        // rightly so.
                        outcome = "ok";
                        shows.merge("real jamb " + here.nameAt(cx, cy, cz), 1, Integer::sum);
                        break;
                    }
                    outcome = "hole";
                    where = spot;
                    if (holed < 3)
                    {
                        out.append("hole ray through plane x ").append(px).append(" y ").append(py).append(" z ")
                            .append(pz).append(":").append(trail).append('\n');
                    }
                    break;
                }
                if (!"ok".equals(outcome))
                {
                    holed++;
                    final String why = (where == null) ? "escaped"
                        : ("real " + here.nameAt(where.x(), where.y(), where.z()) + " at " + where
                            + ", verdict: " + verdicts.getOrDefault(where, "never walked"));
                    holes.merge(why, 1, Integer::sum);
                    holeAt.putIfAbsent(why, where);
                }
            }
        }
        out.append(rays).append(" rays through the opening, ").append(holed).append(" holes\n");
        // For each hole judged hidden: every drawn solid in a nearer layer whose projection
        // overlaps the hole's, which is what the occlusion grid believed covered it.
        final java.util.regex.Pattern rectOf = java.util.regex.Pattern.compile(
            "(?:layer (-?\\d+), )?rect \\[(-?[\\d.]+), (-?[\\d.]+), (-?[\\d.]+), (-?[\\d.]+)\\]");
        for (final java.util.Map.Entry<String, MirrorWindow.Spot> hole : holeAt.entrySet())
        {
            final java.util.regex.Matcher mine = rectOf.matcher(hole.getKey());
            if ((hole.getValue() == null) || !mine.find())
            {
                continue;
            }
            final double[] r = { Double.parseDouble(mine.group(2)), Double.parseDouble(mine.group(3)),
                Double.parseDouble(mine.group(4)), Double.parseDouble(mine.group(5)) };
            final int layerOfHole = ((hole.getValue().x() - bx) * intoXProbe)
                + ((hole.getValue().z() - bz) * intoZProbe) - 1;
            out.append("coverers of ").append(hole.getValue()).append(" (layer ").append(layerOfHole)
                .append(", rect ").append(java.util.Arrays.toString(r)).append("):\n");
            for (final java.util.Map.Entry<MirrorWindow.Spot, String> v : verdicts.entrySet())
            {
                if (!v.getValue().startsWith("drawn"))
                {
                    continue;
                }
                final java.util.regex.Matcher m = rectOf.matcher(v.getValue());
                if (!m.find() || (m.group(1) == null) || (Integer.parseInt(m.group(1)) >= layerOfHole))
                {
                    continue;
                }
                final double[] o = { Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)),
                    Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5)) };
                if ((o[1] > r[0]) && (o[0] < r[1]) && (o[3] > r[2]) && (o[2] < r[3]))
                {
                    out.append("  ").append(v.getKey()).append(' ').append(v.getValue()).append('\n');
                }
            }
        }
        out.append("what the rays end on:\n");
        shows.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20)
            .forEach(e -> out.append("  ").append(e.getValue()).append(" x ").append(e.getKey()).append('\n'));
        holes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
            .forEach(e -> out.append("  ").append(e.getValue()).append(" x ").append(e.getKey()).append('\n'));
        out.append("window base ").append(shape.base()).append(", far ").append(shape.far())
            .append(", into ").append(shape.into()).append(", ahead ").append(shape.ahead()).append('\n');
        for (int depth = 1; depth <= 3; depth++)
        {
            for (int y = by - 1; y <= by; y++)
            {
                final MirrorWindow.Spot at = shape.farOf(bx + (intoXProbe * depth), y, bz + (intoZProbe * depth));
                out.append("  depth ").append(depth).append(" y ").append(y).append(" -> far ").append(at)
                    .append(' ').append(far.nameAt(at.x(), at.y(), at.z())).append('\n');
            }
        }
        // Top-down maps of the far side around the arrival point, one per height, so the
        // layout of what a window shows can be checked against the layout of what it should.
        final int ax = (int) Math.floor(destination.x());
        final int ay = (int) Math.floor(destination.y());
        final int az = (int) Math.floor(destination.z());
        out.append("far side top-down, x ").append(ax - 12).append("..").append(ax + 12)
            .append(" left to right, z ").append(az - 12).append(" (top) to ").append(az + 12)
            .append(": w water, p planks, b bookshelf, g glass, G glowstone, # other, . air, @ arrival\n");
        for (int y = ay - 2; y <= ay + 1; y++)
        {
            out.append("far y ").append(y).append('\n');
            for (int z = az - 12; z <= az + 12; z++)
            {
                out.append(String.format("%5d ", z));
                for (int x = ax - 12; x <= ax + 12; x++)
                {
                    out.append(((x == ax) && (z == az)) ? '@' : glyphOf(far.nameAt(x, y, z)));
                }
                out.append('\n');
            }
        }
        out.append("this side top-down, x ").append(bx - 12).append("..").append(bx + 12)
            .append(", z ").append(bz - 12).append(" (top) to ").append(bz + 12).append(", B banner\n");
        for (int y = by - 2; y <= by + 1; y++)
        {
            out.append("here y ").append(y).append('\n');
            for (int z = bz - 12; z <= bz + 12; z++)
            {
                out.append(String.format("%5d ", z));
                for (int x = bx - 12; x <= bx + 12; x++)
                {
                    out.append(((x == bx) && (z == bz)) ? 'B' : glyphOf(here.nameAt(x, y, z)));
                }
                out.append('\n');
            }
        }
        System.out.println(out);
        java.nio.file.Files.writeString(new File(dir, "replay-out.txt").toPath(), out.toString());
    }

    private static char glyphOf(final String name)
    {
        if ((name == null) || name.endsWith(":air") || name.endsWith(":cave_air"))
        {
            return '.';
        }
        final String bare = name.contains("[") ? name.substring(0, name.indexOf('[')) : name;
        return bare.endsWith("water") ? 'w' : name.contains("fence") ? 'f' : name.contains("planks") ? 'p' : name.contains("bookshelf") ? 'b'
            : name.contains("glass") ? 'g' : name.contains("glowstone") ? 'G' : '#';
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
