package com.wormhole_xtreme.wormhole.command;

import static com.wormhole_xtreme.wormhole.command.CommandReaders.gate;
import static com.wormhole_xtreme.wormhole.command.CommandReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wx idc}, {@code remove}, {@code force}, {@code go} and {@code refresh} say, read by key
 * from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command twice: to an English admin, against the concatenation the
 * code built before the move, copied from it, so a reworded template, a swapped argument or a branch
 * taken the wrong way shows as a changed line; and to an admin whose client reads the test language qq,
 * so a call site that reads the server's language instead of the reader's shows as English. A line only
 * the console can be sent is read in the server's language, set to qq for it.
 */
class GateAdminMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final String NOWHERE = "nowhere";
    private static final String EN = "en";
    private static final String QQ = "qq";
    private static final String CODE = "1234";
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
        CommandReaders.clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        CommandReaders.clearGates();
        ConfigTestSupport.clear();
        Messages.load();
        PluginTestSupport.remove();
    }

    private static String oldNoGate(final String name)
    {
        return ChatText.error("No gate called " + ChatText.name(name) + ". " + ChatText.command(LIST_HINT)
            + " shows them.");
    }

    private static String qqNoGate(final String name)
    {
        return ChatText.error("QQ no gate " + ChatText.name(name) + " " + ChatText.command(LIST_HINT) + ".");
    }

    private static Block lever()
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("w");
        final Block block = mock(Block.class);
        when(block.getX()).thenReturn(1);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(0);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        // Setting a code hangs a lever here; without its data that throws, and the command swallows it.
        final Switch data = mock(Switch.class);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private static void idc(final CommandSender who, final String... args)
    {
        new WXIDC().onCommand(who, null, "idc", args);
    }

    /** A gate with a lever, one without, and a name there is no gate for. */
    private static void idcEach(final Player player)
    {
        gate(ALPHA).setGateIrisLeverBlock(lever());
        gate(BETA);
        idc(player, ALPHA);
        idc(player, ALPHA, CODE);
        idc(player, BETA);
        idc(player, NOWHERE);
    }

    @Test
    void idcReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        idcEach(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " has no IDC."));
        verify(player).sendMessage(ChatText.info("IDC for " + ChatText.name(ALPHA) + " is " + ChatText.value(CODE)
            + "."));
        verify(player).sendMessage(ChatText.error("Iris not available: " + ChatText.name(BETA)
            + " dials by sign or has no iris lever."));
        verify(player).sendMessage(oldNoGate(NOWHERE));
    }

    @Test
    void idcIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        idcEach(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " no idc."));
        verify(player).sendMessage(ChatText.info("QQ idc " + ChatText.name(ALPHA) + " " + ChatText.value(CODE) + "."));
        verify(player).sendMessage(ChatText.error("QQ no iris " + ChatText.name(BETA) + "."));
        verify(player).sendMessage(qqNoGate(NOWHERE));
    }

    private static void remove(final CommandSender who, final String... args)
    {
        new WXRemove().onCommand(who, null, "remove", args);
    }

    /** Removes a gate, names one there is not, and hands the command no arguments at all, which it cannot read. */
    private static void removeEach(final Player player)
    {
        gate(ALPHA);
        remove(player, ALPHA);
        remove(player, NOWHERE);
        new WXRemove().onCommand(player, null, "remove", null);
    }

    @Test
    void removeReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        removeEach(player);

        verify(player).sendMessage(ChatText.info("Removed " + ChatText.name(ALPHA) + "."));
        verify(player).sendMessage(oldNoGate(NOWHERE));
        verify(player).sendMessage(ChatText.error("An error occurred while removing the gate. Check server logs."));
    }

    @Test
    void removeIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        removeEach(player);

        verify(player).sendMessage(ChatText.info("QQ removed " + ChatText.name(ALPHA) + "."));
        verify(player).sendMessage(qqNoGate(NOWHERE));
        verify(player).sendMessage(ChatText.error("QQ remove failed."));
    }

    private static void force(final CommandSender who, final String... args)
    {
        new Force().onCommand(who, null, "force", args);
    }

    /** Closes one gate, every gate, and names a gate there is not. */
    private static void forceEach(final Player player)
    {
        gate(ALPHA);
        gate(BETA);
        force(player, ALPHA);
        force(player, "-all");
        force(player, NOWHERE);
    }

    @Test
    void forceReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        forceEach(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA)
            + " has been closed, darkened and its iris opened."));
        verify(player).sendMessage(ChatText.info("All gates have been closed, darkened and their irises opened."));
        verify(player).sendMessage(oldNoGate(NOWHERE));
    }

    @Test
    void forceIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        forceEach(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " closed."));
        verify(player).sendMessage(ChatText.info("QQ all closed."));
        verify(player).sendMessage(qqNoGate(NOWHERE));
    }

    @Test
    void goReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        new Go().onCommand(player, null, "go", new String[] { NOWHERE });

        verify(player).sendMessage(ChatText.error("No gate or beam destination called " + ChatText.name(NOWHERE)
            + "."));
    }

    @Test
    void goIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        new Go().onCommand(player, null, "go", new String[] { NOWHERE });

        verify(player).sendMessage(ChatText.error("QQ nowhere " + ChatText.name(NOWHERE) + "."));
    }

    @Test
    void refreshReadsAsItDidBefore()
    {
        final Player player = reading(EN);
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        new Refresh().onCommand(player, null, "refresh", new String[0]);
        new Refresh().onCommand(console, null, "refresh", new String[0]);
        Refresh.removePendingRefresh(player);

        verify(player).sendMessage(ChatText.info("Click the DHD of the gate to regenerate it."));
        verify(console).sendMessage(ChatText.error("Only a player can do that: it waits for a click on a DHD."));
    }

    @Test
    void refreshIsInTheReadersLanguage()
    {
        final Player player = reading(QQ);
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        ConfigTestSupport.set(ConfigKeys.SERVER_LANGUAGE, QQ);
        Messages.load();
        new Refresh().onCommand(player, null, "refresh", new String[0]);
        new Refresh().onCommand(console, null, "refresh", new String[0]);
        Refresh.removePendingRefresh(player);

        verify(player).sendMessage(ChatText.info("QQ refresh click."));
        verify(console).sendMessage(ChatText.error("QQ refresh player only."));
    }
}
