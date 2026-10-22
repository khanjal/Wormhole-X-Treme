package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static com.wormhole_xtreme.wormhole.utils.ChatText.usageBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingPattern;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The verb list and the usage lines of {@code /wormhole ring}, read by key from {@code lang/} (#26).
 *
 * <p>Each goes through the real command to an English reader, against the text the code built before
 * the move, written here from it; and to a reader of the test language qr, which holds the usage and
 * help keys, so a call site that reads the server's language or nobody's shows as English. The two
 * lines only the console is sent read the server's language, set to qr.
 */
class RingUsageMessagesTest
{
    private static final String WORLD = "world";
    private static final String PAIR_ID = "aaaa0001";
    private static final String RING = "ring";
    private static final String QR = "qr";
    private static final String EDIT_FORM = "/wormhole ring edit [id] <ring|light|flash|built|name|access|style|reset> [value]";
    private static final String BUILD_FORM = "/wormhole ring build <world> <x1> <y1> <z1> <x2> <y2> <z2>";
    private static final String FIRE_FORM = "/wormhole ring fire <id>|<world> <x> <y> <z>";

    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        RingManager.clear();
        final World world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
        final Ring a = new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        final Ring b = new Ring(200, 64, 200, RingPattern.ODD, RingOrientation.FLOOR, Material.STONE_SLAB,
            Material.GLOWSTONE);
        RingManager.addPair(new RingPair(PAIR_ID, WORLD, a, b), 4);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        RingManager.clear();
        PluginTestSupport.remove();
    }

    private static List<String> sentTo(final CommandSender sender)
    {
        final ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(lines.capture());
        return new ArrayList<>(lines.getAllValues());
    }

    private static void ring(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = RING;
        System.arraycopy(rest, 0, args, 1, rest.length);
        new RingCommand().execute(sender, args);
    }

    /** What the syntax-only lines said before: the label, then the coloured syntax. */
    private static String oldUsage(final String line)
    {
        return info("Usage: " + usageBody(line));
    }

    private static String qrUsage(final String line)
    {
        return info("QR use " + usageBody(line) + ".");
    }

    private static String oldHelp(final String usage, final String what)
    {
        return info(usageBody(usage) + " — " + what);
    }

    private static String qrHelp(final String usage, final String what)
    {
        return info("QR " + what + " " + usageBody(usage) + "!");
    }

    /** Every verb of the list, as the code built it before the move. */
    private static List<String> oldHelpLines()
    {
        return List.of(
            oldHelp("/wormhole ring create", "stand in a circle of slabs; run it at each end to pair"),
            oldHelp("/wormhole ring cancel", "forget a half-built pair"),
            oldHelp("/wormhole ring list", "your pairs"),
            oldHelp("/wormhole ring remove [id]", "remove both ends"),
            oldHelp(EDIT_FORM, "change one setting"),
            oldHelp("/wormhole ring allow|deny <player> [id]", "who may use a private pair"),
            oldHelp("/wormhole ring owner <player> [id]", "hand a pair to somebody else"),
            oldHelp(BUILD_FORM, "pair two laid circles by coordinates (admin)"),
            oldHelp(FIRE_FORM, "fire a pair with nobody in it (admin)"));
    }

    private static List<String> qrHelpLines()
    {
        return List.of(
            qrHelp("/wormhole ring create", "create"),
            qrHelp("/wormhole ring cancel", "cancel"),
            qrHelp("/wormhole ring list", "list"),
            qrHelp("/wormhole ring remove [id]", "remove"),
            qrHelp(EDIT_FORM, "edit"),
            qrHelp("/wormhole ring allow|deny <player> [id]", "allow"),
            qrHelp("/wormhole ring owner <player> [id]", "owner"),
            qrHelp(BUILD_FORM, "build"),
            qrHelp(FIRE_FORM, "fire"));
    }

    @Test
    void theVerbListReadsAsItDidBefore()
    {
        final Player player = reading("en");
        ring(player);

        assertEquals(oldHelpLines(), sentTo(player));
    }

    @Test
    void theVerbListIsInTheReadersLanguage()
    {
        final Player player = reading(QR);
        ring(player, "help");

        assertEquals(qrHelpLines(), sentTo(player));
    }

    /** The console is told which verbs it can use, in the server's language: English, then qr once set. */
    @Test
    void theConsoleIsToldWhichVerbsWorkFromHereInTheServersLanguage()
    {
        final CommandSender english = mock(ConsoleCommandSender.class);
        ring(english, "create");
        final CommandSender qr = mock(ConsoleCommandSender.class);
        try (MockedStatic<ConfigManager> config = mockStatic(ConfigManager.class))
        {
            config.when(ConfigManager::getServerLanguage).thenReturn(QR);
            Messages.load();
            try
            {
                ring(qr, "create");
            }
            finally
            {
                config.when(ConfigManager::getServerLanguage).thenReturn(Messages.DEFAULT_LANGUAGE);
                Messages.load();
            }
        }

        assertEquals(List.of(error("Rings are built and edited in the world, so this is a player command."),
            oldHelp(BUILD_FORM, "works from here"), oldHelp(FIRE_FORM, "works from here")), sentTo(english));
        assertEquals(List.of(error("Rings are built and edited in the world, so this is a player command."),
            qrHelp(BUILD_FORM, "here"), qrHelp(FIRE_FORM, "here")), sentTo(qr));
    }

    /** Edit, allow, deny and owner with too little after them answer with their usage. */
    @Test
    void theEditAllowDenyAndOwnerUsagesReadAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading(QR);
        for (final Player player : List.of(english, qr))
        {
            ring(player, "edit", PAIR_ID);
            ring(player, "allow");
            ring(player, "deny");
            ring(player, "owner");
        }

        assertEquals(List.of(oldUsage(EDIT_FORM), oldUsage("/wormhole ring allow <player> [id]"),
            oldUsage("/wormhole ring deny <player> [id]"), oldUsage("/wormhole ring owner <player> [id]")),
            sentTo(english));
        assertEquals(List.of(qrUsage(EDIT_FORM), qrUsage("/wormhole ring allow <player> [id]"),
            qrUsage("/wormhole ring deny <player> [id]"), qrUsage("/wormhole ring owner <player> [id]")),
            sentTo(qr));
    }

    /** Build and fire, wrongly typed in each way they can be, answer with their usage. */
    @Test
    void theBuildAndFireUsagesReadAsBeforeAndInTheReadersLanguage()
    {
        final Player english = reading("en");
        final Player qr = reading(QR);
        for (final Player player : List.of(english, qr))
        {
            ring(player, "build");
            ring(player, "build", WORLD, "1", "2", "3", "4", "5", "x");
            ring(player, "build", WORLD, "1", "2", "3", "4", "5", "6", "7");
            ring(player, "fire");
            ring(player, "fire", WORLD, "1", "2", "x");
        }

        assertEquals(List.of(oldUsage(BUILD_FORM), oldUsage(BUILD_FORM), oldUsage(BUILD_FORM), oldUsage(FIRE_FORM),
            oldUsage(FIRE_FORM)), sentTo(english));
        assertEquals(List.of(qrUsage(BUILD_FORM), qrUsage(BUILD_FORM), qrUsage(BUILD_FORM), qrUsage(FIRE_FORM),
            qrUsage(FIRE_FORM)), sentTo(qr));
    }
}
