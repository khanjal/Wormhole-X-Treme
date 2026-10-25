package com.wormhole_xtreme.wormhole.model.window;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * The fixed-view walk as it was before it was built a step at a time (#609), kept to check the
 * step-at-a-time build against: one walk, all at once, reading the real world through
 * {@link WindowSight}'s caches.
 */
final class LegacyFixedView
{
    /** What a walk gives past its limit, told apart by identity. */
    static final Map<Long, BlockData> TOO_MANY = Collections.unmodifiableMap(new HashMap<>());

    private LegacyFixedView()
    {
    }

    /** The view to a depth, tunnelled, or {@link #TOO_MANY} if it would hold more than {@code most}. */
    static Map<Long, BlockData> fixedTo(final WindowState window, final int depth, final long now, final int most)
    {
        final Map<Long, BlockData> view = window.capture.complete() ? byVolume(window, depth, now, most)
            : byKept(window, depth, now, most);
        if (view == TOO_MANY)
        {
            return TOO_MANY;
        }
        final Map<Long, BlockData> tunnelled = Windows.tunnelled(window, view, depth, now, most);
        return Windows.tooMany(tunnelled) ? TOO_MANY : tunnelled;
    }

    /** As the old {@code fixedView} cut it: a quarter shallower until it fits, empty past four. */
    static Map<Long, BlockData> cutToFit(final WindowState window, final int configured, final long now, final int most,
        final int[] depthOut)
    {
        int depth = configured;
        Map<Long, BlockData> view = fixedTo(window, depth, now, most);
        while ((view == TOO_MANY) && (depth > 4))
        {
            depth = Math.max(4, (depth * 3) / 4);
            view = fixedTo(window, depth, now, most);
        }
        depthOut[0] = depth;
        return (view == TOO_MANY) ? new HashMap<>() : view;
    }

    private static Map<Long, BlockData> byKept(final WindowState window, final int depth, final long now, final int most)
    {
        final WindowShape shape = window.shape;
        final Capture capture = window.capture;
        final World here = window.anchor().getWorld();
        final double[] centre = Windows.centreOf(shape);
        final int min = here.getMinHeight();
        final int max = here.getMaxHeight();
        final BlockData air = Bukkit.createBlockData(Material.AIR);
        final double reach = (double) depth * depth;
        final Map<Long, BlockData> view = new HashMap<>();
        final boolean[] over = { false };
        capture.forEachKept((fx, fy, fz, farAir) ->
        {
            if (over[0])
            {
                return;
            }
            final Spot at = shape.hereOf(fx, fy, fz);
            final int x = at.x();
            final int y = at.y();
            final int z = at.z();
            final int layer = ((x - shape.base().x()) * shape.into().x()) + ((z - shape.base().z()) * shape.into().z());
            if ((layer < 1) || (y < min) || (y >= max) || !window.showsFar(x, y, z))
            {
                return;
            }
            final double dx = (x + 0.5) - centre[0];
            final double dy = (y + 0.5) - centre[1];
            final double dz = (z + 0.5) - centre[2];
            if (((dx * dx) + (dy * dy) + (dz * dz)) >= reach)
            {
                return;
            }
            if (!farAir)
            {
                view.put(Cells.key(x, y, z), Windows.turned(window, capture.at(fx, fy, fz)));
            }
            else if (!WindowSight.reallyEmpty(here, x, y, z, now))
            {
                view.put(Cells.key(x, y, z), air);
            }
            if (view.size() > most)
            {
                over[0] = true;
            }
        });
        return over[0] ? TOO_MANY : view;
    }

    private static Map<Long, BlockData> byVolume(final WindowState window, final int depth, final long now, final int most)
    {
        final Volume volume = new Volume(window, depth, now, most);
        for (int layer = 1; layer < depth; layer++)
        {
            final double wide = Math.sqrt(volume.reach - ((double) layer * layer));
            for (int across = (int) Math.floor(volume.centreAcross - wide);
                across <= (int) Math.ceil(volume.centreAcross + wide); across++)
            {
                if (volume.overInColumn(layer, across, wide))
                {
                    return TOO_MANY;
                }
            }
        }
        return volume.view;
    }

    /** One walk through the volume, as the old {@code Windows.Volume} was. */
    private static final class Volume
    {
        private final WindowState window;
        private final World here;
        private final boolean alongX;
        private final int sign;
        private final int baseAlong;
        private final double[] centre;
        private final double centreAcross;
        private final int min;
        private final int max;
        private final BlockData air = Bukkit.createBlockData(Material.AIR);
        private final double reach;
        private final long now;
        private final int most;
        private final Map<Long, BlockData> view = new HashMap<>();

        Volume(final WindowState window, final int depth, final long now, final int most)
        {
            this.most = most;
            this.window = window;
            final WindowShape shape = window.shape;
            this.here = window.anchor().getWorld();
            this.alongX = shape.into().x() != 0;
            this.sign = alongX ? shape.into().x() : shape.into().z();
            this.baseAlong = alongX ? shape.base().x() : shape.base().z();
            this.centre = Windows.centreOf(shape);
            this.centreAcross = centre[alongX ? 2 : 0];
            this.min = here.getMinHeight();
            this.max = here.getMaxHeight();
            this.reach = (double) depth * depth;
            this.now = now;
        }

        boolean overInColumn(final int layer, final int across, final double wide)
        {
            final int along = baseAlong + (sign * layer);
            final double offAcross = (across + 0.5) - centreAcross;
            final int yTo = Math.min(max - 1, (int) Math.ceil(centre[1] + wide));
            for (int y = Math.max(min, (int) Math.floor(centre[1] - wide)); y <= yTo; y++)
            {
                final double offY = (y + 0.5) - centre[1];
                if (((((double) layer * layer) + (offAcross * offAcross) + (offY * offY)) < reach)
                    && block(alongX ? along : across, y, alongX ? across : along))
                {
                    return true;
                }
            }
            return false;
        }

        private boolean block(final int x, final int y, final int z)
        {
            final Capture capture = window.capture;
            final Spot at = window.shape.farOf(x, y, z);
            if (!window.showsFar(x, y, z) || !capture.contains(at.x(), at.y(), at.z()))
            {
                return false;
            }
            if (!capture.isAir(at.x(), at.y(), at.z()))
            {
                view.put(Cells.key(x, y, z), Windows.turned(window, capture.at(at.x(), at.y(), at.z())));
            }
            else if (!WindowSight.reallyEmpty(here, x, y, z, now))
            {
                view.put(Cells.key(x, y, z), air);
            }
            return view.size() > most;
        }
    }
}
