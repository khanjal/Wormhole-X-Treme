package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.command.WXRemove;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.integration.RegionFlags;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable;
import com.wormhole_xtreme.wormhole.logic.StargateUpdateRunnable.ActionToTake;
import com.wormhole_xtreme.wormhole.model.GateSpacing;
import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.StargateRestrictions;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * The gate entry and transit lines read by key from {@code lang/} (#26).
 *
 * <p>Two things can go wrong moving a line into en.yml: the English drifts from what the code built,
 * which a player sees as a reworded message nobody asked for, and the call site keeps reading the
 * server's language, which a player sees as English after translating it. The first test holds
 * each template to the old concatenation, copied from the code before the move; the rest send a
 * line from each converted file through its real code to a player whose client reads the test
 * language qq.
 */
class GateTransitMessagesTest
{
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        GateSpatialIndex.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        player = mock(Player.class);
        when(player.getName()).thenReturn("traveller");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocale()).thenReturn("qq");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        GateSpatialIndex.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    /**
     * Each template reads exactly as the code built the line before it moved: colours, spacing and full
     * stops included. The right-hand sides are the old expressions, so extraction never rewords.
     */
    @Test
    void theConvertedLinesReadAsTheyDidBefore()
    {
        final String name = "Abydos";
        assertEquals("Valid stargate design.", Messages.get(null, "gate.design.valid"));
        assertEquals("Valid sign nav stargate design.", Messages.get(null, "gate.design.valid-sign"));
        assertEquals("Type " + ChatText.command("/wormhole complete") + " " + ChatText.name(name)
            + " [idc=IDC] [net=NET] to finish it.", Messages.get(null, "gate.design.complete-hint",
                ChatText.command("/wormhole complete"), ChatText.name(name), "[idc=IDC] [net=NET]"));
        assertEquals("Valid design found nearby. Type " + ChatText.command("/wormhole complete")
            + " " + ChatText.name("<name>") + " to finish it.", Messages.get(null, "gate.design.nearby",
                ChatText.command("/wormhole complete"), ChatText.name("<name>")));
        assertEquals("No gate detected at the clicked block. Click the DHD's button or lever again.",
            Messages.get(null, "gate.design.none-clicked"));
        assertEquals("Completing the gate failed. Check server logs.", Messages.get(null, "gate.design.complete-failed"));
        assertEquals("Completing the gate failed after it was detected. Check the server log.",
            Messages.get(null, "gate.design.complete-failed-detected"));

        assertEquals("Gate deactivated.", Messages.get(null, "gate.activate.deactivated"));
        assertEquals("Gate deactivated; " + ChatText.name(name) + " had activated it.",
            Messages.get(null, "gate.activate.deactivated-by", ChatText.name(name)));
        assertEquals("Your pending gate activation was cleared by " + ChatText.name(name) + ".",
            Messages.get(null, "gate.activate.cleared-by", ChatText.name(name)));
        assertEquals("Stargates connected!", Messages.get(null, "gate.activate.connected"));
        assertEquals("Chevrons locked.", Messages.get(null, "gate.activate.chevrons-locked"));
        assertEquals("Type " + ChatText.usageBody("/dial <gate> [idc]") + " to dial.",
            Messages.get(null, "gate.activate.type-to-dial", ChatText.usageBody("/dial <gate> [idc]")));

        assertEquals("Invalid gate target. " + ChatText.name(name) + " is not on this gate's network.",
            Messages.get(null, "gate.dial.other-network", ChatText.name(name)));
        assertEquals("Remote Iris is active. Dial again with its " + ChatText.command("IDC") + " after the gate's name.",
            Messages.get(null, "gate.dial.remote-iris", ChatText.command("IDC")));
        assertEquals("IDC accepted. Iris has been deactivated.", Messages.get(null, "gate.dial.idc-accepted"));

        assertEquals("This gate's iris is closed.", Messages.get(null, "gate.walk.iris-closed"));
        assertEquals("What you are riding could not be sent through.", Messages.get(null, "gate.walk.ride-failed"));
        assertEquals("Arrived at " + ChatText.name(name) + ".", Messages.get(null, "gate.welcome.arrived", ChatText.name(name)));
        final String fare = ChatText.value(2.5 + " " + "coins");
        assertEquals("Charged " + fare + ".", Messages.get(null, "gate.fare.charged", fare));
        assertEquals("Gate " + ChatText.name(name) + " timed out and deactivated.",
            Messages.get(null, "gate.lifecycle.timed-out", ChatText.name(name)));
        assertEquals("Dialer set to " + ChatText.name(name) + ".", Messages.get(null, "gate.sign.dialer-set", ChatText.name(name)));
        assertEquals("No other gates to dial.", Messages.get(null, "gate.sign.no-other-gates"));
    }

    /** The lines with more than one part, and the region and block lines, against their old expressions. */
    @Test
    void theRefusalsReadAsTheyDidBefore()
    {
        final String name = "Abydos";
        final long remaining = 28;
        assertEquals("You must wait " + ChatText.value(remaining + " seconds") + " before using a stargate again.",
            Messages.get(null, "gate.restrict.cooldown", ChatText.value(Messages.get(null, "gate.restrict.seconds", remaining))));
        assertEquals("You must wait " + ChatText.value("1 second") + " before using a stargate again.",
            Messages.get(null, "gate.restrict.cooldown", ChatText.value(Messages.get(null, "gate.restrict.one-second"))));

        final String distance = ChatText.value(String.format(Locale.ROOT, "%.1f", 10.0));
        assertEquals("Too close to " + ChatText.name(name) + ": " + distance + " blocks away, and gates must be at least "
            + ChatText.value(String.valueOf(20)) + " apart.",
            Messages.get(null, "gate.spacing.too-close", ChatText.name(name), distance, ChatText.value("20")));

        final String buildRefusal = "This region does not allow building gates.";
        assertEquals("This region does not allow using gates.", Messages.get(null, "region.use-refused"));
        assertEquals(buildRefusal, Messages.get(null, "region.build-refused"));
        assertEquals(buildRefusal + " Nothing placed.", Messages.get(null, "region.build-refused-nothing-placed"));
        assertEquals(RegionFlags.USE_REFUSED, RegionFlags.useRefused(null), "the English constant the tests compare against");
        assertEquals(RegionFlags.BUILD_REFUSED, RegionFlags.buildRefused(null));
        assertEquals(RegionFlags.BUILD_REFUSED_NOTHING_PLACED, RegionFlags.buildRefusedNothingPlaced(null));

        assertEquals("This block is part of the gate " + ChatText.name(name) + ".",
            Messages.get(null, "block.part-of-gate", ChatText.name(name)));
        assertEquals("Remove the gate first with " + ChatText.command("/wormhole remove") + " " + ChatText.name(name)
            + "; add " + ChatText.command(WXRemove.DESTROY) + " to break its blocks too.",
            Messages.get(null, "block.remove-gate-first", ChatText.command("/wormhole remove"), ChatText.name(name),
                ChatText.command(WXRemove.DESTROY)));
        assertEquals("That is part of a mirror. " + ChatText.command("/wormhole mirror remove") + " takes one down.",
            Messages.get(null, "block.part-of-mirror", ChatText.command("/wormhole mirror remove")));
        assertEquals("You cannot build inside the gate " + ChatText.name(name) + ".",
            Messages.get(null, "block.build-inside-gate", ChatText.name(name)));
        assertEquals(ChatText.good("Standard is built!") + " Press its button to check it.",
            Messages.get(null, "preview.built", "Standard"));
    }

    /** A new design is announced in the builder's language. */
    @Test
    void aValidDesignIsAnnouncedInThePlayersLanguage()
    {
        GateInteractionHandler.announceValidDesign(player, new Stargate());

        verify(player).sendMessage(ChatText.info("QQ valid."));
    }

    /** The welcome at the far end, in the traveller's language. */
    @Test
    void theWelcomeIsInThePlayersLanguage()
    {
        ConfigTestSupport.set(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, true);
        final Stargate abydos = new Stargate();
        abydos.setGateName("Abydos");

        GateWelcome.greet(player, abydos);

        verify(player).sendMessage(ChatText.info("QQ arrived " + ChatText.name("Abydos") + "."));
    }

    /** The toll taken after a trip, in the traveller's language. */
    @Test
    void theTollIsInThePlayersLanguage()
    {
        try (MockedStatic<EconomySupport> economy = mockStatic(EconomySupport.class))
        {
            economy.when(() -> EconomySupport.currencyName(anyDouble())).thenReturn("coins");

            GateFare.charge(player, 2.5);
        }

        verify(player).sendMessage(ChatText.info("QQ charged " + ChatText.value("2.5 coins") + "."));
    }

    /** The cooldown refusal, in the traveller's language: a player with no cooldown left reads one second. */
    @Test
    void theCooldownRefusalIsInThePlayersLanguage()
    {
        assertEquals(ChatText.error("QQ wait " + ChatText.value("1 second") + "."), StargateRestrictions.cooldownRefusal(player));
    }

    /** A gate built too near another is refused in the builder's language. */
    @Test
    void theSpacingRefusalIsInThePlayersLanguage()
    {
        ConfigTestSupport.set(ConfigKeys.MIN_GATE_DISTANCE, 50);
        final World world = mock(World.class);
        final Stargate near = new Stargate();
        near.setGateName("Near");
        near.setGateWorld(world);
        near.getGatePortalBlocks().add(new Location(world, 0, 64, 0));
        StargateManager.registerStargate(near);
        try
        {
            assertEquals("QQ near " + ChatText.name("Near") + " " + ChatText.value("5.0") + " " + ChatText.value("50") + ".",
                GateSpacing.refusal(player, world, List.of(new Location(world, 5, 64, 0))));
        }
        finally
        {
            StargateManager.removeStargate(near);
        }
    }

    /** A region's refusal, in the player's language. */
    @Test
    void theRegionRefusalIsInThePlayersLanguage()
    {
        assertEquals(ChatText.error("QQ region."), RegionFlags.useRefused(player));
    }

    /** An activation that times out says so in the activator's language. */
    @Test
    void aTimeoutIsInThePlayersLanguage()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("Abydos");
        gate.setGateLightsActive(true);

        gate.timeoutStargate(player);

        verify(player).sendMessage(ChatText.info("QQ " + ChatText.name("Abydos") + " timed out."));
    }

    /** A dial sign's click answers in the clicker's language. */
    @Test
    void aDialSignClickIsInThePlayersLanguage()
    {
        final Stargate gate = mock(Stargate.class);
        final Stargate target = mock(Stargate.class);
        when(target.getGateName()).thenReturn("Abydos");
        when(gate.getGateDialSignTarget()).thenReturn(target);

        new StargateUpdateRunnable(gate, player, ActionToTake.DIAL_SIGN_CLICK).run();

        verify(player).sendMessage(ChatText.info("QQ dialer " + ChatText.name("Abydos") + "."));
    }

    /** Breaking a gate's block is refused in the player's language. */
    @Test
    void aRefusedBreakIsInThePlayersLanguage()
    {
        final World world = mock(World.class);
        final Stargate gate = new Stargate();
        gate.setGateWorld(world);
        gate.setGateName("gateA");
        gate.getGateStructureBlocks().add(new Location(world, 10, 64, 20));
        StargateManager.registerStargate(gate);
        try
        {
            final Block block = mock(Block.class);
            when(block.getLocation()).thenReturn(new Location(world, 10, 64, 20));
            when(block.getWorld()).thenReturn(world);
            when(block.getX()).thenReturn(Integer.valueOf(10));
            when(block.getY()).thenReturn(Integer.valueOf(64));
            when(block.getZ()).thenReturn(Integer.valueOf(20));
            when(block.getType()).thenReturn(Material.AIR);

            new WormholeXTremeBlockListener().onBlockBreak(new BlockBreakEvent(block, player));

            verify(player).sendMessage(ChatText.error("QQ part of " + ChatText.name("gateA") + "."));
        }
        finally
        {
            StargateManager.removeStargate(gate);
        }
    }
}
