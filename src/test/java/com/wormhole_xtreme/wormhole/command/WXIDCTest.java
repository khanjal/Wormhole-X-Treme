package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Reading and setting a gate's iris deactivation code.
 *
 * <p>The IDC is what lets somebody dial through a closed iris, so who may change it matters:
 * the gate's owner, or an admin holding the config node. Nothing covered any of it.
 *
 * <p>Two refusals are worth holding. A sign-powered gate has no iris to unlock, and neither
 * does a gate without an iris lever, so both are told rather than silently accepting a code
 * that would never be asked for.
 */
class WXIDCTest
{
    private CommandSender console;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        console = mock(CommandSender.class);
        clearGates();
    }

    @AfterEach
    void tearDown()
    {
        clearGates();
    }

    private static void clearGates()
    {
        for (final Stargate s : new java.util.ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private Block block(final int x)
    {
        final Block b = mock(Block.class);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(64);
        when(b.getZ()).thenReturn(0);
        when(b.getWorld()).thenReturn(world);
        when(b.getLocation()).thenReturn(new Location(world, x, 64, 0));
        return b;
    }

    /** A gate with an iris lever, which is what the command needs to work on. */
    private Stargate gateWithIris(final String name)
    {
        final Stargate s = new Stargate();
        s.setGateName(name);
        s.setGateIrisLeverBlock(block(1));
        StargateManager.registerStargate(s);
        return s;
    }

    private boolean idc(final CommandSender who, final String... args)
    {
        return new WXIDC().onCommand(who, null, "idc", args);
    }

    /** With no gate named there is nothing to do, and the usage line is printed by returning false. */
    @Test
    void namingNoGateIsAUsageError()
    {
        assertFalse(idc(console), "returning false is what prints the usage line");
    }

    /** A gate nobody built is named back rather than silently ignored. */
    @Test
    void anUnknownGateIsNamedBack()
    {
        assertTrue(idc(console, "nowhere"));

        verify(console).sendMessage(contains("Invalid Stargate: nowhere"));
    }

    /** Asked with only a name, it reports the code and changes nothing. */
    @Test
    void askingReportsTheCodeWithoutChangingIt()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateIrisDeactivationCode("secret");

        assertTrue(idc(console, "alpha"));

        verify(console).sendMessage(contains("is:secret"));
        assertEquals("secret", s.getGateIrisDeactivationCode(), "asking must not change it");
    }

    /** Giving a value sets it. */
    @Test
    void givingAValueSetsTheCode()
    {
        final Stargate s = gateWithIris("alpha");

        assertTrue(idc(console, "alpha", "hunter2"));

        assertEquals("hunter2", s.getGateIrisDeactivationCode());
    }

    /** {@code -clear} empties it rather than setting the code to the literal word. */
    @Test
    void clearEmptiesTheCode()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateIrisDeactivationCode("secret");

        assertTrue(idc(console, "alpha", "-clear"));

        assertEquals("", s.getGateIrisDeactivationCode(),
            "-clear is an instruction, not a code to store");
    }

    /**
     * A gate with no iris lever is refused.
     *
     * <p>There is no iris to unlock, so a code set here would never be asked for.
     */
    @Test
    void aGateWithNoIrisLeverIsRefused()
    {
        final Stargate s = new Stargate();
        s.setGateName("noiris");
        StargateManager.registerStargate(s);

        assertTrue(idc(console, "noiris", "hunter2"));

        verify(console).sendMessage(contains("Iris not available"));
        assertEquals("", s.getGateIrisDeactivationCode(), "and nothing is stored");
    }

    /** A sign-powered gate is refused for the same reason. */
    @Test
    void aSignPoweredGateIsRefused()
    {
        final Stargate s = gateWithIris("signy");
        s.setGateSignPowered(true);

        assertTrue(idc(console, "signy", "hunter2"));

        verify(console).sendMessage(contains("Iris not available"));
        assertEquals("", s.getGateIrisDeactivationCode());
    }

    /** A player who neither owns the gate nor holds the config node may not change the code. */
    @Test
    void aStrangerMayNotChangeTheCode()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateOwner("00000000-0000-0000-0000-000000000001");
        s.setGateIrisDeactivationCode("secret");

        final Player stranger = mock(Player.class);
        when(stranger.getName()).thenReturn("stranger");
        when(stranger.isOp()).thenReturn(false);
        when(stranger.hasPermission(anyString())).thenReturn(false);
        when(stranger.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        assertTrue(idc(stranger, "alpha", "mine-now"));

        verify(stranger).sendMessage(contains("ermission"));
        assertEquals("secret", s.getGateIrisDeactivationCode(), "somebody else's code is untouched");
    }

    /** The gate's owner may change it without holding any node. */
    @Test
    void theOwnerMayChangeTheirOwnCode()
    {
        final Stargate s = gateWithIris("alpha");
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        s.setGateOwner(owner.toString());

        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("owner");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(owner);

        assertTrue(idc(player, "alpha", "mine"));

        assertEquals("mine", s.getGateIrisDeactivationCode());
    }
}
