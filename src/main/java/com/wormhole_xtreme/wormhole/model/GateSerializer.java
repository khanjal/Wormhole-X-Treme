/*
 * Gate serialization/deserialization extracted from StargateHelper.
 */
package com.wormhole_xtreme.wormhole.model;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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
    private static final String SIGN_UNREADABLE = "Unable to get sign for stargate: ";
    private static final String SIGN_UNREADABLE_TAIL = " and will be unable to change dial target.";
    private static final String TRAILING_BYTES = "While loading gate, not all byte data was read. This could be bad: ";

    private static final byte STARGATE_SAVE_VERSION = 9;

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

    public static Stargate parseVersionedData(final byte[] gateData, final World w, final String name, final StargateNetwork network)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateNetwork(network);
        final ByteBuffer byteBuff = ByteBuffer.wrap(gateData);

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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, SIGN_UNREADABLE + s.getGateName() + SIGN_UNREADABLE_TAIL);
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, SIGN_UNREADABLE + s.getGateName() + SIGN_UNREADABLE_TAIL);
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
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, SIGN_UNREADABLE + s.getGateName() + SIGN_UNREADABLE_TAIL);
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

            s.getGateLightBlocks().set(1, new ArrayList<>());

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
        return readVersion6Or7(s, byteBuff, w, false);
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
        return readVersion6Or7(s, byteBuff, w, true);
    }

    /**
     * Reads a save-version 6 or 7 gate, which differ by one field.
     *
     * <p>Version 7 added the minecart arrival point. Everything else is byte-for-byte the
     * same, which is why they were two copies of one method for as long as they existed --
     * the only difference in 145 lines was those two reads.
     *
     * <p>Neither version stores the third redstone block, the redstone-powered flag, or any
     * of the custom materials and timings; those arrived with version 8. So this shares the
     * later readers' helpers where the format agrees and reads its own anchors and redstone
     * where it does not.
     *
     * @param s
     *            the gate being built, already carrying its name, network and world
     * @param byteBuff
     *            the buffer, positioned just past the version byte
     * @param w
     *            the world the gate belongs to
     * @param hasMinecartLocation
     *            true for version 7, which stores a minecart arrival point of its own
     * @return the gate
     */
    private static Stargate readVersion6Or7(final Stargate s, final ByteBuffer byteBuff, final World w,
        final boolean hasMinecartLocation)
    {
        final byte[] blocArray = new byte[12];

        readEarlyAnchors(s, byteBuff, w, blocArray, hasMinecartLocation);
        readSignAndTarget(s, byteBuff, w, blocArray);
        readEarlyFacing(s, byteBuff);
        readIrisAndLights(s, byteBuff);
        readEarlyRedstone(s, byteBuff, w, blocArray);

        readBlockRun(byteBuff, w, blocArray, s.getGateStructureBlocks());
        readBlockRun(byteBuff, w, blocArray, s.getGatePortalBlocks());
        readWaves(byteBuff, w, blocArray, s.getGateLightBlocks());
        readWaves(byteBuff, w, blocArray, s.getGateWooshBlocks());

        warnIfBytesRemain(byteBuff);
        return s;
    }

    /**
     * The blocks and locations a version 6 or 7 gate has.
     *
     * <p>Version 6 stores one arrival point and version 7 two, which is the whole difference
     * between the formats.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     * @param hasMinecartLocation
     *            true for version 7
     */
    private static void readEarlyAnchors(final Stargate s, final ByteBuffer byteBuff, final World w,
        final byte[] blocArray, final boolean hasMinecartLocation)
    {
        final byte[] locArray = new byte[32];

        byteBuff.get(blocArray);
        s.setGateDialLeverBlock(DataUtils.blockFromBytes(blocArray, w));

        byteBuff.get(blocArray);
        s.setGateIrisLeverBlock(DataUtils.blockFromBytes(blocArray, w));

        byteBuff.get(blocArray);
        s.setGateNameBlockHolder(DataUtils.blockFromBytes(blocArray, w));

        byteBuff.get(locArray);
        s.setGatePlayerTeleportLocation(DataUtils.locationFromBytes(locArray, w));

        if (hasMinecartLocation)
        {
            byteBuff.get(locArray);
            s.setGateMinecartTeleportLocation(DataUtils.locationFromBytes(locArray, w));
        }
    }

    /**
     * The way the gate faces, and the arrival point it stands a player on.
     *
     * <p>Only the player's, unlike version 8: version 7 stores a minecart arrival point but
     * never stood it up, and version 6 has none at all. Recorded rather than corrected --
     * whether that was deliberate is a question about the old format, not about this reader.
     *
     * @param s
     *            the gate being built, already carrying its teleport locations
     * @param byteBuff
     *            the buffer
     */
    private static void readEarlyFacing(final Stargate s, final ByteBuffer byteBuff)
    {
        final int facingSize = byteBuff.getInt();
        final byte[] strBytes = new byte[facingSize];
        byteBuff.get(strBytes);
        final String faceName = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
        s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceName));

        orientArrival(s.getGatePlayerTeleportLocation(), s.getGateFacing());
    }

    /**
     * The two redstone activation blocks these versions know about.
     *
     * <p>The gate-activated block and the redstone-powered flag arrived in version 8, so
     * there is nothing here to read them from.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     */
    private static void readEarlyRedstone(final Stargate s, final ByteBuffer byteBuff, final World w,
        final byte[] blocArray)
    {
        final Block dialActivation = readOptionalBlock(byteBuff, w, blocArray);
        if (dialActivation != null)
        {
            s.setGateRedstoneDialActivationBlock(dialActivation);
        }
        final Block signActivation = readOptionalBlock(byteBuff, w, blocArray);
        if (signActivation != null)
        {
            s.setGateRedstoneSignActivationBlock(signActivation);
        }
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
        final byte[] blocArray = new byte[12];

        readAnchors(s, byteBuff, w, blocArray);
        readSignAndTarget(s, byteBuff, w, blocArray);
        readFacingAndOrientation(s, byteBuff);
        readIrisAndLights(s, byteBuff);
        readRedstone(s, byteBuff, w, blocArray);
        readCustom(s, byteBuff, s.getLoadedVersion() >= 9);

        readBlockRun(byteBuff, w, blocArray, s.getGateStructureBlocks());
        readBlockRun(byteBuff, w, blocArray, s.getGatePortalBlocks());
        readWaves(byteBuff, w, blocArray, s.getGateLightBlocks());
        readWaves(byteBuff, w, blocArray, s.getGateWooshBlocks());

        warnIfBytesRemain(byteBuff);
        return s;
    }

    /**
     * The blocks and locations every gate has, in the order the writer put them.
     *
     * <p>An absent iris lever or name holder still occupies its slot as a run of zeroes, so
     * these are read unconditionally and it is the block lookup that decides what they mean.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array, reused down the whole read
     */
    private static void readAnchors(final Stargate s, final ByteBuffer byteBuff, final World w,
                                    final byte[] blocArray)
    {
        final byte[] locArray = new byte[32];

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
    }

    /**
     * The dial sign, and the gate this one was dialled to when the server stopped.
     *
     * <p>The sign's own state is only read if its chunk is already loaded; a gate in an
     * unloaded chunk keeps the block and picks the sign up later.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     */
    private static void readSignAndTarget(final Stargate s, final ByteBuffer byteBuff, final World w,
                                          final byte[] blocArray)
    {
        s.setGateSignPowered(DataUtils.byteToBoolean(byteBuff.get()));

        byteBuff.get(blocArray);
        s.setGateDialSignIndex(byteBuff.getInt());
        s.setGateTempSignTarget(byteBuff.getLong());
        if (s.isGateSignPowered())
        {
            s.setGateDialSignBlock(DataUtils.blockFromBytes(blocArray, w));
            readDialSignState(s, w);
        }

        s.setGateActive(DataUtils.byteToBoolean(byteBuff.get()));
        s.setGateTempTargetId(byteBuff.getLong());
    }

    /**
     * Picks up the sign itself, if its chunk is there to pick it up from.
     *
     * @param s
     *            the gate being built, already carrying its dial sign block
     * @param w
     *            the world the gate belongs to
     */
    private static void readDialSignState(final Stargate s, final World w)
    {
        if (!w.isChunkLoaded(s.getGateDialSignBlock().getChunk()))
        {
            return;
        }
        try
        {
            s.setGateDialSign((Sign) s.getGateDialSignBlock().getState());
        }
        catch (final Exception e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, SIGN_UNREADABLE + s.getGateName() + SIGN_UNREADABLE_TAIL);
        }
    }

    /**
     * The way the gate faces, and the two arrival points it puts you at.
     *
     * <p>The writer takes a block off each height and this puts it back, because what is
     * saved is the block and what is wanted is where somebody stands on it. Yaw comes from
     * the facing rather than from the file, so an arrival always looks out of the gate.
     *
     * @param s
     *            the gate being built, already carrying its teleport locations
     * @param byteBuff
     *            the buffer
     */
    private static void readFacingAndOrientation(final Stargate s, final ByteBuffer byteBuff)
    {
        final int facingSize = byteBuff.getInt();
        final byte[] strBytes = new byte[facingSize];
        byteBuff.get(strBytes);
        final String faceName = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
        s.setGateFacing(org.bukkit.block.BlockFace.valueOf(faceName));

        orientArrival(s.getGatePlayerTeleportLocation(), s.getGateFacing());
        orientArrival(s.getGateMinecartTeleportLocation(), s.getGateFacing());
    }

    /**
     * Stands an arrival on top of its saved block, looking out of the gate.
     *
     * @param arrival
     *            the saved location, moved in place
     * @param facing
     *            the way the gate faces
     */
    private static void orientArrival(final Location arrival, final org.bukkit.block.BlockFace facing)
    {
        arrival.setY(arrival.getY() + 1.0);
        arrival.setYaw(WorldUtils.getDegreesFromBlockFace(facing));
        arrival.setPitch(0);
    }

    /**
     * The iris, its code, and whether the lights were on.
     *
     * <p>The saved iris state becomes the default as well as the current one: the default is
     * what the gate returns to after every use, so a gate left shut comes back shut.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     */
    private static void readIrisAndLights(final Stargate s, final ByteBuffer byteBuff)
    {
        final int idcLen = byteBuff.getInt();
        final byte[] idcBytes = new byte[idcLen];
        byteBuff.get(idcBytes);
        s.setGateIrisDeactivationCode(new String(idcBytes, java.nio.charset.StandardCharsets.UTF_8));

        s.setGateIrisActive(DataUtils.byteToBoolean(byteBuff.get()));
        s.setGateIrisDefaultActive(s.isGateIrisActive());
        s.setGateLightsActive(DataUtils.byteToBoolean(byteBuff.get()));
    }

    /**
     * The three redstone activation blocks, and whether the gate is wired at all.
     *
     * <p>Each is a flag followed by a block-sized slot, and the slot is consumed either way,
     * so a gate that has only one of them still lines up.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     */
    private static void readRedstone(final Stargate s, final ByteBuffer byteBuff, final World w,
                                     final byte[] blocArray)
    {
        final Block dialActivation = readOptionalBlock(byteBuff, w, blocArray);
        if (dialActivation != null)
        {
            s.setGateRedstoneDialActivationBlock(dialActivation);
        }
        final Block signActivation = readOptionalBlock(byteBuff, w, blocArray);
        if (signActivation != null)
        {
            s.setGateRedstoneSignActivationBlock(signActivation);
        }
        final Block gateActivated = readOptionalBlock(byteBuff, w, blocArray);
        if (gateActivated != null)
        {
            s.setGateRedstoneGateActivatedBlock(gateActivated);
        }
        s.setGateRedstonePowered(DataUtils.byteToBoolean(byteBuff.get()));
    }

    /**
     * Reads a present-or-not flag and the block slot behind it.
     *
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array, filled with the slot either way
     * @return the block, or null if the flag said there is none
     */
    private static Block readOptionalBlock(final ByteBuffer byteBuff, final World w, final byte[] blocArray)
    {
        final boolean present = DataUtils.byteToBoolean(byteBuff.get());
        byteBuff.get(blocArray);
        return present ? DataUtils.blockFromBytes(blocArray, w) : null;
    }

    /**
     * The gate's own material overrides and animation timings.
     *
     * <p>The squared woosh depth is derived here rather than stored, because it is what the
     * woosh compares against; a gate with no depth of its own keeps -1 rather than squaring
     * it into 1.
     *
     * @param s
     *            the gate being built
     * @param byteBuff
     *            the buffer
     * @param materialsByName
     *            whether materials are stored by name, which is true from version 9
     */
    private static void readCustom(final Stargate s, final ByteBuffer byteBuff, final boolean materialsByName)
    {
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
    }

    /**
     * Reads a counted run of blocks into one list.
     *
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     * @param target
     *            the list to fill
     */
    private static void readBlockRun(final ByteBuffer byteBuff, final World w, final byte[] blocArray,
                                     final List<Location> target)
    {
        final int count = byteBuff.getInt();
        for (int i = 0; i < count; i++)
        {
            byteBuff.get(blocArray);
            target.add(DataUtils.blockFromBytes(blocArray, w).getLocation());
        }
    }

    /**
     * Reads a counted set of counted runs, one run per wave.
     *
     * <p>A wave with nothing in it was written as a zero count rather than skipped, so the
     * layers are grown to the saved number first and the wave numbers line up either way.
     *
     * @param byteBuff
     *            the buffer
     * @param w
     *            the world the gate belongs to
     * @param blocArray
     *            a block-sized scratch array
     * @param waves
     *            the list of waves to fill
     */
    private static void readWaves(final ByteBuffer byteBuff, final World w, final byte[] blocArray,
                                  final List<List<Location>> waves)
    {
        final int numLayers = byteBuff.getInt();
        while (waves.size() < numLayers)
        {
            waves.add(new ArrayList<>());
        }
        for (int i = 0; i < numLayers; i++)
        {
            readBlockRun(byteBuff, w, blocArray, waves.get(i));
        }
    }

    /**
     * Says so if the gate did not use its whole record.
     *
     * <p>Left-over bytes mean the reader and the writer disagree about the format, which is
     * worth knowing about before the next save writes the disagreement back out.
     *
     * @param byteBuff
     *            the buffer, after everything has been read
     */
    private static void warnIfBytesRemain(final ByteBuffer byteBuff)
    {
        if (byteBuff.remaining() > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, TRAILING_BYTES + byteBuff.remaining());
        }
    }

    // S1168 asks for an empty array here. It must stay null: the caller has to tell "could not
    // encode this gate" from "encoded it", and a gate file written with no data in it loads as
    // a gate with no blocks at all. See StargateYamlManager.saveStargate, which skips on null.
    @SuppressWarnings("java:S1168")
    public static byte[] stargateToBinary(final Stargate s)
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to store gate in DB, byte encoding failed", e);
            return null;
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
        dataArr.put(STARGATE_SAVE_VERSION);
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
    private static void writeBlockRun(final ByteBuffer dataArr, final List<Location> blocks)
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
    private static void writeWaves(final ByteBuffer dataArr, final List<List<Location>> waves)
    {
        dataArr.putInt(waves.size());
        for (final List<Location> wave : waves)
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
