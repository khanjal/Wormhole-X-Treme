package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Something drawn as a window onto a far side: a mirror's banner, or an open gate (#522).
 *
 * <p>What {@link Windows} needs to know of the thing it draws, and no more. Each sweep offers one
 * afresh, so an answer need only hold for that sweep.
 */
public interface WindowSource
{
    /** @return what the sweep knows it by: a mirror's name, or a gate's under its prefix */
    String name();

    /** @return the block a viewer's distance to it is measured from */
    Block anchor();

    /** @return the opening, onto where it goes */
    WindowShape shape();

    /** @return the opening's cells a view is seen through */
    List<Spot> open();

    /** @return where it goes, facing the way a traveller leaves */
    Place destination();

    /** @return true for an opening walked into, which is never barred, punched through or drawn over */
    boolean walkThrough();

    /** @return how far past the opening its view is drawn */
    int depth();

    /** @return the mirror a punch at this window travels through, or null for none */
    default QuantumMirror punchTarget()
    {
        return null;
    }
}
