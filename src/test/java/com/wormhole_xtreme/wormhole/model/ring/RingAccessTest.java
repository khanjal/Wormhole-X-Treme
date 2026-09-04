package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Who may travel by a pair, and why that question belongs to the pair rather than an end.
 *
 * <p>Both ends fire together and everything in both interiors swaps in the same instant, so
 * there is no way to authorise half of it. A pair whose ends disagreed would let someone
 * leave and not return, which is not a setting anyone wants and not a state the swap can
 * represent. That is the opposite of how materials work, and deliberately: a material is
 * cosmetic and local, access is functional and about the link.
 */
public class RingAccessTest
{
    private static final String WORLD = "world";
    private static final int REACH = 4;
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String FRIEND = "11111111-2222-3333-4444-555555555555";
    private static final String STRANGER = "99999999-8888-7777-6666-555555555555";

    @TempDir
    File directory;

    private static RingPair pair()
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair pair = new RingPair("acc00001", WORLD, a, b);
        pair.setOwner(OWNER);
        pair.setOwnerName("Justin");
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
    public void aNewPairIsPrivateToItsOwner()
    {
        // Rings are personal point-to-point links rather than public network infrastructure,
        // so the safe default is the closed one. A server that wants otherwise flips it.
        final RingPair pair = pair();
        assertEquals(RingAccess.PRIVATE, pair.getAccess());
        assertTrue(pair.mayUse(OWNER));
        assertFalse(pair.mayUse(STRANGER));
    }

    @Test
    public void aPublicPairIsUsableByAnybody()
    {
        final RingPair pair = pair();
        pair.setAccess(RingAccess.PUBLIC);
        assertTrue(pair.mayUse(STRANGER));
        assertTrue(pair.mayUse(null), "even somebody we cannot identify");
    }

    @Test
    public void namedPlayersMayUseAPrivatePair()
    {
        final RingPair pair = pair();
        assertFalse(pair.mayUse(FRIEND));

        assertTrue(pair.allow(FRIEND));
        assertTrue(pair.mayUse(FRIEND));
        assertFalse(pair.mayUse(STRANGER), "only the ones actually named");
    }

    @Test
    public void namingSomebodyTwiceIsNotAnError()
    {
        final RingPair pair = pair();
        assertTrue(pair.allow(FRIEND));
        assertFalse(pair.allow(FRIEND), "already on the list");
        assertEquals(1, pair.getAllowed().size());
    }

    @Test
    public void takingSomebodyOffTheListRevokesThem()
    {
        final RingPair pair = pair();
        pair.allow(FRIEND);
        assertTrue(pair.deny(FRIEND));
        assertFalse(pair.mayUse(FRIEND));
        assertFalse(pair.deny(FRIEND), "and they were only removed once");
    }

    @Test
    public void theOwnerCannotBeLockedOutByRevokingThem()
    {
        // The owner's access comes from ownership, not from the list, so a stray deny
        // cannot leave a pair nobody can use or change.
        final RingPair pair = pair();
        pair.deny(OWNER);
        assertTrue(pair.mayUse(OWNER));
    }

    @Test
    public void theAllowListCannotBeEditedThroughItsGetter()
    {
        assertThrows(UnsupportedOperationException.class, () -> pair().getAllowed().add(STRANGER));
    }

    @Test
    public void accessAndTheAllowListSurviveARoundTrip() throws IOException
    {
        final RingPair pair = pair();
        pair.setAccess(RingAccess.PUBLIC);
        pair.allow(FRIEND);
        RingManager.addPair(pair, REACH);

        RingYamlManager.saveWorld(directory, WORLD);
        RingManager.clear();
        RingYamlManager.loadAll(directory, REACH);

        final RingPair loaded = RingManager.getPair("acc00001");
        assertEquals(RingAccess.PUBLIC, loaded.getAccess());
        assertTrue(loaded.getAllowed().contains(FRIEND));
        assertTrue(loaded.mayUse(FRIEND));
    }

    @Test
    public void aStoredPairWithNoAccessFieldLoadsAsPrivate() throws IOException
    {
        // Files written before access existed have no such field. Reading that as public
        // would silently open every ring on an upgrading server.
        final String yaml =
            "World: world\n"
            + "Pairs:\n"
            + "  legacy01:\n"
            + "    Owner: '" + OWNER + "'\n"
            + "    OwnerName: Justin\n"
            + "    Label: ''\n"
            + "    Created: 1\n"
            + "    A: {X: 0, Y: 64, Z: 0, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "    B: {X: 90, Y: 64, Z: 90, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n";
        Files.write(new File(directory, "world.yml").toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        RingYamlManager.loadAll(directory, REACH);

        final RingPair loaded = RingManager.getPair("legacy01");
        assertEquals(RingAccess.PRIVATE, loaded.getAccess());
        assertFalse(loaded.mayUse(STRANGER));
    }

    @Test
    public void anUnreadableAccessFieldFailsClosedRatherThanOpen() throws IOException
    {
        // The one failure here that cannot be undone once people have used the ring, so a
        // corrupted value resolves to private rather than to whatever it looks most like.
        final String yaml =
            "World: world\n"
            + "Pairs:\n"
            + "  broken02:\n"
            + "    Owner: '" + OWNER + "'\n"
            + "    OwnerName: Justin\n"
            + "    Label: ''\n"
            + "    Created: 1\n"
            + "    Access: EVERYONE_PLEASE\n"
            + "    A: {X: 0, Y: 64, Z: 0, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n"
            + "    B: {X: 90, Y: 64, Z: 90, Pattern: ODD, Orientation: FLOOR, Ring: STONE_SLAB, Light: GLOWSTONE}\n";
        Files.write(new File(directory, "world.yml").toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        RingYamlManager.loadAll(directory, REACH);

        assertEquals(RingAccess.PRIVATE, RingManager.getPair("broken02").getAccess());
    }
}
