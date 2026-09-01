package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;

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
            sign.getSide(Side.FRONT).setLine(0, "-" + gate.getGateName() + "-");
            if (gate.getGateNetwork() != null)
            {
                sign.getSide(Side.FRONT).setLine(1, "N:" + gate.getGateNetwork().getNetworkName());
            }
            if (gate.getGateOwner() != null)
            {
                final String ownerDisplay = gate.getGateOwnerName();
                sign.getSide(Side.FRONT).setLine(2, "O:" + (ownerDisplay != null && ownerDisplay.length() > 13
                    ? ownerDisplay.substring(0, 13) : ownerDisplay));
            }
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
        final BlockData blockData = material.createBlockData();
        for (final Location bc : portalBlocks)
        {
            final Location at = new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            for (final Player p : recipients)
            {
                p.sendBlockChange(at, blockData);
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
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            if (!shouldRedrawFor(gate, playerAt))
            {
                continue;
            }
            final BlockData blockData = gate.getEffectivePortalMaterial().createBlockData();
            for (final Location bc : gate.getGatePortalBlocks())
            {
                player.sendBlockChange(
                    new Location(gate.getGateWorld(), bc.getBlockX(), bc.getBlockY(), bc.getBlockZ()),
                    blockData);
            }
        }
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
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(material);
        }
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
                try
                {
                    final Powerable llp = (Powerable) gate.getGateDialLeverBlock().getBlockData();
                    llp.setPowered(gate.isGateActive());
                    gate.getGateDialLeverBlock().setBlockData(llp);
                }
                catch (final Throwable ignore) {}
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
            final Powerable rp = (Powerable) gate.getGateRedstoneGateActivatedBlock().getBlockData();
            rp.setPowered(gate.isGateActive());
            gate.getGateRedstoneGateActivatedBlock().setBlockData(rp);
        }
    }
}
