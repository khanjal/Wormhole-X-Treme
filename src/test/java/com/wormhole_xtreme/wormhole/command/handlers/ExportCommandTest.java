package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.errorBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.infoBody;
import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.GateSerializer;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole export sqlite}: admin only, one format, and a report of what did not carry over
 * (#719).
 *
 * <p>The node matters more than most: the command reads every gate on the server into a file an
 * ordinary player could then be handed, and it writes to disk.
 */
class ExportCommandTest
{
    @TempDir
    File pluginFolder;

    private final ExportCommand command = new ExportCommand();

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        when(plugin.getDataFolder()).thenReturn(pluginFolder);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
        PluginTestSupport.forgetAllGates();
    }

    private static CommandSender console()
    {
        final CommandSender console = mock(CommandSender.class);
        when(console.hasPermission(anyString())).thenReturn(true);
        return console;
    }

    @Test
    void aPlayerWithoutTheNodeIsRefusedAndNothingIsWritten()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(false);

        assertTrue(command.execute(player, new String[] { "export", "sqlite" }));

        verify(player).sendMessage(ChatText.noPermission(player));
        assertFalse(DataLayout.exports().exists(), "a refused export must not make a folder");
    }

    @Test
    void theNodeIsExportAndAnOpHoldsItByDefault()
    {
        final Player player = mock(Player.class);
        when(player.hasPermission(ExportCommand.PERMISSION)).thenReturn(true);

        assertTrue(command.execute(player, new String[] { "export", "sqlite" }));

        assertTrue(DataLayout.exports().isDirectory());
        assertEquals("wormhole.export", ExportCommand.PERMISSION);
    }

    @Test
    void aFormatWeDoNotWriteIsNamedAndNothingIsWritten()
    {
        final CommandSender console = console();

        assertTrue(command.execute(console, new String[] { "export", "dakto" }));

        verify(console).sendMessage(errorBody("No export format called dakto. Try: /wormhole export sqlite."));
        assertFalse(DataLayout.exports().exists());
    }

    /** The reasons an export cannot run are lang keys, so a translation reaches them. */
    @Test
    void theProblemMessagesComeFromTheLanguageFile()
    {
        final CommandSender console = console();

        assertEquals("This server has no SQLite driver, so no database can be written. Run the export on a server that has one.",
            Messages.get(console, "export.no-driver"));
        assertEquals("Could not create a folder under /x.", Messages.get(console, "export.no-folder", "/x"));
        assertEquals("Could not write /x/db: disk full", Messages.get(console, "export.write-failed", "/x/db", "disk full"));
    }

    @Test
    void aMissingFormatAsksForTheUsage()
    {
        assertFalse(command.execute(console(), new String[] { "export" }),
            "false makes /wormhole send the usage line");
    }

    /** What did not carry over is said every time, even when every gate went. */
    @Test
    void theReportSaysWhatTheFormatCannotCarry()
    {
        final CommandSender console = console();

        assertTrue(command.execute(console, new String[] { "export", "sqlite" }));

        verify(console).sendMessage(plainContains("Exported 0 gates to "));
        verify(console).sendMessage(infoBody("Transport rings, mirrors and windows are not exported; the older plugins have none."));
        verify(console).sendMessage(infoBody("Custom materials are stored by number, so they only match on the same Minecraft version."));
        verify(console, never()).sendMessage(infoBody("These per-gate settings have no place in the older format, and were left out:"));
    }

    /** Each skipped gate and each kind of setting left out is listed. */
    @Test
    void skippedGatesAndDroppedSettingsAreListed()
    {
        final World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("gw");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        final Stargate kept = mock(Stargate.class);
        when(kept.getGateName()).thenReturn("Abydos");
        when(kept.getGateShapeName()).thenReturn("Standard");
        when(kept.getGateWorld()).thenReturn(world);
        when(kept.getGateOwnerName()).thenReturn("Daniel");
        when(kept.getGateUseCost()).thenReturn(2.0);
        final Stargate left = mock(Stargate.class);
        when(left.getGateName()).thenReturn("Chulak");
        when(left.getGateShapeName()).thenReturn("Grand");
        final CommandSender console = console();

        try (MockedStatic<StargateManager> gates = mockStatic(StargateManager.class);
            MockedStatic<GateSerializer> serializer = mockStatic(GateSerializer.class))
        {
            gates.when(StargateManager::getAllGatesUnsorted).thenReturn(List.of(kept, left));
            serializer.when(() -> GateSerializer.stargateToLegacyBinary(any())).thenReturn(new byte[] { 8 });

            assertTrue(command.execute(console, new String[] { "export", "sqlite" }));
        }

        verify(console).sendMessage(plainContains("Exported 1 gate to "));
        verify(console).sendMessage(plainContains("skipped Chulak: its shape \"Grand\" is not one the older plugins know"));
        verify(console).sendMessage(infoBody("These per-gate settings have no place in the older format, and were left out:"));
        verify(console).sendMessage(plainContains("own use cost (gates: 1)"));
    }
}
