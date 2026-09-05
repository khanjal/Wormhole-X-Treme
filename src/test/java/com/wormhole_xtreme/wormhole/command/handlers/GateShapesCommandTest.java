package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
    }

    @Test
    void tooFewArgumentsShowsUsageRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes" }));
        verify(sender).sendMessage(contains("/wormhole gate shapes"));
    }

    @Test
    void anUnknownActionIsReportedRatherThanSilentlyIgnored()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "frobnicate" }));
        verify(sender).sendMessage(contains("No such shapes command"));
    }

    @Test
    void validateWithNoNameShowsUsageRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender, new String[] { "gate", "shapes", "validate" }));
        verify(sender).sendMessage(contains("/wormhole gate shapes validate"));
    }

    @Test
    void validatingAFileThatDoesNotExistReportsAProblemRatherThanThrowing()
    {
        final CommandSender sender = mock(CommandSender.class);
        assertTrue(new GateShapesCommand().execute(sender,
            new String[] { "gate", "shapes", "validate", "DefinitelyNotARealShape" }));
        verify(sender).sendMessage(contains("problem"));
    }

    // reload with no name calls StargateShapeRegistry.reloadAllShapes(), which re-scans and
    // can create the real plugins/WormholeXTreme/GateShapes/ directory on disk -- not
    // something a unit test should trigger as a side effect. Its dispatch (as opposed to what
    // loadShapes() itself does) is covered by the other tests above; reloadAllShapes()'s own
    // "clear then loadShapes()" logic is simple enough to read directly rather than needing a
    // test that touches the filesystem to prove it.
}
