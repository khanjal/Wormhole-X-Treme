package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;

/**
 * Who may curate the beam destination list, and who may move players with it.
 *
 * <p>`/wormhole beam admin` reads as one command behind one permission, and it is not. `goto`
 * and `send` are dispatched before the `wormhole.beam.admin` check and carry their own
 * `wormhole.beam.admin.teleport` instead -- which looks like the gate being skipped, and is
 * how console gets to use `send` at all when everything below it is player-only.
 *
 * <p>Nothing covered any of it. The whole of `admin` was uncovered, and the shape of it is one
 * a tidying refactor would get wrong in either direction: hoisting the `admin` check up for
 * consistency takes `send` away from console, and dropping the per-method checks hands every
 * player the ability to teleport anybody anywhere.
 */
class BeamAdminPermissionsTest
{
    private Player admin;
    private World world;
    private CommandSender console;
    private BeamCommand command;
    private MockedStatic<BeamYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        BeamManager.clear();
        command = new BeamCommand();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        admin = mock(Player.class);
        when(admin.getName()).thenReturn("Justin");
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.getWorld()).thenReturn(world);
        when(admin.getLocation()).thenReturn(new Location(world, 10.5, 64.0, 20.5, 90.0f, 0.0f));
        // Op bypasses every node, so an unstubbed isOp() returning false is doing real work in
        // each of these -- stated rather than relied on.
        when(admin.isOp()).thenReturn(Boolean.FALSE);
        when(admin.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        console = mock(CommandSender.class);
        when(console.isOp()).thenReturn(Boolean.FALSE);
        when(console.hasPermission(anyString())).thenReturn(Boolean.FALSE);

        yaml = mockStatic(BeamYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        BeamManager.clear();
    }

    private static void holding(final CommandSender who, final String node)
    {
        when(who.hasPermission(node)).thenReturn(Boolean.TRUE);
    }

    private boolean run(final CommandSender who, final String... rest)
    {
        final String[] args = new String[rest.length + 2];
        args[0] = "beam";
        args[1] = "admin";
        System.arraycopy(rest, 0, args, 2, rest.length);
        return command.execute(who, args);
    }

    /**
     * Beaming yourself anywhere needs the teleport node, and nothing else does instead.
     *
     * <p>`goto` is answered before the `wormhole.beam.admin` check, so this node is the only
     * thing standing between an ordinary player and instant travel to any coordinates they
     * care to type.
     */
    @Test
    void aPlayerWithoutTheTeleportNodeMayNotBeamThemselvesAnywhere()
    {
        assertTrue(run(admin, "goto", "100", "64", "100"));

        verify(admin).sendMessage(contains("permission"));
    }

    /**
     * And the curating node is not a substitute for it.
     *
     * <p>The two are deliberately separate: `wormhole.beam.admin` is for the destination list,
     * `wormhole.beam.admin.teleport` is for moving people. Somebody trusted to name places is
     * not thereby trusted to put anyone anywhere.
     */
    @Test
    void theCuratingNodeDoesNotCarryTheRightToBeamAnywhere()
    {
        holding(admin, BeamPermissions.ADMIN);

        assertTrue(run(admin, "goto", "100", "64", "100"));

        verify(admin).sendMessage(contains("permission"));
    }

    /** Nor the other way about: the teleport node does not open the destination list. */
    @Test
    void theTeleportNodeDoesNotCarryTheRightToCurateTheList()
    {
        holding(admin, BeamPermissions.ADMIN_TELEPORT);

        assertTrue(run(admin, "set", "hub"));

        verify(admin).sendMessage(contains("permission"));
        assertNull(BeamManager.getPublicDestination("hub"), "nothing was added to the list");
    }

    /** Moving somebody else needs the teleport node too, whoever is asking. */
    @Test
    void aSenderWithoutTheTeleportNodeMayNotSendAnybody()
    {
        assertTrue(run(console, "send", "Grace", "100", "64", "100"));

        verify(console).sendMessage(contains("permission"));
    }

    /**
     * Console is refused `goto` for having nowhere to beam from, not for want of permission.
     *
     * <p>The reason matters: told it lacks a permission, an operator goes and grants one that
     * would never have helped. There is no console to move.
     */
    @Test
    void consoleIsRefusedGotoForHavingNowhereToBeamFrom()
    {
        holding(console, BeamPermissions.ADMIN_TELEPORT);

        assertTrue(run(console, "goto", "100", "64", "100"));

        verify(console).sendMessage(contains("nowhere for console"));
    }

    /**
     * Everything that reads where the sender is standing stays player-only.
     *
     * <p>`set` records the sender's own location, so there is nothing for console to record.
     * Said as its own message rather than as a permission refusal, for the same reason.
     */
    @Test
    void curatingTheListFromConsoleIsRefusedAsPlayerOnly()
    {
        holding(console, BeamPermissions.ADMIN);

        assertTrue(run(console, "set", "hub"));

        verify(console).sendMessage(contains("player-only"));
    }

    /** With the node, setting a destination records where the admin is standing. */
    @Test
    void settingADestinationRecordsWhereTheAdminStands()
    {
        holding(admin, BeamPermissions.ADMIN);

        assertTrue(run(admin, "set", "hub"));

        assertNotNull(BeamManager.getPublicDestination("hub"), "the destination is in the list");
        assertEquals(10.5, BeamManager.getPublicDestination("hub").getX(), 1.0e-9);
        assertEquals(20.5, BeamManager.getPublicDestination("hub").getZ(), 1.0e-9);
        yaml.verify(BeamYamlManager::saveAll);
    }

    /**
     * Removing one that is there takes it out of the list and writes the list out.
     *
     * <p>The invocations are cleared between the two commands because the {@code set} that
     * arranges this saves as well, and a bare verify would pass on that one alone -- which is
     * the whole thing this is trying to check.
     */
    @Test
    void removingADestinationTakesItOutAndSavesTheList()
    {
        holding(admin, BeamPermissions.ADMIN);
        run(admin, "set", "hub");
        yaml.clearInvocations();

        assertTrue(run(admin, "remove", "hub"));

        assertNull(BeamManager.getPublicDestination("hub"));
        yaml.verify(BeamYamlManager::saveAll);
        verify(admin).sendMessage(contains("Removed public beam destination"));
    }

    /**
     * Removing one that was never there writes nothing.
     *
     * <p>A save is a file written for every mistyped name otherwise, and the list is unchanged
     * either way.
     */
    @Test
    void removingADestinationThatIsNotThereWritesNothing()
    {
        holding(admin, BeamPermissions.ADMIN);

        assertTrue(run(admin, "remove", "nosuchplace"));

        yaml.verify(BeamYamlManager::saveAll, never());
        verify(admin).sendMessage(contains("No public beam destination named"));
    }

    /** An action nobody recognises shows what the command does take. */
    @Test
    void anActionNobodyRecognisesShowsTheUsage()
    {
        holding(admin, BeamPermissions.ADMIN);

        assertTrue(run(admin, "frobnicate", "hub"));

        verify(admin).sendMessage(contains("set|remove|cost"));
        assertTrue(BeamManager.getAllPublicDestinations().isEmpty(), "and changes nothing");
    }

    /** So does asking for admin with nothing after it, before any permission is consulted. */
    @Test
    void adminWithNoActionShowsTheUsageWithoutCheckingAnything()
    {
        assertTrue(run(console));

        verify(console).sendMessage(contains("goto"));
        verify(console, never()).hasPermission(anyString());
    }

    /** A named action with no name after it says what is missing rather than acting. */
    @Test
    void anActionWithNoNameAfterItShowsTheUsage()
    {
        holding(admin, BeamPermissions.ADMIN);

        assertTrue(run(admin, "set"));

        verify(admin).sendMessage(contains("set|remove|cost <name>"));
        assertTrue(BeamManager.getAllPublicDestinations().isEmpty(), "and sets nothing");
    }
}
