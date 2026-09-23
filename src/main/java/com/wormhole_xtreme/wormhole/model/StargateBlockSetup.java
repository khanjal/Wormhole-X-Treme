package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.GateRedstoneWrite;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.SignStyle;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * Handles all physical block placement and removal for a stargate: the name
 * sign, iris lever, redstone wiring, portal fills, and gate/portal block deletion.
 *
 * <p>All methods are static and operate on a {@link Stargate} instance.
 */
class StargateBlockSetup
{
    private StargateBlockSetup() {}

    // -----------------------------------------------------------------------
    // Sign
    // -----------------------------------------------------------------------

    /**
     * Places or removes the gate name sign adjacent to the name block holder.
     * The sign faces {@link Stargate#getGateFacing()}, the same direction the
     * gate/DHD button faces.
     *
     * @param gate   the gate
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
        else if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(placeBlock.getType()))
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
    private static String describe(final java.util.function.Supplier<Object> read)
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
            final org.bukkit.Location pbLoc = placeBlock != null ? placeBlock.getLocation() : null;
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
        placeBlock.setType(gate.getEffectiveSignMaterial(), false);
        final Directional signData = (Directional) placeBlock.getBlockData();
        signData.setFacing(toward);
        placeBlock.setBlockData(signData, false);

        final Sign sign = (Sign) placeBlock.getState();
        final org.bukkit.block.sign.SignSide front = sign.getSide(Side.FRONT);
        // Colour codes do not count toward a sign's visible width, so the owner is still
        // truncated on the text alone -- painting it cannot push it off the sign.
        front.setLine(0, SignStyle.paint(
            SignStyle.resolveColor(ConfigManager.getSignColorGateName(), ChatColor.DARK_AQUA),
            "-" + gate.getGateName() + "-"));
        if (gate.getGateNetwork() != null)
        {
            front.setLine(1, SignStyle.paint(
                SignStyle.resolveColor(ConfigManager.getSignColorNetwork(), ChatColor.GRAY),
                "N:" + gate.getGateNetwork().getNetworkName()));
        }
        if (gate.getGateOwner() != null)
        {
            final String ownerDisplay = gate.getGateOwnerName();
            front.setLine(2, SignStyle.paint(
                SignStyle.resolveColor(ConfigManager.getSignColorOwner(), ChatColor.GRAY),
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
        final Material frame = com.wormhole_xtreme.wormhole.logic.GateRederivation.frameMaterialAt(gate, placeBlock);
        if (frame != null)
        {
            placeBlock.setType(frame);
            return;
        }
        gate.getGateStructureBlocks().remove(placeBlock.getLocation());
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
            final org.bukkit.block.BlockState before = signBlock.getState();
            if (!(before instanceof Sign))
            {
                return;
            }
            final Sign old = (Sign) before;
            final String[] frontLines = old.getSide(Side.FRONT).getLines();
            final String[] backLines = old.getSide(Side.BACK).getLines();
            final boolean frontGlows = old.getSide(Side.FRONT).isGlowingText();
            final boolean backGlows = old.getSide(Side.BACK).isGlowingText();

            BlockFace facing = null;
            final BlockData oldData = signBlock.getBlockData();
            if (oldData instanceof Directional oldFacing)
            {
                facing = oldFacing.getFacing();
            }

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

            final org.bukkit.block.BlockState after = signBlock.getState();
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
    private static void restoreSignSide(final org.bukkit.block.sign.SignSide side,
                                        final String[] lines,
                                        final boolean glowing)
    {
        if (lines != null)
        {
            for (int i = 0; i < lines.length && i < 4; i++)
            {
                side.setLine(i, lines[i] != null ? lines[i] : "");
            }
        }
        side.setGlowingText(glowing);
    }

    // -----------------------------------------------------------------------
    // Iris lever
    // -----------------------------------------------------------------------

    /**
     * Places or removes the iris control lever below the DHD block.
     *
     * @param gate   the gate
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
        iris.setType(Material.LEVER);
        final org.bukkit.block.data.type.Switch irisSwitch =
            (org.bukkit.block.data.type.Switch) iris.getBlockData();
        irisSwitch.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
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
        iris.setType(Material.AIR);
    }


    // -----------------------------------------------------------------------
    // Redstone wiring
    // -----------------------------------------------------------------------

    /**
     * Places or removes all redstone components (dial wire, sign wire, gate-
     * activated lever) in a single call.
     *
     * @param gate   the gate
     * @param create {@code true} to place; {@code false} to remove
     */
    static void setupRedstone(final Stargate gate, final boolean create)
    {
        if (gate.isGateSignPowered())
        {
            setupRedstoneDialWire(gate, create);
            setupRedstoneSignDialWire(gate, create);
        }
        setupRedstoneGateActivatedLever(gate, create);
    }

    /** Places or removes the dial activation redstone wire. */
    static void setupRedstoneDialWire(final Stargate gate, final boolean create)
    {
        setupRedstoneWire(gate, gate.getGateRedstoneDialActivationBlock(), "RD", create);
    }

    /** Places or removes the sign-dial redstone wire. */
    static void setupRedstoneSignDialWire(final Stargate gate, final boolean create)
    {
        setupRedstoneWire(gate, gate.getGateRedstoneSignActivationBlock(), "RS", create);
    }

