package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;

/**
 * A mirror hung on a wall, as the window drawing sees it (#522).
 *
 * @param mirror
 *            the mirror as shown: its room, or the room of the mirror chosen at it
 * @param anchor
 *            its loaded banner block
 * @param shape
 *            the opening behind the banner, onto the room shown
 * @param open
 *            the opening's cells with nothing solid in front of them
 * @param depth
 *            how far past the opening the view is drawn
 */
public record MirrorSource(QuantumMirror mirror, Block anchor, WindowShape shape, List<Spot> open, int depth)
    implements WindowSource
{
    /**
     * A mirror drawn to {@code mirror-view-depth}.
     *
     * @param mirror
     *            the mirror as shown
     * @param anchor
     *            its loaded banner block
     * @param shape
     *            the opening behind the banner
     * @param open
     *            the opening's cells with nothing solid in front of them
     */
    public MirrorSource(final QuantumMirror mirror, final Block anchor, final WindowShape shape, final List<Spot> open)
    {
        this(mirror, anchor, shape, open, ConfigManager.getMirrorViewDepth());
    }

    @Override
    public String name()
    {
        return mirror.name();
    }

    @Override
    public Place destination()
    {
        return mirror.destination();
    }

    @Override
    public boolean walkThrough()
    {
        return false;
    }

    @Override
    public QuantumMirror punchTarget()
    {
        return mirror;
    }
}
