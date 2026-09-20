package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * Every subcommand that takes an argument tries to complete it.
 *
 * <p>Five did not. Four were flat names that moved under {@code /wormhole gate} and kept a
 * completer written for the shorter command they used to be -- {@code build} offered nothing
 * where {@code gate build} offers every shape, and {@code regen} offered a gate name where
 * {@code gate regen} offers its flags too. The fifth, {@code wooshdepth}, offered nothing in a
 * slot that accepts exactly six values.
 *
 * <p>The guard below is the part meant to outlast this change: a subcommand registered with a
 * completer of {@code null} and an argument in its usage line fails it, so the next command
 * added has to say out loud that it means to offer nothing.
 */
class CommandCompletionCoverageTest
{
    /**
     * The subcommands that legitimately complete nothing, and why.
     *
     * <p>Two take a number with no bounds worth listing; one is retired and answers that it is;
     * one is deliberately unlisted, so completing it would hand it to whoever pressed tab.
     */
    private static final Set<String> NOTHING_TO_OFFER =
        Set.of("shutdown_timeout", "activate_timeout", "restrict", "freya");

    private java.util.Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        clearGates();
        savedShapes = new java.util.HashMap<>(StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().put("Standard", new StargateShape());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private static void gateNamed(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        StargateManager.registerStargate(gate);
    }

    private static List<String> complete(final String command, final String... args)
    {
        return SubCommands.find(command).completeArgs(null, args);
    }

    /**
     * A command whose usage names an argument has a completer, or is listed as having nothing
     * to offer.
     *
     * <p>Structural rather than behavioural on purpose: it asks whether anybody wrote one, not
     * what it returns, because what a completer returns depends on what is on the server.
     */
    @Test
    void everySubcommandThatTakesAnArgumentTriesToCompleteIt()
    {
        final List<String> missing = new ArrayList<>();
        int checked = 0;
        for (final SubCommands.Entry entry : SubCommands.all())
        {
            final String usage = entry.getUsage();
            if (!usage.contains("<") && !usage.contains("["))
            {
                continue;
            }
            checked++;
            if (!entry.completesArguments() && !NOTHING_TO_OFFER.contains(entry.getName()))
            {
                missing.add(entry.getName() + "  --  " + usage);
            }
        }

        assertTrue(checked >= 20, "only " + checked + " subcommands take arguments, which is too few to be right");
        assertEquals(List.of(), missing, "these take an argument and complete nothing");
    }

    /** The exemptions name commands that exist, so a rename cannot quietly widen the list. */
    @Test
    void everyExemptionNamesARealSubcommand()
    {
        for (final String name : NOTHING_TO_OFFER)
        {
            assertTrue(SubCommands.find(name) != null, name + " is exempted but not registered");
            assertFalse(SubCommands.find(name).completesArguments(),
                name + " completes its arguments now, so it should come off the exemption list");
        }
    }

    /**
     * {@code gate list} offers networks, which is what it takes, not gates.
     *
     * <p>Every verb {@code completeGate} did not name fell through to one line that offered a
     * gate name, because most of them do take one. {@code list} does not -- it narrows the
     * listing to a network -- so the completion named the wrong kind of thing entirely.
     */
    @Test
    void gateListOffersNetworksRatherThanGates()
    {
        gateNamed("Abydos");

        final List<String> offered = complete("gate", "gate", "list", "");
        assertTrue(offered.contains("Public"), "the network every gate is on unless told otherwise: " + offered);
        assertFalse(offered.contains("Abydos"), "and not a gate name, which list does not take: " + offered);
        assertEquals(complete("list", "list", ""), offered, "the same as the flat name it is short for");
    }

    /**
     * {@code gate complete} does not offer gates in the slot for a name that must be new.
     *
     * <p>The flat {@code complete} has said so in a comment since it was written -- suggesting
     * an existing gate's name there is suggesting the one name the command will refuse.
     */
    @Test
    void gateCompleteOffersNoGateForANameThatMustBeNew()
    {
        gateNamed("Abydos");

        assertEquals(List.of(), complete("gate", "gate", "complete", ""));
        assertEquals(List.of("idc=", "net="), complete("gate", "gate", "complete", "NewGate", ""),
            "and the two things it does take after the name");
        assertEquals(List.of(), complete("gate", "gate", "create", ""),
            "create is complete's other name and completes the same way");
    }

    /** {@code remove} offers the one flag it takes, under both names. */
    @Test
    void removeOffersDestroyUnderBothNames()
    {
        gateNamed("Abydos");

        assertEquals(List.of("Abydos"), complete("remove", "remove", ""));
        assertEquals(List.of("-destroy"), complete("remove", "remove", "Abydos", ""));
        assertEquals(List.of("-destroy"), complete("gate", "gate", "remove", "Abydos", ""),
            "and the same through the gate verb");
    }

    /**
     * A word {@code gate} does not dispatch completes nothing, even when it names another
     * subcommand.
     *
     * <p>{@code set} is the {@code config} alias. Delegating on the name alone would complete
     * {@code gate set } with every setting in config.yml, none of which {@code gate} would then
     * accept.
     */
    @Test
    void aWordGateDoesNotDispatchCompletesNothing()
    {
        assertEquals(List.of(), complete("gate", "gate", "set", ""));
        assertFalse(complete("gate", "gate", "list", "").isEmpty(),
            "though a verb it does dispatch still completes, or the assertion above proves nothing");
    }

    /** The flat {@code build} name offers shapes, the same as the {@code gate build} it is short for. */
    @Test
    void theFlatBuildNameOffersShapes()
    {
        assertEquals(List.of("Standard"), complete("build", "build", ""));
        assertEquals(complete("gate", "gate", "build", "Sta"), complete("build", "build", "Sta"),
            "and offers exactly what the verb it is short for offers");
    }

    /** {@code regen} offers {@code -all} and its flags, not just a gate name. */
    @Test
    void theFlatRegenNameOffersItsFlagsToo()
    {
        gateNamed("Abydos");

        assertTrue(complete("regen", "regen", "").contains("Abydos"));
        assertTrue(complete("regen", "regen", "-a").contains("-all"), "the sweep-every-gate flag");
        assertEquals(List.of("Standard"), complete("regen", "regen", "Abydos", "-shape", ""),
            "and a shape after -shape, which it could not reach before");
    }

    /** {@code owner} offers who to hand the gate to, once a gate has been named. */
    @Test
    void ownerOffersTheOnlinePlayers()
    {
        gateNamed("Abydos");
        final Player bob = mock(Player.class);
        when(bob.getName()).thenReturn("Bob");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(bob));

            assertEquals(List.of("Abydos"), complete("owner", "owner", ""));
            assertEquals(List.of("Bob"), complete("owner", "owner", "Abydos", ""),
                "the second slot is a player, not a second gate");
        }
    }

    /**
     * {@code idc} offers {@code -clear} and nothing else in the value slot.
     *
     * <p>The code itself is the player's to invent, so there is nothing to suggest -- but
     * {@code -clear} is the plugin's own word and was not offered anywhere.
     */
    @Test
    void idcOffersClearAndNotACodeToGuessAt()
    {
        gateNamed("Abydos");

        assertEquals(List.of("Abydos"), complete("idc", "idc", ""));
        assertEquals(List.of("-clear"), complete("idc", "idc", "Abydos", ""));
        assertEquals(List.of(), complete("idc", "idc", "Abydos", "7"), "a typed code is left alone");
    }

    /**
     * {@code wooshdepth} offers every depth the command accepts, and only those.
     *
     * <p>Asserted against the range the refusal itself reads, so a completion that suggests a
     * depth the command would then reject fails here rather than in chat.
     */
    @Test
    void wooshdepthOffersOnlyTheDepthsItAccepts()
    {
        gateNamed("Abydos");

        assertEquals(List.of("Abydos"), complete("wooshdepth", "wooshdepth", ""));
        assertEquals(List.of("0", "1", "2", "3", "4", "5"), complete("wooshdepth", "wooshdepth", "Abydos", ""));
        assertEquals(List.of(), complete("wooshdepth", "wooshdepth", "Abydos", "6"),
            "six is out of range, so offering it would be offering a refusal");
    }
}
