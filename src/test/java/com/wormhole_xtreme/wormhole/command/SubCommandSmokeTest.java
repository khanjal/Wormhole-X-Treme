package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Drives every {@code /wormhole} subcommand through the real dispatcher with a mocked
 * operator player.
 *
 * <p>This is not a substitute for playing the game — most of these commands need a real
 * player's position, inventory and world to do anything useful, and this asserts nothing
 * about whether they do the right thing. What it does check is that each one still
 * <em>runs</em>: that the class is reachable from the registry, that its entry point has
 * not gone stale against the 1.20 API, and that missing or malformed arguments produce a
 * message rather than an exception.
 *
 * <p>Nine of these were dead code until recently, so "does it still load and execute at
 * all" is a real question worth pinning.
 */
public class SubCommandSmokeTest
{
    private Player player;

    @BeforeEach
    public void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field pluginField = WormholeXTreme.class.getDeclaredField("thisPlugin");
        pluginField.setAccessible(true);
        pluginField.set(null, plugin);

        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final java.lang.reflect.Field schedField = WormholeXTreme.class.getDeclaredField("scheduler");
        schedField.setAccessible(true);
        schedField.set(null, scheduler);

        final World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        player = mock(Player.class);
        when(player.getName()).thenReturn("tester");
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    }

    /** Every subcommand, invoked with only its own name and nothing else. */
    @Test
    public void noSubcommandThrowsWhenGivenNoArguments()
    {
        final List<String> failures = new ArrayList<String>();
        for (final SubCommands.Entry entry : SubCommands.all())
        {
            try
            {
                entry.run(player, new String[] { entry.getName() });
            }
            catch (final Throwable t)
            {
                failures.add(entry.getName() + " -> " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            }
        }
        assertTrue(failures.isEmpty(), "subcommands threw on bare invocation:\n" + String.join("\n", failures));
    }

    /** Every subcommand, invoked with a gate name that does not exist. */
    @Test
    public void noSubcommandThrowsOnAnUnknownGateName()
    {
        final List<String> failures = new ArrayList<String>();
        for (final SubCommands.Entry entry : SubCommands.all())
        {
            try
            {
                entry.run(player, new String[] { entry.getName(), "no-such-gate-xyz" });
            }
            catch (final Throwable t)
            {
                failures.add(entry.getName() + " -> " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            }
        }
        assertTrue(failures.isEmpty(), "subcommands threw on an unknown gate:\n" + String.join("\n", failures));
    }

    /** Every subcommand, invoked with a junk third argument. */
    @Test
    public void noSubcommandThrowsOnAJunkValueArgument()
    {
        final List<String> failures = new ArrayList<String>();
        for (final SubCommands.Entry entry : SubCommands.all())
        {
            try
            {
                entry.run(player, new String[] { entry.getName(), "no-such-gate-xyz", "not-a-valid-value" });
            }
            catch (final Throwable t)
            {
                failures.add(entry.getName() + " -> " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            }
        }
        assertTrue(failures.isEmpty(), "subcommands threw on a junk value:\n" + String.join("\n", failures));
    }

    /** The migration reports rather than acting when not confirmed. */
    @Test
    public void customCleanReportsWithoutConfirmAndDoesNotThrow()
    {
        final SubCommands.Entry custom = SubCommands.find("custom");
        assertDoesNotThrow(() -> custom.run(player, new String[] { "custom", "-clean" }));
        assertDoesNotThrow(() -> custom.run(player, new String[] { "custom", "-clean", "confirm" }));
        // Something was said to the player in both cases.
        verify(player, atLeastOnce()).sendMessage(anyString());
    }
}
