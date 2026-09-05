package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A gate whose name, owner or iris code is not plain ASCII survives being stored.
 *
 * <p>The writer and the reader used to disagree. {@code StargateYamlManager} wrote each gate
 * file through a bare {@code FileWriter}, which encodes in the host's default charset, and read
 * it back through SnakeYAML, which decodes UTF-8 unconditionally. {@code GateSerializer} had
 * the same split inside a single file: it wrote the iris deactivation code with an explicit
 * {@code getBytes("UTF8")} and read it back with a bare {@code new String(idcBytes)}.
 *
 * <p>What that looked like to someone running the plugin: a gate whose name carried an accent
 * came back with a question mark in place of it after a restart, and the question mark was now
 * the gate's real name -- the next save wrote it back, so the damage stuck. An iris
 * deactivation code with an accent in it stopped matching what the owner typed, on a gate they
 * could no longer rename to something typeable.
 *
 * <p>These tests assert the encoding on disk directly rather than only round-tripping through
 * this JVM, because a round-trip alone proves nothing on a UTF-8 host: both halves of the old
 * bug agreed there, which is exactly why it went unnoticed. Pinning the bytes makes the
 * assertion mean the same thing wherever it runs. The blunter guard against the whole class of
 * mistake is {@code PlatformCharsetIsNeverUsedTest}, which reads the sources.
 *
 * <p>The literals below are written as escapes on purpose. This is the charset test, so it
 * should not itself depend on the encoding its own source file was saved in.
 */
public class Utf8GateStorageTest
{
    /** "cafe-gate", with an acute accent on the e. */
    private static final String GATE_NAME = "caf\u00e9-gate";

    /** "Bjorn", with an umlaut on the o. */
    private static final String OWNER_NAME = "Bj\u00f6rn";

    /** "oppna", with an umlaut on the leading o. */
    private static final String IRIS_CODE = "\u00f6ppna";

    @TempDir
    File tempDir;

    private Object previousPlugin;

    @BeforeEach
    public void installPluginMock() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        previousPlugin = f.get(null);
        f.set(null, plugin);
    }

    @AfterEach
    public void restorePlugin() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, previousPlugin);
    }

    private static World mockWorld()
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn("gw");
        when(w.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            final int x = invocation.getArgument(0);
            final int y = invocation.getArgument(1);
            final int z = invocation.getArgument(2);
            final Block b = mock(Block.class);
            when(b.getX()).thenReturn(x);
            when(b.getY()).thenReturn(y);
            when(b.getZ()).thenReturn(z);
            when(b.getLocation()).thenReturn(new Location(w, x, y, z));
            return b;
        });
        return w;
    }

    private static Stargate minimalGate(final World w, final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        final Block dial = mock(Block.class);
        when(dial.getX()).thenReturn(10);
        when(dial.getY()).thenReturn(64);
        when(dial.getZ()).thenReturn(20);
        when(dial.getLocation()).thenReturn(new Location(w, 10, 64, 20));
        s.setGateDialLeverBlock(dial);
        s.setGatePlayerTeleportLocation(new Location(w, 65.0, 65.0, 65.0));
        s.setGateFacing(org.bukkit.block.BlockFace.NORTH);
        return s;
    }

    /** Where these tests have the manager write, in place of the live plugin folder. */
    private File gatesDir()
    {
        return new File(tempDir, "gates");
    }

    /**
     * The file that {@code saveStargate} wrote for this gate.
     *
     * <p>Mirrors the manager's own filename rule rather than guessing: characters outside the
     * safe set become underscores, so an accented name does not produce an accented filename.
     * The name still has to survive inside the file, which is what these tests are about.
     */
    private File savedFile(final String gateName)
    {
        return new File(gatesDir(), gateName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".yml");
    }

    /**
     * Views raw bytes one-to-one, so comparing against a known encoding of a string tests the
     * bytes on disk rather than this JVM's idea of how to decode them.
     */
    private static String asBytes(final byte[] raw)
    {
        return new String(raw, StandardCharsets.ISO_8859_1);
    }

    private static String utf8Of(final String s)
    {
        return asBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void aGateNameOutsideAsciiIsOnDiskAsUtf8() throws Exception
    {
        final Stargate s = minimalGate(mockWorld(), GATE_NAME);
        StargateYamlManager.saveStargate(s, gatesDir());

        final File written = savedFile(GATE_NAME);
        assertTrue(written.isFile(), "expected a gate file at " + written);

        final String onDisk = asBytes(Files.readAllBytes(written.toPath()));
        assertTrue(onDisk.contains(utf8Of(GATE_NAME)),
            "the name must be on disk in UTF-8, because SnakeYAML reads it back as UTF-8 and "
                + "nothing tells it otherwise. Any other encoding here loses the character.");
    }

    @Test
    public void theSameYamlReaderTheLoaderUsesGetsTheNameBack() throws Exception
    {
        final Stargate s = minimalGate(mockWorld(), GATE_NAME);
        s.setGateOwner("00000000-0000-0000-0000-000000000001");
        s.setGateOwnerName(OWNER_NAME);
        StargateYamlManager.saveStargate(s, gatesDir());

        @SuppressWarnings("unchecked")
        final Map<String, Object> parsed = (Map<String, Object>) new Yaml().load(
            new String(Files.readAllBytes(savedFile(GATE_NAME).toPath()), StandardCharsets.UTF_8));

        assertEquals(GATE_NAME, parsed.get("Name"),
            "the gate came back under a different name than it was saved under, which is the "
                + "restart that renamed someone's gate to one with a question mark in it");
        assertEquals(OWNER_NAME, parsed.get("OwnerName"),
            "the owner's name is shown on the gate's sign, so a mangled one is visible in game");
    }

    /**
     * The iris code round trip, which is the half of this bug that lived inside one file.
     *
     * <p>Worth its own test because the consequence is not cosmetic: the code is compared
     * against what a player types to open the iris. A code that does not survive storage is a
     * gate whose owner is locked out by their own password.
     */
    @Test
    public void anIrisCodeOutsideAsciiSurvivesTheBinaryRoundTrip()
    {
        final World w = mockWorld();
        final Stargate s = minimalGate(w, "iris-gate");
        s.setGateIrisDeactivationCode(IRIS_CODE);

        final byte[] data = GateSerializer.stargatetoBinary(s);
        final Stargate back = GateSerializer.parseVersionedData(data, w, s.getGateName(), null);

        assertEquals(IRIS_CODE, back.getGateIrisDeactivationCode(),
            "the code is written as UTF-8, so it has to be read as UTF-8; decoding it in the "
                + "host charset locks the owner out of their own iris");
    }
}
