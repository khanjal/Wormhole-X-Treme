package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What {@code /wx list} shows, and to whom.
 *
 * <p>Two things worth holding. The network filter treats "Public" as meaning the gates that
 * are on no network at all, rather than one literally called Public -- a gate with no network
 * would otherwise be unlistable. And the output is broken into several messages once it grows,
 * because a long enough line is truncated by the client rather than wrapped, which would lose
 * gate names off the end silently.
 */
class WXListTest
{
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        // Not a player, so the listing is not gated on a permission node.
        sender = mock(CommandSender.class);
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

    private static Stargate gate(final String name, final String network)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        if (network != null)
        {
            s.setGateNetwork(StargateManager.addStargateNetwork(network));
        }
        StargateManager.registerStargate(s);
        return s;
    }

    private void list(final String... args)
    {
        new WXList().onCommand(sender, null, "list", args);
    }

    /** Everything the sender sees, in order. */
    private List<String> messages()
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(sent.capture());
        return sent.getAllValues();
    }

    @Test
    void withNoFilterEveryGateIsListed()
    {
        gate("alpha", null);
        gate("beta", "secret");

        list();

        final String all = String.join(" ", messages());
        assertTrue(all.contains("alpha"), all);
        assertTrue(all.contains("beta"), all);
    }

    @Test
    void aNetworkFilterShowsOnlyThatNetwork()
    {
        gate("alpha", null);
        gate("beta", "secret");
        gate("gamma", "other");

        list("secret");

        final String all = String.join(" ", messages());
        assertTrue(all.contains("beta"), all);
        assertTrue(!all.contains("gamma"), "another network's gates are not on this list: " + all);
        assertTrue(!all.contains("alpha"), "nor are the ones on no network: " + all);
    }

    /**
     * "Public" means the gates on no network at all.
     *
     * <p>Not a network of that name: a gate that was never given one would otherwise never
     * appear on any filtered list.
     */
    @Test
    void publicMeansTheGatesOnNoNetwork()
    {
        gate("alpha", null);
        gate("beta", "secret");

        list("public");

        final String all = String.join(" ", messages());
        assertTrue(all.contains("alpha"), all);
        assertTrue(!all.contains("beta"), all);
    }

    /** A filter matching nothing says so rather than showing an empty line. */
    @Test
    void aFilterMatchingNothingSaysSo()
    {
        gate("alpha", "secret");

        list("nowhere");

        verify(sender).sendMessage(contains("No gates found"));
    }

    /**
     * A long list is broken up rather than sent as one line.
     *
     * <p>A single message past the client's limit is truncated, not wrapped, so the names off
     * the end would simply not be shown.
     */
    @Test
    void aLongListIsBrokenIntoSeveralMessages()
    {
        for (int i = 0; i < 12; i++)
        {
            gate("gate-with-a-longish-name-" + i, null);
        }

        list();

        int listingLines = 0;
        for (final String m : messages())
        {
            if (m.contains("gate-with-a-longish-name-"))
            {
                listingLines++;
            }
        }
        assertTrue(listingLines > 1, "twelve gates should not arrive as one line, got " + listingLines);
    }

    /** A short list arrives as one line, so the breaking up is not gratuitous. */
    @Test
    void aShortListArrivesAsOneLine()
    {
        gate("zulu", null);
        gate("yankee", null);

        list();

        // Distinctive names on purpose: the header itself contains most short letters, so a
        // loose match counts it as a listing line and the assertion means nothing.
        int listingLines = 0;
        for (final String m : messages())
        {
            if (m.contains("zulu") && m.contains("yankee"))
            {
                listingLines++;
            }
        }
        assertEquals(1, listingLines, "two short names fit in one message");
    }

    /** A player without the list node is refused and shown nothing. */
    @Test
    void aPlayerWithoutTheNodeIsRefused()
    {
        gate("alpha", null);
        final org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getName()).thenReturn("nosy");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        new WXList().onCommand(player, null, "list", new String[0]);

        verify(player).sendMessage(contains("ermission"));
        verify(player, never()).sendMessage(contains("alpha"));
    }
}
