/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
 *   Copyright (C) 2026  Justin Harding
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wormhole_xtreme.wormhole.model;

import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Powerable;

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
            placeBlock.setType(gate.getGateShape().getShapeSignMaterial());
            final Directional signData = (Directional) placeBlock.getBlockData();
            signData.setFacing(toward);
            placeBlock.setBlockData(signData);

            final Sign sign = (Sign) placeBlock.getState();
            sign.setLine(0, "-" + gate.getGateName() + "-");
            if (gate.getGateNetwork() != null)
            {
                sign.setLine(1, "N:" + gate.getGateNetwork().getNetworkName());
            }
            if (gate.getGateOwner() != null)
            {
                final String ownerDisplay = gate.getGateOwnerName();
                sign.setLine(2, "O:" + (ownerDisplay != null && ownerDisplay.length() > 13
                    ? ownerDisplay.substring(0, 13) : ownerDisplay));
            }
            sign.update(true);
        }
        else
        {
            if (com.wormhole_xtreme.wormhole.utils.LegacyCompat.isWallSign(placeBlock.getType()))
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
            && (gate.getGateShape() != null))
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
                gate.setGateIrisLeverBlock(irisBlock);
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
                gate.getGateStructureBlocks().add(gate.getGateRedstoneDialActivationBlock().getLocation());
                gate.getGateRedstoneDialActivationBlock().setType(Material.REDSTONE_WIRE);
            }
            else
            {
                if (gate.getGateRedstoneGateActivatedBlock().getType() == Material.REDSTONE_WIRE)
                {
                    gate.getGateStructureBlocks().remove(gate.getGateRedstoneDialActivationBlock().getLocation());
                    gate.getGateRedstoneDialActivationBlock().setType(Material.AIR);
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
                gate.getGateStructureBlocks().add(gate.getGateRedstoneGateActivatedBlock().getLocation());
                gate.getGateRedstoneGateActivatedBlock().setType(Material.LEVER);
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
                gate.getGateStructureBlocks().add(gate.getGateRedstoneSignActivationBlock().getLocation());
                gate.getGateRedstoneSignActivationBlock().setType(Material.REDSTONE_WIRE);
            }
            else
            {
                if (gate.getGateRedstoneGateActivatedBlock().getType() == Material.REDSTONE_WIRE)
                {
                    gate.getGateStructureBlocks().remove(gate.getGateRedstoneSignActivationBlock().getLocation());
                    gate.getGateRedstoneSignActivationBlock().setType(Material.AIR);
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

    /**
     * Sets all portal blocks to {@link Material#AIR}.
     *
     * @param gate the gate
     */
    static void deletePortalBlocks(final Stargate gate)
    {
        for (final Location bc : gate.getGatePortalBlocks())
        {
            final Block b = gate.getGateWorld().getBlockAt(bc.getBlockX(), bc.getBlockY(), bc.getBlockZ());
            b.setType(Material.AIR);
        }
    }

    /**
     * Fills every portal block with the given material.
     *
     * @param gate     the gate
     * @param material the material to place
     */
    static void fillGateInterior(final Stargate gate, final Material material)
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
