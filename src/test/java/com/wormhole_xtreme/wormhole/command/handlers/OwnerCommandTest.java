package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Who a gate belongs to, and who is allowed to change that.
 *
 * <p>{@code /wormhole owner} both reports and reassigns, and had no test. The reassignment
 * side matters most: it resolves a player name to a UUID so ownership survives the player
 * renaming themselves, and falls back to storing the raw name only when there is no UUID to
 * be had. Getting that wrong hands somebody else's gate away.
 */
class OwnerCommandTest
{
    private Player sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        sender = mock(Player.class);
        when(sender.getName()).thenReturn("admin");
        when(sender.isOp()).thenReturn(true);
        clearGates();
    }

    @AfterEach
    void tearDown()
    {
        clearGates();
    }

    private static void clearGates()
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private static Stargate gate(final String name, final String owner)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateOwner(owner);
        s.setGateOwnerName(owner);
        StargateManager.registerStargate(s);
        return s;
    }

    private boolean run(final String... args)
    {
        return new OwnerCommand().execute(sender, args);
    }

    /** A player without the config node is refused, whatever they were asking for. */
    @Test
    void aPlayerWithoutTheConfigNodeIsRefused()
    {
        when(sender.isOp()).thenReturn(false);
        when(sender.hasPermission(anyString())).thenReturn(false);
        when(sender.getUniqueId()).thenReturn(UUID.randomUUID());
        gate("alpha", "someone");

        assertTrue(run("owner", "alpha"));

        verify(sender).sendMessage(contains("ermission"));
    }

    /** Naming no gate is a usage error, and says so by returning false. */
    @Test
    void namingNoGateIsAUsageError()
    {
        assertFalse(run("owner"), "returning false is what prints the usage line");

        verify(sender).sendMessage(contains("gate"));
    }

    /** A gate nobody has built is named back to the player rather than silently ignored. */
    @Test
    void anUnknownGateNameIsQuotedBack()
    {
        assertTrue(run("owner", "nowhere"));

        verify(sender).sendMessage(contains("nowhere"));
    }

    /** Asked about a gate, it reports the owner and changes nothing. */
    @Test
    void askingAboutAGateReportsItsOwner()
    {
        final Stargate s = gate("alpha", "Ada");

        assertTrue(run("owner", "alpha"));

        verify(sender).sendMessage(contains("Owned by: Ada"));
        assertEquals("Ada", s.getGateOwner(), "asking must not reassign");
    }


    /**
     * The roster is matched without regard to case.
     *
     * <p>An admin typing a name from memory should not have to get the capitalisation right,
     * and the name stored is the server's spelling rather than theirs.
     */
    @Test
    void aKnownPlayerIsFoundWhateverTheCase()
    {
        final OfflinePlayer grace = known("Grace", true);

        final OfflinePlayer found = OwnerCommand.findKnownPlayer("grace", new OfflinePlayer[] {grace});

        assertSame(grace, found);
        assertEquals("Grace", found.getName(), "the server's spelling, not the admin's");
    }

    /**
     * A name that matches somebody who has never played is not a match.
     *
     * <p>Bukkit hands back an OfflinePlayer for any name asked about, played or not, so the
     * name matching alone would accept anything.
     */
    @Test
    void aPlayerWhoHasNeverPlayedIsNotAMatch()
    {
        final OfflinePlayer neverSeen = known("Stranger", false);

        assertNull(OwnerCommand.findKnownPlayer("Stranger", new OfflinePlayer[] {neverSeen}));
    }

    /** A roster with nobody of that name in it finds nobody. */
    @Test
    void anUnknownNameFindsNobody()
    {
        assertNull(OwnerCommand.findKnownPlayer("Nobody", new OfflinePlayer[] {known("Grace", true)}));
    }

    /** A null in the roster is stepped over rather than thrown on. */
    @Test
    void aNullInTheRosterIsSteppedOver()
    {
        final OfflinePlayer grace = known("Grace", true);

        assertSame(grace, OwnerCommand.findKnownPlayer("Grace", new OfflinePlayer[] {null, grace}));
    }

    private static OfflinePlayer known(final String name, final boolean hasPlayed)
    {
        final OfflinePlayer op = mock(OfflinePlayer.class);
        when(op.getName()).thenReturn(name);
        when(op.hasPlayedBefore()).thenReturn(hasPlayed);
        return op;
    }
}
