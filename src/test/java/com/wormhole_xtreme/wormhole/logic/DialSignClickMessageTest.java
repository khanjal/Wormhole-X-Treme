package com.wormhole_xtreme.wormhole.logic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.utils.ChatMatchers;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Clicking a dial sign answers under the plugin's header, with the gate it now shows in aqua (#438).
 *
 * <p>These two lines were the last bare ones a player saw: "Dialer set to: Abydos" in plain
 * white, with nothing to say it came from this plugin rather than another one on the server.
 */
class DialSignClickMessageTest
{
    private final Stargate gate = mock(Stargate.class);
    private final Player player = mock(Player.class);

    @BeforeEach
    void setUp() throws ReflectiveOperationException
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        when(gate.getGateName()).thenReturn("Earth");
    }

    @AfterEach
    void tearDown() throws ReflectiveOperationException
    {
        PluginTestSupport.remove();
    }

    @Test
    void aClickNamesTheGateTheSignNowShows()
    {
        final Stargate target = mock(Stargate.class);
        when(target.getGateName()).thenReturn("Abydos");
        when(gate.getGateDialSignTarget()).thenReturn(target);

        new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK).run();

        verify(gate).teleportSignClicked(true);
        verify(player).sendMessage(ChatText.HEADER + "Dialer set to " + ChatText.NAME_COLOUR + "Abydos"
            + ChatText.BODY_COLOUR + ".");
    }

    @Test
    void aClickWithNowhereToDialIsAnError()
    {
        new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK).run();

        verify(player).sendMessage(ChatMatchers.errorBody("No other gates to dial."));
    }
}
