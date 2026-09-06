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
    private static final byte StargateSaveVersion = 9;

    private GateSerializer() {}

    /**
     * Reads one optional custom material.
     *
     * <p>Version 9 writes the material's name; version 8 wrote {@code Material.ordinal()}.
     * Ordinals are a property of the enum's declaration order in the Bukkit jar the gate
     * was saved against, and that order shifts whenever Minecraft adds or removes a block.
     * A gate saved on one server version and read on another therefore silently came back
     * with a different material — obsidian becoming glass, an iris becoming air — with no
     * error to show for it. Names survive version changes; a material that genuinely no
     * longer exists resolves to null and falls back to the shape or palette default.
     *
     * @param byteBuff
     *            the buffer positioned at the material field
     * @param byName
     *            true for version 9+ (name-encoded), false for version 8 (ordinal)
     * @param gateName
     *            the gate being read, for the warning message
     * @param field
     *            which material this is, for the warning message
     * @return the material, or null if none was stored or it no longer exists
     */
    private static Material readCustomMaterial(final ByteBuffer byteBuff, final boolean byName,
        final String gateName, final String field)
    {
        if (!byName)
        {
            // Legacy version 8. Only trustworthy if this server runs the same Bukkit
            // version the gate was saved on; there is no way to detect when it does not.
            final int ordinal = byteBuff.getInt();
            return (ordinal >= 0 && ordinal < Material.values().length) ? Material.values()[ordinal] : null;
        }

        final int length = byteBuff.getInt();
        if (length <= 0)
        {
            return null; // no custom material stored
        }
        final byte[] raw = new byte[length];
        byteBuff.get(raw);
        final String materialName = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
        final Material material = Material.matchMaterial(materialName);
        if (material == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Gate \"" + gateName + "\" has an unknown custom " + field + " material \"" + materialName
                + "\"; falling back to the shape or palette default.");
        }
        return material;
    }

    /**
     * Writes one optional custom material as a length-prefixed name, or a length of 0
     * when the gate has none. See {@link #readCustomMaterial} for why not the ordinal.
     *
     * @param dataArr
     *            the buffer to write into
     * @param material
     *            the material, may be null
     */
    private static void writeCustomMaterial(final ByteBuffer dataArr, final Material material)
    {
        if (material == null)
        {
            dataArr.putInt(0);
            return;
        }
        final byte[] raw = material.name().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        dataArr.putInt(raw.length);
        dataArr.put(raw);
    }

    /**
     * Gets the byte length {@link #writeCustomMaterial} will use for a material.
     *
     * @param material
     *            the material, may be null
     * @return the encoded size in bytes, including the length prefix
     */
    private static int customMaterialSize(final Material material)
    {
        return 4 + (material == null ? 0 : material.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

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
            return readVersion3(s, byteBuff, w);
        }
        else if (s.getLoadedVersion() == 4)
        {
            return readVersion4(s, byteBuff, w);
        }
        else if (s.getLoadedVersion() == 5)
        {
            return readVersion5(s, byteBuff, w);
        }
        else if (s.getLoadedVersion() == 6)
        {
            return readVersion6(s, byteBuff, w);
        }
        else if (s.getLoadedVersion() == 7)
        {
            return readVersion7(s, byteBuff, w);
        }
        else if (s.getLoadedVersion() == 8 || s.getLoadedVersion() == 9)
        {
            return readVersion8Or9(s, byteBuff, w);
        }
        return null;
    }

    /**
     * Reads a save-version 3 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion3(final Stargate s, final ByteBuffer byteBuff, final World w)
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getInt());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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

    /**
     * Reads a save-version 4 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion4(final Stargate s, final ByteBuffer byteBuff, final World w)
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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

    /**
     * Reads a save-version 5 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion5(final Stargate s, final ByteBuffer byteBuff, final World w)
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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

    /**
     * Reads a save-version 6 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion6(final Stargate s, final ByteBuffer byteBuff, final World w)
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));

            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
    }

    /**
     * Reads a save-version 7 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion7(final Stargate s, final ByteBuffer byteBuff, final World w)
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));
            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
    }

    /**
     * Reads a save-version 8 and 9 gate out of the buffer.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @return the gate
     */
    private static Stargate readVersion8Or9(final Stargate s, final ByteBuffer byteBuff, final World w)
    {
            final boolean materialsByName = s.getLoadedVersion() >= 9;
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Unable to get sign for stargate: " + s.getGateName() + " and will be unable to change dial target.");
                    }
                }
            }

            s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
            s.setGateTempTargetId(byteBuff.getLong());

            final int facingSize = byteBuff.getInt();
            final byte[] strBytes = new byte[facingSize];
            byteBuff.get(strBytes);
            final String faceStr = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
            s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceStr));
            s.getGatePlayerTeleportLocation().setY(s.getGatePlayerTeleportLocation().getY() + 1.0);
            s.getGatePlayerTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGatePlayerTeleportLocation().setPitch(0);
            s.getGateMinecartTeleportLocation().setY(s.getGateMinecartTeleportLocation().getY() + 1.0);
            s.getGateMinecartTeleportLocation().setYaw(WorldUtils.getDegreesFromBlockFace(s.getGateFacing()));
            s.getGateMinecartTeleportLocation().setPitch(0);

            final int idcLen = byteBuff.getInt();
            final byte[] idcBytes = new byte[idcLen];
            byteBuff.get(idcBytes);
            s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

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
            s.setGateCustomStructureMaterial(readCustomMaterial(byteBuff, materialsByName, s.getGateName(), "structure"));
            s.setGateCustomPortalMaterial(readCustomMaterial(byteBuff, materialsByName, s.getGateName(), "portal"));
            s.setGateCustomLightMaterial(readCustomMaterial(byteBuff, materialsByName, s.getGateName(), "light"));
            s.setGateCustomIrisMaterial(readCustomMaterial(byteBuff, materialsByName, s.getGateName(), "iris"));
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
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "While loading gate, not all byte data was read. This could be bad: " + byteBuff.remaining());
            }

            return s;
    }

    public static byte[] stargatetoBinary(final Stargate s)
    {
        byte[] utfFaceBytes;
        byte[] utfIdcBytes;
        try
        {
            utfFaceBytes = s.getGateFacing().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            utfIdcBytes = s.getGateIrisDeactivationCode().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to store gate in DB, byte encoding failed: " + e.getMessage());
            final byte[] b = null;
            return b;
        }

        final int size = computeSize(s, utfFaceBytes, utfIdcBytes);

        final ByteBuffer dataArr = ByteBuffer.allocate(size);

        writeAnchors(dataArr, s);
        writeSignAndTarget(dataArr, s, utfFaceBytes, utfIdcBytes);
        writeRedstoneAndCustom(dataArr, s);
        writeBlockLists(dataArr, s);
        if (dataArr.remaining() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Gate data not filling whole byte array. This could be bad:" + dataArr.remaining());
        }

        return dataArr.array();
    }

    /**
     * A block-sized run of zeroes, standing in for a block this gate does not have.
     *
     * <p>The reader counts its way through the buffer rather than looking for markers, so an
     * absent block still has to occupy its slot. A fresh array each time because it is handed
     * to a buffer, and a shared one would be a mutable static for no gain.
     */
    private static byte[] emptyBlock()
    {
        return new byte[12];
    }

    /**
     * The blocks and locations every gate has, in the order the reader expects them.
     *
     * <p>A gate without an iris lever or a name holder still writes a block-sized run of
     * zeroes, because the reader counts its way through rather than looking for markers.
     */
    private static void writeAnchors(final ByteBuffer dataArr, final Stargate s)
    {
        dataArr.put(StargateSaveVersion);
        dataArr.put(DataUtils.blockToBytes(s.getGateDialLeverBlock()));
        dataArr.put(s.getGateIrisLeverBlock() != null ? DataUtils.blockToBytes(s.getGateIrisLeverBlock()) : emptyBlock());
        dataArr.put(s.getGateNameBlockHolder() != null ? DataUtils.blockToBytes(s.getGateNameBlockHolder()) : emptyBlock());
        // Serialize player teleport location as the EP block location (feet Y - 1)
        final Location playerSaveLoc = s.getGatePlayerTeleportLocation().clone();
        playerSaveLoc.setY(playerSaveLoc.getY() - 1.0);
        dataArr.put(DataUtils.locationToBytes(playerSaveLoc));

        // Serialize minecart teleport location similarly; fall back to player location if null
        if (s.getGateMinecartTeleportLocation() != null)
        {
            final Location minecartSaveLoc = s.getGateMinecartTeleportLocation().clone();
            minecartSaveLoc.setY(minecartSaveLoc.getY() - 1.0);
            dataArr.put(DataUtils.locationToBytes(minecartSaveLoc));
        }
        else
        {
            dataArr.put(DataUtils.locationToBytes(playerSaveLoc));
        }
    }

    /** The dial sign, the gate this one is dialled to, its facing, its IDC and its flags. */
    private static void writeSignAndTarget(final ByteBuffer dataArr, final Stargate s, final byte[] utfFaceBytes, final byte[] utfIdcBytes)
    {
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
            dataArr.put(emptyBlock());
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
    }

    /** The three redstone activation blocks, and the gate's own material overrides. */
    private static void writeRedstoneAndCustom(final ByteBuffer dataArr, final Stargate s)
    {
        if (s.getGateRedstoneDialActivationBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneDialActivationBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock());
        }

        if (s.getGateRedstoneSignActivationBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneSignActivationBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock());
        }

        if (s.getGateRedstoneGateActivatedBlock() != null)
        {
            dataArr.put((byte) 1);
            dataArr.put(DataUtils.blockToBytes(s.getGateRedstoneGateActivatedBlock()));
        }
        else
        {
            dataArr.put((byte) 0);
            dataArr.put(emptyBlock());
        }
        dataArr.put(s.isGateRedstonePowered() ? (byte) 1 : (byte) 0);
        dataArr.put(s.isGateCustom() ? (byte) 1 : (byte) 0);

        writeCustomMaterial(dataArr, s.getGateCustomStructureMaterial());
        writeCustomMaterial(dataArr, s.getGateCustomPortalMaterial());
        writeCustomMaterial(dataArr, s.getGateCustomLightMaterial());
        writeCustomMaterial(dataArr, s.getGateCustomIrisMaterial());
        dataArr.putInt(s.getGateCustomWooshTicks());
        dataArr.putInt(s.getGateCustomLightTicks());
        dataArr.putInt(s.getGateCustomWooshDepth());
    }

    /**
     * The gate's own blocks: structure, portal, and the light and woosh waves.
     *
     * <p>A wave with nothing in it writes a zero count rather than being skipped, so the
     * wave numbers still line up when the reader walks them back.
     */
    private static void writeBlockLists(final ByteBuffer dataArr, final Stargate s)
    {
        writeBlockRun(dataArr, s.getGateStructureBlocks());
        writeBlockRun(dataArr, s.getGatePortalBlocks());
        writeWaves(dataArr, s.getGateLightBlocks());
        writeWaves(dataArr, s.getGateWooshBlocks());
    }

    /** How many blocks, then that many of them. */
    private static void writeBlockRun(final ByteBuffer dataArr, final ArrayList<Location> blocks)
    {
        dataArr.putInt(blocks.size());
        for (final Location block : blocks)
        {
            dataArr.put(DataUtils.blockLocationToBytes(block));
        }
    }

    /**
     * How many waves, then each wave as a run of its own.
     *
     * <p>A wave with nothing in it writes a count of zero rather than being skipped, so the
     * wave numbers still line up when the reader walks them back.
     */
    private static void writeWaves(final ByteBuffer dataArr, final ArrayList<ArrayList<Location>> waves)
    {
        dataArr.putInt(waves.size());
        for (final ArrayList<Location> wave : waves)
        {
            if (wave == null)
            {
                dataArr.putInt(0);
                continue;
            }
            writeBlockRun(dataArr, wave);
        }
    }

    /**
     * Works out exactly how many bytes this gate needs.
     *
     * <p>The buffer is allocated to this and then filled, so an error here is not a
     * resize but a corrupt save: too small throws, too large leaves trailing zeroes the
     * reader walks into. GateSerializerTest pins it by reading a written buffer back and
     * checking nothing is left over.
     */
    private static int computeSize(final Stargate s, final byte[] utfFaceBytes,
        final byte[] utfIdcBytes)
    {
        final int numBlocks = 7;
        final int numLocations = 2;
        final int locationSize = 32;
        final int blockSize = 12;
        final int numBytesWithVersion = 10;
        // The four custom materials used to be fixed-width ordinals and were counted here;
        // as of version 9 they are length-prefixed names and are sized individually below.
        final int numInts = 8;
        final int numLongs = 2;

        int size = numBytesWithVersion + (numInts * 4) + (numLongs * 8) + (numBlocks * blockSize) + (numLocations * locationSize);
        size += customMaterialSize(s.getGateCustomStructureMaterial())
            + customMaterialSize(s.getGateCustomPortalMaterial())
            + customMaterialSize(s.getGateCustomLightMaterial())
            + customMaterialSize(s.getGateCustomIrisMaterial());
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
        return size;
    }
}
