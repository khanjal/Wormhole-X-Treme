/*
 *   Wormhole X-Treme Plugin for Bukkit
 *   Trimmed helper: delegates serialization and shape loading,
 *   and exposes a small set of utility methods used across the codebase.
 */
package com.wormhole_xtreme.wormhole.logic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import com.wormhole_xtreme.wormhole.model.GateSerializer;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, trimmed Stargate helper. Responsibilities:
 * - Provide geometry utility used by unit tests
 * - Delegate serialization to GateSerializer
 * - Delegate shape loading/registry to StargateShapeRegistry
 * - Provide small stubs for legacy APIs still referenced elsewhere
 */
public final class StargateHelper
{
    private StargateHelper() { }

    /**
     * Compute the gate facing direction by analysing the spatial variance of
     * the gate's structure blocks while excluding the DHD/dial block location.
     * Returns null for indeterminate or invalid inputs.
     */
    public static BlockFace computeGateFacingFromGeometry(final Stargate gate)
    {
        if (gate == null)
        {
            return null;
        }
        final Block dhd = gate.getGateDialLeverBlock();
        if (dhd == null)
        {
            return null;
        }

        final List<Location> structure = gate.getGateStructureBlocks();
        if (structure == null || structure.isEmpty())
        {
            return null;
        }

        // Accumulate excluding any structure entry equal to the DHD coordinates
        double sumX = 0.0, sumZ = 0.0;
        int count = 0;
        for (final Location loc : structure)
        {
            if (loc == null)
            {
                continue;
            }
            final int lx = loc.getBlockX();
            final int ly = loc.getBlockY();
            final int lz = loc.getBlockZ();
            if (lx == dhd.getX() && ly == dhd.getY() && lz == dhd.getZ())
            {
                continue; // exclude DHD coordinate
            }
            sumX += lx;
            sumZ += lz;
            count++;
        }

        if (count == 0)
        {
            return null;
        }

        final double meanX = sumX / count;
        final double meanZ = sumZ / count;

        double varX = 0.0, varZ = 0.0;
        for (final Location loc : structure)
        {
            if (loc == null) continue;
            final int lx = loc.getBlockX();
            final int ly = loc.getBlockY();
            final int lz = loc.getBlockZ();
            if (lx == dhd.getX() && ly == dhd.getY() && lz == dhd.getZ()) continue;
            final double dx = lx - meanX;
            final double dz = lz - meanZ;
            varX += dx * dx;
            varZ += dz * dz;
        }

        // Use population variance (divide by count). For comparison only relative magnitudes matter.
        varX = varX / count;
        varZ = varZ / count;

        final double eps = 1e-9;
        if (Math.abs(varX - varZ) < eps)
        {
            return null; // indeterminate
        }

        if (varX > varZ)
        {
            // Frame spread mainly along X → gate faces NORTH/SOUTH depending on DHD Z
            return dhd.getZ() > meanZ ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        else
        {
            // Frame spread mainly along Z → gate faces EAST/WEST depending on DHD X
            return dhd.getX() > meanX ? BlockFace.EAST : BlockFace.WEST;
        }
    }

    // ---------------------------------------------------------------------
    // Delegation helpers (thin wrappers)
    // ---------------------------------------------------------------------

    public static void loadShapes()
    {
        StargateShapeRegistry.loadShapes();
    }

    public static StargateShape getStargateShape(final String name)
    {
        return StargateShapeRegistry.getStargateShape(name);
    }

    public static boolean isStargateShape(final String name)
    {
        return StargateShapeRegistry.isStargateShape(name);
    }

    public static Stargate parseVersionedData(final byte[] gate_data, final World w, final String name, final StargateNetwork network)
    {
        return GateSerializer.parseVersionedData(gate_data, w, name, network);
    }

    public static byte[] stargatetoBinary(final Stargate s)
    {
        return GateSerializer.stargatetoBinary(s);
    }

    // ---------------------------------------------------------------------
    // Gate detection
    // ---------------------------------------------------------------------

    /**
     * Attempts to find a valid stargate whose activation button/lever is
     * {@code clickedBlock} facing {@code direction}.  Iterates every loaded
     * 3-D shape and returns the first match, or {@code null} if none match.
     */
    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction)
    {
        if (clickedBlock == null || direction == null)
        {
            return null;
        }
        for (final StargateShape shape : StargateShapeRegistry.getStargateShapes().values())
        {
            if (shape instanceof Stargate3DShape)
            {
                final Stargate result = check3DShape(clickedBlock, direction, (Stargate3DShape) shape);
                if (result != null)
                {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Like {@link #checkStargate(Block, BlockFace)} but only tests the given
     * {@code shape}.
     */
    public static Stargate checkStargate(final Block clickedBlock, final BlockFace direction, final StargateShape shape)
    {
        if (clickedBlock == null || direction == null || shape == null)
        {
            return null;
        }
        if (shape instanceof Stargate3DShape)
        {
            return check3DShape(clickedBlock, direction, (Stargate3DShape) shape);
        }
        return null;
    }

    /**
     * Core V2 (3-D) shape detection.
     *
     * <p>Coordinate system (per StargateShapeLayer):
     * <ul>
     *   <li>{@code L} – layer index (1-based; increases away from the player)
     *   <li>{@code R} – row from the bottom (0 = ground row)
     *   <li>{@code C} – column from the right (when looking at the gate face)
     * </ul>
     *
     * <p>Given gate-facing direction {@code F} and its perpendicular-right
     * direction {@code RIGHT}:
     * <pre>
     *   wx = ox + (L-1)*F.modX  + C*RIGHT.modX
     *   wy = oy + R
     *   wz = oz + (L-1)*F.modZ  + C*RIGHT.modZ
     * </pre>
     * where the origin is derived from the activation-holder position.
     */
    private static Stargate check3DShape(final Block clickedBlock,
                                          final BlockFace facing,
                                          final Stargate3DShape shape)
    {
        final int activationLayerIdx = shape.getShapeActivationLayer();
        if (activationLayerIdx < 1)
        {
            return null;
        }
        final ArrayList<StargateShapeLayer> shapeLayers = shape.getShapeLayers();
        if (shapeLayers == null || shapeLayers.size() <= activationLayerIdx)
        {
            return null;
        }
        final StargateShapeLayer actLayer = shapeLayers.get(activationLayerIdx);
        if (actLayer == null)
        {
            return null;
        }
        final int[] aPos = actLayer.getLayerActivationPosition();
        if (aPos.length < 3)
        {
            return null;
        }
        final int aRow = aPos[1]; // row from bottom
        final int aCol = aPos[2]; // col from right

        // The button/lever is mounted on the gate-facing face of the activation
        // holder block, so the holder is one step opposite to the facing direction.
        final Block holder = clickedBlock.getRelative(WorldUtils.getInverseDirection(facing));

        // Note: Shapes may declare an `ACTIVATION` metadata value, but we do
        // not enforce the activation block's material here. Players may place
        // buttons or levers freely; detection should succeed regardless of the
        // exact activation item used.

        // Derive the gate's coordinate-system origin.
        final BlockFace right = WorldUtils.getPerpendicularRightDirection(facing);
        final int ox = holder.getX() - (activationLayerIdx - 1) * facing.getModX() - aCol * right.getModX();
        final int oy = holder.getY() - aRow;
        final int oz = holder.getZ() - (activationLayerIdx - 1) * facing.getModZ() - aCol * right.getModZ();

        final World world = clickedBlock.getWorld();
        final org.bukkit.Material structMat = shape.getShapeStructureMaterial();
        final int numLayers = shapeLayers.size();

        // Verify every structure (S) block has the expected material,
        // AND every portal (P) block is NOT the structure material.
        // The second check prevents false positives inside solid obsidian rooms
        // (or any room built from the gate's structure material) where the frame
        // outline happens to match a shape but the interior is not open space.
        for (int layerIdx = 1; layerIdx < numLayers; layerIdx++)
        {
            final StargateShapeLayer layer = shapeLayers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                final int wy = oy + pos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                if (world.getBlockAt(wx, wy, wz).getType() != structMat)
                {
                    return null; // structure block mismatch
                }
            }
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                final int wy = oy + pos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                if (world.getBlockAt(wx, wy, wz).getType() == structMat)
                {
                    return null; // portal interior is solid — not a real gate
                }
            }
        }

        // All structure blocks match.  Build and populate the Stargate object.
        final Stargate gate = new Stargate();
        gate.setGateShape(shape);
        gate.setGateFacing(facing);
        gate.setGateWorld(world);
        gate.setGateDialLeverBlock(clickedBlock);

        boolean hasDialSign = false;

        for (int layerIdx = 1; layerIdx < numLayers; layerIdx++)
        {
            final StargateShapeLayer layer = shapeLayers.get(layerIdx);
            if (layer == null)
            {
                continue;
            }

            // Structure blocks
            for (final Integer[] pos : layer.getLayerBlockPositions())
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                final int wy = oy + pos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                gate.getGateStructureBlocks().add(world.getBlockAt(wx, wy, wz).getLocation());
            }

            // Portal blocks
            for (final Integer[] pos : layer.getLayerPortalPositions())
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                final int wy = oy + pos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                gate.getGatePortalBlocks().add(world.getBlockAt(wx, wy, wz).getLocation());
            }

            // Light blocks — shape uses 1-based wave indices; runtime lighting expects
            // a placeholder at index 0 and real waves starting at index 1.
            final ArrayList<ArrayList<Integer[]>> lightWaves = layer.getLayerLightPositions();
            if (lightWaves != null)
            {
                for (int waveIdx = 1; waveIdx < lightWaves.size(); waveIdx++)
                {
                    final ArrayList<Integer[]> wavePositions = lightWaves.get(waveIdx);
                    if (wavePositions == null)
                    {
                        continue;
                    }
                    final int gateWaveIdx = waveIdx; // keep index 1..N so index 0 stays as placeholder
                    while (gate.getGateLightBlocks().size() <= gateWaveIdx)
                    {
                        gate.getGateLightBlocks().add(null);
                    }
                    if (gate.getGateLightBlocks().get(gateWaveIdx) == null)
                    {
                        gate.getGateLightBlocks().set(gateWaveIdx, new ArrayList<Location>());
                    }
                    for (final Integer[] pos : wavePositions)
                    {
                        final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                        final int wy = oy + pos[1];
                        final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                        gate.getGateLightBlocks().get(gateWaveIdx).add(world.getBlockAt(wx, wy, wz).getLocation());
                    }
                }
            }

            // Woosh blocks — same 1-based → 0-based shift.
            final ArrayList<ArrayList<Integer[]>> wooshWaves = layer.getLayerWooshPositions();
            if (wooshWaves != null)
            {
                for (int waveIdx = 1; waveIdx < wooshWaves.size(); waveIdx++)
                {
                    final ArrayList<Integer[]> wavePositions = wooshWaves.get(waveIdx);
                    if (wavePositions == null)
                    {
                        continue;
                    }
                    final int gateWaveIdx = waveIdx - 1;
                    while (gate.getGateWooshBlocks().size() <= gateWaveIdx)
                    {
                        gate.getGateWooshBlocks().add(new ArrayList<Location>());
                    }
                    for (final Integer[] pos : wavePositions)
                    {
                        final int wx = ox + (layerIdx - 1) * facing.getModX() + pos[2] * right.getModX();
                        final int wy = oy + pos[1];
                        final int wz = oz + (layerIdx - 1) * facing.getModZ() + pos[2] * right.getModZ();
                        gate.getGateWooshBlocks().get(gateWaveIdx).add(world.getBlockAt(wx, wy, wz).getLocation());
                    }
                }
            }

            // Name sign holder (N)
            final int[] nPos = layer.getLayerNameSignPosition();
            if (nPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + nPos[2] * right.getModX();
                final int wy = oy + nPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + nPos[2] * right.getModZ();
                gate.setGateNameBlockHolder(world.getBlockAt(wx, wy, wz));
            }

            // Player teleport exit (EP)
            final int[] epPos = layer.getLayerPlayerExitPosition();
            if (epPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + epPos[2] * right.getModX();
                final int wy = oy + epPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + epPos[2] * right.getModZ();
                // EP is the block the player's feet land on. Add 1.0 Y so feet are on
                // top of it, offset one block in the -facing direction to place the
                // player just outside the portal water, and face them in the gate's
                // facing direction with pitch zeroed.
                // Move one block in the gate's facing direction (outwards)
                // so the player appears just outside the portal rather than
                // being placed inside it. Use facing's mod components directly.
                final Location tpLoc = new Location(world, wx + 0.5 + facing.getModX(), wy + 1.0, wz + 0.5 + facing.getModZ());
                try { tpLoc.setYaw(WorldUtils.getDegreesFromBlockFace(facing)); } catch (final Throwable ignore) {}
                try { tpLoc.setPitch(0f); } catch (final Throwable ignore) {}
                gate.setGatePlayerTeleportLocation(tpLoc);
            }

            // Minecart teleport exit (EM)
            final int[] emPos = layer.getLayerMinecartExitPosition();
            if (emPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + emPos[2] * right.getModX();
                final int wy = oy + emPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + emPos[2] * right.getModZ();
                // Use a half-block Y offset so minecarts spawn above the ground and do not sink into blocks.
                gate.setGateMinecartTeleportLocation(new Location(world, wx + 0.5, wy + 0.5, wz + 0.5));
            }

            // Dial-sign holder (D) — the sign sits on the gate-facing face of this block.
            final int[] dPos = layer.getLayerDialSignPosition();
            if (dPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + dPos[2] * right.getModX();
                final int wy = oy + dPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + dPos[2] * right.getModZ();
                final Block signBlock = world.getBlockAt(wx, wy, wz).getRelative(facing);
                if (com.wormhole_xtreme.wormhole.utils.MaterialUtils.isWallSign(signBlock.getType()))
                {
                    try
                    {
                        final Sign signState = (Sign) signBlock.getState();
                        gate.setGateDialSignBlock(signBlock);
                        gate.setGateDialSign(signState);
                        // Read the name the player wrote on line 0 of the sign.
                        final String signName = signState.getLine(0).trim();
                        if (!signName.isEmpty())
                        {
                            gate.setGateName(signName);
                        }
                        hasDialSign = true;
                    }
                    catch (final Exception e)
                    {
                        // Sign state not available — treat as no sign.
                    }
                }
            }

            // Iris activation holder (IA) — iris lever is on the gate-facing face.
            final int[] iaPos = layer.getLayerIrisActivationPosition();
            if (iaPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + iaPos[2] * right.getModX();
                final int wy = oy + iaPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + iaPos[2] * right.getModZ();
                gate.setGateIrisLeverBlock(world.getBlockAt(wx, wy, wz).getRelative(facing));
            }

            // Redstone dial activation (RD)
            final int[] rdPos = layer.getLayerRedstoneDialActivationPosition();
            if (rdPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + rdPos[2] * right.getModX();
                final int wy = oy + rdPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + rdPos[2] * right.getModZ();
                gate.setGateRedstoneDialActivationBlock(world.getBlockAt(wx, wy, wz));
                gate.setGateRedstonePowered(true);
            }

            // Redstone sign activation (RS)
            final int[] rsPos = layer.getLayerRedstoneSignActivationPosition();
            if (rsPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + rsPos[2] * right.getModX();
                final int wy = oy + rsPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + rsPos[2] * right.getModZ();
                gate.setGateRedstoneSignActivationBlock(world.getBlockAt(wx, wy, wz));
            }

            // Redstone gate-activated output (RA)
            final int[] raPos = layer.getLayerRedstoneGateActivatedPosition();
            if (raPos.length >= 3)
            {
                final int wx = ox + (layerIdx - 1) * facing.getModX() + raPos[2] * right.getModX();
                final int wy = oy + raPos[1];
                final int wz = oz + (layerIdx - 1) * facing.getModZ() + raPos[2] * right.getModZ();
                gate.setGateRedstoneGateActivatedBlock(world.getBlockAt(wx, wy, wz));
            }
        }

        gate.setGateSignPowered(hasDialSign);
        return gate;
    }

    public static void debugShapeMatch(final Block buttonBlock, final BlockFace facing, final StargateShape shape)
    {
        // Intentionally no-op
    }
}
