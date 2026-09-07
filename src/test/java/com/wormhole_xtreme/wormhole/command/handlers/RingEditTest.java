package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Editing a ring pair's settings.
 *
 * <p>{@code edit} takes an optional pair id before the field, so the same words mean
 * different things depending on whether the first one happens to name a pair. That decides
 * both where the field is and whether the change lands on one end or both -- and the
 * difference is real: a material is cosmetic and per end, access is functional and belongs
 * to the link.
 *
 * <p>Nothing in the whole of RingCommand had a test.
 */
class RingEditTest
{
    private static final String WORLD = "world";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private Player player;
    private World world;

    @BeforeEach
    void setUp()
    {
        RingManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);

        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.fromString(OWNER));
        when(player.getName()).thenReturn("Justin");
        when(player.hasPermission(anyString())).thenReturn(false);
        standAt(500, 64, 500);
    }

    @AfterEach
    void tearDown()
    {
        RingManager.clear();
    }

    private void standAt(final int x, final int y, final int z)
    {
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
    }

    /** A registered pair owned by the player, its A end at the origin. */
    private static RingPair registeredPair(final String id)
    {
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        final RingPair pair = new RingPair(id, WORLD, a, b);
        pair.setOwner(OWNER);
        pair.setOwnerName("Justin");
        RingManager.addPair(pair, 4);
        return pair;
    }

    /** Runs the command with saving stubbed out; nothing here should touch disk. */
    private boolean edit(final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "ring";
        args[1] = "edit";
        System.arraycopy(rest, 0, args, 2, rest.length);
        try (MockedStatic<RingYamlManager> yaml = mockStatic(RingYamlManager.class))
        {
            return new RingCommand().execute(player, args);
        }
    }

    /** Not standing anywhere and not naming a pair leaves nothing to edit. */
    @Test
    void withoutAPairThereIsNothingToEdit()
    {
        assertTrue(edit("access", "private"));

        verify(player).sendMessage(contains("Stand in a ring, or name a pair by its id"));
    }

    /** Naming a pair by id works from anywhere. */
    @Test
    void namingAPairEditsItFromAnywhere()
    {
        final RingPair pair = registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "access", "private"));

        assertEquals(RingAccess.PRIVATE, pair.getAccess());
    }

    /**
     * The field position depends on whether the first word named a pair.
     *
     * <p>This is the whole awkwardness of the command. "edit access private" and
     * "edit aaaa0001 access private" both set access, and the only thing that tells them
     * apart is whether the first word happens to be an id.
     */
    @Test
    void theFieldSitsAfterTheIdOnlyWhenAnIdWasGiven()
    {
        final RingPair named = registeredPair("aaaa0001");
        assertTrue(edit("aaaa0001", "access", "private"));
        assertEquals(RingAccess.PRIVATE, named.getAccess());

        // The same words without the id act on the pair underfoot instead.
        RingManager.clear();
        final RingPair underfoot = registeredPair("bbbb0002");
        standAt(0, 65, 0);
        assertTrue(edit("access", "private"));
        assertEquals(RingAccess.PRIVATE, underfoot.getAccess());
    }

    /** A word that is not an access level is refused rather than stored. */
    @Test
    void anUnknownAccessLevelIsRefused()
    {
        final RingPair pair = registeredPair("aaaa0001");
        pair.setAccess(RingAccess.PUBLIC);

        assertTrue(edit("aaaa0001", "access", "sideways"));

        verify(player).sendMessage(contains("Access is public or private"));
        assertEquals(RingAccess.PUBLIC, pair.getAccess(), "the old value stands");
    }

    /**
     * Naming a pair by id cannot set a ring's name.
     *
     * <p>The name exists so a traveller can be told where they are going, which differs by
     * end. Setting it on both would defeat the point.
     */
    @Test
    void aPairCannotBeNamedByIdOnlyAnEndUnderfoot()
    {
        registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "name", "Mineshaft"));

        verify(player).sendMessage(contains("Stand in the ring you want to name"));
    }

    /** Standing in an end, name sets that end only. */
    @Test
    void standingInAnEndNamesThatEnd()
    {
        final RingPair pair = registeredPair("aaaa0001");
        standAt(0, 65, 0);

        assertTrue(edit("name", "Mineshaft"));

        assertEquals("Mineshaft", pair.getEndA().getName());
        assertEquals("", nullToEmpty(pair.getEndB().getName()),
            "the far end keeps its own name");
    }

    private static String nullToEmpty(final String s)
    {
        return s == null ? "" : s;
    }

    /** Style set by id reaches both ends, because it is about how the pair moves. */
    @Test
    void styleNamedByIdSetsBothEnds()
    {
        final RingPair pair = registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "style", "slow"));

        assertEquals(RingStyle.SEQUENTIAL, pair.getEndA().getStyle());
        assertEquals(RingStyle.SEQUENTIAL, pair.getEndB().getStyle());
    }

    /** A word that is not a style is refused with the two that are. */
    @Test
    void anUnknownStyleIsRefused()
    {
        registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "style", "diagonal"));

        verify(player).sendMessage(contains("Style is fast"));
    }

    /** A field nobody recognises gets the list of the ones that exist. */
    @Test
    void anUnknownFieldListsTheRealOnes()
    {
        registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "colour", "red"));

        verify(player).sendMessage(contains("Fields are:"));
    }

    /** {@code edit} with no field at all reaches the usage line rather than an index error. */
    @Test
    void editWithNoFieldIsAUsageError()
    {
        registeredPair("aaaa0001");
        standAt(0, 65, 0);

        assertTrue(edit());

        verify(player).sendMessage(contains("Usage: /wormhole ring edit"));
    }

    /** {@code reset} is the one field that needs no value. */
    @Test
    void resetNeedsNoValue()
    {
        registeredPair("aaaa0001");

        assertTrue(edit("aaaa0001", "reset"));

        verify(player, org.mockito.Mockito.never()).sendMessage(contains("Usage:"));
    }

    /** Somebody else's pair is not editable. */
    @Test
    void anotherPlayersPairIsRefused()
    {
        final RingPair pair = registeredPair("aaaa0001");
        pair.setOwner("99999999-8888-7777-6666-555555555555");
        pair.setAccess(RingAccess.PUBLIC);

        assertTrue(edit("aaaa0001", "access", "private"));

        verify(player).sendMessage(contains("not your ring pair"));
        assertEquals(RingAccess.PUBLIC, pair.getAccess());
    }

    /**
     * Naming a pair by id means both ends even while standing in one of them.
     *
     * <p>This is the case that tells the two forms apart. Everywhere else the player is
     * nowhere near a ring, so "the end underfoot" is null either way and the distinction
     * cannot show. Standing in end A and naming the pair by id has to reach end B as well,
     * or the id form would silently mean the same as the no-id form for anyone who happened
     * to be standing on their own ring.
     */
    @Test
    void namingAPairByIdReachesBothEndsEvenWhileStandingInOne()
    {
        final RingPair pair = registeredPair("aaaa0001");
        standAt(0, 65, 0);

        assertTrue(edit("aaaa0001", "style", "slow"));

        assertEquals(RingStyle.SEQUENTIAL, pair.getEndA().getStyle());
        assertEquals(RingStyle.SEQUENTIAL, pair.getEndB().getStyle(),
            "the far end must move too -- the id named the pair, not the ring underfoot");
    }
}
