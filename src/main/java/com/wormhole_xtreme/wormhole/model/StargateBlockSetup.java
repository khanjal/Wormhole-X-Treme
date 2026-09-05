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
        if (placeBlock == null)
        {
            return;
        }

        if (create)
        {
            final WormholeXTreme _plugin_for_log = WormholeXTreme.getThisPlugin();
            if (_plugin_for_log != null)
            {
                final StringBuilder dbg = new StringBuilder(256);
                dbg.append("Sign placement: Gate=").append(gate.getGateName());

                try
                {
                    final org.bukkit.Location nhLoc = nameSign.getLocation();
                    dbg.append(" NameHolderLoc=").append(nhLoc != null ? nhLoc.toString() : "null");
                }
                catch (final Exception e)
                {
                    dbg.append(" NameHolderLoc=null");
                }

                dbg.append(" GateFacing=").append(toward != null ? toward.toString() : "null");

                try
                {
                    final org.bukkit.Location pbLoc = placeBlock != null ? placeBlock.getLocation() : null;
                    dbg.append(" PlaceBlock=").append(pbLoc != null ? pbLoc.toString() : "null");
                }
                catch (final Exception e)
                {
                    dbg.append(" PlaceBlock=null");
                }

                Material pbType = null;
                try
                {
                    pbType = placeBlock != null ? placeBlock.getType() : null;
                }
                catch (final Throwable t)
                {
                    pbType = null;
                }
                dbg.append(" PlaceBlockType=").append(pbType != null ? pbType.toString() : "null");

                final BlockFace[] facesToCheck = new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                    BlockFace.UP, BlockFace.DOWN };
                for (final BlockFace f : facesToCheck)
                {
                    Block n = null;
                    try
                    {
                        n = nameSign.getRelative(f);
                    }
                    catch (final Exception e)
                    {
                        n = null;
                    }
                    String nType = "null";
                    String nLoc = "null";
                    if (n != null)
                    {
                        try
                        {
                            final Material t = n.getType();
                            nType = t != null ? t.toString() : "null";
                        }
                        catch (final Exception e)
                        {
                            nType = "null";
                        }
                        try
                        {
                            final org.bukkit.Location nl = n.getLocation();
                            nLoc = nl != null ? nl.toString() : "null";
                        }
                        catch (final Exception e)
                        {
                            nLoc = "null";
                        }
                    }
                    dbg.append(' ').append(f.toString()).append("=[").append(nType).append("@").append(nLoc).append("]");
                }

                try
                {
                    final org.bukkit.block.data.BlockData bd = nameSign.getBlockData();
                    if (bd instanceof Directional)
                    {
                        final Directional d = (Directional) bd;
                        dbg.append(" NameHolderFacing=").append(d.getFacing() != null ? d.getFacing().toString() : "null");
                    }
                }
                catch (final Exception e)
                {
                    // ignore
                }

                _plugin_for_log.prettyLog(Level.INFO, false, dbg.toString());
            }

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
        else
        {
            if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(placeBlock.getType()))
            {
                final WormholeXTreme _plugin_for_log = WormholeXTreme.getThisPlugin();
                if (_plugin_for_log != null)
                {
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
                    _plugin_for_log.prettyLog(Level.INFO, false, dbg.toString());
                }
                gate.getGateStructureBlocks().remove(placeBlock.getLocation());
                placeBlock.setType(Material.AIR);
            }
        }
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
            if (oldData instanceof Directional)
            {
                facing = ((Directional) oldData).getFacing();
            }

            signBlock.setType(want, false);

            if (facing != null)
            {
                final BlockData newData = signBlock.getBlockData();
                if (newData instanceof Directional)
                {
                    ((Directional) newData).setFacing(facing);
                    signBlock.setBlockData(newData, false);
                }
            }

            final org.bukkit.block.BlockState after = signBlock.getState();
            if (after instanceof Sign)
            {
                final Sign fresh = (Sign) after;
                restoreSignSide(fresh.getSide(Side.FRONT), frontLines, frontGlows);
                restoreSignSide(fresh.getSide(Side.BACK), backLines, backGlows);
                fresh.update(true, false);
                // The gate holds this state and writes destinations through it, so leaving
                // the old one in place would send every later write at a block that is gone.
                gate.setGateDialSign(fresh);
            }
        }
        catch (final Throwable t)
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, false,
                    "Could not match dial sign material on gate " + gate.getGateName() + ": " + t.getMessage());
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
        if ((gate.getGateIrisLeverBlock() == null)
            && (gate.getGateShape() != null)
            && !((gate.getGateShape() instanceof Stargate3DShape) && ((Stargate3DShape) gate.getGateShape()).isShapeRedstoneActivated()))
        {
            final Block button = gate.getGateDialLeverBlock();
            if (button != null)
            {
                // The button is a wall-mounted button on one face of the DHD column.
                // Its Directional facing tells us WHICH direction the button face points,
                // so inverse(buttonFacing) is the direction toward the DHD column backing block.
                //
                // Algorithm (mirrors how the sign uses nameHolder → nameHolder.getRelative(gateFacing)):
                //   backing  = button.getRelative(inverse(buttonFacing))   — DHD column top
                //   dhdBase  = backing.getRelative(DOWN)                   — DHD column base
                //   irisBlock = dhdBase.getRelative(gateFacing)            — front face of DHD base
                //
                // gateFacing (not buttonFacing) is used for the final step so the lever
                // faces toward the player standing in front of the gate.
                BlockFace buttonFacing = gate.getGateFacing(); // fallback if block data unavailable
                final org.bukkit.block.data.BlockData bd = button.getBlockData();
                if (bd instanceof Directional)
                {
                    buttonFacing = ((Directional) bd).getFacing();
                }
                final Block backing = button.getRelative(WorldUtils.getInverseDirection(buttonFacing));
                final Block dhdBase = backing.getRelative(BlockFace.DOWN);
                final Block irisBlock = dhdBase.getRelative(gate.getGateFacing());
                // Do not claim the iris position if it is already occupied by an RD or RS
                // redstone-activation block (e.g. StandardSignDialRedstone places [S:RD]
                // directly below [S:A], which is exactly where this algorithm lands).
                final Block rdBlock = gate.getGateRedstoneDialActivationBlock();
                final Block rsBlock = gate.getGateRedstoneSignActivationBlock();
                final boolean collidesRd = (rdBlock != null) && WorldUtils.isSameBlock(rdBlock, irisBlock);
                final boolean collidesRs = (rsBlock != null) && WorldUtils.isSameBlock(rsBlock, irisBlock);
                if (!collidesRd && !collidesRs)
                {
                    gate.setGateIrisLeverBlock(irisBlock);
                }
            }
        }
        if (gate.getGateIrisLeverBlock() != null)
        {
            if (create)
            {
                final Block iris = gate.getGateIrisLeverBlock();
                final WormholeXTreme _plugin_for_log = WormholeXTreme.getThisPlugin();
                if (_plugin_for_log != null)
                {
                    final StringBuilder dbg = new StringBuilder(128);
                    dbg.append("Iris lever placement: Gate=").append(gate.getGateName());
                    try
                    {
                        final org.bukkit.Location dialLoc = gate.getGateDialLeverBlock() != null ? gate.getGateDialLeverBlock().getLocation() : null;
                        dbg.append(" DialLever=").append(dialLoc != null ? dialLoc.toString() : "null");
                    }
                    catch (final Exception e)
                    {
                        dbg.append(" DialLever=null");
                    }
                    try
                    {
                        final org.bukkit.Location irisLoc = iris != null ? iris.getLocation() : null;
                        dbg.append(" IrisBlock=").append(irisLoc != null ? irisLoc.toString() : "null");
                    }
                    catch (final Exception e)
                    {
                        dbg.append(" IrisBlock=null");
                    }
                    Material irisType = null;
                    try
                    {
                        irisType = iris != null ? iris.getType() : null;
                    }
                    catch (final Throwable t)
                    {
                        irisType = null;
                    }
                    dbg.append(" IrisBlockType=").append(irisType != null ? irisType.toString() : "null");
                    dbg.append(" GateFacing=").append(gate.getGateFacing() != null ? gate.getGateFacing().toString() : "null");
                    _plugin_for_log.prettyLog(Level.INFO, false, dbg.toString());
                }

                gate.getGateStructureBlocks().add(gate.getGateIrisLeverBlock().getLocation());
                gate.getGateIrisLeverBlock().setType(Material.LEVER);
                final org.bukkit.block.data.type.Switch irisSwitch =
                    (org.bukkit.block.data.type.Switch) gate.getGateIrisLeverBlock().getBlockData();
                irisSwitch.setAttachedFace(org.bukkit.block.data.FaceAttachable.AttachedFace.WALL);
                irisSwitch.setFacing(gate.getGateFacing());
                gate.getGateIrisLeverBlock().setBlockData(irisSwitch);
            }
            else
            {
                if (gate.getGateIrisLeverBlock().getType() == Material.LEVER)
                {
                    gate.getGateStructureBlocks().remove(gate.getGateIrisLeverBlock().getLocation());
                    gate.getGateIrisLeverBlock().setType(Material.AIR);
                }
            }
        }
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
        if (gate.getGateRedstoneDialActivationBlock() != null)
        {
            if (create)
            {
                final Block rd = gate.getGateRedstoneDialActivationBlock();
                try
                {
                    final Material current = rd.getType();
                    if ((current == Material.AIR) || (current == Material.REDSTONE_WIRE))
                    {
                        gate.getGateStructureBlocks().add(rd.getLocation());
                        rd.setType(Material.REDSTONE_WIRE);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Skipping RD placement; target occupied: " + current);
                    }
                }
                catch (final Throwable ignore) {}
            }
            else
            {
                final Block rd = gate.getGateRedstoneDialActivationBlock();
                if (rd != null && rd.getType() == Material.REDSTONE_WIRE)
                {
                    gate.getGateStructureBlocks().remove(rd.getLocation());
                    rd.setType(Material.AIR);
                }
            }
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Skipping RA lever placement; target occupied: " + current);
                    }
                }
                catch (final Throwable ignore) {}
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

    /** Places or removes the sign-dial redstone wire. */
    static void setupRedstoneSignDialWire(final Stargate gate, final boolean create)
    {
        if (gate.getGateRedstoneSignActivationBlock() != null)
        {
            if (create)
            {
                final Block rs = gate.getGateRedstoneSignActivationBlock();
                try
                {
                    final Material current = rs.getType();
                    if ((current == Material.AIR) || (current == Material.REDSTONE_WIRE))
                    {
                        gate.getGateStructureBlocks().add(rs.getLocation());
                        rs.setType(Material.REDSTONE_WIRE);
                    }
                    else
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Skipping RS placement; target occupied: " + current);
                    }
                }
                catch (final Throwable ignore) {}
            }
            else
            {
                final Block rs = gate.getGateRedstoneSignActivationBlock();
                if (rs != null && rs.getType() == Material.REDSTONE_WIRE)
                {
                    gate.getGateStructureBlocks().remove(rs.getLocation());
                    rs.setType(Material.AIR);
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
        final List<Player> recipients = new ArrayList<Player>();
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
        final BlockData blockData = MaterialUtils.drawnAs(material);
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
            new Runnable()
            {
                @Override
                public void run()
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
        final List<Player> recipients = new ArrayList<Player>();
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
        final BlockData blockData = MaterialUtils.drawnAs(material);
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
        // fixtureOn being null covers both "this gate has no chevron material" and "it has one
        // but the block cannot be switched on" -- a gold-block chevron drawn as itself would
        // simply never appear to light, so those fall back to the light material.
        if ((fixtureOn != null) && (standing == chevronMaterial))
        {
            return fixtureOn;
        }
        return lightData;
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
        final Set<String> stillOpen = new HashSet<String>();

        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (!shouldRedrawFor(gate, playerAt))
            {
                continue;
            }
            final BlockData blockData = MaterialUtils.drawnAs(gate.getEffectivePortalMaterial());
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
            stillOpen.add(gate.getGateName());
        }

        // Anything this player was shown that is not open to them any more has to be taken
        // back. The close-time send only reaches whoever was within range at that moment, and
        // a client keeps a drawing until something hands it a fresh copy of the chunk --
        // which walking one chunk away and back does not do, because the chunk never left.
        // Without this the portal stays on their screen with nothing behind it: water in a
        // gate that is off.
        final Set<String> showing = drawnFor(player);
        for (final String name : showing)
        {
            if (!stillOpen.contains(name))
            {
                undrawFor(player, StargateManager.getStargate(name));
            }
        }
        showing.clear();
        showing.addAll(stillOpen);
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
        final List<java.util.ArrayList<Location>> groups = gate.getGateLightBlocks();
        if (groups == null)
        {
            return;
        }
        final BlockData lightData = lit ? MaterialUtils.drawnAs(gate.getEffectiveLightMaterial()) : null;
        final Material chevronMaterial = lit ? gate.getEffectiveChevronMaterial() : null;
        final BlockData fixtureOn = lit ? MaterialUtils.litFormOf(chevronMaterial) : null;

        for (final java.util.ArrayList<Location> group : groups)
        {
            if (group == null)
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
    private static Set<String> drawnFor(final Player player)
    {
        final java.util.UUID uuid = player.getUniqueId();
        if (uuid == null)
        {
            // No identity to file it under, so there is nothing to remember between calls.
            // A throwaway set keeps every caller free of null checks.
            return new HashSet<String>();
        }
        Set<String> showing = DRAWN.get(uuid);
        if (showing == null)
        {
            showing = new HashSet<String>();
            DRAWN.put(uuid, showing);
        }
        return showing;
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
        // An iris is made of real blocks, which the client gets from the chunk like any
        // other block. Redrawing here would paint the portal over the iris the gate is
        // currently closed with.
        if ((gate == null) || (playerAt == null) || gate.isGateIrisActive())
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
     * This is deliberately <em>not</em> how the iris is drawn: see
     * {@link #fillGateIris(Stargate, Material)}.
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
     * Fills every portal block with a real, solid iris block.
     * <p>
     * The iris is the gate's barrier, so unlike the portal it must exist server-side:
     * a client-only iris would let a traveller walk straight through a closed one, and
     * would drop anything standing on a horizontal gate's iris. Placing real blocks
     * also makes the server send its own block updates, which clears any portal visual
     * clients were still showing for these positions.
     *
     * @param gate     the gate
     * @param material the iris material to place
     */
    static void fillGateIris(final Stargate gate, final Material material)
    {
        clearIrisPath(gate);
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(material);
        }
    }

    /**
     * Moves anyone standing in the gate opening clear before the iris fills it with blocks.
     *
     * <p>The iris is real blocks, and {@link #fillGateIris} placed them without looking at
     * who was there. Every path that closes an iris can therefore land solid blocks inside a
     * player: the gate shutting down onto an iris that defaults closed, an activation timing
     * out, someone flipping the iris lever, or an IDC being cleared. A traveller walking into
     * the event horizon as the far gate times out is the case that gets reported, because it
     * needs no bad timing on anyone's part -- the shutdown timer picks the moment.
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
                if (!(entity instanceof org.bukkit.entity.LivingEntity))
                {
                    continue;
                }
                if (!isInIrisPath(gate, entity.getLocation()))
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
                if (entity instanceof Player)
                {
                    ((Player) entity).setNoDamageTicks(5);
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Moved " + entity.getType() + " clear of closing iris on gate: " + gate.getGateName());
            }
            catch (final RuntimeException t)
            {
                // One entity that cannot be moved does not stop the iris closing, or stop
                // the rest of the sweep. Errors are left to propagate rather than being
                // swallowed here, where they would look like an ordinary immovable mob.
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Failed to move " + entity.getType() + " clear of closing iris on gate: "
                        + gate.getGateName() + ": " + t.getMessage());
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
            catch (final Throwable t)
            {
                mat = Material.AIR;
            }

            if (regenerate)
            {
                // Only create a lever if the activation holder is empty. Preserve
                // the player's placed activation item (button/lever) otherwise.
                if (mat == Material.AIR)
                {
                    gate.getGateDialLeverBlock().setType(Material.LEVER);
                    final Directional rld = (Directional) gate.getGateDialLeverBlock().getBlockData();
                    rld.setFacing(gate.getGateFacing());
                    gate.getGateDialLeverBlock().setBlockData(rld);
                    mat = gate.getGateDialLeverBlock().getType();
                }
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
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
