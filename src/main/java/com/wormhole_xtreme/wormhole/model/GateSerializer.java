/*
 * Gate serialization/deserialization extracted from StargateHelper.
 */
package com.wormhole_xtreme.wormhole.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.DataUtils;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

public final class GateSerializer
{
    private static final byte StargateSaveVersion = 8;

    private GateSerializer() {}

    public static Stargate parseVersionedData(final byte[] gate_data, final World w, final String name, final StargateNetwork network)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateNetwork(network);
        final ByteBuffer byteBuff = ByteBuffer.wrap(gate_data);

        // First get version byte
        s.setLoadedVersion(byteBuff.get());
        s.setGateWorld(w);

        if (s.getLoadedVersion() == 3)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];
            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getInt());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getInt());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));

            int numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            return s;
        }
        else if (s.getLoadedVersion() == 4)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];

            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getLong());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateIrisDefaultActive(s.isGateIrisActive());
            int numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            return s;
        }
        else if (s.getLoadedVersion() == 5)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];

            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getLong());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateIrisDefaultActive(s.isGateIrisActive());
            s.setGateLightsActive(DataUtils.byteToBoolean(byteBuff.get()));

            int numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            while (s.getGateLightBlocks().size() < 2)
            {
                s.getGateLightBlocks().add(null);
            }

            s.getGateLightBlocks().set(1, new ArrayList<Location>());

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateLightBlocks().get(1).add(bl.getLocation());
            }

            return s;
        }
        else if (s.getLoadedVersion() == 6)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];

            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getLong());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateIrisDefaultActive(s.isGateIrisActive());
            s.setGateLightsActive(DataUtils.byteToBoolean(byteBuff.get()));

            boolean isRedstone = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstone)
            {
                s.setGateRedstoneDialActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            isRedstone = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstone)
            {
                s.setGateRedstoneSignActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            int numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            int numLayers = byteBuff.getInt();

            while (s.getGateLightBlocks().size() < numLayers)
            {
                s.getGateLightBlocks().add(new ArrayList<Location>());
            }
            for (int i = 0; i < numLayers; i++)
            {
                numBlocks = byteBuff.getInt();
                for (int j = 0; j < numBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateLightBlocks().get(i).add(bl.getLocation());
                }
            }

            numLayers = byteBuff.getInt();

            while (s.getGateWooshBlocks().size() < numLayers)
            {
                s.getGateWooshBlocks().add(new ArrayList<Location>());
            }
            for (int i = 0; i < numLayers; i++)
            {
                numBlocks = byteBuff.getInt();
                for (int j = 0; j < numBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateWooshBlocks().get(i).add(bl.getLocation());
                }
            }

            if (byteBuff.remaining() > 0)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
        }
        else if (s.getLoadedVersion() == 7)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];

            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            byteBuff.get(locArray);
            s.setGateMinecartTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getLong());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateIrisDefaultActive(s.isGateIrisActive());
            s.setGateLightsActive(DataUtils.byteToBoolean(byteBuff.get()));

            boolean isRedstone = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstone)
            {
                s.setGateRedstoneDialActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            isRedstone = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstone)
            {
                s.setGateRedstoneSignActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            int numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            numBlocks = byteBuff.getInt();
            for (int i = 0; i < numBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            int numLayers = byteBuff.getInt();

            while (s.getGateLightBlocks().size() < numLayers)
            {
                s.getGateLightBlocks().add(new ArrayList<Location>());
            }
            for (int i = 0; i < numLayers; i++)
            {
                numBlocks = byteBuff.getInt();
                for (int j = 0; j < numBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateLightBlocks().get(i).add(bl.getLocation());
                }
            }

            numLayers = byteBuff.getInt();

            while (s.getGateWooshBlocks().size() < numLayers)
            {
                s.getGateWooshBlocks().add(new ArrayList<Location>());
            }
            for (int i = 0; i < numLayers; i++)
            {
                numBlocks = byteBuff.getInt();
                for (int j = 0; j < numBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateWooshBlocks().get(i).add(bl.getLocation());
                }
            }

            if (byteBuff.remaining() > 0)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
        }
        else if (s.getLoadedVersion() == 8)
        {
            final byte[] locArray = new byte[32];
            final byte[] blocArray = new byte[12];
            byteBuff.get(blocArray);
            s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(blocArray);
            s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

            byteBuff.get(locArray);
            s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            byteBuff.get(locArray);
            s.setGateMinecartTeleportLocation(DataUtils.locationFromBytes(locArray, w));

            s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

            byteBuff.get(blocArray);
            s.setGateDialSignIndex(byteBuff.getInt());
            s.setGateTempSignTarget(byteBuff.getLong());
            if (s.isGateSignPowered())
            {
                s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));

                if (w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
                {
                    try
                    {
                        s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
                    }
                    catch (final Exception e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);
            s.getGateMinecartTeleportLocation().setY(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGateMinecartTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes));

            s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateIrisDefaultActive(s.isGateIrisActive());
            s.setGateLightsActive(DataUtils.byteToBoolean(byteBuff.get()));

            final boolean isRedstoneDA = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstoneDA)
            {
                s.setGateRedstoneDialActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            final boolean isRedstoneSA = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstoneSA)
            {
                s.setGateRedstoneSignActivationBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            final boolean isRedstoneGA = DataUtils.byteToBoolean(byteBuff.get());
            byteBuff.get(blocArray);
            if (isRedstoneGA)
            {
                s.setGateRedstoneGateActivatedBlock(DataUtils.blockFromBytes(blocArray, w));
            }

            s.setGateRedstonePowered(DataUtils.byteToBoolean(byteBuff.get()));

            s.setGateCustom(DataUtils.byteToBoolean(byteBuff.get()));
            final int gateCustomStructureMaterial = byteBuff.getInt();
            s.setGateCustomStructureMaterial(gateCustomStructureMaterial >= 0 && gateCustomStructureMaterial < Material.values().length
                ? Material.values()[gateCustomStructureMaterial]
                : null);
            final int gateCustomPortalMaterial = byteBuff.getInt();
            s.setGateCustomPortalMaterial(gateCustomPortalMaterial >= 0 && gateCustomPortalMaterial < Material.values().length
                ? Material.values()[gateCustomPortalMaterial]
                : null);
            final int gateCustomLightMaterial = byteBuff.getInt();
            s.setGateCustomLightMaterial(gateCustomLightMaterial >= 0 && gateCustomLightMaterial < Material.values().length
                ? Material.values()[gateCustomLightMaterial]
                : null);
            final int gateCustomIrisMaterial = byteBuff.getInt();
            s.setGateCustomIrisMaterial(gateCustomIrisMaterial >= 0 && gateCustomIrisMaterial < Material.values().length
                ? Material.values()[gateCustomIrisMaterial]
                : null);
            s.setGateCustomWooshTicks(byteBuff.getInt());
            s.setGateCustomLightTicks(byteBuff.getInt());
            s.setGateCustomWooshDepth(byteBuff.getInt());
            s.setGateCustomWooshDepthSquared(s.getGateCustomWooshDepth() >= 0
                ? s.getGateCustomWooshDepth() * s.getGateCustomWooshDepth()
                : -1);

            final int numStructureBlocks = byteBuff.getInt();
            for (int i = 0; i < numStructureBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGateStructureBlocks().add(bl.getLocation());
            }

            final int numPortalBlocks = byteBuff.getInt();
            for (int i = 0; i < numPortalBlocks; i++)
            {
                byteBuff.get(blocArray);
                final Block bl = DataUtils.blockFromBytes(blocArray, w);
                s.getGatePortalBlocks().add(bl.getLocation());
            }

            final int numLightLayers = byteBuff.getInt();

            while (s.getGateLightBlocks().size() < numLightLayers)
            {
                s.getGateLightBlocks().add(new ArrayList<Location>());
            }

            for (int i = 0; i < numLightLayers; i++)
            {
                final int numLightBlocks = byteBuff.getInt();
                for (int j = 0; j < numLightBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateLightBlocks().get(i).add(bl.getLocation());
                }
            }

            final int numWooshLayers = byteBuff.getInt();

            while (s.getGateWooshBlocks().size() < numWooshLayers)
            {
                s.getGateWooshBlocks().add(new ArrayList<Location>());
            }
            for (int i = 0; i < numWooshLayers; i++)
            {
                final int numWooshBlocks = byteBuff.getInt();
                for (int j = 0; j < numWooshBlocks; j++)
                {
                    byteBuff.get(blocArray);
                    final Block bl = DataUtils.blockFromBytes(blocArray, w);
                    s.getGateWooshBlocks().get(i).add(bl.getLocation());
                }
            }

            if (byteBuff.remaining() > 0)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
        }
        return null;
    }

    public static byte[] stargatetoBinary(final Stargate s)
    {
        byte[] utfFaceBytes;
        byte[] utfIdcBytes;
        try
        {
            utfFaceBytes = s.getGateFacing().toString().getBytes("UTF8");
            utfIdcBytes = s.getGateIrisDeactivationCode().getBytes("UTF8");
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to store gate in DB, byte encoding failed: " + e.getMessage());
            e.printStackTrace();
            final byte[] b = null;
            return b;
        }

        final int numBlocks = 7;
        final int numLocations = 2;
        final int locationSize = 32;
        final int blockSize = 12;
        final int numBytesWithVersion = 10;
        final int numInts = 12;
        final int numLongs = 2;

        int size = numBytesWithVersion + (numInts * 4) + (numLongs * 8) + (numBlocks * blockSize) + (numLocations * locationSize);
        size += (s.getGateStructureBlocks().size() * blockSize) + (s.getGatePortalBlocks().size() * blockSize);
        int numIntsOther = 2;
        for (int i = 0; i < s.getGateLightBlocks().size(); i++)
        {
            if (s.getGateLightBlocks().get(i) != null)
            {
                size += s.getGateLightBlocks().get(i).size() * blockSize;
            }
            numIntsOther++;
        }
        for (int i = 0; i < s.getGateWooshBlocks().size(); i++)
        {
            if (s.getGateWooshBlocks().get(i) != null)
            {
                size += s.getGateWooshBlocks().get(i).size() * blockSize;
            }
            numIntsOther++;
        }
        size += utfFaceBytes.length + utfIdcBytes.length;
        size += numIntsOther * 4;

        final ByteBuffer dataArr = ByteBuffer.allocate(size);

        dataArr.put(StargateSaveVersion);
        dataArr.put(DataUtils.blockToBytes(s.getGateDialLeverBlock()));
        final byte[] emptyBlock = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        dataArr.put(s.getGateIrisLeverBlock() != null ? DataUtils.blockToBytes(s.getGateIrisLeverBlock()) : emptyBlock);
        dataArr.put(s.getGateNameBlockHolder() != null ? DataUtils.blockToBytes(s.getGateNameBlockHolder()) : emptyBlock);
        dataArr.put(DataUtils.locationToBytes(s.getGatePlayerTeleportLocation()));
        dataArr.put(s.getGateMinecartTeleportLocation() != null ? DataUtils.locationToBytes(s.getGateMinecartTeleportLocation()) : DataUtils.locationToBytes(s.getGatePlayerTeleportLocation()));

        if (s.isGateSignPowered())
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateDialSignBlock()));
            dataArr.putInt(s.getGateDialSignIndex());
            dataArr.putLong(s.getGateDialSignTarget() != null ? s.getGateDialSignTarget().getGateId() : -1);
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock);
            dataArr.putInt(-1);
            dataArr.putLong(-1);
        }

        if (s.isGateActive() && (s.getGateTarget() != null))
        {
            dataArr.put((byte) 1);
            dataArr.putLong(s.getGateTarget().getGateId());
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.putLong(-1);
        }

        dataArr.putInt(utfFaceBytes.length);
        dataArr.put(utfFaceBytes);
        dataArr.putInt(utfIdcBytes.length);
        dataArr.put(utfIdcBytes);
        dataArr.put(s.isGateIrisActive() ? (byte) 1 : (byte) 0);
        dataArr.put(s.isGateLightsActive() ? (byte) 1 : (byte) 0);

        if (s.getGateRedstoneDialActivationBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneDialActivationBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock);
        }

        if (s.getGateRedstoneSignActivationBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneSignActivationBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock);
        }

        if (s.getGateRedstoneGateActivatedBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneGateActivatedBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock);
        }
        dataArr.put(s.isGateRedstonePowered() ? (byte) 1 : (byte) 0);
        dataArr.put(s.isGateCustom() ? (byte) 1 : (byte) 0);

        dataArr.putInt(s.getGateCustomStructureMaterial() != null ? s.getGateCustomStructureMaterial().ordinal() : -1);
        dataArr.putInt(s.getGateCustomPortalMaterial() != null ? s.getGateCustomPortalMaterial().ordinal() : -1);
        dataArr.putInt(s.getGateCustomLightMaterial() != null ? s.getGateCustomLightMaterial().ordinal() : -1);
        dataArr.putInt(s.getGateCustomIrisMaterial() != null ? s.getGateCustomIrisMaterial().ordinal() : -1);
        dataArr.putInt(s.getGateCustomWooshTicks());
        dataArr.putInt(s.getGateCustomLightTicks());
        dataArr.putInt(s.getGateCustomWooshDepth());

        dataArr.putInt(s.getGateStructureBlocks().size());
        for (int i = 0; i < s.getGateStructureBlocks().size(); i++)
        {
            dataArr.put(DataUtils.blockLocationToBytes(s.getGateStructureBlocks().get(i)));
        }
        dataArr.putInt(s.getGatePortalBlocks().size());
        for (int i = 0; i < s.getGatePortalBlocks().size(); i++)
        {
            dataArr.put(DataUtils.blockLocationToBytes(s.getGatePortalBlocks().get(i)));
        }

        dataArr.putInt(s.getGateLightBlocks().size());
        for (int i = 0; i < s.getGateLightBlocks().size(); i++)
        {
            if (s.getGateLightBlocks().get(i) != null)
            {
                dataArr.putInt(s.getGateLightBlocks().get(i).size());
                for (int j = 0; j < s.getGateLightBlocks().get(i).size(); j++)
                {
                    dataArr.put(DataUtils.blockLocationToBytes(s.getGateLightBlocks().get(i).get(j)));
                }
            }
            else
            {
                dataArr.putInt(0);
            }
        }

        dataArr.putInt(s.getGateWooshBlocks().size());
        for (int i = 0; i < s.getGateWooshBlocks().size(); i++)
        {
            if (s.getGateWooshBlocks().get(i) != null)
            {
                dataArr.putInt(s.getGateWooshBlocks().get(i).size());
                for (int j = 0; j < s.getGateWooshBlocks().get(i).size(); j++)
                {
                    dataArr.put(DataUtils.blockLocationToBytes(s.getGateWooshBlocks().get(i).get(j)));
                }
            }
            else
            {
                dataArr.putInt(0);
            }
        }

        if (dataArr.remaining() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Gate data not filling whole byte array. This could be bad:" + dataArr.remaining());
        }

        return dataArr.array();
    }
}
