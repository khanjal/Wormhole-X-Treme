package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The seventeen whole lines that {@code ConfigManager.MessageStrings} held now come from
 * {@code lang/en.yml} by key (#689).
 *
 * <p>Moving a line can reword it, which a player sees as a message nobody asked for, or leave a
 * call site reading English, which a translator sees as a catalogue that does nothing. The first
 * test holds every key to the sentence the enum had; the second holds the test language qq to the
 * same keys; the third sends one of them through real code to a player whose client reads qq.
 */
class CommonMessagesTest
{
    /** Each key and the sentence the enum held, copied from it before it was removed. */
    private static final Map<String, String> ENGLISH = Map.ofEntries(
        Map.entry("common.permission-no", "You lack the permissions to do this."),
        Map.entry("common.target-self", "A gate cannot dial itself."),
        Map.entry("common.target-invalid", "Invalid gate target."),
        Map.entry("common.target-active", "Target gate is currently active."),
        Map.entry("common.cross-world-disabled", "Cross-world travel is disabled on this server."),
        Map.entry("common.gate-not-active", "No gate activated to dial. Press a DHD first."),
        Map.entry("common.gate-remote-active", "Gate remotely activated."),
        Map.entry("common.gate-shutdown", "Gate shut down."),
        Map.entry("common.far-end-closed", "The far end closed the wormhole."),
        Map.entry("common.gate-activated", "Gate successfully activated."),
        Map.entry("common.gate-deactivated", "Gate successfully deactivated."),
        Map.entry("common.gate-connected", "Stargates connected."),
        Map.entry("common.construct-success", "Gate successfully constructed."),
        Map.entry("common.gate-not-specified", "No gate name specified."),
        Map.entry("common.recent-arrival", "You can't enter an incoming wormhole."),
        Map.entry("common.remote-iris-closed", "The far gate's iris is closed."),
        Map.entry("common.insufficient-funds", "Insufficient funds to use this gate."));

    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.getLocale()).thenReturn("qq");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** The server's language says exactly what the enum did, key by key. */
    @Test
    void everyKeyReadsAsTheEnumDid()
    {
        assertEquals(17, ENGLISH.size());
        ENGLISH.forEach((key, english) -> assertEquals(english, Messages.get(null, key), key));
    }

    /** A translation of each key is read by a player whose client speaks it, not the English. */
    @Test
    void aTranslatedCatalogueReachesEveryKey()
    {
        ENGLISH.forEach((key, english) ->
        {
            final String translated = Messages.get(player, key);
            assertTrue(translated.startsWith("QQ "), key + " read as " + translated);
        });
    }

    /** The toll refusal goes through Messages for the player it is sent to, and keeps its error header. */
    @Test
    void theTollRefusalReadsInThePlayersLanguage()
    {
        ConfigTestSupport.set(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigTestSupport.set(ConfigKeys.ECONOMY_USE_COST, 3.0);
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(EconomySupport::isAvailable).thenReturn(true);
            economy.when(() -> EconomySupport.canAfford(player, 3.0)).thenReturn(false);

            assertEquals(-1.0, GateFare.affordable(player, new Stargate()));
            verify(player).sendMessage(ChatText.error("QQ insufficient funds."));
        }
    }
}
