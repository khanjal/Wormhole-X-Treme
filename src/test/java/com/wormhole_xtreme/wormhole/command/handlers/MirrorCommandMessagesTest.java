package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wormhole mirror} says when it creates, sets, starts, captures and removes, read by
 * key from {@code lang/} (#26).
 *
 * <p>Each scenario runs the real command twice, with the same mirrors set up: for an English player,
 * where every line is held to the concatenation the code built before the move, copied from it, so a
 * reworded template, a swapped argument or a lost colour shows; and for a player whose client reads the
 * test language qq, where every line must be the qq one, so a call site that reads the server's language
 * instead of the reader's shows as English. The whole run is compared, in order, so a line sent twice or
 * not at all fails too.
 */
class MirrorCommandMessagesTest
{
    private static final String MIRROR = "mirror";
    private static final String MUSEUM = "museum";
    private static final String GALLERY = "gallery";
    private static final String HALL = "hall";
    private static final String CREATE = "create";
    private static final String SET = "set";
    private static final String WORLD = "world";
    private static final String START = "-start";
    private static final String QQ = "qq";
    private static final String NOT_A_BANNER = ", not a banner.";

    @TempDir
    File dataFolder;

    private World here;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigManager.ConfigKeys.VIEW_DEPTH, 32);
        ConfigTestSupport.set(ConfigManager.ConfigKeys.MIRROR_PER_WORLD_LIMIT, 0);
        MirrorManager.clear();
        here = mock(World.class);
        when(here.getName()).thenReturn(WORLD);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        Messages.load();
        PluginTestSupport.remove();
    }

    /** A banner at 1 64 1 hung north on solid wall, where a mirror may be made. */
    private Block banner(final Material type)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(here);
        when(block.getX()).thenReturn(1);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(1);
        final Directional facing = mock(Directional.class);
        when(facing.getFacing()).thenReturn(BlockFace.NORTH);
        when(block.getBlockData()).thenReturn(facing);
        final BlockData solid = mock(BlockData.class);
        when(solid.isOccluding()).thenReturn(true);
        final Block wall = mock(Block.class);
        when(wall.getBlockData()).thenReturn(solid);
        when(here.getBlockAt(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(),
            ArgumentMatchers.anyInt())).thenReturn(wall);
        return block;
    }

    /** The banner the player looks at, with the command run for it. */
    private void lookAtBanner(final Player player)
    {
        final Block block = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(block);
    }

    private static void run(final CommandSender sender, final String... words)
    {
        new MirrorCommand().execute(sender, words);
    }

    private static void mirror(final String name, final int x, final int z)
    {
        MirrorManager.add(new QuantumMirror(name, new BlockPlace(WORLD, x, 64, z), null));
    }

    private static List<String> sent(final Player player)
    {
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(0)).sendMessage(said.capture());
        return said.getAllValues();
    }

    /** Runs the scenario for an English reader and for a qq one, and compares what each was told. */
    private void check(final Consumer<Player> scenario, final List<String> english, final List<String> qq)
    {
        final Player reading = AdminReaders.reading("en");
        scenario.accept(reading);
        assertEquals(english, sent(reading), "an English player");
        MirrorManager.clear();
        final Player other = AdminReaders.reading(QQ);
        scenario.accept(other);
        assertEquals(qq, sent(other), "a qq player");
    }

    private static String usageLine()
    {
        return info(ChatText.usage("/wormhole mirror create|set|remove|list"));
    }

    private static String setUsage()
    {
        return info(ChatText.usage("/wormhole mirror set [<name>] <-stamp|-start|-capture> ..."));
    }

    private static String value(final long number)
    {
        return MirrorText.VALUE_COLOUR + number + MirrorText.BODY_COLOUR;
    }

    /** The help a bare {@code /wormhole mirror} gives, with the list of what {@code set} changes. */
    @Test
    void theHelpListsWhatSetChanges()
    {
        check(player -> run(player, MIRROR),
            List.of(usageLine(),
                info("A mirror is a wall banner. Make one with " + MirrorText.command(CREATE) + " while looking at it."),
                info("Right-click a mirror to choose another, and punch it to go through."),
                info(MirrorText.command(SET) + " changes one thing it has: " + MirrorText.command("-stamp") + ", "
                    + MirrorText.command(START) + ", " + MirrorText.command("-capture") + ".")),
            List.of(usageLine(), info("QQ banner " + MirrorText.command(CREATE) + "."), info("QQ click punch."),
                info("QQ " + MirrorText.command(SET) + " sets " + MirrorText.command("-stamp") + " | "
                    + MirrorText.command(START) + " | " + MirrorText.command("-capture") + ".")));
    }

    /** A word after set that is no property, and a name that starts with a dash. */
    @Test
    void aWordThatIsNoPropertyAndADashedNameAreRefused()
    {
        check(player -> run(player, MIRROR, SET, MUSEUM, "bogus"),
            List.of(error(MirrorText.quoted("bogus") + " is not something a mirror has."), setUsage()),
            List.of(error("QQ <" + MirrorText.name("bogus") + "> not a setting."), setUsage()));
        check(player -> run(player, MIRROR, CREATE, "-x"),
            List.of(error("A mirror's name cannot start with " + MirrorText.command("-")
                + "; words that do are options, like " + MirrorText.command(START) + ".")),
            List.of(error("QQ no dash " + MirrorText.command("-") + " " + MirrorText.command(START) + ".")));
    }

    /** A new mirror, told it is made, and told when another is too near for either to be drawn whole. */
    @Test
    void aNewMirrorIsToldItIsMadeAndWhenAnotherIsTooNear()
    {
        check(player -> {
            mirror(HALL, 1, 12);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(
            info("Mirror " + MirrorText.quoted(MUSEUM) + " is this banner. Walk up to it to see its reflection."),
            info("Right-click it to choose another mirror, and punch it to go through."),
            info("It is " + value(11) + " blocks from " + MirrorText.quoted(HALL) + ". Mirrors nearer than " + value(64)
                + " (twice view-depth) are not drawn whole."),
            info("Each shows only what a viewer sees through it, which costs more as people walk past.")),
            List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> made."), info("QQ made hint."),
                info("QQ " + value(11) + " from <" + MirrorText.name(HALL) + "> within " + value(64) + "."),
                info("QQ too near cost.")));
    }

    /** A mirror renamed, one the banner already is, and one whose name is a mirror somewhere else. */
    @Test
    void renamingARepeatedNameAndANameTakenElsewhereAreSaid()
    {
        check(player -> {
            mirror(MUSEUM, 1, 1);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, GALLERY);
        }, List.of(info("Mirror " + MirrorText.quoted(MUSEUM) + " is " + MirrorText.quoted(GALLERY) + " now.")),
            List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> renamed <" + MirrorText.name(GALLERY) + ">.")));
        check(player -> {
            mirror(MUSEUM, 1, 1);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(info("Mirror " + MirrorText.quoted(MUSEUM) + " is already this"
            + " banner. Nothing to do.")), List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> already.")));
        check(player -> {
            mirror(MUSEUM, 1, 1);
            mirror(GALLERY, 9, 9);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, GALLERY);
        }, List.of(error("This banner is already " + MirrorText.quoted(MUSEUM) + ", and " + MirrorText.quoted(GALLERY)
            + " is a mirror somewhere else."),
            info("Renaming this one would leave that one on no banner. Remove one of them first.")),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> elsewhere <" + MirrorText.name(GALLERY) + ">."),
                info("QQ elsewhere hint.")));
    }

    /** A mirror already on a post is renamed, and told where to click. */
    @Test
    void aMirrorOnAPostIsToldWhereToClick()
    {
        check(player -> {
            mirror(MUSEUM, 1, 1);
            final Block post = banner(Material.WHITE_BANNER);
            when(post.getBlockData()).thenReturn(mock(Rotatable.class));
            when(player.getTargetBlockExact(6)).thenReturn(post);
            run(player, MIRROR, CREATE, GALLERY);
        }, List.of(
            info("That one stands on a post, so click near its base to travel; the cloth above it cannot be clicked."),
            info("A banner on a wall works anywhere on it."),
            info("Mirror " + MirrorText.quoted(MUSEUM) + " is " + MirrorText.quoted(GALLERY) + " now.")),
            List.of(info("QQ post base."), info("QQ post wall."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> renamed <" + MirrorText.name(GALLERY) + ">.")));
    }

    /** Looking at nothing, and at a block that is no banner. */
    @Test
    void lookingAtNothingOrAtTheWrongBlockIsRefused()
    {
        check(player -> {
            when(player.getTargetBlockExact(6)).thenReturn(null);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error("Look at the banner you want to use, within six blocks.")),
            List.of(error("QQ look at a banner.")));
        check(player -> {
            final Block stone = mock(Block.class);
            when(stone.getType()).thenReturn(Material.STONE);
            when(player.getTargetBlockExact(6)).thenReturn(stone);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error("That is a " + MirrorText.name("stone")
            + ", not a banner. A mirror has to be a banner hung on a wall.")),
            List.of(error("QQ a " + MirrorText.name("stone") + " not a banner.")));
    }

    /** The console has no client language, so it reads the server's: English, then qq once set. */
    @Test
    void theConsoleIsToldToRunItInGameInTheServersLanguage()
    {
        final CommandSender console = mock(CommandSender.class);
        run(console, MIRROR, CREATE, MUSEUM);
        verify(console).sendMessage(error("That has to be run in game: it depends on where you are standing."));

        ConfigTestSupport.set(ConfigManager.ConfigKeys.SERVER_LANGUAGE, QQ);
        Messages.load();
        run(console, MIRROR, CREATE, MUSEUM);
        verify(console).sendMessage(error("QQ in game."));
    }

    /** A world nobody loaded, and coordinates that hold no banner. */
    @Test
    void coordinatesThatNameNoBannerAreRefused()
    {
        final Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(here.getBlockAt(5, 64, 5)).thenReturn(stone);
        check(player -> {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
            {
                bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(here);
                run(player, MIRROR, CREATE, MUSEUM, "nether", "5", "64", "5");
                run(player, MIRROR, CREATE, MUSEUM, WORLD, "5", "64", "5");
            }
        }, List.of(error("No world called " + MirrorText.name("nether") + " is loaded."),
            error("The block at 5 64 5 is " + MirrorText.name("stone") + NOT_A_BANNER)),
            List.of(error("QQ no world " + MirrorText.name("nether") + "."),
                error("QQ block 5 64 5 is " + MirrorText.name("stone") + ".")));
    }

    /** A name no mirror has, and a banner that is no mirror. */
    @Test
    void aMirrorNobodyHasAndABannerThatIsNoMirrorAreRefused()
    {
        check(player -> run(player, MIRROR, SET, "ghost", START, MUSEUM),
            List.of(error("There is no mirror called " + MirrorText.quoted("ghost") + ".")),
            List.of(error("QQ no mirror <" + MirrorText.name("ghost") + ">.")));
        check(player -> {
            lookAtBanner(player);
            run(player, MIRROR, "remove");
        }, List.of(error("That banner is not a mirror."), info("Make it one with "
            + ChatText.usageBody("/wormhole mirror create <name>") + ", or name the mirror you meant.")),
            List.of(error("QQ banner not a mirror."),
                info("QQ make one " + ChatText.usageBody("/wormhole mirror create <name>") + ".")));
    }

    /** A start chosen, cleared and refused for the mirror's own name. */
    @Test
    void startSaysWhatItDid()
    {
        check(player -> {
            mirror(MUSEUM, 1, 1);
            mirror(HALL, 1, 30);
            run(player, MIRROR, SET, MUSEUM, START, HALL);
            run(player, MIRROR, SET, MUSEUM, START, "-none");
            run(player, MIRROR, SET, MUSEUM, START, MUSEUM);
        }, List.of(
            info("A right-click on " + MirrorText.quoted(MUSEUM) + " opens onto " + MirrorText.quoted(HALL) + " first."),
            info("A right-click on " + MirrorText.quoted(MUSEUM) + " goes through the other mirrors by name."),
            error("A mirror starts on its own room already; " + MirrorText.command("-none")
                + " is the way to say so.")),
            List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> starts <" + MirrorText.name(HALL) + ">."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> start none."),
                error("QQ own room " + MirrorText.command("-none") + ".")));
    }

    /** A capture taken again, one that cannot be because the world is not loaded, and a mirror removed. */
    @Test
    void captureAndRemoveSayWhatTheyDid()
    {
        check(player -> {
            MirrorManager.add(new QuantumMirror(MUSEUM, new BlockPlace(WORLD, 1, 64, 1),
                new Place("archive", 0, 64, 0, 0f, 0f)));
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
            {
                bukkit.when(() -> Bukkit.getWorld("archive")).thenReturn(null);
                run(player, MIRROR, SET, MUSEUM, "-capture");
            }
            try (MockedStatic<Captures> captures = mockStatic(Captures.class))
            {
                captures.when(() -> Captures.retake(ArgumentMatchers.any())).thenReturn(true);
                run(player, MIRROR, SET, MUSEUM, "-capture");
            }
            run(player, MIRROR, "remove", MUSEUM);
        }, List.of(
            error(MirrorText.quoted(MUSEUM) + "'s room cannot be captured now: " + MirrorText.name("archive")
                + " is not loaded."),
            info("Capturing " + MirrorText.quoted(MUSEUM) + "'s room again; the view"
                + " changes when the new one is ready."),
            info(MirrorText.quoted(MUSEUM) + " is an ordinary banner again.")),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> capture unloaded " + MirrorText.name("archive") + "."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> capture again."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> removed.")));
    }
}
