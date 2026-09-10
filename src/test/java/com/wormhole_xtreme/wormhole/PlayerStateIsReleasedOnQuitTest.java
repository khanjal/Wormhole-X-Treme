package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.command.Refresh;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;

/**
 * Nothing remembers a player after they have gone.
 *
 * <p>Six static maps across this plugin are keyed by {@link Player} rather than by id, and
 * before this nothing removed from any of them on logout. A Bukkit Player is not a light thing
 * to hold: the object reaches the entity, its inventory and the world it was standing in, so a
 * server with real churn accumulated one per person who had ever half-built a gate, activated
 * one, picked a shape, or travelled through a wormhole -- and kept every one of them until it
 * was restarted. On a big server that is the leak that matters, because it grows with how many
 * people have ever played rather than with how many are playing.
 *
 * <p>Nothing observable is taken away by clearing them. Bukkit hands out a fresh Player object
 * on the next login, so a stale entry could never have been matched against the person it
 * belonged to again -- it was unreachable state being kept alive. That is worth stating
 * plainly, because "clear the cooldown on quit" would otherwise read like a way to dodge a
 * cooldown by relogging, and it is not: the cooldown was already unreachable.
 */
class PlayerStateIsReleasedOnQuitTest
{
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateManager.forgetPlayer(player);
        StargateRestrictions.forgetPlayer(player);
        Refresh.removePendingRefresh(player);
        PluginTestSupport.remove();
    }

    /** A quit event for the player under test. Mocked rather than constructed: the
     * constructor's signature has moved more than once across the 1.20-1.21.10 range, and the
     * listener reads nothing off the event but the player. */
    private PlayerQuitEvent quit()
    {
        final PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    /**
     * A half-built gate does not outlive the builder.
     *
     * <p>Somebody who places the ring, is asked to name it, and closes the game instead was
     * remembered forever: the incomplete gate held them, and they held their world.
     */
    @Test
    void anIncompleteGateIsForgottenWhenItsBuilderLeaves()
    {
        final Stargate half = new Stargate();
        half.setGateName("half-built");
        StargateManager.addIncompleteStargate(player, half);

        new WormholeXTremePlayerListener().onPlayerQuit(quit());

        assertNull(StargateManager.getIncompleteStargateName(player),
            "a gate nobody finished naming should not keep its builder in memory for the life"
                + " of the server");
    }

    /**
     * Neither does an activated gate, or a chosen build shape.
     *
     * <p>Both are ordinary things to leave in the middle of -- click a DHD and log off, or run
     * the build command and change your mind.
     */
    @Test
    void anActivatedGateAndAChosenShapeAreForgottenTogether()
    {
        final Stargate activated = new Stargate();
        activated.setGateName("activated");
        StargateManager.addActivatedStargate(player, activated);
        StargateManager.addPlayerBuilderShape(player, new StargateShape());

        new WormholeXTremePlayerListener().onPlayerQuit(quit());

        assertNull(StargateManager.getPlayerBuilderShape(player), "the chosen shape is released");
        assertNull(StargateManager.removeActivatedStargate(player),
            "and so is the gate they left activated");
    }

    /**
     * So is a pending refresh.
     *
     * <p>{@code /wormhole refresh} arms the next DHD click. Logging off instead left the arming
     * -- and the player -- in a map with nothing to disarm it.
     */
    @Test
    void aPendingRefreshIsForgottenWhenThePlayerLeaves()
    {
        Refresh.addPendingRefresh(player);

        new WormholeXTremePlayerListener().onPlayerQuit(quit());

        assertFalse(Refresh.isPendingRefresh(player), "refresh mode does not outlive the session");
    }

    /**
     * And so is a recent arrival.
     *
     * <p>This one is cleared by a scheduled task a few seconds later, which is why it looked
     * bounded. It is not: under a scheduler that never runs the task -- a restart, a plugin
     * reload, or simply a task lost -- the entry stays, and with it the player.
     */
    @Test
    void aRecentArrivalIsForgottenWhenThePlayerLeaves()
    {
        final Stargate from = new Stargate();
        from.setGateName("origin");
        StargateRestrictions.addPlayerRecentArrival(player, from);

        new WormholeXTremePlayerListener().onPlayerQuit(quit());

        assertFalse(StargateRestrictions.isPlayerRecentArrivalFrom(player, from),
            "the arrival flag, and the Player object it was keyed on, are both released");
    }
}
