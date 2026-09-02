package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Storage is where a mistake is permanent, so it is worth exercising off a live server.
 *
 * <p>Two properties matter more than the rest. A pair has to come back the same shape it
 * went in, because only the anchor, pattern and orientation are written and the whole
 * footprint is re-derived from them on the way out — a wrong anchor produces a ring that
 * loads happily and fires from the wrong blocks.
 *
 * <p>And a world file has to survive damage. Keeping a world's pairs in one file is what
 * makes startup cheap, but it also means a single bad entry is standing next to every other
 * ring in that world. Losing one broken pair is recoverable; losing a base's whole transport
 * network to a typo is not, so bad entries are skipped rather than fatal.
 */
public class RingYamlManagerTest
{
    private static final String WORLD = "world";
    private static final int REACH = 4;

    @TempDir
    File directory;

    private static RingPair pair(final String id, final int x, final int z)
    {
        final Ring a = new Ring(x, 64, z, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(x + 100, 31, z + 100, RingPattern.EVEN, RingOrientation.CEILING,
            Material.DEEPSLATE_TILE_SLAB, Material.SEA_LANTERN);
        final RingPair pair = new RingPair(id, WORLD, a, b);
        pair.setOwner("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        pair.setOwnerName("Justin");
        a.setName("Base");
        b.setName("Mine");
        pair.setCreated(1756771200000L);
        return pair;
    }

    @BeforeEach
    public void clearBefore()
    {
        RingManager.clear();
    }

    @AfterEach
    public void clearAfter()
    {
        RingManager.clear();
    }

    @Test
    public void aPairComesBackExactlyAsItWentIn() throws IOException
    {
        RingManager.addPair(pair("7f3a1c2e", 10, 10), REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();

        assertEquals(1, RingYamlManager.loadAll(directory, REACH));

        final RingPair loaded = RingManager.getPair("7f3a1c2e");
        assertNotNull(loaded);
        assertEquals(WORLD, loaded.getWorldName());
        assertEquals("Justin", loaded.getOwnerName());
        assertEquals("Base", loaded.getEndA().getName());
        assertEquals("Mine", loaded.getEndB().getName());
        assertEquals(1756771200000L, loaded.getCreated());

        assertEquals(10, loaded.getEndA().getAnchorX());
        assertEquals(64, loaded.getEndA().getAnchorY());
        assertEquals(RingPattern.ODD, loaded.getEndA().getPattern());
        assertEquals(RingOrientation.FLOOR, loaded.getEndA().getOrientation());

        assertEquals(110, loaded.getEndB().getAnchorX());
        assertEquals(RingPattern.EVEN, loaded.getEndB().getPattern());
        assertEquals(RingOrientation.CEILING, loaded.getEndB().getOrientation());
    }

    @Test
    public void eachEndKeepsItsOwnMaterials() throws IOException
    {
        // Per-end materials are the point of storing them on the end rather than the pair.
        // A round trip that quietly copied one end's materials over the other would undo it.
        RingManager.addPair(pair("abcd1234", 0, 0), REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();
        RingYamlManager.loadAll(directory, REACH);

        final RingPair loaded = RingManager.getPair("abcd1234");
        assertEquals(Material.STONE_SLAB, loaded.getEndA().getRingMaterial());
        assertEquals(Material.GLOWSTONE, loaded.getEndA().getLightMaterial());
        assertEquals(Material.DEEPSLATE_TILE_SLAB, loaded.getEndB().getRingMaterial());
        assertEquals(Material.SEA_LANTERN, loaded.getEndB().getLightMaterial());
    }

    @Test
    public void aLoadedPairIsIndexedSoItWorksWithoutBeingRebuilt() throws IOException
    {
        // Loading has to put rings back in the index, not just in the registry. A pair that
        // loads but is not indexed is a ring that exists in a listing and does nothing when
        // you stand in it.
        RingManager.addPair(pair("11112222", 300, 300), REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();
        RingYamlManager.loadAll(directory, REACH);

        assertNotNull(RingIndex.volumeAt(WORLD, 300, 64, 300));
    }

    @Test
    public void everyPairInAWorldSharesOneFile() throws IOException
    {
        RingManager.addPair(pair("aaaaaaaa", 0, 0), REACH);
        RingManager.addPair(pair("bbbbbbbb", 500, 500), REACH);
        RingYamlManager.saveWorld(directory, WORLD);

        final File[] files = directory.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length, "one world, one file");

        RingManager.clear();
        assertEquals(2, RingYamlManager.loadAll(directory, REACH));
    }

    @Test
    public void oneDamagedPairDoesNotCostTheWorldItsOtherRings() throws IOException
    {
        // The whole reason a shared file is acceptable. A pair with an unreadable pattern is
        // logged and skipped, and its neighbours still load.
        final String yaml =
            "World: world\n"
            + "Pairs:\n"
            + "  good0001:\n"
            + "    Owner: ''\n"
            + "    OwnerName: ''\n"
            + "    Label: ''\n"
            + "    Created: 1\n"
            + "    A: {X: 0, Y: 64, Z: 0, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "    B: {X: 90, Y: 64, Z: 90, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "  broken01:\n"
            + "    Owner: ''\n"
            + "    OwnerName: ''\n"
            + "    Label: ''\n"
            + "    Created: 1\n"
            + "    A: {X: 0, Y: 64, Z: 0, Pattern: TRIANGLE, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "    B: {X: 90, Y: 64, Z: 90, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n";
        Files.write(new File(directory, "world.yml").toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, RingYamlManager.loadAll(directory, REACH));
        assertNotNull(RingManager.getPair("good0001"));
        assertNull(RingManager.getPair("broken01"));
    }

    @Test
    public void aFileThatIsNotYamlAtAllIsReportedRatherThanThrown() throws IOException
    {
        Files.write(new File(directory, "world.yml").toPath(),
            "\t: : not yaml : [".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, RingYamlManager.loadAll(directory, REACH));
    }

    @Test
    public void aFileNamingNoWorldIsSkippedRatherThanGuessedAt() throws IOException
    {
        // The World field inside the file is authoritative, never the filename, so a file
        // without one cannot be placed and must not be guessed from what it is called.
        Files.write(new File(directory, "world.yml").toPath(),
            "Pairs: {}\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, RingYamlManager.loadAll(directory, REACH));
    }

    @Test
    public void theWorldFieldWinsOverTheFilename() throws IOException
    {
        final String yaml =
            "World: nether_wastes\n"
            + "Pairs:\n"
            + "  cccc3333:\n"
            + "    Owner: ''\n"
            + "    OwnerName: ''\n"
            + "    Label: ''\n"
            + "    Created: 1\n"
            + "    A: {X: 0, Y: 64, Z: 0, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "    B: {X: 90, Y: 64, Z: 90, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n";
        Files.write(new File(directory, "some_other_name.yml").toPath(),
            yaml.getBytes(StandardCharsets.UTF_8));

        RingYamlManager.loadAll(directory, REACH);
        assertEquals("nether_wastes", RingManager.getPair("cccc3333").getWorldName());
    }

    @Test
    public void aWorldNameThatIsNotAValidFilenameIsStillWritable()
    {
        final File file = RingYamlManager.fileForWorld(directory, "my:world/with*junk");
        assertFalse(file.getName().contains(":"));
        assertFalse(file.getName().contains("/"));
        assertFalse(file.getName().contains("*"));
        assertTrue(file.getName().endsWith(".yml"));
    }

    @Test
    public void removingTheLastPairLeavesNoFileBehind() throws IOException
    {
        final RingPair only = pair("dddd4444", 0, 0);
        RingManager.addPair(only, REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        assertEquals(1, directory.listFiles().length);

        RingManager.removePair(only, REACH);
        RingYamlManager.saveWorld(directory, WORLD);

        // An empty file is one that has to be read and skipped every startup, and a world
        // with no rings is better said by there being nothing there.
        assertEquals(0, directory.listFiles().length);
    }

    @Test
    public void eachEndKeepsItsOwnName() throws IOException
    {
        // The name is per end because the useful thing to say is where somebody is going,
        // and that is a different answer depending on which end they walked into.
        RingManager.addPair(pair("name0001", 700, 700), REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();
        RingYamlManager.loadAll(directory, REACH);

        final RingPair loaded = RingManager.getPair("name0001");
        assertEquals("Base", loaded.getEndA().getName());
        assertEquals("Mine", loaded.getEndB().getName());
        assertEquals("Base to Mine (name0001)", loaded.describe());
    }

    @Test
    public void aPairWithNoNamesFallsBackToItsId()
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair unnamed = new RingPair("bare0001", WORLD, a, b);
        assertEquals("bare0001", unnamed.describe());

        // Half named is still worth saying, since one end is better than neither.
        a.setName("Base");
        assertEquals("Base to ? (bare0001)", unnamed.describe());
    }

    @Test
    public void anEmptyDirectoryLoadsNothingAndDoesNotComplain()
    {
        assertEquals(0, RingYamlManager.loadAll(directory, REACH));
        assertTrue(RingManager.getAllPairs().isEmpty());
    }

    @Test
    public void pairsAreFoundByWorldAfterLoading() throws IOException
    {
        RingManager.addPair(pair("eeee5555", 0, 0), REACH);
        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();
        RingYamlManager.loadAll(directory, REACH);

        final List<RingPair> inWorld = RingManager.getPairsInWorld(WORLD);
        assertEquals(1, inWorld.size());
        assertTrue(RingManager.getPairsInWorld("somewhere_else").isEmpty());
    }
}
