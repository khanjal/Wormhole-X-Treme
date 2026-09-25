package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.PluginTestSupport;

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
    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));

        world = mock(World.class);
        when(world.getName()).thenReturn("w");
        console = mock(CommandSender.class);
        clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown()
    {
        db.close();
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
        // Setting a code hangs a lever here; without its data that throws, and the command swallows it.
        final Switch lever = mock(Switch.class);
        when(b.getBlockData()).thenReturn(lever);
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
        db.verify(() -> StargateDBManager.saveStargate(s), never());
    }

    /** Giving a value sets it. */
    @Test
    void givingAValueSetsTheCode()
    {
        final Stargate s = gateWithIris("alpha");

        assertTrue(idc(console, "alpha", "hunter2"));

        assertEquals("hunter2", s.getGateIrisDeactivationCode());
        verify(console).sendMessage(contains("is:hunter2"));
    }

    /**
     * A new code is saved at once, like every other gate edit.
     *
     * <p>It used to wait for the next save, at plugin disable, so a crash before then brought
     * back the old code and the shut iris that goes with it.
     */
    @Test
    void aNewCodeIsSavedAtOnce()
    {
        final Stargate s = gateWithIris("alpha");

        assertTrue(idc(console, "alpha", "hunter2"));

        db.verify(() -> StargateDBManager.saveStargate(s));
    }

    /**
     * {@code gate edit <gate> idc} with no code reports it, as every other field does.
     *
     * <p>It used to clear it, which predates {@code -clear}; with the save above, that asking
     * would have wiped the code on disk.
     */
    @Test
    void gateEditWithNoCodeReportsItWithoutClearing()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateIrisDeactivationCode("secret");

        assertTrue(new GateEditCommand().execute(console, new String[] { "gate", "edit", "alpha", "idc" }));

        verify(console).sendMessage(contains("is:secret"));
        assertEquals("secret", s.getGateIrisDeactivationCode(), "asking must not clear it");
        db.verify(() -> StargateDBManager.saveStargate(s), never());
    }

    /** And with a code, {@code gate edit} sets it and saves it. */
    @Test
    void gateEditWithACodeSetsAndSavesIt()
    {
        final Stargate s = gateWithIris("alpha");

        assertTrue(new GateEditCommand().execute(console, new String[] { "gate", "edit", "alpha", "idc", "hunter2" }));

        assertEquals("hunter2", s.getGateIrisDeactivationCode());
        db.verify(() -> StargateDBManager.saveStargate(s));
    }

    /** Clearing is saved at once too, or a crash would bring the code back. */
    @Test
    void aClearedCodeIsSavedAtOnce()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateIrisDeactivationCode("secret");

        assertTrue(idc(console, "alpha", "-clear"));

        db.verify(() -> StargateDBManager.saveStargate(s));
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

    /** Whatever its capitals, like every other dashed keyword. */
    @Test
    void clearIsRecognisedWhateverItsCapitals()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateIrisDeactivationCode("secret");

        assertTrue(idc(console, "alpha", "-CLEAR"));

        assertEquals("", s.getGateIrisDeactivationCode(), "not stored as a code called -CLEAR");
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
        db.verify(() -> StargateDBManager.saveStargate(s), never());
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

    /** A player holding no node at all, who owns gates under this UUID. */
    private static Player playerWithoutNodes(final UUID id)
    {
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("owner");
        when(player.isOp()).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private static void wormhole(final CommandSender who, final String... args)
    {
        new Wormhole().onCommand(who, null, "wormhole", args);
    }

    /**
     * The owner reaches their own code through {@code gate edit}, the route the guide gives.
     *
     * <p>The test above calls the handler directly, and the handler has always admitted the
     * owner. Nothing typed ever got that far: the dispatcher and {@code gate edit} both refused
     * anyone without {@code wormhole.config} first, so an owner had no way to set their code.
     */
    @Test
    void theOwnerMaySetTheirCodeThroughGateEdit()
    {
        final Stargate s = gateWithIris("alpha");
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        s.setGateOwner(owner.toString());

        final Player player = playerWithoutNodes(owner);

        wormhole(player, "gate", "edit", "alpha", "idc", "mine");

        assertEquals("mine", s.getGateIrisDeactivationCode(),
            "the owner holds no node, so only the handler's own owner check can have let them in");
        verify(player).sendMessage(contains("is:mine"));
    }

    /** And through the older {@code /wormhole idc}, which is hidden but still answers. */
    @Test
    void theOwnerMaySetTheirCodeThroughTheStandaloneCommand()
    {
        final Stargate s = gateWithIris("alpha");
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        s.setGateOwner(owner.toString());

        final Player player = playerWithoutNodes(owner);

        wormhole(player, "idc", "alpha", "mine");

        assertEquals("mine", s.getGateIrisDeactivationCode());
        verify(player).sendMessage(contains("is:mine"));
    }

    /**
     * A stranger is refused before being told anything about the gate's iris.
     *
     * <p>Now that anybody can reach the handler, whether a gate has an iris lever is the
     * owner's business, not the reply to whoever typed its name.
     */
    @Test
    void aStrangerIsRefusedBeforeLearningWhetherTheGateHasAnIris()
    {
        final Stargate s = new Stargate();
        s.setGateName("noiris");
        s.setGateOwner("00000000-0000-0000-0000-000000000001");
        StargateManager.registerStargate(s);
        final Player stranger = playerWithoutNodes(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        wormhole(stranger, "gate", "edit", "noiris", "idc", "x");

        verify(stranger).sendMessage(contains("You lack the permissions"));
        verify(stranger, never()).sendMessage(contains("Iris not available"));
    }

    /** Opening the door to owners must not open it to everybody. */
    @Test
    void aStrangerIsStillRefusedThroughGateEdit()
    {
        final Stargate s = gateWithIris("alpha");
        s.setGateOwner("00000000-0000-0000-0000-000000000001");
        s.setGateIrisDeactivationCode("secret");
        final Player stranger = playerWithoutNodes(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        wormhole(stranger, "gate", "edit", "alpha", "idc", "mine-now");

        verify(stranger).sendMessage(contains("You lack the permissions"));
        assertEquals("secret", s.getGateIrisDeactivationCode(), "somebody else's code is untouched");
    }

    /**
     * Only the code is opened to owners; every other field still wants the config node.
     *
     * <p>{@code group} is the one to try because it has no handler of its own to refuse. Typed,
     * it meets two refusals, the dispatcher's and then gate edit's own; this holds the pair, and
     * the test below holds gate edit's alone.
     */
    @Test
    void theOwnerIsStillRefusedTheOtherFieldsOfTheirGate()
    {
        final Stargate s = gateWithIris("alpha");
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        s.setGateOwner(owner.toString());
        final Player player = playerWithoutNodes(owner);

        wormhole(player, "gate", "edit", "alpha", "group", "anything");

        verify(player).sendMessage(contains("You lack the permissions"));
    }

    /** gate edit's own front door, which the dispatcher otherwise stands in front of. */
    @Test
    void gateEditItselfStillRefusesTheOwnerAnyOtherField()
    {
        final Stargate s = gateWithIris("alpha");
        final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        s.setGateOwner(owner.toString());
        final Player player = playerWithoutNodes(owner);

        new GateEditCommand().execute(player, new String[] { "gate", "edit", "alpha", "group", "anything" });

        verify(player).sendMessage(contains("You lack the permissions"));
        verify(player, never()).sendMessage(contains("No material group"));
    }
}
