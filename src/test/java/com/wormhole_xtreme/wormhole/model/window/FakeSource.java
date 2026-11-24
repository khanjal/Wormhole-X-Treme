package com.wormhole_xtreme.wormhole.model.window;

import java.util.List;

import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/** A window source with nothing special about it, for tests of the drawing that do not need a mirror's room. */
record FakeSource(String name, Block anchor, WindowShape shape, List<Spot> open, Place destination, int depth)
    implements WindowSource
{
}
