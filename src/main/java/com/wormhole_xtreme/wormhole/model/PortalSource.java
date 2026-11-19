package com.wormhole_xtreme.wormhole.model;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;

/**
 * One face of a nether portal as the window drawing sees it (#514): walked through like a gate's,
 * its purple sheet left as it is, drawn one at a time per viewer; its far side's chunks are held for its creatures, like a gate's,
 * only while {@code mirror-show-entities} is on.
 *
 * @param name
 *            what the sweep knows it by
 * @param anchor
 *            the block of the opening distances are measured from
 * @param shape
 *            the opening, onto where the portal went
 * @param open
 *            its portal blocks
 * @param destination
 *            the far side's arrival point, a block out of the far portal
 * @param depth
 *            how far past the opening it is drawn
 */
public record PortalSource(String name, Block anchor, WindowShape shape, List<Spot> open, Place destination,
    int depth) implements WindowSource
{
    /** The permission a player needs to be drawn a nether portal's view. */
    public static final String PERMISSION = "wormhole.view.netherportal";

    @Override
    public boolean walkThrough()
    {
        return true;
    }

    @Override
    public boolean shownTo(final Player player)
    {
        return player.hasPermission(PERMISSION);
    }

    @Override
    public boolean oneAtATime()
    {
        return true;
    }
}
