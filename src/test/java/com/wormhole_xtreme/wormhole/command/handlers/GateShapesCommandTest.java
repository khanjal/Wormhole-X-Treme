package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatMatchers.plainContains;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * {@code /wormhole gate shapes <reload|validate> [name]}'s argument handling.
 *
 * <p>{@code sender} here is a bare {@code CommandSender} mock, not a {@code Player} -- the
 * permission check in {@link GateShapesCommand} only runs {@code if (sender instanceof
 * Player)}, so a console sender (which is what a bare {@code CommandSender} mock is, as far as
 * that check is concerned) exercises the command's actual dispatch logic without needing to
 * also stand up {@code WXPermissions}. Anything that touches the real GateShapes directory on
 * disk is covered instead by {@code StargateShapeRegistryReloadTest}, against plain lines
 * rather than a file path that only exists once a server has actually run.
 */
class GateShapesCommandTest
{
    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        PluginTestSupport.install(plugin);
        StargateShapeRegistry.getStargateShapes().put("Tollan", new StargateShape());
    }

    @AfterEach
    void tearDown()
    {
        StargateShapeRegistry.getStargateShapes().remove("Tollan");
        StargateShapeRegistry.setEnabled("Tollan", true);
    }

    @Test
    void disablingAShapeTurnsItOffWhateverCapitalsWereTypedAndSaysGatesKeepWorking()
    {
        assertTrue(new File(tempDir, "shapes" + File.separator + "gate").mkdirs());
        final CommandSender sender = mock(CommandSender.class);

        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "disable", "tOLLAN" }));

        assertFalse(StargateShapeRegistry.isEnabled("Tollan"));
        verify(sender).sendMessage(plainContains("Tollan can no longer be built. Gates already standing keep working."));
        verify(sender, never()).sendMessage(plainContains("could not be saved"));
    }

    @Test
    void enablingAShapeTurnsItBackOn()
    {
        assertTrue(new File(tempDir, "shapes" + File.separator + "gate").mkdirs());
        StargateShapeRegistry.setEnabled("Tollan", false);
        final CommandSender sender = mock(CommandSender.class);

        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "enable", "Tollan" }));

        assertTrue(StargateShapeRegistry.isEnabled("Tollan"));
        verify(sender).sendMessage(plainContains("Tollan can be built again."));
    }

    @Test
    void aChoiceThatCouldNotBeSavedIsSaidToLastOnlyUntilARestart()
    {
        final CommandSender sender = mock(CommandSender.class);

        new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "disable", "Tollan" });

        assertFalse(StargateShapeRegistry.isEnabled("Tollan"));
        verify(sender).sendMessage(plainContains("could not be saved"));
    }

    @Test
    void switchingAShapeThatIsNotLoadedChangesNothing()
    {
        final CommandSender sender = mock(CommandSender.class);

        new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "disable", "Nowhere" });

        assertTrue(StargateShapeRegistry.isEnabled("Nowhere"));
        verify(sender).sendMessage(plainContains("No shape called Nowhere."));
    }

    @Test
    void enableAndDisableWithoutANameShowTheirUsage()
    {
        final CommandSender sender = mock(CommandSender.class);

        new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "disable" });
        new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "enable" });

        verify(sender).sendMessage(plainContains("/wormhole gate shapes disable <name>"));
        verify(sender).sendMessage(plainContains("/wormhole gate shapes enable <name>"));
    }

    @Test
    void tooFewArgumentsShowsUsageRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes" }));
        verify(sender).sendMessage(plainContains("/wormhole gate shapes"));
    }

    @Test
    void anUnknownActionIsReportedRatherThanSilentlyIgnored()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "frobnicate" }));
        verify(sender).sendMessage(plainContains("No such shapes command"));
    }

    @Test
    void validateWithNoNameShowsUsageRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "validate" }));
        verify(sender).sendMessage(plainContains("/wormhole gate shapes validate"));
    }

    @Test
    void validatingAFileThatDoesNotExistReportsAProblemRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender,
            new String[] { "gate", "shapes", "validate", "DefinitelyNotARealShape" }));
        verify(sender).sendMessage(plainContains("problem"));
    }

    // reload with no name calls StargateShapeRegistry.reloadAllShapes(), which re-scans and
    // can create the real plugins/WormholeXTreme/GateShapes/ directory on disk -- not
    // something a unit test should trigger as a side effect. Its dispatch (as opposed to what
    // loadShapes() itself does) is covered by the other tests above; reloadAllShapes()'s own
    // "clear then loadShapes()" logic is simple enough to read directly rather than needing a
    // test that touches the filesystem to prove it.
}
