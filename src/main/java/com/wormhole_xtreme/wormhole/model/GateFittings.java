package com.wormhole_xtreme.wormhole.model;

import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.GateRederivation;
import com.wormhole_xtreme.wormhole.plugin.CoreProtectLog;
import com.wormhole_xtreme.wormhole.utils.ChatColour;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.SignStyle;
import com.wormhole_xtreme.wormhole.utils.SignText;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * The fittings a gate carries at its base: the name sign, the dial sign's material and the iris
 * lever (#430, #734).
 */
final class GateFittings
{
    /** Static helpers only. */
    private GateFittings()
    {
    }

    /**
     * Places or removes the gate name sign adjacent to the name block holder.
     * The sign faces {@link Stargate#getGateFacing()}, the same direction the
     * gate/DHD button faces.
     *
     * @param create {@code true} to place; {@code false} to remove
     */
    static void setupGateSign(final Stargate gate, final boolean create)
    {
        if (gate.getGateNameBlockHolder() == null)
        {
            return;
        }

        final BlockFace toward = gate.getGateFacing();
        final Block nameSign = gate.getGateNameBlockHolder();
        final Block placeBlock = nameSign.getRelative(toward);

        if (create)
        {
            // Frame material in front of :N is most likely the ring itself, which the sign would replace.
            if (isFrameMaterial(gate, placeBlock.getType()))
            {
                final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
                if (plugin != null)
                {
                    plugin.prettyLog(Level.WARNING, "No name sign for " + gate.getGateName()
                        + ": the block in front of its :N block is the gate's frame material.");
                }
                return;
            }
            logSignPlacement(gate, nameSign, toward, placeBlock);
            placeGateSign(gate, placeBlock, toward);
        }
        else if (MaterialUtils.isWallSign(placeBlock.getType()))
        {
            logSignRemoval(gate, placeBlock);
            removeGateSign(gate, placeBlock);
        }
    }

    /**
     * Records everything around the name holder at the moment a sign is placed.
     *
     * <p>Diagnostics only: it reads six neighbours and the holder's own facing, and each
     * read is guarded because a block that cannot be read is the thing being diagnosed.
     */
    private static void logSignPlacement(final Stargate gate, final Block nameSign,
                                         final BlockFace toward, final Block placeBlock)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE))
        {
            return;
        }
        final StringBuilder dbg = new StringBuilder(256);
        dbg.append("Sign placement: Gate=").append(gate.getGateName());
        dbg.append(" NameHolderLoc=").append(describe(nameSign::getLocation));
        dbg.append(" GateFacing=").append(describe(() -> toward));
        dbg.append(" PlaceBlock=").append(describe(placeBlock::getLocation));
        dbg.append(" PlaceBlockType=").append(describe(placeBlock::getType));

        for (final BlockFace f : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN })
        {
            dbg.append(' ').append(f)
                .append("=[").append(describe(() -> nameSign.getRelative(f).getType()))
                .append('@').append(describe(() -> nameSign.getRelative(f).getLocation()))
                .append(']');
        }
        dbg.append(describeHolderFacing(nameSign));
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /**
     * A value for the log, or {@code "null"} when it is absent or cannot be read.
     *
     * <p>Every read in the placement log wants the same thing, and guarding each one inline is
     * what made the method unreadable.
     */
    static String describe(final Supplier<Object> read)
    {
        try
        {
            final Object value = read.get();
            return value != null ? value.toString() : "null";
        }
        catch (final RuntimeException unreadable)
        {
            return "null";
        }
    }

    /** The name holder's own facing, or nothing at all when it has none or cannot be read. */
    private static String describeHolderFacing(final Block nameSign)
    {
        try
        {
            if (nameSign.getBlockData() instanceof Directional d)
            {
                return " NameHolderFacing=" + describe(d::getFacing);
            }
        }
        catch (final RuntimeException unreadable)
        {
            // a holder that will not report its facing is the sort of thing this log is for
        }
        return "";
    }

    /** Records where a sign was when it is taken down. */
    private static void logSignRemoval(final Stargate gate, final Block placeBlock)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE))
        {
            return;
        }
        final StringBuilder dbg = new StringBuilder(128);
        dbg.append("Sign removal: Gate=").append(gate.getGateName());
        try
        {
            final Location pbLoc = placeBlock != null ? placeBlock.getLocation() : null;
            dbg.append(" PlaceBlock=").append(pbLoc != null ? pbLoc.toString() : "null");
        }
        catch (final Exception e)
        {
            dbg.append(" PlaceBlock=null");
        }
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /** Whether a block is the gate's frame or chevron material. */
    static boolean isFrameMaterial(final Stargate gate, final Material type)
    {
        return (type != null)
            && ((type == gate.getEffectiveStructureMaterial()) || (type == gate.getEffectiveChevronMaterial()));
    }

    /** Places the sign and writes the gate's name, network and owner onto its front. */
    private static void placeGateSign(final Stargate gate, final Block placeBlock, final BlockFace toward)
    {
        gate.getGateStructureBlocks().add(placeBlock.getLocation());
        CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, placeBlock, gate.getEffectiveSignMaterial(), null);
        placeBlock.setType(gate.getEffectiveSignMaterial(), false);
        final Directional signData = (Directional) placeBlock.getBlockData();
        signData.setFacing(toward);
        placeBlock.setBlockData(signData, false);

        final Sign sign = (Sign) placeBlock.getState();
        final SignSide front = sign.getSide(Side.FRONT);
        // Colour codes do not count toward a sign's visible width, so the owner is still
        // truncated on the text alone -- painting it cannot push it off the sign.
        SignText.set(front, 0, SignStyle.paint(
            SignStyle.resolveColor(ConfigManager.getSignColorGateName(), ChatColour.DARK_AQUA),
            "-" + gate.getGateName() + "-"));
        if (gate.getGateNetwork() != null)
        {
            SignText.set(front, 1, SignStyle.paint(
                SignStyle.resolveColor(ConfigManager.getSignColorNetwork(), ChatColour.GRAY),
                "N:" + gate.getGateNetwork().getNetworkName()));
        }
        if (gate.getGateOwner() != null)
        {
            final String ownerDisplay = gate.getGateOwnerName();
            SignText.set(front, 2, SignStyle.paint(
                SignStyle.resolveColor(ConfigManager.getSignColorOwner(), ChatColour.GRAY),
                "O:" + (ownerDisplay != null && ownerDisplay.length() > 13
                    ? ownerDisplay.substring(0, 13) : ownerDisplay)));
        }
        front.setGlowingText(ConfigManager.isSignGlowingText());
        sign.update(true, false);
        // NOTE: gateDialSignBlock/gateDialSign are set during shape detection
        // (check3DShape) from the [D] marker — the player-placed sign on the
        // DHD.  Do NOT overwrite them here; this is the static gate frame sign
        // and it should never be used as the dialer sign.
    }

    /**
     * Takes the sign down and drops it from the gate's structure blocks.
     *
     * <p>A sign hung in the frame, as 1.7.0's {@code Massive} hung it, gets the frame block back
     * rather than leaving a hole the gate cannot be detected with, and stays one of the gate's
     * blocks. The frame is asked about first, because it is laid by those same recorded blocks.
     */
    private static void removeGateSign(final Stargate gate, final Block placeBlock)
    {
        final Material frame = GateRederivation.frameMaterialAt(gate, placeBlock);
        if (frame != null)
        {
            CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, placeBlock, frame, null);
            placeBlock.setType(frame);
            return;
        }
        gate.getGateStructureBlocks().remove(placeBlock.getLocation());
        CoreProtectLog.removed(CoreProtectLog.PLUGIN_USER, placeBlock);
        placeBlock.setType(Material.AIR);
    }


    /**
     * Decides whether a dial sign should be converted to the gate's sign material.
     *
     * <p>Pulled out as a plain function of three values so the rule can be pinned without a
     * world: the block work either side of it is unconditional once this says yes.
     *
     * @param have
     *            the material of the sign the player actually placed
     * @param want
     *            the gate's own sign material
     * @param enabled
     *            whether the server has asked for this at all
     * @return true if the block should be converted
     */
    static boolean shouldMatchDialSignMaterial(final Material have, final Material want, final boolean enabled)
    {
        if (!enabled || (want == null) || (have == want))
        {
            return false;
        }
        // Both ends must be wall signs. A gate whose configured sign material is somehow not
        // one would otherwise turn a working dial sign into a block that cannot hold text,
        // which breaks dialling rather than restyling it.
        return MaterialUtils.isWallSign(have) && MaterialUtils.isWallSign(want);
    }

    /**
     * Converts a player-placed dial sign to the gate's own sign material.
     *
     * <p>The dial sign is the one sign the plugin does not place: a player puts it on the
     * {@code [D]} block themselves, in whatever wood they happened to hold. On a themed gate
     * that left an oak dial sign on a crimson frame. This makes it match.
     *
     * <p>Changing a block's type wipes a sign's contents, so everything worth keeping is read
     * first and written back after: the text on both faces, whether each face glows, and the
     * way the sign is facing. The gate's cached sign state is replaced too -- it refers to the
     * block as it was, and every later write to the dial sign goes through it.
     *
     * <p>Waxed state is deliberately neither read nor preserved. {@code Sign.isWaxed} does not
     * exist before 1.20.4, so calling it would compile against this project's target and throw
     * on a 1.20 server. It costs nothing here in practice: the plugin rewrites the dial sign
     * every time someone clicks it, so a waxed dial sign could never have worked as one.
     *
     * @param gate
     *            the gate whose dial sign should be matched
     */
    static void matchDialSignMaterial(final Stargate gate)
    {
        final Block signBlock = gate.getGateDialSignBlock();
        if (signBlock == null)
        {
            return;
        }
        final Material want = gate.getEffectiveSignMaterial();
        if (!shouldMatchDialSignMaterial(signBlock.getType(), want,
            ConfigManager.isSignDialMatchMaterial()))
        {
            return;
        }
        try
        {
            final BlockState before = signBlock.getState();
            if (!(before instanceof Sign))
            {
                return;
            }
            final Sign old = (Sign) before;
            final String[] frontLines = SignText.lines(old.getSide(Side.FRONT));
            final String[] backLines = SignText.lines(old.getSide(Side.BACK));
            final boolean frontGlows = old.getSide(Side.FRONT).isGlowingText();
            final boolean backGlows = old.getSide(Side.BACK).isGlowingText();

            BlockFace facing = null;
            final BlockData oldData = signBlock.getBlockData();
            if (oldData instanceof Directional oldFacing)
            {
                facing = oldFacing.getFacing();
            }

            CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, signBlock, want, null);
            signBlock.setType(want, false);

            if (facing != null)
            {
                final BlockData newData = signBlock.getBlockData();
                if (newData instanceof Directional newFacing)
                {
                    newFacing.setFacing(facing);
                    signBlock.setBlockData(newData, false);
                }
            }

            final BlockState after = signBlock.getState();
            if (after instanceof Sign fresh)
            {
                restoreSignSide(fresh.getSide(Side.FRONT), frontLines, frontGlows);
                restoreSignSide(fresh.getSide(Side.BACK), backLines, backGlows);
                fresh.update(true, false);
                // The gate holds this state and writes destinations through it, so leaving
                // the old one in place would send every later write at a block that is gone.
                gate.setGateDialSign(fresh);
            }
        }
        catch (final Exception | LinkageError t)
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING,
                    "Could not match dial sign material on gate " + gate.getGateName(), t);
            }
        }
    }

    /** Writes saved lines and glow back onto one face of a replaced sign. */
    private static void restoreSignSide(final SignSide side,
                                        final String[] lines,
                                        final boolean glowing)
    {
        if (lines != null)
        {
            for (int i = 0; i < lines.length && i < 4; i++)
            {
                SignText.set(side, i, lines[i] != null ? lines[i] : "");
            }
        }
        side.setGlowingText(glowing);
    }


    /**
     * Places or removes the iris control lever below the DHD block.
     *
     * @param create {@code true} to place; {@code false} to remove
     */
    static void setupIrisLever(final Stargate gate, final boolean create)
    {
        if (gate.getGateIrisLeverBlock() == null)
        {
            gate.setGateIrisLeverBlock(deriveIrisLeverBlock(gate));
        }
        if (gate.getGateIrisLeverBlock() == null)
        {
            return;
        }
        if (create)
        {
            logIrisLeverPlacement(gate);
            placeIrisLever(gate);
        }
        else
        {
            removeIrisLever(gate);
        }
    }

    /**
     * Works out where the iris lever hangs when the shape has not marked it.
     *
     * <p>The button is wall-mounted on one face of the DHD column, and its facing says which
     * way that face points. So: back one step from it to the column, down one to the base,
     * then forward along the gate's own facing, which is what puts the lever where somebody
     * standing at the gate can reach it.
     *
     * @return the block, or null if there is nothing to derive from or the spot is taken
     */
    private static Block deriveIrisLeverBlock(final Stargate gate)
    {
        if (gate.getGateShape() == null)
        {
            return null;
        }
        // A redstone-dialled gate has no DHD button for the walk below to start from.
        if ((gate.getGateShape() instanceof Stargate3DShape shape) && shape.isShapeRedstoneActivated())
        {
            return null;
        }
        final Block button = gate.getGateDialLeverBlock();
        if (button == null)
        {
            return null;
        }

        BlockFace buttonFacing = gate.getGateFacing();
        if (button.getBlockData() instanceof Directional buttonData)
        {
            buttonFacing = buttonData.getFacing();
        }
        final Block backing = button.getRelative(WorldUtils.getInverseDirection(buttonFacing));
        final Block dhdBase = backing.getRelative(BlockFace.DOWN);
        final Block irisBlock = dhdBase.getRelative(gate.getGateFacing());

        // StandardSignDialRedstone puts [S:RD] directly below [S:A], which is exactly where
        // this walk lands. Taking it would put a lever on the block that dials the gate.
        if (isTakenByRedstone(gate, irisBlock))
        {
            return null;
        }
        return irisBlock;
    }

    /** Whether a redstone activation block already holds this position. */
    private static boolean isTakenByRedstone(final Stargate gate, final Block irisBlock)
    {
        return WorldUtils.isSameBlock(gate.getGateRedstoneDialActivationBlock(), irisBlock)
            || WorldUtils.isSameBlock(gate.getGateRedstoneSignActivationBlock(), irisBlock);
    }

    /** Records what was around the DHD when the iris lever went up. */
    private static void logIrisLeverPlacement(final Stargate gate)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE))
        {
            return;
        }
        final Block iris = gate.getGateIrisLeverBlock();
        final StringBuilder dbg = new StringBuilder(128);
        dbg.append("Iris lever placement: Gate=").append(gate.getGateName());
        dbg.append(" DialLever=").append(describe(() -> gate.getGateDialLeverBlock().getLocation()));
        dbg.append(" IrisBlock=").append(describe(iris::getLocation));
        dbg.append(" IrisBlockType=").append(describe(iris::getType));
        dbg.append(" GateFacing=").append(describe(gate::getGateFacing));
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /** Hangs the lever on the wall, facing the way the gate faces. */
    private static void placeIrisLever(final Stargate gate)
    {
        final Block iris = gate.getGateIrisLeverBlock();
        gate.getGateStructureBlocks().add(iris.getLocation());
        CoreProtectLog.placing(CoreProtectLog.PLUGIN_USER, iris, Material.LEVER, null);
        iris.setType(Material.LEVER);
        final Switch irisSwitch =
            (Switch) iris.getBlockData();
        irisSwitch.setAttachedFace(FaceAttachable.AttachedFace.WALL);
        irisSwitch.setFacing(gate.getGateFacing());
        iris.setBlockData(irisSwitch);
    }

    /** Takes the lever down, but only if what is there is a lever. */
    private static void removeIrisLever(final Stargate gate)
    {
        final Block iris = gate.getGateIrisLeverBlock();
        if (iris.getType() != Material.LEVER)
        {
            return;
        }
        gate.getGateStructureBlocks().remove(iris.getLocation());
        CoreProtectLog.removed(CoreProtectLog.PLUGIN_USER, iris);
        iris.setType(Material.AIR);
    }
}
