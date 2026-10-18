package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingBlockage;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingTemplate;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What ring create, cancel, list and remove, and ring build and fire, say, read by key from {@code lang/} (#26).
 *
 * <p>Moving a line into en.yml can go wrong two ways: the English drifts from what the code built, which
 * a player sees as a reworded message nobody asked for, and the call site keeps reading the server's
 * language, which a player sees as English after translating it. The first tests hold each template to
 * the concatenation the code built before the move, copied from it, each variant and plural; the rest
 * send lines through the real commands to a player whose client reads the test language qq, and to the
 * console with qq as the server's language.
 */
class RingCommandMessagesTest
{
    private static final String WORLD = "world";

    @TempDir
    File dataFolder;

    private World world;
    private final Map<String, Block> blocks = new HashMap<>();
    private MockedStatic<ConfigManager> config;
    private MockedStatic<RingYamlManager> yaml;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception
    {
        RingManager.clear();
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn(WORLD);
        when(world.getMinHeight()).thenReturn(Integer.valueOf(-64));
        when(world.getMaxHeight()).thenReturn(Integer.valueOf(320));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> blockAt(
            inv.getArgument(0, Integer.class).intValue(),
            inv.getArgument(1, Integer.class).intValue(),
            inv.getArgument(2, Integer.class).intValue()));

        config = mockStatic(ConfigManager.class);
        config.when(ConfigManager::getRingReach).thenReturn(Integer.valueOf(5));
        config.when(ConfigManager::getRingDefaultLight).thenReturn(Material.GLOWSTONE);
        config.when(ConfigManager::getRingMinSeparation).thenReturn(Integer.valueOf(0));
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(10));
        config.when(ConfigManager::getServerLanguage).thenReturn(Messages.DEFAULT_LANGUAGE);
        yaml = mockStatic(RingYamlManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        bukkit.close();
        yaml.close();
        config.close();
        RingManager.clear();
        PluginTestSupport.remove();
    }

    /** Air everywhere: no circle of slabs anywhere in this world. */
    private Block blockAt(final int x, final int y, final int z)
    {
        return blocks.computeIfAbsent(x + "," + y + "," + z, key -> {
            final Block b = mock(Block.class);
            when(b.getType()).thenReturn(Material.AIR);
            when(b.isPassable()).thenReturn(Boolean.TRUE);
            when(b.getLocation()).thenReturn(new Location(world, x, y, z));
            when(b.getWorld()).thenReturn(world);
            return b;
        });
    }

    /** A player whose client reads {@code locale}, standing at 100 64 100, with only the given nodes. */
    private Player playerReading(final String locale, final String... nodes)
    {
        final Player player = mock(Player.class);
        when(player.getLocale()).thenReturn(locale);
        when(player.getName()).thenReturn("Justin");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));
        when(player.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        for (final String node : nodes)
        {
            when(player.hasPermission(node)).thenReturn(Boolean.TRUE);
        }
        return player;
    }

    private static void run(final CommandSender sender, final String... args)
    {
        new RingCommand().execute(sender, args);
    }

    /** Every reason a circle is refused, in the server's language, against the old expression. */
    @Test
    void theRefusalReasonsReadAsTheyDidBefore()
    {
        assertEquals("That ring is built from more than one kind of slab. Use one kind; the rings are made of it.",
            RingCommand.explain(null, RingTemplate.Failure.MIXED_MATERIALS));
        assertEquals("Some of those slabs rest on the floor and others hang from the ceiling. "
            + "Use all bottom or all top slabs.", RingCommand.explain(null, RingTemplate.Failure.MIXED_HALVES));
        assertEquals("That circle is filled in. Lay only the ring and leave the middle clear to stand in.",
            RingCommand.explain(null, RingTemplate.Failure.INTERIOR_NOT_CLEAR));
        assertEquals("No ring of slabs here. Lay a circle of slabs and stand inside it.",
            RingCommand.explain(null, RingTemplate.Failure.NO_RING_FOUND));

        for (final int maxDrop : new int[] { 1, 10 })
        {
            config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(maxDrop));
            assertEquals("That ring is more than " + ChatText.value(String.valueOf(maxDrop)) + " blocks above its floor. "
                + "Lower the ring, raise the floor, or raise "
                + ChatText.usageBody("/wormhole config ring-max-ceiling-drop <blocks>") + ".",
                RingCommand.explain(null, RingBlockage.CEILING_TOO_HIGH), "a drop of " + maxDrop);
        }
        assertEquals("That ring has no room between it and the floor. Leave at least "
            + ChatText.value(String.valueOf(Ring.MIN_CEILING_DROP)) + " blocks below it for the rings to fall.",
            RingCommand.explain(null, RingBlockage.CEILING_TOO_LOW));
        assertEquals("There is not enough clear air for the rings. They need "
            + ChatText.value(String.valueOf(Ring.STACK_HEIGHT)) + " clear blocks above the pad.",
            RingCommand.explain(null, RingBlockage.NO_HEADROOM));
        assertEquals("That ring has a hole in its floor. Fill it so there is something to stand on.",
            RingCommand.explain(null, RingBlockage.NO_GROUND));
        assertEquals("There is something built inside that ring. Clear the inside; what is built around it "
            + "does not matter.", RingCommand.explain(null, RingBlockage.OBSTRUCTED));
        assertEquals(RingCommand.explain(null, RingBlockage.OBSTRUCTED), RingCommand.explain(RingBlockage.OBSTRUCTED),
            "the one-argument form the build checks read is the server's language");

        assertEquals("There is another ring close by. Move this one further away and try again.",
            RingCommand.explain(null, RingManager.Refusal.TOO_CLOSE));
        assertEquals("That overlaps another ring. Two rings cannot share ground, whatever "
            + "height they are at.", RingCommand.explain(null, RingManager.Refusal.OVERLAPS_RING));
        assertEquals("That ring cannot go there.", RingCommand.explain(null, RingManager.Refusal.OVERLAPS_GATE));
        assertEquals(RingCommand.OVERLAPS_GATE, RingCommand.overlapsGate(null), "the English constant the tests compare against");
    }

    /** The lines create, cancel, list and remove send, each variant and plural, against the old expression. */
    @Test
    void theCreateListAndRemoveLinesReadAsTheyDidBefore()
    {
        final String cancel = ChatText.command("/wormhole ring cancel");
        assertEquals("Rings are built and edited in the world, so this is a player command.",
            Messages.get(null, "ring.cmd.player-only"));
        assertEquals("That is not your ring pair.", Messages.get(null, "ring.cmd.not-your-pair"));
        assertEquals("Stand in a ring, or name a pair by its id.", Messages.get(null, "ring.cmd.stand-in-one"));
        assertEquals("There is no ring pair called " + ChatText.name("x") + ".",
            Messages.get(null, "ring.cmd.no-pair", ChatText.name("x")));
        assertEquals("You may not build transport rings.", Messages.get(null, "ring.cmd.create.no-permission"));
        assertEquals("That circle overlaps a stargate. Rings and gates cannot share blocks.",
            Messages.get(null, "ring.cmd.create.overlaps-gate"));
        for (final int quota : new int[] { 1, 2 })
        {
            final String limit = ChatText.value(String.valueOf(quota));
            final String old = "You already have " + limit + " ring pairs, which is the limit.";
            assertEquals(old, Messages.get(null, "ring.cmd.create.quota.one", limit));
            assertEquals(old, Messages.get(null, "ring.cmd.create.quota.other", limit));
        }
        assertEquals("First ring noted, in " + ChatText.material("STONE_SLAB") + ". Lay the other end and run "
            + ChatText.command("/wormhole ring create") + " in it.", Messages.get(null, "ring.cmd.create.first-noted",
                ChatText.material("STONE_SLAB"), ChatText.command("/wormhole ring create")));
        assertEquals("Its slabs stay put until the pair is finished. Run " + cancel + " to forget it.",
            Messages.get(null, "ring.cmd.create.slabs-stay", cancel));
        assertEquals("Both ends have to be in the same world. Your first ring is in " + ChatText.name("a")
            + ", and this one is in " + ChatText.name("b") + ".",
            Messages.get(null, "ring.cmd.create.other-world", ChatText.name("a"), ChatText.name("b")));
        assertEquals("Run " + cancel + " to give up on that one.", Messages.get(null, "ring.cmd.create.give-up-that-one", cancel));
        final String why = RingCommand.explain(null, RingManager.Refusal.TOO_CLOSE);
        assertEquals("Your first ring is not free any more: " + why, Messages.get(null, "ring.cmd.create.first-taken", why));
        assertEquals("Run " + cancel + " to give up on it.", Messages.get(null, "ring.cmd.create.give-up-on-it", cancel));
        assertEquals("Those two rings are " + ChatText.value("20") + " blocks apart on the ground, and rings reach "
            + ChatText.value("10") + ".", Messages.get(null, "ring.cmd.create.too-far", ChatText.value("20"), ChatText.value("10")));
        assertEquals("Build a stargate for a trip that long. Rings are for getting around one place.",
            Messages.get(null, "ring.cmd.create.build-a-gate"));
        assertEquals("Those two rings are " + ChatText.value("400") + " blocks apart in height, and rings reach "
            + ChatText.value("384") + ".", Messages.get(null, "ring.cmd.create.too-high", ChatText.value("400"), ChatText.value("384")));
        assertEquals("That is the ring you already laid. Go and build the other end.", Messages.get(null, "ring.cmd.create.same-ring"));
        assertEquals("Ring pair " + ChatText.name("aaaa0001") + " is live. Step into either end.",
            Messages.get(null, "ring.cmd.create.live", ChatText.name("aaaa0001")));
        final String usage = ChatText.usageBody("/wormhole ring allow <player>");
        assertEquals("It is " + ChatText.value("PRIVATE") + ". Use " + usage + " to let others in.",
            Messages.get(null, "ring.cmd.create.private", ChatText.value("PRIVATE"), usage));
        assertEquals("It is " + ChatText.value("PUBLIC") + ".", Messages.get(null, "ring.cmd.create.access", ChatText.value("PUBLIC")));

        assertEquals("You have no half-built ring pair.", Messages.get(null, "ring.cmd.cancel.none"));
        assertEquals("Forgotten. The circle you laid is still there, so you can pair it "
            + "later or take the slabs back yourself.", Messages.get(null, "ring.cmd.cancel.forgotten"));
        assertEquals(ChatText.name("aaaa0001") + " — " + ChatText.name(WORLD) + ", " + "PUBLIC" + ", " + "CONCURRENT" + "/"
            + "SEQUENTIAL", Messages.get(null, "ring.cmd.list.pair", ChatText.name("aaaa0001"), ChatText.name(WORLD),
                "PUBLIC", "CONCURRENT", "SEQUENTIAL"));
        assertEquals("You have no transport rings.", Messages.get(null, "ring.cmd.list.none"));

        final String id = ChatText.name("aaaa0001");
        for (final int returned : new int[] { 1, 2 })
        {
            final String slabs = ChatText.value(String.valueOf(returned));
            final String old = "Removed both ends of " + id + (" and put " + slabs + " slabs back.");
            assertEquals(old, Messages.get(null, "ring.cmd.remove.returned.one", id, slabs));
            assertEquals(old, Messages.get(null, "ring.cmd.remove.returned.other", id, slabs));
        }
        assertEquals("Removed both ends of " + id + ".", Messages.get(null, "ring.cmd.remove.removed", id));
        assertEquals("Its world is not loaded, so the slabs were left where they are.", Messages.get(null, "ring.cmd.remove.not-loaded"));
    }

    /** What ring build and fire send, against the old expression. */
    @Test
    void theConsoleLinesReadAsTheyDidBefore()
    {
        final String where = ChatText.value("100 64 100");
        assertEquals("Building and firing rings by coordinates and id needs " + ChatText.value(RingPermissions.ADMIN) + ".",
            Messages.get(null, "ring.cmd.console.needs-admin", ChatText.value(RingPermissions.ADMIN)));
        assertEquals("No world called " + ChatText.name("nether") + " is loaded.",
            Messages.get(null, "ring.cmd.console.no-world", ChatText.name("nether")));
        assertEquals(RingCommand.OVERLAPS_GATE + " (at " + where + ")",
            Messages.get(null, "ring.cmd.console.at", RingCommand.OVERLAPS_GATE, where));
        assertEquals("A player has laid that circle, or one beside it, as the first end of a"
            + " pair they are building.", Messages.get(null, "ring.cmd.console.pending"));
        assertEquals("Ring pair " + ChatText.name("aaaa0001") + " is live and public. Arrivals at "
            + ChatText.value("100.5 64 100.5") + " and " + ChatText.value("100.5 64 120.5") + ".",
            Messages.get(null, "ring.cmd.console.built", ChatText.name("aaaa0001"), ChatText.value("100.5 64 100.5"),
                ChatText.value("100.5 64 120.5")));
        assertEquals("Fire it with " + ChatText.command("/wormhole ring fire aaaa0001") + ".",
            Messages.get(null, "ring.cmd.console.fire-it", ChatText.command("/wormhole ring fire aaaa0001")));
        assertEquals("Ring pair " + ChatText.name("Tower") + " is counting down.",
            Messages.get(null, "ring.cmd.console.counting-down", ChatText.name("Tower")));
        assertEquals("Ring pair " + ChatText.name("Tower") + " did not fire: it is already"
            + " cycling or cooling down, an end is blocked, or its world is not loaded.",
            Messages.get(null, "ring.cmd.console.did-not-fire", ChatText.name("Tower")));
        assertEquals("No ring at " + where + " in " + ChatText.name(WORLD) + ".",
            Messages.get(null, "ring.cmd.console.no-ring-at", where, ChatText.name(WORLD)));
    }

    /** Ring create and list answer a player in their client's language. */
    @Test
    void createAndListAnswerInThePlayersLanguage()
    {
        final Player player = playerReading("qq");

        run(player, "ring", "create");
        run(player, "ring", "list");

        verify(player).sendMessage(ChatText.error("QQ may not build."));
        verify(player).sendMessage(ChatText.info("QQ no rings."));
    }

    /**
     * Why a circle is refused is one key, read by ring create and by ring build alike, each in the
     * reader's language: a translation of the reason reaches both.
     */
    @Test
    void theRefusalReasonReachesBothCommandsInThePlayersLanguage()
    {
        final Player player = playerReading("qq", RingPermissions.BUILD, RingPermissions.ADMIN);

        run(player, "ring", "create");
        run(player, "ring", "build", WORLD, "100", "64", "100", "100", "64", "140");

        verify(player).sendMessage(ChatText.error("QQ no ring."));
        verify(player).sendMessage(ChatText.error("QQ QQ no ring. at " + ChatText.value("100 64 100") + "."));
    }

    /** Ring build and fire answer a player in their client's language, refused or not. */
    @Test
    void buildAndFireAnswerInThePlayersLanguage()
    {
        final Player refused = playerReading("qq");
        final Player admin = playerReading("qq", RingPermissions.ADMIN);

        run(refused, "ring", "fire", "nothere");
        run(admin, "ring", "fire", "nothere");

        verify(refused).sendMessage(ChatText.error("QQ needs " + ChatText.value(RingPermissions.ADMIN) + "."));
        verify(admin).sendMessage(ChatText.error("QQ no pair " + ChatText.name("nothere") + "."));
    }

    /** The console has no client language, so it reads {@code server-language}: English, then qq once set. */
    @Test
    void theConsoleReadsTheServersLanguage()
    {
        final CommandSender console = mock(ConsoleCommandSender.class);
        run(console, "ring", "fire", "nothere");
        verify(console).sendMessage(ChatText.error("There is no ring pair called " + ChatText.name("nothere") + "."));

        config.when(ConfigManager::getServerLanguage).thenReturn("qq");
        Messages.load();
        try
        {
            run(console, "ring", "fire", "nothere");
        }
        finally
        {
            config.when(ConfigManager::getServerLanguage).thenReturn(Messages.DEFAULT_LANGUAGE);
            Messages.load();
        }
        verify(console).sendMessage(ChatText.error("QQ no pair " + ChatText.name("nothere") + "."));
    }

    /** The ceiling refusal picks its singular for a drop of one and its plural otherwise, in the player's language. */
    @Test
    void theCeilingRefusalIsSingularForADropOfOne()
    {
        final Player player = playerReading("qq");

        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(1));
        final String one = RingCommand.explain(player, RingBlockage.CEILING_TOO_HIGH);
        config.when(ConfigManager::getRingMaxCeilingDrop).thenReturn(Integer.valueOf(2));
        final String other = RingCommand.explain(player, RingBlockage.CEILING_TOO_HIGH);

        assertEquals(List.of("QQ high " + ChatText.value("1") + " one.", "QQ high " + ChatText.value("2") + " other."),
            List.of(one, other));
    }
}
