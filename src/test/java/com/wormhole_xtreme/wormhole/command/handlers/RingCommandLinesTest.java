package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The {@code ring} lines no other test reads: cancel, list, the material edits, and the usage
 * lines of allow and owner.
 *
 * <p>Each is a refusal that says what was wrong, or a confirmation that says what changed, and
 * the block named in a confirmation is the one that was actually stored -- so a material edit
 * is checked against the ring as well as against what was said.
 *
 * <p>The light edits are not here: they ask {@code Material.isBlock()}, which goes through the
 * block registry from 1.20.6 and throws without a server, so that refusal and its two
 * confirmations are left to the lab.
 */
class RingCommandLinesTest
{
    private static final String WORLD = "world";
    private static final String OWNER = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private Player player;
    private World world;
    private MockedStatic<RingYamlManager> yaml;

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
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 500));
        yaml = mockStatic(RingYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        RingManager.clearPending(UUID.fromString(OWNER));
        RingManager.clear();
    }

    private static Ring ringAt(final int x)
    {
        return new Ring(x, 64, 0, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB, Material.GLOWSTONE);
    }

    private static RingPair registeredPair()
    {
        final RingPair pair = new RingPair("aaaa0001", WORLD, ringAt(0), ringAt(200));
        pair.setOwner(OWNER);
        pair.setOwnerName("Justin");
        RingManager.addPair(pair, 4);
        return pair;
    }

    private void run(final String... args)
    {
        new RingCommand().execute(player, args);
    }

    /** Cancelling with nothing waiting says so; cancelling a waiting end forgets it and keeps the slabs. */
    @Test
    void cancelSaysWhetherThereWasAnythingToForget()
    {
        run("ring", "cancel");
        verify(player).sendMessage(ChatText.error("You have no half-built ring pair."));

        RingManager.setPending(UUID.fromString(OWNER), ringAt(0), WORLD);
        run("ring", "cancel");

        assertNull(RingManager.getPending(UUID.fromString(OWNER)), "the waiting end is forgotten");
        verify(player).sendMessage(plainBody("Forgotten. The circle you laid is still there, so you can pair it "
            + "later or take the slabs back yourself."));
    }

    /** The list names each pair and its world in aqua; an empty one says so. */
    @Test
    void theListNamesEachPairAndItsWorld()
    {
        run("ring", "list");
        verify(player).sendMessage(plainBody("You have no transport rings."));

        final RingPair pair = registeredPair();
        run("ring", "list");

        verify(player).sendMessage(ChatText.HEADER + ChatText.NAME_COLOUR + "aaaa0001" + ChatText.BODY_COLOUR + " — "
            + ChatText.NAME_COLOUR + WORLD + ChatText.BODY_COLOUR + ", " + pair.getAccess() + ", "
            + pair.getEndA().getStyle() + "/" + pair.getEndB().getStyle());
    }

    /** The travelling ring and the laid-in slab must both be slabs; a slab is stored and named in yellow. */
    @Test
    void theRingAndBuiltMaterialsMustBeSlabs()
    {
        final RingPair pair = registeredPair();

        run("ring", "edit", "aaaa0001", "ring", "OAK_PLANKS");
        run("ring", "edit", "aaaa0001", "built", "OAK_PLANKS");

        verify(player).sendMessage(plainBody("The travelling ring has to be a slab, so it can move half a block at a time."));
        verify(player).sendMessage(plainBody("The laid-in slab has to be a slab, since reset puts it back."));
        assertEquals(Material.STONE_SLAB, pair.getEndA().getRingMaterial(), "a refusal changes nothing");

        run("ring", "edit", "aaaa0001", "ring", "OAK_SLAB");
        run("ring", "edit", "aaaa0001", "built", "BRICK_SLAB");

        assertEquals(Material.OAK_SLAB, pair.getEndB().getRingMaterial(), "naming the pair sets both ends");
        assertEquals(Material.BRICK_SLAB, pair.getEndA().getBuiltMaterial());
        verify(player).sendMessage(ChatText.HEADER + "Ring material set to " + ChatText.MATERIAL_COLOUR + "OAK_SLAB"
            + ChatText.BODY_COLOUR + ".");
        verify(player).sendMessage(plainBody("Built material set to BRICK_SLAB."));
    }

    /** allow, deny and owner with no player name show what they take. */
    @Test
    void allowDenyAndOwnerWithoutAPlayerShowTheUsage()
    {
        run("ring", "allow");
        run("ring", "deny");
        run("ring", "owner");

        verify(player).sendMessage(plainBody("Usage: /wormhole ring allow <player> [id]"));
        verify(player).sendMessage(plainBody("Usage: /wormhole ring deny <player> [id]"));
        verify(player).sendMessage(plainBody("Usage: /wormhole ring owner <player> [id]"));
    }

    /** Removing with no id while standing in nothing says how to pick a pair. */
    @Test
    void removingWithNothingUnderfootSaysHowToPickAPair()
    {
        run("ring", "remove");

        verify(player).sendMessage(plainBody("Stand in a ring, or name a pair by its id."));
    }
}
