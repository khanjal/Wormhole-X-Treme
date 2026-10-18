package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.utils.ActionBar;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The ring's in-world lines read by key from {@code lang/} (#26).
 *
 * <p>Each line is held to the concatenation {@code RingMessages} built before the move, copied from
 * the old code, so extraction never rewords; then the countdown, sent to everyone in the rings at once,
 * is checked to reach each passenger in their own language with one lookup each. {@link ActionBar} is
 * mocked: the real one goes through {@code player.spigot()}, which a bare mock cannot answer.
 */
class RingMessageKeysTest
{
    private static Player playerReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        return player;
    }

    /** Everything said in chat to a player turned away by one end. */
    private static List<String> refusalFor(final Player player, final RingBlockage why, final String destination)
    {
        RingMessages.cannotReceive(player, destination, why);
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, times(2)).sendMessage(said.capture());
        return said.getAllValues();
    }

    /** Each action-bar line, both variants of each, against the old expression. */
    @Test
    void theActionBarLinesReadAsTheyDidBefore()
    {
        final Player player = playerReading("en");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            RingMessages.engaged(player, "Tower");
            RingMessages.engaged(player, "");
            RingMessages.counting(List.of(new BukkitRingPassenger(player)), 1);
            RingMessages.counting(List.of(new BukkitRingPassenger(player)), 2);
            RingMessages.stoodDown(player);
            RingMessages.committed(List.of(new BukkitRingPassenger(player)));
            RingMessages.arrived(player, "Tower");
            RingMessages.arrived(player, "");
            RingMessages.recharging(player, 1000L);
            RingMessages.recharging(player, 1001L);
            RingMessages.busy(player);

            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport rings engaging — travelling to "
                + ChatText.name("Tower") + "." + " Step clear to cancel.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport rings engaging." + " Step clear to cancel.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport in " + ChatText.value(String.valueOf(1))
                + " second" + "...")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport in " + ChatText.value(String.valueOf(2))
                + " seconds" + "...")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Transport rings powering down.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status(ChatText.NAME_COLOUR + "Rings deploying. Hold still.")));
            bar.verify(() -> ActionBar.send(player, ChatText.status(ChatText.NAME_COLOUR + "Arrived at " + "Tower" + ".")));
            bar.verify(() -> ActionBar.send(player, ChatText.status(ChatText.NAME_COLOUR + "Transport complete.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Rings recharging. Ready in "
                + ChatText.value(String.valueOf(1L)) + " second.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Rings recharging. Ready in "
                + ChatText.value(String.valueOf(2L)) + " seconds.")));
            bar.verify(() -> ActionBar.send(player, ChatText.info("Rings are already in use.")));
        }
    }

    /** Each blockage reason, its follow-up and both ways of naming the end, against the old expression. */
    @Test
    void theRefusalsReadAsTheyDidBefore()
    {
        final String named = "The " + ChatText.name("Tower") + " end";
        final String ceiling = ChatText.error("A ceiling ring drops its rings to the floor, so it needs one "
            + "near enough to reach.");
        final String clear = ChatText.error("Clear the inside of that ring and try again. What is "
            + "built around it does not matter.");
        assertEquals(List.of(ChatText.error(named + " has a hole in its floor."), clear),
            refusalFor(playerReading("en"), RingBlockage.NO_GROUND, "Tower"));
        assertEquals(List.of(ChatText.error("The other end of these rings" + " has a hole in its floor."), clear),
            refusalFor(playerReading("en"), RingBlockage.NO_GROUND, ""));
        assertEquals(List.of(ChatText.error(named + " is too far above its floor."), ceiling),
            refusalFor(playerReading("en"), RingBlockage.CEILING_TOO_HIGH, "Tower"));
        assertEquals(List.of(ChatText.error(named + " has no room between it and the floor."), ceiling),
            refusalFor(playerReading("en"), RingBlockage.CEILING_TOO_LOW, "Tower"));
        assertEquals(List.of(ChatText.error(named + " has too low a ceiling for the rings."),
            ChatText.error("The rings need " + ChatText.value(String.valueOf(Ring.STACK_HEIGHT))
                + " blocks of clear air above the pad.")),
            refusalFor(playerReading("en"), RingBlockage.NO_HEADROOM, "Tower"));
        assertEquals(List.of(ChatText.error(named + " has something built inside it."), clear),
            refusalFor(playerReading("en"), RingBlockage.OBSTRUCTED, "Tower"));

        final Player player = playerReading("en");
        RingMessages.notYours(player);
        verify(player).sendMessage(ChatText.error("These transport rings are private."));
    }

    /**
     * One countdown tick reaches two passengers each in their own language, with one lookup each.
     *
     * <p>It used to build one line for every passenger, which after #26 would have sent the whole
     * ring the first passenger's language; and it runs every second, so a second lookup per
     * passenger would be waste nobody sees.
     */
    @Test
    void oneCountdownTickReachesEachPassengerInTheirOwnLanguage()
    {
        final Player english = playerReading("en");
        final Player qq = playerReading("qq");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            RingMessages.counting(List.of(new BukkitRingPassenger(english), new BukkitRingPassenger(mock(Entity.class)),
                new BukkitRingPassenger(qq)), 2);

            bar.verify(() -> ActionBar.send(english, ChatText.info("Transport in " + ChatText.value("2") + " seconds...")));
            bar.verify(() -> ActionBar.send(qq, ChatText.info("QQ " + ChatText.value("2") + " seconds.")));
            bar.verify(() -> ActionBar.send(any(), anyString()), times(2));
        }
        verify(english, times(1)).getLocale();
        verify(qq, times(1)).getLocale();
    }

    /** The last second reads the singular in the passenger's language too. */
    @Test
    void theLastSecondIsSingularInThePassengersLanguage()
    {
        final Player qq = playerReading("qq");
        try (MockedStatic<ActionBar> bar = mockStatic(ActionBar.class))
        {
            RingMessages.counting(List.of(new BukkitRingPassenger(qq)), 1);

            bar.verify(() -> ActionBar.send(qq, ChatText.info("QQ " + ChatText.value("1") + " second.")));
        }
    }

    /** A refusal names the end and the reason in the player's language. */
    @Test
    void aRefusalIsInThePlayersLanguage()
    {
        assertEquals(ChatText.error("QQ QQ end " + ChatText.name("Tower") + " hole."),
            refusalFor(playerReading("qq"), RingBlockage.NO_GROUND, "Tower").get(0));
        final Player player = playerReading("qq");
        RingMessages.notYours(player);
        verify(player).sendMessage(ChatText.error("QQ private."));
    }
}
