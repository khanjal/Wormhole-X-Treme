package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.gate;
import static com.wormhole_xtreme.wormhole.command.handlers.AdminReaders.reading;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole custom} says, read by key from {@code lang/} (#26).
 *
 * <p>Each line goes through the real command to an English admin, against the concatenation the code
 * built before the move, and to a qq admin, so a call site reading the wrong recipient shows as
 * English. The {@code -clean} counts are checked for one gate and for two: English says
 * {@code gate(s)} either way, so only qq can tell the plural was chosen the right way round, and two
 * names show how the list is joined.
 */
class CustomCommandMessagesTest
{
    private static final String ALPHA = "alpha";
    private static final String CLEAN = "-clean";
    private static final String CONFIRM = "-confirm";
    private static final String HINT = "/wormhole custom -clean -confirm";
    private static final String ABYDOS = "Abydos";
    private static final String CHULAK = "Chulak";
    private static final String BARE = "bare";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        AdminReaders.clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        AdminReaders.clearGates();
        PluginTestSupport.remove();
    }

    private static void custom(final CommandSender sender, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "custom";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new CustomCommand().execute(sender, args);
    }

    /** A gate with the four built-in defaults as its own overrides: what {@code -clean} looks for. */
    private static void snapshotted(final String name)
    {
        final Stargate gate = gate(name);
        gate.setGateCustomStructureMaterial(Material.OBSIDIAN);
        gate.setGateCustomPortalMaterial(Material.WATER);
        gate.setGateCustomIrisMaterial(Material.STONE);
        gate.setGateCustomLightMaterial(Material.GLOWSTONE);
    }

    /** Reports, a refused word, a gate with no shape, setting every gate, and nothing to clean. */
    private static void setModes(final Player player)
    {
        gate(ALPHA).setGateShape(new StargateShape());
        gate(BARE).setGateShape(null);
        custom(player, ALPHA);
        custom(player, ALPHA, "maybe");
        custom(player, BARE, TRUE);
        custom(player, "-all", TRUE);
        AdminReaders.clearGates();
        custom(player, CLEAN);
    }

    /** One snapshotted gate reported and cleared, then two. */
    private static void clean(final Player player)
    {
        snapshotted(ABYDOS);
        custom(player, CLEAN);
        custom(player, CLEAN, CONFIRM);
        AdminReaders.clearGates();
        snapshotted(ABYDOS);
        snapshotted(CHULAK);
        custom(player, CLEAN);
        custom(player, CLEAN, CONFIRM);
    }

    private static List<String> names()
    {
        return StargateManager.getAllGatesUnsorted().stream().map(g -> ChatText.name(g.getGateName())).toList();
    }

    @Test
    void modeLinesReadAsTheyDidBefore()
    {
        final Player player = reading("en");
        setModes(player);

        verify(player).sendMessage(ChatText.info(ChatText.name(ALPHA) + " custom mode: " + ChatText.value(FALSE) + "."));
        verify(player).sendMessage(ChatText.info("Change it with " + ChatText.command(TRUE) + " or "
            + ChatText.command(FALSE) + "."));
        verify(player).sendMessage(ChatText.error("Custom mode is " + ChatText.command(TRUE) + " or "
            + ChatText.command(FALSE) + ", not " + ChatText.command("maybe") + "."));
        verify(player).sendMessage(ChatText.error(ChatText.name(BARE)
            + " has no gate shape to base custom data on. Make sure its shape file is loaded."));
        verify(player).sendMessage(ChatText.info("Custom mode set to " + ChatText.value(TRUE)
            + " on every gate with a shape."));
        verify(player).sendMessage(ChatText.info("No gates are carrying snapshotted material overrides."));
    }

    @Test
    void modeLinesAreInTheReadersLanguage()
    {
        final Player player = reading("qq");
        setModes(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name(ALPHA) + " custom " + ChatText.value(FALSE) + "."));
        verify(player).sendMessage(ChatText.info("QQ change " + ChatText.command(TRUE) + " "
            + ChatText.command(FALSE) + "."));
        verify(player).sendMessage(ChatText.error("QQ custom " + ChatText.command(TRUE) + " "
            + ChatText.command(FALSE) + " not " + ChatText.command("maybe") + "."));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.name(BARE) + " no shape."));
        verify(player).sendMessage(ChatText.info("QQ custom all " + ChatText.value(TRUE) + "."));
        verify(player).sendMessage(ChatText.info("QQ none snapshotted."));
    }

    /** The report and the clean, as the code built them before the move, for one gate and for two. */
    @Test
    void cleaningReadsAsItDidBefore()
    {
        final Player player = reading("en");
        snapshotted(ABYDOS);
        snapshotted(CHULAK);
        final String both = names().stream().collect(Collectors.joining(", "));
        AdminReaders.clearGates();
        clean(player);

        for (final int count : new int[] { 1, 2 })
        {
            verify(player).sendMessage(ChatText.info(ChatText.value(String.valueOf(count))
                + " gate(s) carry material overrides matching the built-in defaults:"));
            verify(player).sendMessage(ChatText.info("Cleared material overrides on "
                + ChatText.value(String.valueOf(count)) + " gate(s); they now follow their material group."));
        }
        verify(player).sendMessage(ChatText.info(ChatText.name(ABYDOS)));
        verify(player).sendMessage(ChatText.info(both));
        verify(player, times(2)).sendMessage(ChatText.info("Clearing them lets those gates follow their material group."));
        verify(player, times(2)).sendMessage(ChatText.info("Run " + ChatText.command(HINT) + " to apply."));
    }

    @Test
    void cleaningIsInTheReadersLanguage()
    {
        final Player player = reading("qq");
        snapshotted(ABYDOS);
        snapshotted(CHULAK);
        final String both = String.join(" | ", names());
        AdminReaders.clearGates();
        clean(player);

        verify(player).sendMessage(ChatText.info("QQ found " + ChatText.value("1") + " one:"));
        verify(player).sendMessage(ChatText.info("QQ found " + ChatText.value("2") + " other:"));
        verify(player).sendMessage(ChatText.info("QQ cleared " + ChatText.value("1") + " one."));
        verify(player).sendMessage(ChatText.info("QQ cleared " + ChatText.value("2") + " other."));
        verify(player).sendMessage(ChatText.info(ChatText.name(ABYDOS)));
        verify(player).sendMessage(ChatText.info(both));
        verify(player, times(2)).sendMessage(ChatText.info("QQ clearing explained."));
        verify(player, times(2)).sendMessage(ChatText.info("QQ run " + ChatText.command(HINT) + "."));
    }
}
