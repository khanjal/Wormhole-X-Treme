/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Copyright (C) 2011  Ben Echols
 *                       Dean Bailey
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
     * The sign always faces {@link Stargate#getGateFacing()} so it is visible
     * from the player/portal side.
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

        if (create)
        {
            final Block nameSign = gate.getGateNameBlockHolder();
            // Always use gateFacing — the direction toward the open portal / player side.
            // Do NOT derive this from the lever's getFacing(); a button placed ON TOP of a
            // block returns BlockFace.UP, which would misplace the sign on a side face.
            final BlockFace forward = gate.getGateFacing();
            final BlockFace inverse = WorldUtils.getInverseDirection(forward);
            final BlockFace right  = WorldUtils.getPerpendicularRightDirection(forward);
            final BlockFace left   = WorldUtils.getPerpendicularRightDirection(inverse);

            // Preference order: forward face first (toward player), then sides, then back.
            final BlockFace[] candidates = { forward, right, left, inverse };

            Block placeBlock = null;
            BlockFace chosenFace = null;

            for (final BlockFace face : candidates)
            {
                try
                {
                    final Block candidate = nameSign.getRelative(face);
                    if (candidate.getType() == Material.AIR)
                    {
                        placeBlock = candidate;
                        chosenFace = face;
                        break;
                    }
                }
                catch (final Throwable ignored) {}
            }

            // Extended search: 2–3 steps forward (gate built into a wall).
            for (int dist = 2; dist <= 3 && placeBlock == null; dist++)
            {
                try
                {
                    Block cursor = nameSign;
                    for (int step = 0; step < dist; step++)
                    {
                        cursor = cursor.getRelative(forward);
                    }
                    if (cursor.getType() == Material.AIR)
                    {
                        placeBlock = cursor;
                        chosenFace = forward;
                    }
                }
                catch (final Throwable ignored) {}
            }

            // Diagonal fallbacks (forward + right, forward + left).
            if (placeBlock == null)
            {
                try
                {
                    final Block c = nameSign.getRelative(forward).getRelative(right);
                    if (c.getType() == Material.AIR) { placeBlock = c; chosenFace = forward; }
                }
                catch (final Throwable ignored) {}
            }
            if (placeBlock == null)
            {
                try
                {
                    final Block c = nameSign.getRelative(forward).getRelative(left);
                    if (c.getType() == Material.AIR) { placeBlock = c; chosenFace = forward; }
                }
                catch (final Throwable ignored) {}
            }

            // Last resort: place on the holder block itself.
            if (placeBlock == null)
            {
                placeBlock = nameSign;
                chosenFace = forward;
            }

            gate.getGateStructureBlocks().add(placeBlock.getLocation());
            placeBlock.setType(Material.OAK_WALL_SIGN);
            final Directional signData = (Directional) placeBlock.getBlockData();
            signData.setFacing(chosenFace);
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
            final Block nameSign = gate.getGateNameBlockHolder();
            // The sign was placed at nameSign.getRelative(gateFacing) during creation.
            // Check that block first; fall back to the holder itself for legacy gates.
            final Block signBlock = nameSign.getRelative(gate.getGateFacing());
            if (signBlock.getType() == Material.OAK_WALL_SIGN)
            {
                gate.getGateStructureBlocks().remove(signBlock.getLocation());
                signBlock.setType(Material.AIR);
            }
            else if (nameSign.getType() == Material.OAK_WALL_SIGN)
            {
                gate.getGateStructureBlocks().remove(nameSign.getLocation());
                nameSign.setType(Material.AIR);
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
            && !(gate.getGateShape() instanceof Stargate3DShape))
        {
            gate.setGateIrisLeverBlock(gate.getGateDialLeverBlock().getRelative(BlockFace.DOWN));
        }
        if (gate.getGateIrisLeverBlock() != null)
        {
            if (create)
            {
                gate.getGateStructureBlocks().add(gate.getGateIrisLeverBlock().getLocation());
                gate.getGateIrisLeverBlock().setType(Material.LEVER);
                final Directional leverData = (Directional) gate.getGateIrisLeverBlock().getBlockData();
                leverData.setFacing(gate.getGateFacing());
                gate.getGateIrisLeverBlock().setBlockData(leverData);
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
            org.bukkit.Material mat = gate.getGateDialLeverBlock().getType();
            if (regenerate)
            {
                gate.getGateDialLeverBlock().setType(Material.LEVER);
                final Directional rld = (Directional) gate.getGateDialLeverBlock().getBlockData();
                rld.setFacing(gate.getGateFacing());
                gate.getGateDialLeverBlock().setBlockData(rld);
                mat = gate.getGateDialLeverBlock().getType();
            }
            if (mat == Material.STONE_BUTTON)
            {
                gate.getGateDialLeverBlock().setType(Material.LEVER);
                final Directional bld = (Directional) gate.getGateDialLeverBlock().getBlockData();
                bld.setFacing(gate.getGateFacing());
                gate.getGateDialLeverBlock().setBlockData(bld);
                WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false,
                    "Automaticially replaced Button on gate \"" + gate.getGateName() + "\" with Lever.");
                final Powerable blp = (Powerable) gate.getGateDialLeverBlock().getBlockData();
                blp.setPowered(gate.isGateActive());
                gate.getGateDialLeverBlock().setBlockData(blp);
            }
            else if (mat == Material.LEVER)
            {
                final Powerable llp = (Powerable) gate.getGateDialLeverBlock().getBlockData();
                llp.setPowered(gate.isGateActive());
                gate.getGateDialLeverBlock().setBlockData(llp);
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
