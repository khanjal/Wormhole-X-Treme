package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.utils.ChatColour;
import com.wormhole_xtreme.wormhole.utils.Messages;
import com.wormhole_xtreme.wormhole.utils.SignStyle;
import com.wormhole_xtreme.wormhole.utils.SignText;

/**
 * A sign beside a mirror's banner that shows the previous, current and next mirror, the way a
 * gate's dial sign does, and moves the mirror on when right-clicked (#280).
 *
 * <p>A sign is one block seen by everybody, so it shows the public list only: the mirror's start,
 * then its group's mirrors, never a hidden or private one. A private mirror chosen at the banner
 * by somebody allowed it leaves the current line blank rather than naming it on the wall.
 *
 * <p>A sign becomes one by being written {@code [mirror]} on its front's first line and touching a
 * banner; once drawn, that line is the mirror's name, which is how it is known again.
 */
public final class MirrorChoosingSign
{
    /** What a player writes on a sign's first line to make it a mirror's. */
    static final String MARKER = "[mirror]";

    /** Every sign material, worked out once, so a click on anything else costs a set lookup. */
    private static final Set<Material> SIGNS = Arrays.stream(Material.values())
        .filter(m -> m.name().endsWith("SIGN"))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));

    /** Where a sign touching a banner can be. */
    private static final BlockFace[] BESIDE = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
        BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    /** Static use only. */
    private MirrorChoosingSign()
    {
    }

    /**
     * The mirror a clicked sign chooses for, if it is one.
     *
     * @param block
     *            the block clicked
     * @return the mirror whose banner this sign touches and which it is written for, or null
     */
    static QuantumMirror mirrorFor(final Block block)
    {
        // Type first: almost no click is on a sign, and the rest costs a world lookup per side.
        if (!SIGNS.contains(block.getType()) || (MirrorManager.count() == 0))
        {
            return null;
        }
        String first = null;
        for (final BlockFace face : BESIDE)
        {
            final Block next = block.getRelative(face);
            if (!MirrorInteraction.isBanner(next.getType()))
            {
                continue;
            }
            final QuantumMirror mirror = MirrorManager.at(BlockPlace.of(next));
            if ((mirror == null) || (mirror.destination() == null))
            {
                continue;
            }
            if (first == null)
            {
                first = firstLine(block);
            }
            if (marks(first, mirror))
            {
                return mirror;
            }
        }
        return null;
    }

    /**
     * Whether a sign's first line makes it this mirror's.
     *
     * @param line
     *            the front's first line as written, colours and all
     * @return true for {@code [mirror]} or the mirror's own name as drawn, ignoring case
     */
    static boolean marks(final String line, final QuantumMirror mirror)
    {
        final String plain = SignStyle.stripFormatting(line).trim();
        return plain.equalsIgnoreCase(MARKER) || plain.equalsIgnoreCase(heading(mirror));
    }

    /**
     * What the sign says, top to bottom.
     *
     * @return the mirror's name; then the previous, current and next of the public list, the
     *         current marked; or its own room before anything is chosen
     */
    static String[] lines(final QuantumMirror mirror)
    {
        final List<QuantumMirror> order = MirrorNetwork.order(mirror);
        final String[] lines = {heading(mirror), "", "", ""};
        if (order.isEmpty())
        {
            lines[2] = Messages.get(null, "mirror.sign.no-others");
            return lines;
        }
        final QuantumMirror current = MirrorNetwork.chosen(mirror);
        int at = -1;
        for (int i = 0; i < order.size(); i++)
        {
            if (order.get(i).name().equalsIgnoreCase(current.name()))
            {
                at = i;
            }
        }
        if (at < 0)
        {
            // Off, or a mirror this sign does not list: a click goes to the first.
            lines[2] = (current == mirror) ? Messages.get(null, "mirror.sign.own-room") : "";
            lines[3] = order.get(0).name();
            return lines;
        }
        lines[2] = "»" + order.get(at).name() + "«";
        if (order.size() > 1)
        {
            lines[1] = order.get(((at - 1) + order.size()) % order.size()).name();
            lines[3] = order.get((at + 1) % order.size()).name();
        }
        return lines;
    }

    /**
     * Writes every sign written for this mirror beside its banners.
     *
     * <p>Called when its choice changes, not every sweep; a sign in a chunk that is not loaded is
     * left as it is rather than loaded for it.
     *
     * @param world
     *            the banners' world
     */
    public static void redraw(final QuantumMirror mirror, final World world)
    {
        if ((world == null) || (mirror.destination() == null))
        {
            return;
        }
        for (final BlockPlace banner : mirror.banners())
        {
            for (final BlockFace face : BESIDE)
            {
                final int x = banner.x() + face.getModX();
                final int z = banner.z() + face.getModZ();
                if (!world.isChunkLoaded(x >> 4, z >> 4))
                {
                    continue;
                }
                final Block block = world.getBlockAt(x, banner.y() + face.getModY(), z);
                if (SIGNS.contains(block.getType()) && marks(firstLine(block), mirror))
                {
                    draw(block, mirror);
                }
            }
        }
    }

    /** Writes the lines on a sign's front, in the dial sign's colours. */
    private static void draw(final Block block, final QuantumMirror mirror)
    {
        if (!(block.getState() instanceof Sign sign))
        {
            return;
        }
        final SignSide face = sign.getSide(Side.FRONT);
        final ChatColour neighbour = SignStyle.resolveColor(ConfigManager.getSignColorNeighbour(), ChatColour.GRAY);
        final String[] lines = lines(mirror);
        face.setGlowingText(ConfigManager.isSignGlowingText());
        SignText.set(face, 0, SignStyle.paint(
            SignStyle.resolveColor(ConfigManager.getSignColorGateName(), ChatColour.DARK_AQUA), lines[0]));
        SignText.set(face, 1, SignStyle.paint(neighbour, lines[1]));
        SignText.set(face, 2, SignStyle.paint(
            SignStyle.resolveColor(ConfigManager.getSignColorSelected(), ChatColour.DARK_GREEN), lines[2]));
        SignText.set(face, 3, SignStyle.paint(neighbour, lines[3]));
        sign.update(true, false);
    }

    /** The front's first line, or blank for a block that is not a sign after all. */
    private static String firstLine(final Block block)
    {
        final BlockState state = block.getState();
        return (state instanceof Sign sign) ? SignText.get(sign.getSide(Side.FRONT), 0) : "";
    }

    private static String heading(final QuantumMirror mirror)
    {
        return "-" + mirror.name() + "-";
    }
}
