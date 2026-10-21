package com.wormhole_xtreme.wormhole.plugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * The newer-release notice a joining admin reads, by key from {@code lang/} (#26).
 *
 * <p>The line used to be built once, when the check came back, so every player read it in the
 * language of whoever built it. It is built at each join now: an English admin reads what the code
 * built before the move, copied from it, and an admin whose client reads the test language qq reads
 * qq, the versions and the link unchanged.
 */
class UpdateNoticeMessageTest
{
    private static final UpdateCheck.Release RELEASE = new UpdateCheck.Release("9.9.9", UpdateCheck.MODRINTH_PAGE);

    private static final String RUNNING = "1.12.0";

    private static Player joining(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.hasPermission(UpdateCheck.NOTIFY_PERMISSION)).thenReturn(Boolean.TRUE);
        final PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        UpdateCheck.notice(RELEASE, RUNNING).onPlayerJoin(event);
        return player;
    }

    @Test
    void anEnglishAdminReadsTheNoticeAsBefore()
    {
        verify(joining("en")).sendMessage(ChatText.info("Wormhole X-Treme " + ChatText.value(RELEASE.version())
            + " is available (running " + RUNNING + "): " + RELEASE.url()));
    }

    @Test
    void aQqAdminReadsTheNoticeInQq()
    {
        verify(joining("qq")).sendMessage(ChatText.info("QQ update " + ChatText.value(RELEASE.version()) + " "
            + RUNNING + " " + RELEASE.url()));
    }
}