    /**
     * Lays one marker's redstone wire, or takes it up again.
     *
     * <p>Both callers were the same thirty lines, differing only in which block they read and
     * two characters of a log line.
     *
     * <p>Neither direction touches a block it did not put there. On the way in, a cell
     * somebody has already built in is left alone and reported rather than overwritten; on
     * the way out, only an actual wire is cleared, because between laying and lifting a
     * player may have replaced it. The gate's structure list follows the same rule, so it
     * never claims a block it did not place or abandons one it did.
     *
     * @param gate
     *            the gate being wired
     * @param target
     *            the block the marker resolved to, or null if the shape has no such marker
     * @param marker
     *            the marker's name, for the log line when the cell is occupied
     * @param create
     *            true to lay the wire, false to take it up
     */
    private static void setupRedstoneWire(final Stargate gate, final Block target,
        final String marker, final boolean create)
    {
        if (target == null)
        {
            return;
        }
        if (create)
        {
            try
            {
                final Material current = target.getType();
                if ((current == Material.AIR) || (current == Material.REDSTONE_WIRE))
                {
                    gate.getGateStructureBlocks().add(target.getLocation());
                    target.setType(Material.REDSTONE_WIRE);
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                        "Skipping " + marker + " placement; target occupied: " + current);
                }
            }
            catch (final RuntimeException ignore) { /* placing the marker is best effort */ }
        }
        else if (target.getType() == Material.REDSTONE_WIRE)
        {
            gate.getGateStructureBlocks().remove(target.getLocation());
            target.setType(Material.AIR);
        }
    }

    /** Places or removes the gate-activated output lever. */
    static void setupRedstoneGateActivatedLever(final Stargate gate, final boolean create)
    {
        if (gate.getGateRedstoneGateActivatedBlock() != null)
        {
            if (create)
            {
                final Block ra = gate.getGateRedstoneGateActivatedBlock();
                try
                {
                    final Material current = ra.getType();
                    if (current == Material.AIR)
                    {
                        gate.getGateStructureBlocks().add(ra.getLocation());
                        ra.setType(Material.LEVER);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Skipping RA lever placement; target occupied: " + current);
                    }
                }
                catch (final RuntimeException ignore) { /* placing the marker is best effort */ }
            }
            else
            {
                if (gate.getGateRedstoneGateActivatedBlock().getType() == Material.LEVER)
                {
                    gate.getGateStructureBlocks().remove(gate.getGateRedstoneGateActivatedBlock().getLocation());
                    gate.getGateRedstoneGateActivatedBlock().setType(Material.AIR);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Block deletion & interior fill
    // -----------------------------------------------------------------------

    /**
     * Removes the dial sign (sign-powered gate) that sits in front of the dial
     * sign block, on the gate-facing side.
     *
     * @param gate the gate
     */
    static void deleteTeleportSign(final Stargate gate)
    {
        if ((gate.getGateDialSignBlock() != null) && (gate.getGateDialSign() != null))
        {
            final Block teleportSign = gate.getGateDialSignBlock().getRelative(gate.getGateFacing());
            teleportSign.setType(Material.AIR);
        }
    }

    /**
     * Sets all structure blocks to {@link Material#AIR}.
     *
     * @param gate the gate
     */
    static void deleteGateBlocks(final Stargate gate)
    {
        for (final Location bc : gate.getGateStructureBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(Material.AIR);
        }
    }

    /** Radius, in blocks, within which clients are sent portal visual updates. */
    private static final double VISUAL_RADIUS = 64.0;

    /** How often the arrival splash is redrawn while it is showing, in ticks. */
    private static final long SPLASH_REDRAW_INTERVAL = 2L;

    /**
     * Which gates each player is currently being shown a portal for.
     *
     * <p>A portal is a drawing in the client's copy of the chunk, and the server has no way
     * to ask what a client is currently showing. Remembering what was sent is the only way to
     * know what needs taking back.
     */
    private static final java.util.Map<java.util.UUID, Set<String>> DRAWN =
        new java.util.concurrent.ConcurrentHashMap<java.util.UUID, Set<String>>();

    /**
     * Sends a client-side-only appearance for every portal block of {@code gate}.
     * <p>
     * The recipient list is resolved once per call rather than once per block: a
     * Standard gate has 21 portal blocks and the woosh animation redraws them on
     * every frame, so a per-block player scan multiplies quickly on a busy world.
     *
     * @param gate     the gate whose portal blocks are being redrawn
     * @param material the appearance to send to clients
     */
    private static void sendPortalVisual(final Stargate gate, final Material material)
    {
        final List<Location> portalBlocks = gate.getGatePortalBlocks();
        if (portalBlocks.isEmpty())
        {
            return;
        }
        // Portal blocks all sit within a gate-sized box, so proximity to any one of
        // them is a good enough filter for the whole gate.
        final Location reference = new Location(gate.getGateWorld(),
            portalBlocks.get(0).getBlockX(), portalBlocks.get(0).getBlockY(), portalBlocks.get(0).getBlockZ());
        final List<Player> recipients = new ArrayList<>();
        for (final Player p : gate.getGateWorld().getPlayers())
        {
            if (p.getLocation().distanceSquared(reference) <= (VISUAL_RADIUS * VISUAL_RADIUS))
            {
                recipients.add(p);
            }
        }
        if (recipients.isEmpty())
        {
            return;
        }
        // Built only once nobody-is-watching has been ruled out: createBlockData()
        // needs a live server, and the woosh animation calls this every frame.
        final BlockData blockData = MaterialUtils.drawnAcross(material, gate.getGateFacing());
        for (final Location bc : portalBlocks)
        {
            final Location at = new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            for (final Player p : recipients)
            {
                p.sendBlockChange(at, blockData);
            }
        }
        // Note who is now showing this, so it can be taken back from them later even if they
        // have wandered out of range by then. AIR is the close, which is the taking back.
        for (final Player p : recipients)
        {
            if (material == Material.AIR)
            {
                drawnFor(p).remove(gate.getGateName());
            }
            else
            {
                drawnFor(p).add(gate.getGateName());
            }
        }
    }

    /**
     * Draws some of a gate's cells for whoever is near enough to see them.
     *
     * <p>The iris sweep's one piece of drawing. Unlike {@link #sendPortalVisual} this takes the
     * cells rather than the whole opening, and can be asked for the truth instead of a picture:
     * a null material sends each cell's real block, which is how a sweep finishes and how one
     * that is called off puts things back.
     *
     * @param gate
     *            the gate the cells belong to
     * @param cells
     *            the cells to draw
     * @param material
     *            what to draw them as, or null to send what is really there
     */
    static void sendCells(final Stargate gate, final List<Location> cells, final Material material)
    {
        if ((gate == null) || (gate.getGateWorld() == null) || (cells == null) || cells.isEmpty())
        {
            return;
        }
        final Location reference = new Location(gate.getGateWorld(),
            cells.get(0).getBlockX(), cells.get(0).getBlockY(), cells.get(0).getBlockZ());
        final List<Player> recipients = new ArrayList<>();
        for (final Player p : gate.getGateWorld().getPlayers())
        {
            if (p.getLocation().distanceSquared(reference) <= (VISUAL_RADIUS * VISUAL_RADIUS))
            {
                recipients.add(p);
            }
        }
        if (recipients.isEmpty())
        {
            return;
        }
        // Built once nobody-is-watching has been ruled out, for the same reason
        // sendPortalVisual does it: createBlockData needs a live server.
        final BlockData drawn = (material == null) ? null
            : MaterialUtils.drawnAcross(material, gate.getGateFacing());
        for (final Location bc : cells)
        {
            final Location at = new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            // getBlockAt by coordinate rather than Location.getBlock(), which is the same
            // lookup with a Location built and thrown away on the way -- the round trip
            // sendPortalVisual's own comment says buys nothing.
            final BlockData data = (drawn != null) ? drawn
                : gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ()).getBlockData();
            for (final Player p : recipients)
            {
                p.sendBlockChange(at, data);
            }
        }
    }

    /**
     * Shows a traveller a moment of water as they come out of a gate.
     *
     * <p>The client draws its underwater overlay from whichever block it believes its camera
     * is in, so one block sent at eye height is the whole effect: they surface out of the
     * event horizon and it clears. Nobody else sees anything, and nothing is written.
     *
     * <p>Deliberately brief, and that is not just taste. Water is physics to the client, not
     * decoration -- for as long as it believes it is submerged it predicts swimming, and the
     * server does not agree. A short flash is over before that argument can be felt. It is
     * the one drawing here that makes the client's world <em>less</em> solid than the real
     * one, which is the direction that caused trouble before, so it is kept to a moment and
     * can be turned off outright.
     *
     * <p>Only sent where the eye is in open air. Water drawn over somebody's ceiling would be
     * a strange thing to see, and the arrival point is out in the open in any case.
     *
     * @param player
     *            the traveller who has just arrived
     */
    public static void splashArrival(final Player player)
    {
        final long ticks = com.wormhole_xtreme.wormhole.config.ConfigManager
            .getGateArrivalSplashTicks();
        if ((player == null) || (ticks <= 0L) || !player.isOnline())
        {
            return;
        }
        try
        {
            final Block eye = player.getEyeLocation().getBlock();
            if (!eye.getType().isAir())
            {
                return;
            }
            final Location at = eye.getLocation();
            // Drawn again every couple of ticks rather than once. An arrival hands the
            // client a fresh copy of the chunk, and a fresh copy erases anything drawn into
            // the old one -- so a single block change lands before the chunk does and is
            // wiped by it. How long that takes is not observable from here and is not fixed:
            // a hop to a nearby gate reuses chunks the client already has, while a trip
            // across the world makes it fetch everything from scratch. The portal redraw
            // above hit exactly this and answered it the same way.
            for (long t = 0L; t < ticks; t += SPLASH_REDRAW_INTERVAL)
            {
                drawSplash(player, at, true, t);
            }
            drawSplash(player, at, false, ticks);
        }
        catch (final RuntimeException ignore)
        {
            // Decoration. Not worth a log line on the travel path.
        }
    }

    /**
     * Draws or clears the arrival splash after a delay.
     *
     * @param player
     *            the traveller
     * @param at
     *            the block their eye was in when they landed
     * @param water
     *            true to show water, false to put the real block back
     * @param delay
     *            how many ticks to wait
     */
    private static void drawSplash(final Player player, final Location at, final boolean water,
        final long delay)
    {
        WormholeXTreme.getScheduler().runTaskLater(WormholeXTreme.getThisPlugin(),
            () ->
            {
                if (!player.isOnline())
                {
                    return;
                }
                try
                {
                    if (water)
                    {
                        // Only while they are still in it. Somebody who has walked on is
                        // no longer surfacing, and redrawing would put water behind them.
                        if (player.getEyeLocation().getBlock().getLocation().equals(at))
                        {
                            player.sendBlockChange(at, Material.WATER.createBlockData());
                        }
                        return;
                    }
                    // Read again rather than remember: somebody may have put something
                    // there in the meantime, and the real block is always the right
                    // answer. A chunk arriving later than this shows the truth anyway,
                    // so the failure that is left over is a splash nobody saw rather
                    // than water nobody can clear.
                    final Block now = at.getWorld().getBlockAt(at.getBlockX(),
                        at.getBlockY(), at.getBlockZ());
                    player.sendBlockChange(at, now.getBlockData());
                }
                catch (final RuntimeException ignore)
                {
                    // As above.
                }
            }, Math.max(1L, delay));
    }

    /**
     * Everyone near enough to a gate to be shown its drawings.
     *
     * <p>Resolved once per call rather than once per block: a gate has tens of blocks and the
     * woosh redraws them every frame, so a per-block player scan multiplies quickly.
     *
     * @param gate
     *            the gate being drawn
     * @return the players to send to, empty if nobody is close
     */
    private static List<Player> nearby(final Stargate gate)
    {
        final List<Player> recipients = new ArrayList<>();
        if ((gate == null) || (gate.getGateWorld() == null))
        {
            return recipients;
        }
        final Location reference = gate.getGateNameBlockHolder() != null
            ? gate.getGateNameBlockHolder().getLocation()
            : gate.getGatePlayerTeleportLocation();
        if (reference == null)
        {
            return recipients;
        }
        for (final Player p : gate.getGateWorld().getPlayers())
        {
            if (p.getLocation().distanceSquared(reference) <= (VISUAL_RADIUS * VISUAL_RADIUS))
            {
                recipients.add(p);
            }
        }
        return recipients;
    }

    /**
     * Shows nearby clients a set of blocks as something they are not.
     *
     * <p>Nothing is written to the world. The chevrons and the woosh used to be real blocks,
     * which meant a server that stopped mid-dial left lit chevrons welded into the frame and
     * a half-expanded woosh hanging in the air, with the originals it would have restored
     * from having died with the process. A drawing cannot outlive the thing that drew it.
     *
     * @param gate
     *            the gate the blocks belong to
     * @param blocks
     *            the positions to draw
     * @param material
     *            what to show there
     */
    public static void drawBlocks(final Stargate gate, final List<Location> blocks,
        final Material material)
    {
        if ((blocks == null) || blocks.isEmpty())
        {
            return;
        }
        final List<Player> recipients = nearby(gate);
        if (recipients.isEmpty())
        {
            return;
        }
        // Built only once nobody-is-watching has been ruled out: createBlockData() needs a
        // live server, and the woosh calls this every frame.
        final BlockData blockData = MaterialUtils.drawnAcross(material, gate.getGateFacing());
        for (final Location bc : blocks)
        {
            final Location at = new Location(gate.getGateWorld(),
                bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            for (final Player p : recipients)
            {
                p.sendBlockChange(at, blockData);
            }
        }
    }

    /**
     * Shows nearby clients one wave of a gate's chevrons lit.
     *
     * <p>Separate from {@link #drawBlocks} because a chevron is not necessarily drawn as the
     * gate's light material. Where the player built one out of the chevron material and that
     * material is a light with an off and an on, the better thing to show is that same fixture
     * switched on -- a redstone lamp lighting up, rather than a redstone lamp being replaced
     * by glowstone for the duration of the call.
     *
     * <p>Decided per position rather than once for the wave, because detection accepts either
     * material at a chevron cell: a gate can quite legitimately have lamps at three chevrons
     * and obsidian at the other four, and each should light as whatever it actually is.
     *
     * @param gate
     *            the gate whose chevrons these are
     * @param blocks
     *            the positions to light
     */
    static void drawLights(final Stargate gate, final List<Location> blocks)
    {
        if ((blocks == null) || blocks.isEmpty())
        {
            return;
        }
        final List<Player> recipients = nearby(gate);
        if (recipients.isEmpty())
        {
            return;
        }
        // Both resolved once for the wave: createBlockData() needs a live server and this
        // runs once per chevron step, so there is no reason to pay for it per block.
        final BlockData lightData = MaterialUtils.drawnAs(gate.getEffectiveLightMaterial());
        final Material chevronMaterial = gate.getEffectiveChevronMaterial();
        final BlockData fixtureOn = MaterialUtils.litFormOf(chevronMaterial);

        for (final Location bc : blocks)
        {
            final Block real = gate.getGateWorld()
                .getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            final BlockData data = litChevron(real.getType(), chevronMaterial, fixtureOn, lightData);
            for (final Player p : recipients)
            {
                p.sendBlockChange(real.getLocation(), data);
            }
        }
    }

    /**
     * What one chevron position should be shown as while it is lit.
     *
     * <p>Split out from {@link #drawLights} so the choice can be tested without a live server:
     * everything either side of it needs {@code createBlockData()}, which does not work off a
     * running Bukkit instance.
     *
     * @param standing
     *            the material actually built at that position
     * @param chevronMaterial
     *            what an unlit chevron of this gate is built from, may be null
     * @param fixtureOn
     *            the chevron material switched on, or null if it has no lit state
     * @param lightData
     *            the gate's light material, used for everything else
     * @return what to draw there
     */
    static BlockData litChevron(final Material standing, final Material chevronMaterial,
        final BlockData fixtureOn, final BlockData lightData)
    {
        return MaterialUtils.litChevron(standing, chevronMaterial, fixtureOn, lightData);
    }

    /**
     * Real water or lava still standing in a closed gate's opening or woosh.
     *
     * <p>The portal and the woosh are drawn to clients now, but older versions built them from
     * real blocks, and a dial that glitched part-way could leave some behind. Nothing standing
     * there belongs to the gate, though a gate built underwater has ordinary water in those cells,
     * which is why clearing it is asked for rather than done.
     *
     * @param gate
     *            the gate
     * @return the liquid blocks, none while the gate is open
     */
    public static List<Block> strandedLiquid(final Stargate gate)
    {
        final List<Block> found = new ArrayList<>();
        if ((gate == null) || gate.isGateActive() || (gate.getGateWorld() == null))
        {
            return found;
        }
        final List<Location> cells = new ArrayList<>(gate.getGatePortalBlocks());
        for (int i = 0; i < StargateAnimator.wooshWaveCount(gate); i++)
        {
            final List<Location> wave = StargateAnimator.wooshWave(gate, i);
            if (wave != null)
            {
                cells.addAll(wave);
            }
        }
        final Set<String> seen = new HashSet<>();
        for (final Location cell : cells)
        {
            if (!seen.add(cell.getBlockX() + "," + cell.getBlockY() + "," + cell.getBlockZ()))
            {
                continue;
            }
            final Block block = gate.getGateWorld().getBlockAt(cell.getBlockX(), cell.getBlockY(), cell.getBlockZ());
            if ((block.getType() == Material.WATER) || (block.getType() == Material.LAVA))
            {
                found.add(block);
            }
        }
        return found;
    }

    /**
     * Clears the real water and lava standing in a closed gate's opening and woosh.
     *
     * @param gate
     *            the gate
     * @return how many blocks were cleared
     */
    public static int clearStrandedLiquid(final Stargate gate)
    {
        final List<Block> liquid = strandedLiquid(gate);
        for (final Block block : liquid)
        {
            block.setType(Material.AIR);
        }
        return liquid.size();
    }

    /**
     * Puts a set of drawn blocks back to whatever is really there.
     *
     * <p>Read from the world rather than remembered, which is the whole advantage of drawing:
     * there is no original to keep, because nothing was ever changed.
     *
     * @param gate
     *            the gate the blocks belong to
     * @param blocks
     *            the positions to put back
     */
    public static void undrawBlocks(final Stargate gate, final List<Location> blocks)
    {
        if ((blocks == null) || blocks.isEmpty())
        {
            return;
        }
        final List<Player> recipients = nearby(gate);
        if (recipients.isEmpty())
        {
            return;
        }
        for (final Location bc : blocks)
        {
            final Block real = gate.getGateWorld()
                .getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            final BlockData realData = real.getBlockData();
            for (final Player p : recipients)
            {
                p.sendBlockChange(real.getLocation(), realData);
            }
        }
    }

    /**
     * Redraws every open gate's portal for one player.
     *
     * <p>The portal is not a block in the world — the server keeps AIR there so travellers
     * do not drown or burn in it, and each nearby client is sent a block change to make it
     * look solid. That illusion lives only in the client's copy of the chunk, so anything
     * that hands the client a fresh copy erases it: walking far enough away and back,
     * relogging, changing worlds, or arriving by teleport. The client redraws the real
     * block, which is AIR, and the portal simply is not there any more.
     *
     * <p>It is also never sent to anyone who was out of range when the gate opened, which
     * is the common case for the far end of a trip: that gate opened while the traveller
     * was still standing at the near one.
     *
     * <p>Walking the open gates rather than all of them keeps this proportional to how many
     * portals are actually drawn, since it runs on every chunk boundary a player crosses.
     *
     * @param player
     *            the player to redraw for
     */
    public static void refreshPortalVisuals(final Player player)
    {
        if ((player == null) || !player.isOnline())
        {
            return;
        }
        final Location playerAt = player.getLocation();
        final Set<String> stillDrawn = new HashSet<>();

        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (!isNearEnoughToRedraw(gate, playerAt))
            {
                continue;
            }
            if (isLayered(gate))
            {
                // Iris and horizon, stacked from whichever side this player is on.
                sendLayeredTo(player, gate);
            }
            else if (gate.isGateIrisActive())
            {
                // A built iris, on a horizontal gate: the blocks are the iris, so all that is
                // owed is the horizon behind them.
                sendPortalBackdropTo(player, gate, true);
                sendIrisTo(player, gate);
            }
            else
            {
                sendPortalTo(player, gate);
            }
            stillDrawn.add(gate.getGateName());
        }

        drawIdleIrises(player, playerAt, stillDrawn);
        takeBackStaleLayers(player, playerAt);

        // Anything this player was shown that is not open to them any more has to be taken
        // back. The close-time send only reaches whoever was within range at that moment, and
        // a client keeps a drawing until something hands it a fresh copy of the chunk --
        // which walking one chunk away and back does not do, because the chunk never left.
        // Without this the portal stays on their screen with nothing behind it: water in a
        // gate that is off.
        final Set<String> showing = drawnFor(player);
        for (final String name : showing)
        {
            if (!stillDrawn.contains(name))
            {
                undrawFor(player, StargateManager.getStargate(name));
            }
        }
        showing.clear();
        showing.addAll(stillDrawn);
    }

    /**
     * Draws the shut iris of every idle gate near a player.
     *
     * <p>A shut iris is drawn whether or not there is a wormhole behind it, so the gates to
     * walk are not only the open ones: an idle gate sitting shut is invisible to the open-gate
     * loop, and its iris would be there for whoever was nearby when it closed and for nobody
     * else.
     *
     * @param player
     *            the player to draw for
     * @param playerAt
     *            where they are
     * @param stillDrawn
     *            the gates already drawn for them this pass, added to as more are
     */
    private static void drawIdleIrises(final Player player, final Location playerAt,
        final Set<String> stillDrawn)
    {
        for (final Stargate gate : StargateManager.getIrisGates())
        {
            // Registered as well: the set follows the flag, which a gate still being loaded has set.
            if (stillDrawn.contains(gate.getGateName()) || !StargateManager.isRegistered(gate)
                || !isNearEnoughToRedraw(gate, playerAt))
            {
                continue;
            }
            sendIrisTo(player, gate);
            stillDrawn.add(gate.getGateName());
        }
    }

    /**
     * Hands back the layers of any gate this player was layered for that is not layered now.
     *
     * <p>The close-time hand-back reaches whoever is within drawing range at that moment, and a
     * client holds a chunk far past that: somebody who walks away, lets the wormhole close
     * behind them and walks back keeps both layer cells. The horizon one is a sheet of water
     * hanging behind a gate. The iris one is worse -- a solid block in the cell travellers
     * arrive in, which their own client will not let them walk through.
     *
     * <p>The gates they have layers drawn for say which gates to ask about, so this costs
     * nothing for a player who has never been near a layered gate.
     *
     * @param player
     *            the player being refreshed
     * @param playerAt
     *            where they are
     */
    private static void takeBackStaleLayers(final Player player, final Location playerAt)
    {
        final java.util.Map<String, List<IrisLayering.Placement>> drawn =
            LAYER_DRAWN.get(player.getUniqueId());
        if ((drawn == null) || drawn.isEmpty())
        {
            return;
        }
        for (final String name : drawn.keySet().toArray(new String[0]))
        {
            final Stargate gate = StargateManager.getStargate(name);
            // Only once they are near enough to be drawn to again: reading a block in a chunk
            // nobody has loaded would pull it in, and they are owed nothing while out of range.
            if ((gate != null) && !isLayered(gate) && isNearEnoughToRedraw(gate, playerAt))
            {
                takeBackLayersFor(player, gate);
            }
        }
    }

    /**
     * Sends one player an open gate's horizon and lit chevrons.
     *
     * @param player
     *            the player to draw for
     * @param gate
     *            the open gate
     */
    private static void sendPortalTo(final Player player, final Stargate gate)
    {
        final BlockData blockData =
            MaterialUtils.drawnAcross(gate.getEffectivePortalMaterial(), gate.getGateFacing());
        for (final Location bc : gate.getGatePortalBlocks())
        {
            player.sendBlockChange(
                new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ()),
                blockData);
        }
        // The chevrons are a drawing too now, so somebody who arrives after the gate
        // dialled would otherwise find a lit wormhole in an unlit frame.
        if (gate.isGateLightsActive())
        {
            sendLights(player, gate, true);
        }
    }

    /**
     * Sends one player a gate's shut iris.
     *
     * <p>Only for an iris that is drawn. A horizontal gate's is real blocks, which the client
     * gets from the chunk like any other block and which drawing over would do nothing for.
     *
     * @param player
     *            the player to draw for
     * @param gate
     *            the gate whose iris is shut
     */
    private static void sendIrisTo(final Player player, final Stargate gate)
    {
        if (!irisIsDrawn(gate))
        {
            return;
        }
        // A player near enough to be drawn to is a player whose chunks are loaded, which is
        // the moment an older world's built iris can be taken out. It matches nothing once it
        // has run, so it costs a type check per cell after that.
        com.wormhole_xtreme.wormhole.logic.BuiltIrisUpgrade.clearLeftover(gate);
        final BlockData irisData =
            MaterialUtils.drawnAcross(gate.getEffectiveIrisMaterial(), gate.getGateFacing());
        for (final Location bc : gate.getGatePortalBlocks())
        {
            player.sendBlockChange(
                new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ()),
                irisData);
        }
        // Chevrons stay lit behind a shut iris on an open gate, and a player who arrives after
        // it shut is owed those too -- the portal path sends them, and this one skips it.
        if (gate.isGateLightsActive())
        {
            sendLights(player, gate, true);
        }
    }

    /**
     * The cells an open gate's event horizon is shown in while its iris is shut.
     *
     * <p>A gate's opening is one block thick on every shipped shape, so a closed iris fills it
     * and there is nowhere left inside the ring for the water to be. The cell one block behind
     * each portal cell is outside the gate and ordinarily empty, and showing the horizon there
     * puts it back where somebody can see it: through a glass iris from the front, and plainly
     * from behind.
     *
     * <p>Behind rather than in front on purpose. Moving the iris forward would work as well
     * geometrically, but the iris is real blocks -- it would have to find room among whatever a
     * player has built, and every check that asks where the barrier is would have to follow it.
     * The horizon is drawn to clients and collides with nothing, so it can go somewhere the
     * iris cannot.
     *
     * @param gate
     *            the gate
     * @return the cells, which may be empty
     */
    static List<Location> portalBackdropCells(final Stargate gate)
    {
        return offsetCells(gate, -1);
    }

    /**
     * The cells one block in front of each portal cell, where somebody behind the gate is shown
     * the iris.
     *
     * <p>The mirror of {@link #portalBackdropCells}. From behind, the horizon takes the ring and
     * the iris goes a block further off, so the layer nearer the viewer is always the one in
     * the ring and the far one never lands on their own side of the gate.
     *
     * @param gate
     *            the gate
     * @return the cells, in the same order as the portal cells; empty without a facing
     */
    static List<Location> portalForecourtCells(final Stargate gate)
    {
        return offsetCells(gate, 1);
    }

    /**
     * Each portal cell moved one block along the gate's facing, or against it.
     *
     * @param gate
     *            the gate
     * @param sign
     *            1 for along the facing, -1 for against it
     * @return the cells, index for index with the portal cells
     */
    private static List<Location> offsetCells(final Stargate gate, final int sign)
    {
        final List<Location> cells = new ArrayList<>();
        if ((gate == null) || (gate.getGateWorld() == null) || (gate.getGateFacing() == null))
        {
            return cells;
        }
        final BlockFace facing = gate.getGateFacing();
        for (final Location portal : gate.getGatePortalBlocks())
        {
            cells.add(new Location(gate.getGateWorld(),
                (double) portal.getBlockX() + (sign * facing.getModX()),
                (double) portal.getBlockY() + (sign * facing.getModY()),
                (double) portal.getBlockZ() + (sign * facing.getModZ())));
        }
        return cells;
    }

    /**
     * Whether somebody at this eye position is looking at a gate's front, the side the woosh
     * comes out of.
     *
     * <p>Measured from the middle of the first portal cell. Every shipped opening is one
     * block thick, so any cell gives the same answer. Standing in the plane itself counts as
     * the front, the view a gate has always had.
     *
     * @param gate
     *            the gate
     * @param eye
     *            the viewer's eye
     * @return true in front of the gate or in its plane, false behind it
     */
    static boolean seesFront(final Stargate gate, final Location eye)
    {
        final BlockFace facing = gate.getGateFacing();
        if ((facing == null) || (eye == null) || gate.getGatePortalBlocks().isEmpty())
        {
            return true;
        }
        final Location p = gate.getGatePortalBlocks().get(0);
        return IrisLayering.seesFront(facing,
            new IrisLayering.At(p.getBlockX(), p.getBlockY(), p.getBlockZ()),
            eye.getX(), eye.getY(), eye.getZ());
    }

    /**
     * Whether a backdrop cell is free to be drawn in.
     *
     * <p>Only air. A player who has built behind a gate should see what they built, not a
     * sheet of water over it, and a cell holding part of another gate is not this one's to
     * draw in either.
     *
     * @param at
     *            the cell
     * @return true if the horizon may be shown there
     */
    private static boolean backdropIsFree(final Location at)
    {
        final Block block = at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        if (!MaterialUtils.isAirMaterial(block.getType()))
        {
            return false;
        }
        // Air is not enough any more. A gate's opening is server-side air since its iris became
        // a drawing, so two gates built a block apart would draw their layers into each other's
        // rings -- and hand back air over the neighbour's iris when they were taken back.
        return !StargateManager.isPortalBlock(block);
    }

    /**
     * Shows or takes back the horizon behind a gate whose iris is shut.
     *
     * @param gate
     *            the gate
     * @param show
     *            true to draw it, false to hand the real blocks back
     */
    static void sendPortalBackdrop(final Stargate gate, final boolean show)
    {
        final List<Location> cells = portalBackdropCells(gate);
        if (cells.isEmpty())
        {
            return;
        }
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            if (isNearEnoughToRedraw(gate, player.getLocation()))
            {
                sendPortalBackdropTo(player, gate, show);
            }
        }
    }

    /**
     * The same, to one player.
     *
     * @param player
     *            who to show
     * @param gate
     *            the gate
     * @param show
     *            true to draw the horizon, false to hand the real blocks back
     */
    static void sendPortalBackdropTo(final Player player, final Stargate gate, final boolean show)
    {
        final List<Location> cells = portalBackdropCells(gate);
        if (cells.isEmpty())
        {
            return;
        }
        final BlockData horizon = show
            ? MaterialUtils.drawnAcross(gate.getEffectivePortalMaterial(), gate.getGateFacing()) : null;
        for (final Location at : cells)
        {
            if (!backdropIsFree(at))
            {
                continue;
            }
            player.sendBlockChange(at, (horizon != null) ? horizon
                : at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getBlockData());
        }
    }

    /**
     * Where each player's layers were last drawn, per layered gate.
     *
     * <p>A layered gate looks different from every angle it is seen from, not just from each
     * side of it: the far layer is only drawn where the opening hides it, so walking about in
     * front of one changes the picture too. Remembering the placements themselves is how a
     * move that changed nothing is told apart from one that did, without sending a packet to
     * find out.
     */
    private static final java.util.Map<java.util.UUID,
        java.util.Map<String, List<IrisLayering.Placement>>> LAYER_DRAWN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether a gate's opening is drawn in two layers, iris and horizon, and so differently
     * from each side.
     *
     * @param gate
     *            the gate
     * @return true for an open gate whose drawn iris is shut
     */
    static boolean isLayered(final Stargate gate)
    {
        return (gate != null) && gate.isGateActive() && gate.isGateIrisActive() && irisIsDrawn(gate);
    }

    /**
     * Sends one player a layered gate as it looks from where they stand.
     *
     * <p>From the front the iris is in the ring and the horizon a block behind it. From behind it
     * is the other way about: the horizon takes the ring and the iris goes a block further off.
     * The nearer layer is always the one in the ring, so neither ever lands on the viewer's own
     * side -- a drawn liquid there would give their client swim physics the server does not
     * agree with.
     *
     * <p>Only cells that are really air are drawn in, as {@link #backdropIsFree} explains, and
     * only where the opening itself hides the far layer from where they stand. A viewer whose
     * far cell is not free, or who has walked round far enough to see past the gate's own
     * sheet of blocks, gets the iris in the ring and nothing behind it. Every position this
     * viewer is not being drawn in is handed back, so a player who has just walked round the
     * gate is not left with both pictures.
     *
     * @param player
     *            the player to draw for
     * @param gate
     *            the gate, which should be layered
     */
    static void sendLayeredTo(final Player player, final Stargate gate)
    {
        sendLayeredTo(player, gate, player.getLocation());
    }

    /**
     * The same, judged from a given position rather than where the player is reported to be.
     *
     * @param player
     *            the player to draw for
     * @param gate
     *            the gate, which should be layered
     * @param from
     *            where the player is viewing from
     */
    static void sendLayeredTo(final Player player, final Stargate gate, final Location from)
    {
        if (gate.getGatePortalBlocks().isEmpty() || (gate.getGateFacing() == null))
        {
            return;
        }
        drawLayers(player, gate, layersFor(gate, from));
    }

    /**
     * Where every cell of a layered gate's opening puts its layers, for one viewing position.
     *
     * <p>Where the layers go is the same decision a preview makes, so it is made in one place;
     * this only asks it, cell by cell, and the answers are what gets drawn and what is
     * remembered to tell the next move apart.
     *
     * @param gate
     *            the gate, which should be layered
     * @param from
     *            where it is being looked at from
     * @return one placement per portal cell, in the portal cells' own order
     */
    private static List<IrisLayering.Placement> layersFor(final Stargate gate, final Location from)
    {
        final List<IrisLayering.Placement> layers = new ArrayList<>();
        final IrisLayering.Eye eye = new IrisLayering.Eye(from.getX(), from.getY(), from.getZ());
        final List<IrisLayering.At> opening = asPositions(gate.getGatePortalBlocks());
        final Set<IrisLayering.At> cover = new HashSet<>(opening);
        // The gate's own blocks are cover too. A structure block off the ring's plane can never
        // be what a sight line crosses, so the whole lot goes in without sorting them out.
        cover.addAll(asPositions(gate.getGateStructureBlocks()));
        final boolean stacked =
            IrisLayering.hidesFarLayers(opening, gate.getGateFacing(), eye, cover::contains);
        for (final IrisLayering.At cell : opening)
        {
            layers.add(IrisLayering.place(cell, gate.getGateFacing(), eye,
                at -> backdropIsFree(located(gate, at)), stacked));
        }
        return layers;
    }

    /**
     * Moves the wormhole drawn behind a see-through iris, for every gate that has one.
     *
     * <p>Only those gates. An iris that shows the real water needs nothing here -- water moves
     * on its own -- and the loop skips it before it looks at a single player.
     *
     * <p>One sweep over the open gates rather than a task per gate, as the ambient hum does.
     * The frame itself is {@link DrawnHorizon}'s, which a preview shares.
     */
    static void tickHorizon()
    {
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (isLayered(gate) && (gate.getGateWorld() != null)
                && DrawnHorizon.standsIn(gate.getEffectiveIrisMaterial()))
            {
                shimmerHorizon(gate);
            }
        }
    }

    /**
     * Redraws one gate's stand-in horizon for everybody holding one.
     *
     * <p>Only the cells each player was actually drawn in, read back from what they hold: a
     * viewer behind the gate has the real wormhole in the ring and is left alone, and so is
     * anybody the layers have collapsed to one for.
     *
     * @param gate
     *            the gate, whose iris is known to hide water
     */
    private static void shimmerHorizon(final Stargate gate)
    {
        final Material portal = gate.getEffectivePortalMaterial();
        final Material irisMaterial = gate.getEffectiveIrisMaterial();
        final List<Location> ring = gate.getGatePortalBlocks();
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            final java.util.Map<String, List<IrisLayering.Placement>> held =
                LAYER_DRAWN.get(player.getUniqueId());
            final List<IrisLayering.Placement> layers =
                (held == null) ? null : held.get(gate.getGateName());
            if (layers == null)
            {
                continue;
            }
            // The shorter of the two: these placements were recorded at draw time and the ring
            // is read now, and a gate reshaped to a smaller opening under somebody still holding
            // layers would otherwise walk off the end of it -- in a repeating task, so once per
            // shimmer until they moved. The draw that follows their next step puts it right.
            for (int i = 0; i < Math.min(layers.size(), ring.size()); i++)
            {
                final Location bc = ring.get(i);
                final IrisLayering.At cell =
                    new IrisLayering.At(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
                final IrisLayering.At where = layers.get(i).horizon();
                if ((where != null) && !where.equals(cell))
                {
                    player.sendBlockChange(located(gate, where),
                        MaterialUtils.drawnAcross(
                            DrawnHorizon.materialFor(portal, irisMaterial, cell, true),
                            gate.getGateFacing()));
                }
            }
        }
    }

    /**
     * Says what one draw decided, for working out why a gate looks wrong in a world.
     *
     * <p>Where the layers went cannot be seen from a screenshot: a wormhole that was never
     * drawn and one drawn where the client will not show it look exactly alike. One line per
     * draw at {@code log-level: FINE} tells the two apart.
     *
     * @param player
     *            who it was drawn for
     * @param gate
     *            the gate
     * @param layers
     *            what was drawn
     */
    private static void logLayers(final Player player, final Stargate gate,
        final List<IrisLayering.Placement> layers)
    {
        final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
        if ((plugin == null) || !plugin.isLoggable(Level.FINE) || layers.isEmpty())
        {
            return;
        }
        final IrisLayering.Placement first = layers.get(0);
        final StringBuilder dbg = new StringBuilder(256);
        dbg.append("Iris layers: Gate=").append(gate.getGateName());
        dbg.append(" For=").append(player.getName());
        dbg.append(" IrisMaterial=").append(gate.getEffectiveIrisMaterial());
        dbg.append(" PortalMaterial=").append(gate.getEffectivePortalMaterial());
        dbg.append(" Front=").append(seesFront(gate, player.getLocation()));
        dbg.append(" Cells=").append(layers.size());
        dbg.append(" WithHorizon=").append(layers.stream().filter(p -> p.horizon() != null).count());
        dbg.append(" FirstIris=").append(first.iris());
        dbg.append(" FirstHorizon=").append(first.horizon());
        final Location bc = gate.getGatePortalBlocks().get(0);
        final IrisLayering.At ring = new IrisLayering.At(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
        final IrisLayering.At back = ring.moved(gate.getGateFacing(), -1);
        dbg.append(" Behind=").append(describe(() -> located(gate, back).getBlock().getType()))
            .append('/').append(backdropIsFree(located(gate, back)) ? "free" : "taken");
        plugin.prettyLog(Level.FINE, dbg.toString());
    }

    /**
     * Sends one player a set of placements, and remembers it as theirs.
     *
     * @param player
     *            the player to draw for
     * @param gate
     *            the gate
     * @param layers
     *            the placements, one per portal cell
     */
    private static void drawLayers(final Player player, final Stargate gate,
        final List<IrisLayering.Placement> layers)
    {
        // A player near enough to be drawn to is a player whose chunks are loaded, which is the
        // moment an older world's built iris can be taken out. The unlayered path does it in
        // sendIrisTo; a layered gate reaches neither that nor fillGateIris again.
        com.wormhole_xtreme.wormhole.logic.BuiltIrisUpgrade.clearLeftover(gate);
        final Material irisMaterial = gate.getEffectiveIrisMaterial();
        final Material portalMaterial = gate.getEffectivePortalMaterial();
        final BlockData iris = MaterialUtils.drawnAcross(irisMaterial, gate.getGateFacing());
        final List<Location> ring = gate.getGatePortalBlocks();
        for (int i = 0; i < layers.size(); i++)
        {
            final Location bc = ring.get(i);
            final IrisLayering.At cell =
                new IrisLayering.At(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            final IrisLayering.Placement placed = layers.get(i);
            // Handed back first: a cell this viewer is no longer drawn in has to stop showing
            // the old layer before the new one is sent, or a redraw that moves a layer one way
            // and takes it back the other leaves the take-back on top.
            for (final IrisLayering.At back : IrisLayering.handBacks(cell, gate.getGateFacing(),
                placed.iris(), placed.horizon()))
            {
                sendTruthIfFree(player, located(gate, back));
            }
            if (placed.iris() != null)
            {
                player.sendBlockChange(located(gate, placed.iris()), iris);
            }
            if (placed.horizon() != null)
            {
                // Behind the iris it is drawn as a look-alike where the iris would hide the
                // liquid; in the ring, which is where a viewer behind the gate gets it, the
                // real thing has air in front of it and is drawn as it always was. Which of
                // those it is, is DrawnHorizon's to decide -- asking here as well would be the
                // same rule in two places, and one of them would eventually be wrong.
                player.sendBlockChange(located(gate, placed.horizon()),
                    MaterialUtils.drawnAcross(DrawnHorizon.materialFor(portalMaterial, irisMaterial,
                        cell, !placed.horizon().equals(cell)), gate.getGateFacing()));
            }
        }
        if (gate.isGateLightsActive())
        {
            sendLights(player, gate, true);
        }
        logLayers(player, gate, layers);
        layersDrawnFor(player).put(gate.getGateName(), layers);
    }

    /**
     * Sends a layered gate to everybody near enough to see it, each from their own side.
     *
     * @param gate
     *            the gate
     */
    static void sendLayered(final Stargate gate)
    {
        if (!isLayered(gate) || (gate.getGateWorld() == null))
        {
            return;
        }
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            if (isNearEnoughToRedraw(gate, player.getLocation()))
            {
                sendLayeredTo(player, gate);
                drawnFor(player).add(gate.getGateName());
            }
        }
    }

    /**
     * Shows or takes back the far layer for some of a gate's cells, without touching the ring.
     *
     * <p>A sweep draws the iris a ring at a time, and the layers used to be stacked only once
     * it had finished. Behind an opaque iris that is invisible. Behind a see-through one every
     * ring that arrived was a pane of glass with nothing behind it, so a gate spent its whole
     * animation showing the landscape through its own iris; opening, the same the other way
     * round, because the far layer came down before the first ring uncovered.
     *
     * <p>So the far layer follows the sweep, ring by ring: it arrives with the iris that covers
     * it and leaves with the iris that uncovers it, and the wormhole in the ring is never
     * touched by any of it. The ring is the sweep's own to paint.
     *
     * <p>Only the far layer, and only for whoever has one. A viewer behind the gate keeps the
     * horizon in the ring, which is what the sweep is already painting there, so they are left
     * alone rather than sent a second copy of it.
     *
     * @param gate
     *            the gate, whose iris is sweeping
     * @param only
     *            the ring cells this step covers, or null for all of them
     * @param show
     *            true as a ring is covered, false as one is uncovered
     */
    static void horizonBehind(final Stargate gate, final List<Location> only, final boolean show)
    {
        // Not isLayered, which asks whether the iris is shut. An opening sweep runs with that
        // flag already cleared -- setIrisState writes it before it draws -- so asking would have
        // refused the hand-back on every ring of every open, and the stand-in sat behind the
        // uncovered rings until the sweep ended. What matters is that there are layers to move:
        // a wormhole to draw, and an iris that is drawn rather than built.
        if ((gate == null) || !gate.isGateActive() || !irisIsDrawn(gate) || (gate.getGateWorld() == null))
        {
            return;
        }
        final Material portal = gate.getEffectivePortalMaterial();
        final Material irisMaterial = gate.getEffectiveIrisMaterial();
        final List<Location> ring = gate.getGatePortalBlocks();
        final Set<IrisLayering.At> wanted = (only == null) ? null : new HashSet<>(asPositions(only));
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            if (isNearEnoughToRedraw(gate, player.getLocation()))
            {
                horizonBehindFor(player, gate, ring, wanted, show, portal, irisMaterial);
            }
        }
    }

    /**
     * The same for one player, whose own side decides whether they have a far layer at all.
     *
     * @param player
     *            the player
     * @param gate
     *            the gate
     * @param ring
     *            its portal cells, in placement order
     * @param wanted
     *            the cells this step covers, or null for all of them
     * @param show
     *            true to draw the far layer, false to hand it back
     * @param portal
     *            the gate's portal material
     * @param irisMaterial
     *            what its iris is drawn in
     */
    private static void horizonBehindFor(final Player player, final Stargate gate,
        final List<Location> ring, final Set<IrisLayering.At> wanted, final boolean show,
        final Material portal, final Material irisMaterial)
    {
        final List<IrisLayering.Placement> layers = layersFor(gate, player.getLocation());
        for (int i = 0; i < layers.size(); i++)
        {
            final Location bc = ring.get(i);
            final IrisLayering.At cell =
                new IrisLayering.At(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            final IrisLayering.At where = layers.get(i).horizon();
            if ((where == null) || where.equals(cell) || ((wanted != null) && !wanted.contains(cell)))
            {
                continue;
            }
            if (show)
            {
                player.sendBlockChange(located(gate, where),
                    MaterialUtils.drawnAcross(
                        DrawnHorizon.materialFor(portal, irisMaterial, cell, true),
                        gate.getGateFacing()));
            }
            else
            {
                sendTruthIfFree(player, located(gate, where));
            }
        }
    }

    /**
     * Hands the real blocks back in both layer positions, for everybody near the gate.
     *
     * <p>Called whenever a gate stops being layered: its iris opens, or it goes idle with the
     * iris still shut. Before this the horizon drawn behind a shut iris was never taken back
     * when the wormhole closed under it, so a gate that went idle kept a sheet of water hanging
     * behind it for anyone who had been watching.
     *
     * @param gate
     *            the gate
     */
    static void takeBackLayers(final Stargate gate)
    {
        if ((gate == null) || (gate.getGateWorld() == null))
        {
            return;
        }
        for (final Player player : gate.getGateWorld().getPlayers())
        {
            if (isNearEnoughToRedraw(gate, player.getLocation()))
            {
                takeBackLayersFor(player, gate);
            }
        }
    }

    /**
     * The same, for one player.
     *
     * @param player
     *            the player
     * @param gate
     *            the gate
     */
    private static void takeBackLayersFor(final Player player, final Stargate gate)
    {
        for (final Location at : portalBackdropCells(gate))
        {
            sendTruthIfFree(player, at);
        }
        for (final Location at : portalForecourtCells(gate))
        {
            sendTruthIfFree(player, at);
        }
        layersDrawnFor(player).remove(gate.getGateName());
    }

    /**
     * Redraws any layered gate a player has just changed their view of.
     *
     * <p>Called on moves that change block. Walks the open gates, which is a short list, works
     * out what each would look like from where the step ended, and sends nothing unless that
     * differs from what the player is already holding. Crossing the plane is the loud case; a
     * step sideways that takes the far layer out from behind the opening is the quiet one, and
     * before this noticed it the layer stayed drawn where it could be seen beside the gate.
     *
     * @param player
     *            the player who moved
     * @param to
     *            where the move ends, which the player is not yet reported at while the move
     *            event is being handled
     */
    public static void relayerFor(final Player player, final Location to)
    {
        if ((player == null) || (to == null) || !player.isOnline())
        {
            return;
        }
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (!isLayered(gate) || !isNearEnoughToRedraw(gate, to))
            {
                continue;
            }
            // Read here rather than before the loop: this runs on every step every player takes,
            // and filing an empty map for somebody who is nowhere near a layered gate is an
            // allocation on the hottest event there is.
            // Feet rather than eye: only upright gates are layered, and neither which side of
            // one a player is on nor what it hides depends much on how tall they are.
            final List<IrisLayering.Placement> now = layersFor(gate, to);
            if (!now.equals(layersDrawnFor(player).get(gate.getGateName())))
            {
                drawLayers(player, gate, now);
                drawnFor(player).add(gate.getGateName());
            }
        }
    }

    /**
     * The layers one player has been drawn, per layered gate.
     *
     * <p>No guard on the uuid, unlike {@link #drawnFor}: Bukkit declares it non-null, and Sonar
     * refuses a check that can never fire in code this new. The older one keeps its guard, and
     * its suppression, because tests were written against it.
     *
     * @param player
     *            the player
     * @return their live map, created empty if this is the first time
     */
    private static java.util.Map<String, List<IrisLayering.Placement>> layersDrawnFor(final Player player)
    {
        return LAYER_DRAWN.computeIfAbsent(player.getUniqueId(),
            key -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    /**
     * A layering position as a location in the gate's world.
     *
     * @param gate
     *            the gate
     * @param at
     *            the position
     * @return the location
     */
    private static Location located(final Stargate gate, final IrisLayering.At at)
    {
        return new Location(gate.getGateWorld(), at.x(), at.y(), at.z());
    }

    /**
     * Locations as plain coordinates, for asking whether a sight line still crosses them.
     *
     * @param blocks
     *            the locations
     * @return their positions, in the same order
     */
    private static List<IrisLayering.At> asPositions(final List<Location> blocks)
    {
        final List<IrisLayering.At> cells = new ArrayList<>(blocks.size());
        for (final Location block : blocks)
        {
            cells.add(new IrisLayering.At(block.getBlockX(), block.getBlockY(), block.getBlockZ()));
        }
        return cells;
    }

    /**
     * Sends one player the real block in a cell, if the cell is one a layer could be drawn in.
     *
     * @param player
     *            the player
     * @param at
     *            the cell
     */
    private static void sendTruthIfFree(final Player player, final Location at)
    {
        if (backdropIsFree(at))
        {
            player.sendBlockChange(at,
                at.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).getBlockData());
        }
    }

    /**
     * Shows one player the real blocks behind a portal they are still being shown.
     *
     * <p>Read from the world rather than assumed to be air: a gate that closed onto a default
     * iris has real blocks in those positions, and calling them air would swap one wrong
     * picture for another.
     *
     * @param player
     *            the player to correct
     * @param gate
     *            the gate to take back, or null if it has been removed since
     */
    private static void undrawFor(final Player player, final Stargate gate)
    {
        if ((gate == null) || (gate.getGateWorld() == null)
            || !gate.getGateWorld().equals(player.getWorld()))
        {
            return;
        }
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block real = gate.getGateWorld()
                .getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            player.sendBlockChange(real.getLocation(), real.getBlockData());
        }
        // The layers a block either side of the ring are drawings too, and outlive the ring's
        // if nobody takes them back.
        takeBackLayersFor(player, gate);
        sendLights(player, gate, false);
    }

    /**
     * Sends one player every light block of a gate.
     *
     * <p>Lighting here uses the same per-position rule as {@link #drawLights}, and has to:
     * this is the path that catches somebody arriving at a gate that dialled while they were
     * out of range, so a lamp chevron the animation switched on must not come back to a later
     * arrival as glowstone.
     *
     * @param player
     *            the player to send to
     * @param gate
     *            the gate whose chevrons these are
     * @param lit
     *            true to show the chevrons lit, false to show whatever is really there
     */
    private static void sendLights(final Player player, final Stargate gate, final boolean lit)
    {
        final List<java.util.List<Location>> groups = gate.getGateLightBlocks();
        if (groups == null)
        {
            return;
        }
        final BlockData lightData = lit ? MaterialUtils.drawnAs(gate.getEffectiveLightMaterial()) : null;
        final Material chevronMaterial = lit ? gate.getEffectiveChevronMaterial() : null;
        final BlockData fixtureOn = lit ? MaterialUtils.litFormOf(chevronMaterial) : null;

        // The other-world chevron shows only on a link to another world, or while waiting for /dial.
        final int lastShown = lit ? StargateAnimator.lastShownWave(gate, groups) : (groups.size() - 1);
        for (int i = 0; i < groups.size(); i++)
        {
            final java.util.List<Location> group = groups.get(i);
            if ((group == null) || (i > lastShown))
            {
                continue;
            }
            for (final Location bc : group)
            {
                final Block real = gate.getGateWorld()
                    .getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
                player.sendBlockChange(real.getLocation(),
                    lit ? litChevron(real.getType(), chevronMaterial, fixtureOn, lightData)
                        : real.getBlockData());
            }
        }
    }

    /**
     * Which gates a player is currently being shown a portal for.
     *
     * <p>Remembered rather than worked out, so correcting a stale drawing costs only what was
     * actually drawn for them. The alternative is walking every gate in the world on each
     * chunk boundary somebody crosses, and most gates are nowhere near anybody.
     *
     * @param player
     *            the player
     * @return their live set of gate names, created empty if this is the first time
     */
    // Never null on a server; mock players with no UUID would otherwise throw from the map.
    @SuppressWarnings("java:S2583")
    private static Set<String> drawnFor(final Player player)
    {
        final java.util.UUID uuid = player.getUniqueId();
        if (uuid == null)
        {
            // No identity to file it under, so there is nothing to remember between calls.
            // A throwaway set keeps every caller free of null checks.
            return new HashSet<>();
        }
        return DRAWN.computeIfAbsent(uuid, key -> new HashSet<>());
    }

    /**
     * Forgets what a player was being shown.
     *
     * <p>Called when they leave, because the map is keyed by uuid and would otherwise hold an
     * entry for everyone who has ever walked past a gate.
     *
     * @param uuid
     *            the player who has gone
     */
    public static void forgetDrawn(final java.util.UUID uuid)
    {
        DRAWN.remove(uuid);
        LAYER_DRAWN.remove(uuid);
    }

    /**
     * Whether one open gate's portal should be drawn for a player standing at a location.
     *
     * <p>Split out from {@link #refreshPortalVisuals(Player)} so the decision can be tested
     * without a live server: everything past this point needs {@code createBlockData()},
     * which does not work off a running Bukkit instance.
     *
     * @param gate
     *            an open gate
     * @param playerAt
     *            where the player is
     * @return true if the gate's portal blocks should be sent to that player
     */
    static boolean shouldRedrawFor(final Stargate gate, final Location playerAt)
    {
        // Redrawing the portal here would paint water over the iris the gate is currently
        // shut with. What such a gate shows instead is the iris and the backdrop behind it,
        // which refreshPortalVisuals asks for separately.
        return !gate.isGateIrisActive() && isNearEnoughToRedraw(gate, playerAt);
    }

    /**
     * Whether a player is close enough to a gate to be sent its drawing.
     *
     * @param gate
     *            the gate
     * @param playerAt
     *            where the player is
     * @return true if they are near enough, in the same world
     */
    static boolean isNearEnoughToRedraw(final Stargate gate, final Location playerAt)
    {
        if ((gate == null) || (playerAt == null))
        {
            return false;
        }
        if ((gate.getGateWorld() == null) || !gate.getGateWorld().equals(playerAt.getWorld()))
        {
            return false;
        }
        final List<Location> portalBlocks = gate.getGatePortalBlocks();
        if (portalBlocks.isEmpty())
        {
            return false;
        }
        // Portal blocks all sit within a gate-sized box, so distance to any one of them
        // decides the whole gate, the same way the open-time send picks its recipients.
        final Location reference = new Location(gate.getGateWorld(),
            portalBlocks.get(0).getBlockX(), portalBlocks.get(0).getBlockY(), portalBlocks.get(0).getBlockZ());
        return playerAt.distanceSquared(reference) <= (VISUAL_RADIUS * VISUAL_RADIUS);
    }

    /**
     * Sets all portal blocks to {@link Material#AIR}, both on the server and on
     * nearby clients, clearing any client-side portal visual still being shown.
     *
     * @param gate the gate
     */
    static void deletePortalBlocks(final Stargate gate)
    {
        fillGateInterior(gate, Material.AIR);
    }

    /**
     * Opens or clears the portal interior.
     * <p>
     * The server-side block is always {@link Material#AIR} so travellers standing in
     * an active portal are not subject to the portal material's physics — no drowning
     * or buoyancy in a water portal, no burning in a lava one. {@code material} is
     * what nearby clients are shown instead, so the portal still looks solid.
     * <p>
     * A vertical gate's iris is drawn through here too, with the iris material in place of
     * the portal's: see {@link #fillGateIris(Stargate, Material)} for which irises are
     * drawn and which are built.
     *
     * @param gate     the gate
     * @param material the appearance to show clients; {@link Material#AIR} clears the portal
     */
    static void fillGateInterior(final Stargate gate, final Material material)
    {
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(Material.AIR);
        }
        sendPortalVisual(gate, material);
    }

    /**
     * Whether a gate's iris is drawn to clients rather than built out of real blocks.
     *
     * <p>A vertical gate's iris is a wall, and a wall is something a client stops itself at:
     * the drawing is enough to stand at, and the refusals in the move, entity and projectile
     * paths are what actually hold it shut. Drawing it means a crash leaves nothing behind,
     * and means it can be moved about for the viewer, which real blocks in one set of cells
     * can never be.
     *
     * <p>A horizontal gate's iris is a floor, and a floor is not something a client can hold
     * up on its own: to the server the player is then standing on air, which is a kick for
     * flying on a server that does not allow it. Those stay real.
     *
     * @param gate
     *            the gate
     * @return true if this gate's iris is a drawing
     */
    static boolean irisIsDrawn(final Stargate gate)
    {
        final BlockFace facing = (gate == null) ? null : gate.getGateFacing();
        return (facing != null) && (facing != BlockFace.UP) && (facing != BlockFace.DOWN);
    }

    /**
     * Puts the iris over a gate's opening, as a drawing or as real blocks.
     *
     * <p>Drawn for a vertical gate, in the same way and through the same bookkeeping as the
     * portal: the server keeps AIR in the opening and every nearby client is sent the iris
     * material for those cells. Built out of real blocks for a horizontal one, where the iris
     * has to hold a player's weight up. {@link #irisIsDrawn} has the reasoning for both.
     *
     * @param gate     the gate
     * @param material the iris material to show or to place
     */
    static void fillGateIris(final Stargate gate, final Material material)
    {
        clearIrisPath(gate);
        if (irisIsDrawn(gate))
        {
            // Air on the server, iris on the client. fillGateInterior already does exactly
            // that, and going through it keeps the drawn-for bookkeeping in one place, so
            // the iris is taken back from a client by the same path the portal is.
            fillGateInterior(gate, material);
            return;
        }
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(material);
        }
    }

    /**
     * Moves anyone standing in the gate opening clear before the iris covers it.
     *
     * <p>A horizontal gate's iris is real blocks, and {@link #fillGateIris} places them
     * without looking at who was there. Every path that closes an iris can therefore land
     * solid blocks inside a player: the gate shutting down onto an iris that defaults closed,
     * an activation timing out, someone flipping the iris lever, or an IDC being cleared. A
     * traveller walking into the event horizon as the far gate times out is the case that
     * gets reported, because it needs no bad timing on anyone's part -- the shutdown timer
     * picks the moment.
     *
     * <p>A drawn iris cannot bury anybody, and is cleared for anyway: a player left inside one
     * is a player standing in the middle of what everyone else sees as a shut gate.
     *
     * <p>The iris still closes. It is a barrier, and one that could be held open by standing
     * in it would be worth nothing; the occupants are moved rather than the closure refused.
     * They go to the gate's own arrival point, which the shape file already places one block
     * outside the portal, and {@code findSafePlayerLocation} corrects it for terrain that has
     * changed since. A player also gets a few ticks of damage immunity, which is what the
     * remote-iris-locked path in the move listener does for the same reason: the block placed
     * on the tick they leave should not still be able to reach them.
     *
     * <p>Only living entities are moved. Dropped items and arrows do not suffocate, and
     * teleporting a gate's worth of loose items to the exit every time an iris closes would
     * be a surprise of its own.
     *
     * @param gate the gate whose iris is about to close
     */
    static void clearIrisPath(final Stargate gate)
    {
        final org.bukkit.World world = gate.getGateWorld();
        final org.bukkit.util.BoundingBox bounds = gate.getGatePortalBounds();
        final Location exit = gate.getGatePlayerTeleportLocation();
        if (world == null || bounds == null || exit == null)
        {
            return;
        }

        // One query for the whole opening, the same shape as the entity sweep: the box
        // encloses the ring, so candidates are still confirmed against the portal blocks.
        final java.util.Collection<org.bukkit.entity.Entity> candidates = world.getNearbyEntities(bounds);
        if (candidates.isEmpty())
        {
            return;
        }

        Location safe = null;
        for (final org.bukkit.entity.Entity entity : candidates)
        {
            try
            {
                // Only a living thing can suffocate, and only one standing where the iris
                // is about to be is in the way.
                if (!(entity instanceof org.bukkit.entity.LivingEntity)
                    || !isInIrisPath(gate, entity.getLocation()))
                {
                    continue;
                }

                // Deferred until someone is actually in the way: the safe-location search
                // reads the world, and an iris closing on an empty gate is the normal case.
                if (safe == null)
                {
                    safe = WorldUtils.findSafePlayerLocation(exit);
                }
                entity.teleport(safe);
                if (entity instanceof Player traveller)
                {
                    traveller.setNoDamageTicks(5);
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Moved " + entity.getType() + " clear of closing iris on gate: " + gate.getGateName());
            }
            catch (final RuntimeException t)
            {
                // One entity that cannot be moved does not stop the iris closing, or stop
                // the rest of the sweep. Errors are left to propagate rather than being
                // swallowed here, where they would look like an ordinary immovable mob.
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                    "Failed to move " + entity.getType() + " clear of closing iris on gate: "
                        + gate.getGateName(), t);
            }
        }
    }

    /**
     * Whether an entity at this location would have iris blocks placed inside it.
     *
     * <p>Both the block the entity stands in and the one above it are checked. A player
     * whose feet are on the block below the opening still has their head in the lowest
     * portal block, and the head is the half that suffocates -- testing the feet alone,
     * which is all the entity sweep needs for deciding who travels, would walk straight
     * past the person most likely to be hurt.
     *
     * <p>Pulled out as its own function because it is the whole decision. What surrounds it
     * -- querying the world for entities, teleporting them -- needs a live server; this does
     * not, and it is the part that can be wrong.
     *
     * @param gate the gate whose iris is closing
     * @param at   where the entity is
     * @return true if the closing iris would occupy the entity's own space
     */
    static boolean isInIrisPath(final Stargate gate, final Location at)
    {
        if (gate == null || at == null)
        {
            return false;
        }
        return gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ())
            || gate.isGatePortalBlockAt(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ());
    }

    // -----------------------------------------------------------------------
    // Dial lever state & redstone power
    // -----------------------------------------------------------------------

    /**
     * Updates the DHD lever/button block to reflect the current gate activation
     * state. Automatically replaces buttons with levers so the lever can be
     * held in the "on" position.
     *
     * @param gate       the gate
     * @param regenerate {@code true} to forcibly replace a missing lever
     */
    static void toggleDialLeverState(final Stargate gate, final boolean regenerate)
    {
        if (gate.getGateDialLeverBlock() != null)
        {
            if (gate.isGateActive())
            {
                WorldUtils.scheduleChunkLoad(gate.getGateDialLeverBlock());
            }
            org.bukkit.Material mat = Material.AIR;
            try
            {
                mat = gate.getGateDialLeverBlock().getType();
            }
            catch (final RuntimeException t)
            {
                mat = Material.AIR;
            }

            // Only create a lever if the activation holder is empty. Preserve the player's
            // placed activation item (button/lever) otherwise.
            if (regenerate && (mat == Material.AIR))
            {
                gate.getGateDialLeverBlock().setType(Material.LEVER);
                final Directional rld = (Directional) gate.getGateDialLeverBlock().getBlockData();
                rld.setFacing(gate.getGateFacing());
                gate.getGateDialLeverBlock().setBlockData(rld);
                mat = gate.getGateDialLeverBlock().getType();
            }

            // Preserve whatever activation the player placed.  If it's a lever,
            // update its powered state; do not convert buttons to levers.
            if (mat == Material.LEVER)
            {
                // Flipping this lever fires BlockRedstoneEvent straight back at the redstone
                // listener, for the lever and for everything it powers. Marked as ours so the
                // listener does not read the gate opening as somebody pressing the button --
                // which dialled a sign gate twice. See GateRedstoneWrite.
                GateRedstoneWrite.begin();
                try
                {
                    final Powerable llp = (Powerable) gate.getGateDialLeverBlock().getBlockData();
                    llp.setPowered(gate.isGateActive());
                    gate.getGateDialLeverBlock().setBlockData(llp);
                }
                catch (final RuntimeException ignore)
                {
                    // A lever that refuses the write leaves the gate's light wrong, which is
                    // cosmetic. Errors are left to propagate; the finally still clears the
                    // guard either way, so a failure here cannot wedge the redstone listener.
                }
                finally
                {
                    GateRedstoneWrite.end();
                }
            }
            if (!gate.isGateActive())
            {
                WorldUtils.scheduleChunkUnload(gate.getGateDialLeverBlock());
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE,
                "Dial Button Lever Gate: \"" + gate.getGateName() + "\" Material: \"" + mat.toString() + "\"");
        }
    }

    /**
     * Pulses the gate-activated redstone output lever to match the gate's
     * current activation state.
     *
     * @param gate the gate
     */
    static void toggleRedstoneGateActivatedPower(final Stargate gate)
    {
        if (gate.isGateRedstonePowered()
            && (gate.getGateRedstoneGateActivatedBlock() != null)
            && (gate.getGateRedstoneGateActivatedBlock().getType() == Material.LEVER))
        {
            // The listener already refuses this lever as a trigger by position, but not the
            // conductors it powers, and on a small shape those can touch the DHD. Marked as
            // ours for the same reason the dial lever is.
            GateRedstoneWrite.begin();
            try
            {
                final Powerable rp = (Powerable) gate.getGateRedstoneGateActivatedBlock().getBlockData();
                rp.setPowered(gate.isGateActive());
                gate.getGateRedstoneGateActivatedBlock().setBlockData(rp);
            }
            finally
            {
                GateRedstoneWrite.end();
            }
        }
    }
}
