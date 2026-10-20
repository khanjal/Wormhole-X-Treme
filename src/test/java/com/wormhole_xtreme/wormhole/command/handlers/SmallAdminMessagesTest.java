package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.freya.FreyaPreferences;
import com.wormhole_xtreme.wormhole.utils.ChatColour;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wormhole redstone}, {@code owner}, {@code restrict} and {@code freya} say, read by key
 * from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation the
 * code built before the move, copied from it, so a reworded template, a swapped argument or a branch
 * taken the wrong way shows as a changed line; and to an admin whose client reads the test language qq,
 * so a call site that reads the server's language instead of the reader's shows as English. A line
 * only the console can be sent is read in the server's language, set to qq for it.
 */
class SmallAdminMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final String GAMMA = "gamma";
    private static final String NOWHERE = "nowhere";
    private static final String ADA = "Ada";
    private static final String STEVE = "Steve";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String EN = "en";
    private static final String QQ = "qq";
    private static final String LIST_HINT = "/wormhole list";

    @TempDir
    File dataFolder;

    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        AdminReaders.clearGates();
        FreyaPreferences.clear();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        FreyaPreferences.clear();
        AdminReaders.clearGates();
        ConfigTestSupport.clear();
        Messages.load();
        PluginTestSupport.remove();
    }

    private static void redstone(final CommandSender who, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "redstone";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new RedstoneCommand().execute(who, args);
    }

    private static void owner(final CommandSender who, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "owner";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new OwnerCommand().execute(who, args);
    }

    private static void freya(final CommandSender who, final String word)
    {
        new FreyaCommand().execute(who, new String[] { "freya", word });
    }

    /** Asks, sets, is refused a word that is no switch, and names a gate there is not. */
    private static void redstoneEach(final Player player)
    {
        gate(ALPHA);
        redstone(player, ALPHA);
        redstone(player, ALPHA, TRUE);
        redstone(player, ALPHA, "yes");
        redstone(player, NOWHERE);
    }

    /** Alpha owned by Ada, beta with no owner at all, gamma with a blank one. */
    private static void threeOwners(final Player player)
    {
        gate(ALPHA).setGateOwnerName(ADA);
        gate(BETA);
        gate(GAMMA).setGateOwnerName("");
        owner(player, ALPHA);
        owner(player, BETA);
        owner(player, GAMMA);
        owner(player, NOWHERE);
    }

    /** Gives alpha to a player the server has never heard of, as an admin naming one ahead of their first join. */
    private static void giveAlphaToStranger(final Player player)
    {
        final Stargate alpha = gate(ALPHA);
        alpha.setGateOwnerName(ADA);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);
            owner(player, ALPHA, STEVE);
        }
    }

    /** The console reads the server's language; set it to qq. */
    private void serverSpeaksQq()
    {
        ConfigTestSupport.set(ConfigKeys.SERVER_LANGUAGE, QQ);
        Messages.load();
    }

    @Test
    void redstoneReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        redstoneEach(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is redstone powered: "
            + ChatText.value(FALSE) + "."));
        verify(player).sendMessage(ChatText.info("Change it with " + ChatText.command(TRUE) + " or "
            + ChatText.command(FALSE) + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is redstone powered: "
            + ChatText.value(TRUE) + "."));
        verify(player).sendMessage(ChatText.error("Redstone power is " + ChatText.command(TRUE) + " or "
            + ChatText.command(FALSE) + ", not " + ChatText.command("yes") + "."));
        verify(player).sendMessage(ChatText.error("No gate called " + ChatText.name(NOWHERE) + ". "
            + ChatText.command(LIST_HINT) + " shows them."));
    }

    @Test
    void redstoneIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        redstoneEach(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " redstone " + ChatText.value(FALSE)
            + "."));
        verify(player).sendMessage(ChatText.info("QQ change " + ChatText.command(TRUE) + " "
            + ChatText.command(FALSE) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " redstone " + ChatText.value(TRUE)
            + "."));
        verify(player).sendMessage(ChatText.error("QQ redstone " + ChatText.command(TRUE) + " "
            + ChatText.command(FALSE) + " not " + ChatText.command("yes") + "."));
        verify(player).sendMessage(ChatText.error("QQ no gate " + ChatText.name(NOWHERE) + " "
            + ChatText.command(LIST_HINT) + "."));
    }

    @Test
    void ownerReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        threeOwners(player);
        giveAlphaToStranger(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is owned by " + ChatText.name(ADA) + "."));
        verify(player).sendMessage(ChatText.info(ChatText.name(BETA) + " is owned by no one."));
        verify(player).sendMessage(ChatText.info(ChatText.name(GAMMA) + " is owned by no one."));
        verify(player).sendMessage(ChatText.error("No gate called " + ChatText.name(NOWHERE) + ". "
            + ChatText.command(LIST_HINT) + " shows them."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " is now owned by " + ChatText.name(STEVE)
            + "."));
    }

    @Test
    void ownerIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        threeOwners(player);
        giveAlphaToStranger(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " owner " + ChatText.name(ADA) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(BETA) + " owner QQ nobody."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(GAMMA) + " owner QQ nobody."));
        verify(player).sendMessage(ChatText.error("QQ no gate " + ChatText.name(NOWHERE) + " "
            + ChatText.command(LIST_HINT) + "."));
        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " new owner "
            + ChatText.name(STEVE) + "."));
    }

    @Test
    void restrictReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        new RestrictCommand().execute(player, new String[] { "restrict" });

        verify(player).sendMessage(ChatText.error("Build restriction was removed and this command no longer "
            + "does anything."));
        verify(player).sendMessage(ChatText.info("Gate building is controlled by the "
            + ChatText.command("wormhole.build") + " permission instead."));
    }

    @Test
    void restrictIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        new RestrictCommand().execute(player, new String[] { "restrict" });

        verify(player).sendMessage(ChatText.error("QQ restrict removed."));
        verify(player).sendMessage(ChatText.info("QQ restrict permission " + ChatText.command("wormhole.build")
            + "."));
    }

    /** She leaves, is on her way, does not come, and arrives with her years. */
    private static void freyaEach(final String locale, final Player[] players, final MockedStatic<FreyaCompanion> cat)
    {
        final Cat found = mock(Cat.class);
        for (int i = 0; i < players.length; i++)
        {
            players[i] = reading(locale);
        }
        freya(players[0], "off");
        cat.when(() -> FreyaCompanion.isAway(players[1].getUniqueId())).thenReturn(true);
        freya(players[1], "on");
        freya(players[2], "on");
        cat.when(() -> FreyaCompanion.spawnFor(players[3])).thenReturn(found);
        freya(players[3], "on");
    }

    @Test
    void freyaReadsAsItDidBefore()
    {
        final Player[] players = new Player[4];
        try (MockedStatic<FreyaCompanion> cat = mockStatic(FreyaCompanion.class))
        {
            freyaEach(EN, players, cat);
        }

        verify(players[0]).sendMessage(ChatText.info("She curls up somewhere else."));
        verify(players[1]).sendMessage(ChatText.info("She will be along soon."));
        verify(players[2]).sendMessage(ChatText.error("She did not come. Try again somewhere else."));
        verify(players[3]).sendMessage(ChatText.info(ChatText.name("Freya") + " pads over and sits down beside you."));
        verify(players[3]).sendMessage(ChatText.info(ChatColour.ITALIC + FreyaCompanion.YEARS));
    }

    @Test
    void freyaIsInTheReadersLanguage()
    {
        final Player[] players = new Player[4];
        try (MockedStatic<FreyaCompanion> cat = mockStatic(FreyaCompanion.class))
        {
            freyaEach(QQ, players, cat);
        }

        verify(players[0]).sendMessage(ChatText.info("QQ she leaves."));
        verify(players[1]).sendMessage(ChatText.info("QQ she comes."));
        verify(players[2]).sendMessage(ChatText.error("QQ she is absent."));
        verify(players[3]).sendMessage(ChatText.info("QQ " + ChatText.name("Freya") + " arrives."));
        verify(players[3]).sendMessage(ChatText.info(ChatColour.ITALIC + FreyaCompanion.YEARS));
    }

    /** Only a player has a cat: told in English by default, and in the server's language when it is qq. */
    @Test
    void freyaTellsTheConsoleItIsNotAPlayer()
    {
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        freya(console, "on");
        serverSpeaksQq();
        freya(console, "on");

        verify(console).sendMessage(ChatText.error("Only a player can do that."));
        verify(console).sendMessage(ChatText.error("QQ player only."));
    }
}
