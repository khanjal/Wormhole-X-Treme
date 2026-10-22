package com.wormhole_xtreme.wormhole.command.handlers;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What {@code /wormhole gate validate} says, read by key from {@code lang/} (#26).
 *
 * <p>Each line is sent through the real command twice. Once to an English player, against the
 * concatenation the code built before the move, copied from it, so a reworded template, a lost
 * plural or a swapped argument shows as a changed message. Once to a player whose client reads the
 * test language qq, so a call site that reads the server's language instead of the player's shows as
 * English, and each singular and plural variant is told apart by its own qq wording.
 */
class ValidateCommandMessagesTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(Boolean.TRUE);
        clearGates();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
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

    /** An admin whose client reads {@code locale}. */
    private static Player adminReading(final String locale)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Admin");
        when(player.getUniqueId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        when(player.isOp()).thenReturn(Boolean.TRUE);
        when(player.hasPermission(anyString())).thenReturn(Boolean.TRUE);
        return player;
    }

    /** A registered gate with nothing missing, and no dial sign to lose. */
    private static Stargate registeredGate(final String name)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn(name);
        when(gate.getGateStructureBlocks()).thenReturn(new ArrayList<>());
        StargateManager.registerStargate(gate);
        return gate;
    }

    /** A registered gate with {@code frame} frame blocks gone. */
    private Stargate gateMissingFrame(final String name, final int frame)
    {
        final Stargate gate = registeredGate(name);
        final List<Location> locations = new ArrayList<>();
        for (int i = 0; i < frame; i++)
        {
            locations.add(airAt(i, 64, 0));
        }
        when(gate.getGateStructureBlocks()).thenReturn(locations);
        return gate;
    }

    private Location airAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getZ()).thenReturn(z);
        final Location location = new Location(world, x, y, z);
        when(world.getBlockAt(x, y, z)).thenReturn(block);
        when(world.getBlockAt(location)).thenReturn(block);
        return location;
    }

    /** A block where a dial sign was, now something that is not a sign. */
    private Block replacedSign()
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(0);
        when(block.getZ()).thenReturn(0);
        final BlockState state = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        return block;
    }

    /** A gate whose dial sign was replaced by something that is not a sign. */
    private Stargate gateMissingSign(final String name)
    {
        final Stargate gate = registeredGate(name);
        final Block block = replacedSign();
        when(gate.getGateDialSignBlock()).thenReturn(block);
        return gate;
    }

    private static void validate(final Player player, final String... rest)
    {
        final String[] args = new String[rest.length + 1];
        args[0] = "validate";
        System.arraycopy(rest, 0, args, 1, rest.length);
        new ValidateCommand().execute(player, args);
    }

    /** A gate nobody built is named back in the reader's own language, English or qq. */
    @Test
    void anUnknownGateIsNamedBackInThePlayersLanguage()
    {
        final Player english = adminReading("en");
        final Player qq = adminReading("qq");

        validate(english, "nowhere");
        validate(qq, "nowhere");

        verify(english).sendMessage(ChatText.error("No gate called " + ChatText.name("nowhere") + ". "
            + ChatText.command("/wormhole list") + " shows them."));
        verify(qq).sendMessage(ChatText.error("QQ no gate " + ChatText.name("nowhere") + " "
            + ChatText.command("/wormhole list") + "."));
    }

    /** A clean gate, and each way a gate can be missing something, against the old expression. */
    @Test
    void aSingleGateReadsAsItDidBefore()
    {
        registeredGate("alpha");
        gateMissingFrame("beta", 2);
        gateMissingFrame("gamma", 1);
        gateMissingSign("delta");
        final Stargate both = gateMissingFrame("epsilon", 1);
        final Block sign = replacedSign();
        when(both.getGateDialSignBlock()).thenReturn(sign);
        final Player player = adminReading("en");

        validate(player, "alpha");
        validate(player, "beta");
        validate(player, "gamma");
        validate(player, "delta");
        validate(player, "epsilon");

        verify(player).sendMessage(ChatText.info(ChatText.name("alpha") + " is standing where it says it is."));
        verify(player).sendMessage(ChatText.error(ChatText.name("beta") + ": "
            + ChatText.bad("2 frame blocks missing") + "."));
        verify(player).sendMessage(ChatText.error(ChatText.name("gamma") + ": "
            + ChatText.bad("1 frame block missing") + "."));
        verify(player).sendMessage(ChatText.error(ChatText.name("delta") + ": "
            + ChatText.bad("dial sign missing") + "."));
        verify(player).sendMessage(ChatText.error(ChatText.name("epsilon") + ": "
            + ChatText.bad("1 frame block missing; dial sign missing") + "."));
    }

    /** The same lines reach a qq player in qq, singular told apart from plural and the joiner from the words. */
    @Test
    void aSingleGateIsInThePlayersLanguage()
    {
        registeredGate("alpha");
        gateMissingFrame("beta", 2);
        gateMissingFrame("gamma", 1);
        gateMissingSign("delta");
        final Stargate both = gateMissingFrame("epsilon", 1);
        final Block sign = replacedSign();
        when(both.getGateDialSignBlock()).thenReturn(sign);
        final Player player = adminReading("qq");

        validate(player, "alpha");
        validate(player, "beta");
        validate(player, "gamma");
        validate(player, "delta");
        validate(player, "epsilon");

        verify(player).sendMessage(ChatText.info("QQ standing " + ChatText.name("alpha")));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("QQ frame-many 2") + " @ " + ChatText.name("beta")));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("QQ frame-one 1") + " @ " + ChatText.name("gamma")));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("QQ sign gone") + " @ " + ChatText.name("delta")));
        verify(player).sendMessage(ChatText.error("QQ " + ChatText.bad("QQ frame-one 1 & QQ sign gone") + " @ "
            + ChatText.name("epsilon")));
    }

    /** Two broken gates out of three: the plural gates and the plural verb, against the old expression. */
    @Test
    void theSweepSaysAPluralCountAsItDidBefore()
    {
        registeredGate("clean");
        gateMissingFrame("broken", 1);
        gateMissingSign("signless");
        final Player player = adminReading("en");

        validate(player, "-all");

        verify(player).sendMessage(ChatText.info("Checked " + ChatText.value("3") + " gates. " + ChatText.value("2")
            + " have something missing."));
    }

    /** One broken gate out of one: the singular gate and the singular verb, against the old expression. */
    @Test
    void theSweepSaysASingularCountAsItDidBefore()
    {
        gateMissingFrame("broken", 1);
        final Player player = adminReading("en");

        validate(player, "-all");

        verify(player).sendMessage(ChatText.info("Checked " + ChatText.value("1") + " gate. " + ChatText.value("1")
            + " has something missing."));
    }

    /** Nothing broken is zero, which reads as a plural: "0 have", as it did. */
    @Test
    void aSweepFindingNothingSaysZeroHave()
    {
        registeredGate("clean");
        registeredGate("also clean");
        final Player player = adminReading("en");

        validate(player, "-all");

        verify(player).sendMessage(ChatText.info("Checked " + ChatText.value("2") + " gates. " + ChatText.value("0")
            + " have something missing."));
    }

    /** The sweep summary reaches a qq player in qq, each of the two counts choosing its own variant. */
    @Test
    void theSweepIsInThePlayersLanguage()
    {
        registeredGate("clean");
        gateMissingFrame("broken", 1);
        final Player many = adminReading("qq");
        validate(many, "-all");
        verify(many).sendMessage(ChatText.info("QQ checked " + ChatText.value("2") + " QQgates QQ missing-one "
            + ChatText.value("1")));

        clearGates();
        gateMissingFrame("alone", 1);
        final Player one = adminReading("qq");
        validate(one, "-all");
        verify(one).sendMessage(ChatText.info("QQ checked " + ChatText.value("1") + " QQgate QQ missing-one "
            + ChatText.value("1")));

        clearGates();
        registeredGate("clean");
        registeredGate("also clean");
        final Player none = adminReading("qq");
        validate(none, "-all");
        verify(none).sendMessage(ChatText.info("QQ checked " + ChatText.value("2") + " QQgates QQ missing-many "
            + ChatText.value("0")));
    }
}
