package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A player reads their own client's language, everyone else the server's (#26).
 *
 * <p>The test resources ship qq (compass.none, compass.reset), qq_zz (compass.none) and rr
 * (compass.reset, compass.set), none of them a real language, and the server here speaks rr. What
 * these guard: the order a regional client falls back through, a client string that is hostile or
 * empty, two players read in one stretch, and the memory a stream of client languages costs.
 */
class MessagesLocaleTest
{
    @TempDir
    File folder;

    private final String english = "Carry a plain " + ChatText.material("compass")
        + " to see it; lodestone and recovery compasses follow their own targets.";

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        // Bukkit's getLocale, the way Spigot reads it, unless a test installs Paper's.
        PlayerLocales.lookupWith(null);
        Messages.load("rr", new File(folder, "lang"));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PlayerLocales.forget();
        Messages.load(Messages.DEFAULT_LANGUAGE, new File(folder, "none"));
        PluginTestSupport.remove();
    }

    private static Player player(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        return player;
    }

    /** A Paper {@code locale()} stand-in, for {@link PlayerLocales.Lookup}. */
    public static Locale regional()
    {
        return new Locale.Builder().setLanguage("qq").setRegion("ZZ").build();
    }

    /** de_de reads de_de, then de, then the server's language, then English, key by key. */
    @Test
    void aRegionalClientFallsBackToItsLanguageThenTheServersThenEnglish()
    {
        final Player qq = player("qq_ZZ");

        assertEquals("QQ ZZ none.", Messages.get(qq, "compass.none"), "its own region first");
        assertEquals("QQ reset.", Messages.get(qq, "compass.reset"), "then its language, ahead of the server's");
        assertEquals("RR set x.", Messages.get(qq, "compass.set", "x"), "then the server's");
        assertEquals(english, Messages.get(qq, "compass.plain-only"), "then English");
    }

    /** The console, a command block and a null recipient read the server's language. */
    @Test
    void theConsoleReadsTheServersLanguage()
    {
        assertEquals("RR reset.", Messages.get(mock(ConsoleCommandSender.class), "compass.reset"));
        assertEquals("RR reset.", Messages.get(null, "compass.reset"));
        assertEquals("No wormholes to track.", Messages.get(null, "compass.none"), "and English under it");
    }

    /**
     * A client string is trusted for nothing: a path, a stray character, nothing at all or something long
     * is no language, and the player reads the server's. Upper case is folded, since clients send de_DE.
     */
    @Test
    void anUnusableClientLocaleReadsTheServersLanguage()
    {
        for (final String hostile : Arrays.asList("../../qq", "qq/zz", "qq.yml", "", null, "qq_zzzzzzzzzzzzzzzz",
            "qé", "q q", "_qq", "qq_", "qq\u0000"))
        {
            assertNull(Messages.sanitise(hostile), "\"" + hostile + "\" must never become a code");
            assertEquals("RR reset.", Messages.get(player(hostile), "compass.reset"), String.valueOf(hostile));
        }
        assertEquals("qq_zz", Messages.sanitise("QQ_ZZ"));
        assertEquals("QQ ZZ none.", Messages.get(player("QQ_ZZ"), "compass.none"));
    }

    /** Two players in one stretch each read their own language: nothing is kept per call or per player. */
    @Test
    void twoPlayersWithDifferentLanguagesReadTheirOwn()
    {
        final Player qq = player("qq_zz");
        final Player english = player("en_us");

        assertEquals("QQ ZZ none.", Messages.get(qq, "compass.none"));
        assertEquals("No wormholes to track.", Messages.get(english, "compass.none"));
        assertEquals("QQ ZZ none.", Messages.get(qq, "compass.none"));
        assertEquals("Compass back to normal: it points at world spawn again.", Messages.get(english, "compass.reset"),
            "en_us has no file, so en, ahead of the server's");
    }

    /** A player who changes their client's language mid-session reads the new one next time. */
    @Test
    void aChangedClientLanguageIsReadAtTheNextMessage()
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn("qq_zz", "rr_xx");

        assertEquals("QQ ZZ none.", Messages.get(player, "compass.none"));
        assertEquals("No wormholes to track.", Messages.get(player, "compass.none"));
    }

    /** A client sending a new language every time cannot make the plugin remember them all. */
    @Test
    void theClientLanguagesRememberedAreBounded()
    {
        Messages.get(player("qq_zz"), "compass.none");
        assertEquals(1, Messages.remembered(), "a client language should be remembered");

        for (int i = 0; i < Messages.MOST_REMEMBERED * 4; i++)
        {
            Messages.get(player(letters(i)), "compass.none");
            assertTrue(Messages.remembered() <= Messages.MOST_REMEMBERED, "remembered " + Messages.remembered());
            // Each client code looks up at most two languages; the ones found are a handful.
            assertTrue(Messages.lookedUp() <= (2 * Messages.MOST_REMEMBERED) + 8,
                "languages looked up and not found must be let go too: " + Messages.lookedUp());
        }
        assertEquals("QQ ZZ none.", Messages.get(player("qq_zz"), "compass.none"), "and a real one still reads");
    }

    /** On Paper the locale comes from {@code locale()}, not the getLocale it deprecates. */
    @Test
    void papersLocaleIsReadWhereTheServerHasIt() throws ReflectiveOperationException
    {
        PlayerLocales.lookupWith(new PlayerLocales.Lookup(MessagesLocaleTest.class.getMethod("regional")));
        final Player player = player("en_us");

        assertEquals("QQ ZZ none.", Messages.get(player, "compass.none"));
        verify(player, never()).getLocale();
    }

    /** Where Paper's method is missing or fails, Bukkit's getLocale is read instead. */
    @Test
    void getLocaleIsReadWherePapersIsMissingOrFails() throws ReflectiveOperationException
    {
        assertEquals("QQ ZZ none.", Messages.get(player("qq_zz"), "compass.none"), "no Paper method");

        PlayerLocales.lookupWith(new PlayerLocales.Lookup(Object.class.getMethod("wait")));
        assertEquals("QQ ZZ none.", Messages.get(player("qq_zz"), "compass.none"), "a Paper method that throws");
    }

    /** @return a distinct lower-case code for each number */
    private static String letters(final int number)
    {
        final StringBuilder code = new StringBuilder("x");
        int n = number;
        do
        {
            code.append((char) ('a' + (n % 26)));
            n /= 26;
        }
        while (n > 0);
        return code.toString();
    }
}
