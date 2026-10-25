package com.wormhole_xtreme.wormhole.command.handlers;

import static com.wormhole_xtreme.wormhole.utils.ChatText.error;
import static com.wormhole_xtreme.wormhole.utils.ChatText.info;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
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
import com.wormhole_xtreme.wormhole.model.mirror.MirrorNetwork;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPreset;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPresetRegistry;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorStamp;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorView;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Captures;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What {@code /wormhole mirror} says when it creates, sets, stamps, starts, captures, removes and lists, read by
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

    /** The three looks every stamping scenario knows: one chosen by name, one a biome answers with, the indoor one. */
    private static final MirrorPreset NETHER = new MirrorPreset("nether", DyeColor.RED, List.of(),
        Set.of("NETHER_WASTES"), true);
    private static final MirrorPreset WOODLAND = new MirrorPreset("woodland", DyeColor.GREEN, List.of(),
        Set.of("FOREST"), false);
    private static final MirrorPreset INDOORS = new MirrorPreset("indoors", DyeColor.BROWN, List.of(),
        Set.of(), false);

    private static final String NOT_STAMPED = "That banner could not be stamped.";
    private static final String NO_LOOKS = " Check the server log for what went wrong reading shapes/mirror.";
    private static final String LOOK_COUNT_TAIL = " looks to choose from: press tab for the list,"
        + " or leave it out to sample the far side.";
    private static final String STAMP = "-stamp";

    /**
     * What a stamping scenario runs against: the looks the registry holds by name, the preset a biome
     * answers with, what the far side looked like, and whether the banner takes the stamp.
     */
    private record Looks(List<String> names, MirrorPreset byBiome, MirrorView view, boolean applies)
    {
        static Looks of(final String... names)
        {
            return new Looks(List.of(names), WOODLAND, null, true);
        }

        Looks seeing(final MirrorView seen)
        {
            return new Looks(names, byBiome, seen, applies);
        }

        Looks stampFailing()
        {
            return new Looks(names, byBiome, view, false);
        }

        Looks withoutBiomeLook()
        {
            return new Looks(names, null, view, applies);
        }
    }

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
        MirrorNetwork.clear();
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
        check(scenario, english, qq, UnaryOperator.identity());
    }

    /** {@link #check(Consumer, List, List)} comparing what {@code arranged} makes of each list, for lines with no fixed order. */
    private void check(final Consumer<Player> scenario, final List<String> english, final List<String> qq,
        final UnaryOperator<List<String>> arranged)
    {
        MirrorManager.clear();
        MirrorNetwork.clear();
        final Player reading = AdminReaders.reading("en");
        scenario.accept(reading);
        assertEquals(arranged.apply(english), arranged.apply(sent(reading)), "an English player");
        MirrorManager.clear();
        MirrorNetwork.clear();
        final Player other = AdminReaders.reading(QQ);
        scenario.accept(other);
        assertEquals(arranged.apply(qq), arranged.apply(sent(other)), "a qq player");
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
            final Rotatable onAPost = mock(Rotatable.class);
            when(post.getBlockData()).thenReturn(onAPost);
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
        when(here.getBlockAt(5, 64, 7)).thenReturn(stone);
        check(player -> {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
            {
                bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(here);
                run(player, MIRROR, CREATE, MUSEUM, "nether", "5", "64", "7");
                run(player, MIRROR, CREATE, MUSEUM, WORLD, "5", "64", "7");
            }
        }, List.of(error("No world called " + MirrorText.name("nether") + " is loaded."),
            error("The block at 5 64 7 is " + MirrorText.name("stone") + NOT_A_BANNER)),
            List.of(error("QQ no world " + MirrorText.name("nether") + "."),
                error("QQ block 5 64 7 is " + MirrorText.name("stone") + ".")));
    }

    /** The wall behind the banner at 1 64 1 is solid except at one block, and a banner hangs at {@code partner}. */
    private void wallOpenAt(final int[] gap, final Block partner)
    {
        final BlockData solid = mock(BlockData.class);
        when(solid.isOccluding()).thenReturn(true);
        final Block wall = mock(Block.class);
        when(wall.getBlockData()).thenReturn(solid);
        final Block hollow = mock(Block.class);
        final BlockData open = mock(BlockData.class);
        when(hollow.getBlockData()).thenReturn(open);
        when(here.isChunkLoaded(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(true);
        when(here.getBlockAt(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenAnswer(call -> {
                final int x = call.getArgument(0);
                final int y = call.getArgument(1);
                final int z = call.getArgument(2);
                if ((x == gap[0]) && (y == gap[1]) && (z == gap[2]))
                {
                    return hollow;
                }
                return ((partner != null) && (x == 0) && (y == 64) && (z == 1)) ? partner : wall;
            });
    }

    /** The rules that refuse a banner, and the word about a thin wall, said where {@code create} says them. */
    @Test
    void thePlacementRefusalsAndTheThinWallWordAreInTheReadersLanguage()
    {
        check(player -> {
            final Block post = banner(Material.WHITE_BANNER);
            final Rotatable onAPost = mock(Rotatable.class);
            when(post.getBlockData()).thenReturn(onAPost);
            when(player.getTargetBlockExact(6)).thenReturn(post);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error("A mirror hangs on a wall. On a post in the open its world shows past its edges.")),
            List.of(error("QQ post refused.")));
        check(player -> {
            final Block hanging = banner(Material.WHITE_WALL_BANNER);
            wallOpenAt(new int[] { 0, 62, 2 }, null);
            when(player.getTargetBlockExact(6)).thenReturn(hanging);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error("A mirror needs solid wall a block out on every side of its opening,"
            + " and the block at 0 62 2 is not.")), List.of(error("QQ gap 0 62 2.")));
        check(player -> {
            final Block hanging = banner(Material.WHITE_WALL_BANNER);
            wallOpenAt(new int[] { 3, 61, 2 }, null);
            when(player.getTargetBlockExact(6)).thenReturn(hanging);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(
            info("Mirror " + MirrorText.quoted(MUSEUM) + " is this banner. Walk up to it to see its reflection."),
            info("Right-click it to choose another mirror, and punch it to go through."),
            info("Its wall is solid a block out, which is enough; two blocks out hides the room's edges better"
                + " from an angle, and the block at 3 61 2 is not solid.")),
            List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> made."), info("QQ made hint."),
                info("QQ thin at 3 61 2.")));
    }

    /** A pair short of wall says how big its wall has to be. */
    @Test
    void aPairShortOfWallIsToldHowBigItsWallHasToBe()
    {
        check(player -> {
            final Block hanging = banner(Material.WHITE_WALL_BANNER);
            final Block partner = banner(Material.WHITE_WALL_BANNER);
            when(partner.getX()).thenReturn(0);
            wallOpenAt(new int[] { -1, 64, 2 }, partner);
            when(player.getTargetBlockExact(6)).thenReturn(hanging);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error("Two banners make a mirror two wide, which needs solid wall 4 across and 4 tall;"
            + " the block at -1 64 2 is not.")), List.of(error("QQ pair gap 4 4 at -1 64 2.")));
    }

    /** A world at its limit says how many it holds, in the singular for one and the plural otherwise. */
    @Test
    void aWorldAtItsLimitSaysHowManyMirrorsItHolds()
    {
        check(player -> {
            ConfigTestSupport.set(ConfigManager.ConfigKeys.MIRROR_PER_WORLD_LIMIT, 1);
            mirror(HALL, 1, 12);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error(MirrorText.name(WORLD) + " already has 1 mirror, which is as many as "
            + MirrorText.name("mirror-per-world-limit") + " allows.")),
            List.of(error("QQ " + MirrorText.name(WORLD) + " has 1 one limit " + MirrorText.name("mirror-per-world-limit")
                + ".")));
        check(player -> {
            ConfigTestSupport.set(ConfigManager.ConfigKeys.MIRROR_PER_WORLD_LIMIT, 1);
            mirror(HALL, 1, 12);
            mirror("lobby", 1, 20);
            lookAtBanner(player);
            run(player, MIRROR, CREATE, MUSEUM);
        }, List.of(error(MirrorText.name(WORLD) + " already has 2 mirrors, which is as many as "
            + MirrorText.name("mirror-per-world-limit") + " allows.")),
            List.of(error("QQ " + MirrorText.name(WORLD) + " has 2 many limit " + MirrorText.name("mirror-per-world-limit")
                + ".")));
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
            error(MirrorText.quoted(MUSEUM) + "s room cannot be captured now: " + MirrorText.name("archive")
                + " is not loaded."),
            info("Capturing " + MirrorText.quoted(MUSEUM) + "s room again; the view"
                + " changes when the new one is ready."),
            info(MirrorText.quoted(MUSEUM) + " is an ordinary banner again.")),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> capture unloaded " + MirrorText.name("archive") + "."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> capture again."),
                info("QQ <" + MirrorText.name(MUSEUM) + "> removed.")));
    }
    /** Names the way the lists were written before the move: name colour, the separator in the body colour. */
    private static String joined(final String separator, final String... names)
    {
        return MirrorText.NAME_COLOUR + String.join(MirrorText.BODY_COLOUR + separator + MirrorText.NAME_COLOUR, names)
            + MirrorText.BODY_COLOUR;
    }

    private static List<String> manyLooks(final int count)
    {
        return IntStream.range(0, count).mapToObj(i -> "look" + i).toList();
    }

    /** Runs a scenario with the banner's world, the stamp, the far side and the looks stubbed as {@code looks} says. */
    private void withLooks(final Looks looks, final Runnable scenario)
    {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
            MockedStatic<MirrorStamp> stamps = mockStatic(MirrorStamp.class);
            MockedStatic<MirrorView> views = mockStatic(MirrorView.class);
            MockedStatic<MirrorPresetRegistry> registry = mockStatic(MirrorPresetRegistry.class,
                CALLS_REAL_METHODS))
        {
            bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(here);
            stamps.when(() -> MirrorStamp.apply(any(Block.class), any(MirrorPreset.class)))
                .thenReturn(looks.applies());
            stamps.when(() -> MirrorStamp.apply(any(Block.class), any(MirrorPreset.class), any(MirrorView.class)))
                .thenReturn(looks.applies());
            views.when(() -> MirrorView.look(any(Place.class))).thenReturn(looks.view());
            registry.when(MirrorPresetRegistry::names).thenReturn(looks.names().toArray(new String[0]));
            registry.when(() -> MirrorPresetRegistry.byName(NETHER.name())).thenReturn(NETHER);
            registry.when(() -> MirrorPresetRegistry.forBiome(any())).thenReturn(looks.byBiome());
            registry.when(MirrorPresetRegistry::indoors).thenReturn(INDOORS);
            scenario.run();
        }
    }

    /** The mirror {@code museum} on the banner at 1 64 1, going to {@code room}. */
    private void museumGoingTo(final Place room)
    {
        MirrorManager.add(new QuantumMirror(MUSEUM, new BlockPlace(WORLD, 1, 64, 1), room));
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(here.getBlockAt(1, 64, 1)).thenReturn(block);
    }

    private static Place far()
    {
        return new Place("far", 100, 64, 200, 0f, 0f);
    }

    private void stamp(final Player player, final Looks looks, final String... words)
    {
        withLooks(looks, () -> run(player, words));
    }

    private static String stamped(final String look)
    {
        return info(MirrorText.quoted(MUSEUM) + " looks like " + MirrorText.name(look) + " now.");
    }

    /** A look named, stamped or refused, and a banner that is gone or in a world that is not loaded. */
    @Test
    void namingALookStampsTheBannerOrSaysWhyNot()
    {
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of("nether"), MIRROR, SET, MUSEUM, STAMP, "nether");
        }, List.of(stamped("nether")),
            List.of(info("QQ <" + MirrorText.name(MUSEUM) + "> looks " + MirrorText.name("nether") + ".")));
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of("nether").stampFailing(), MIRROR, SET, MUSEUM, STAMP, "nether");
        }, List.of(error(NOT_STAMPED)), List.of(error("QQ not stamped.")));
        check(player -> {
            museumGoingTo(null);
            final Block stone = mock(Block.class);
            when(stone.getType()).thenReturn(Material.STONE);
            when(here.getBlockAt(1, 64, 1)).thenReturn(stone);
            stamp(player, Looks.of("nether"), MIRROR, SET, MUSEUM, STAMP, "nether");
        }, List.of(error(MirrorText.quoted(MUSEUM) + " is not a banner any more. Put one back, or run "
            + MirrorText.command("/wormhole mirror create " + MUSEUM) + " looking at a banner that is there.")),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> banner gone, run "
                + MirrorText.command("/wormhole mirror create " + MUSEUM) + ".")));
        check(player -> {
            MirrorManager.add(new QuantumMirror(MUSEUM, new BlockPlace("gone", 1, 64, 1), null));
            stamp(player, Looks.of("nether"), MIRROR, SET, MUSEUM, STAMP, "nether");
        }, List.of(error(MirrorText.quoted(MUSEUM) + " is in " + MirrorText.name("gone")
            + ", which is not loaded, so its banner cannot be stamped.")),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> banner in " + MirrorText.name("gone") + " unloaded.")));
    }

    /** A look nobody has: the others listed while they fit, counted when they do not, and none at all. */
    @Test
    void aLookNobodyHasOffersTheOthersCountsThemOrSaysThereAreNone()
    {
        final String[] few = { "nether", "end", "ocean" };
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of(few), MIRROR, SET, MUSEUM, STAMP, "chartreuse");
        }, List.of(error("There is no look called " + MirrorText.quoted("chartreuse") + ". Try one of: "
            + joined(", ", few))),
            List.of(error("QQ no look <" + MirrorText.name("chartreuse") + "> try " + joined(" | ", few))));
        final List<String> many = manyLooks(23);
        check(player -> {
            museumGoingTo(null);
            stamp(player, new Looks(many, WOODLAND, null, true), MIRROR, SET, MUSEUM, STAMP, "chartreuse");
        }, List.of(error("There is no look called " + MirrorText.quoted("chartreuse") + ". There are " + value(23)
            + " to choose from; press tab for the list.")),
            List.of(error("QQ no look <" + MirrorText.name("chartreuse") + "> of " + value(23) + " to pick.")));
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of(), MIRROR, SET, MUSEUM, STAMP, "chartreuse");
        }, List.of(error("There are no looks loaded at all." + NO_LOOKS)), List.of(error("QQ no looks at all.")));
    }

    /** The list and the count meet at the budget: 80 characters are named, 81 are counted. */
    @Test
    void theLooksAreNamedAtTheBudgetAndCountedOnePast()
    {
        final String atBudget = "a".repeat(80);
        final String pastBudget = "a".repeat(81);
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of(atBudget), MIRROR, SET, MUSEUM, STAMP, "chartreuse");
        }, List.of(error("There is no look called " + MirrorText.quoted("chartreuse") + ". Try one of: "
            + joined(", ", atBudget))),
            List.of(error("QQ no look <" + MirrorText.name("chartreuse") + "> try "
                + joined(" | ", atBudget))));
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of(pastBudget), MIRROR, SET, MUSEUM, STAMP, "chartreuse");
        }, List.of(error("There is no look called " + MirrorText.quoted("chartreuse") + ". There are " + value(1)
            + " to choose from; press tab for the list.")),
            List.of(error("QQ no look <" + MirrorText.name("chartreuse") + "> of " + value(1) + " to pick.")));
    }

    /** The usage of {@code -stamp} lists the looks while they fit, counts them past that, and says nothing of none. */
    @Test
    void theStampUsageListsCountsOrOmitsTheLooks()
    {
        final String lookNone = "Look at the banner you want to use, within six blocks.";
        final String lookUsage = "/wormhole mirror set [<name>] -stamp [<look>]";
        check(player -> stamp(player, Looks.of("nether", "end", "ocean"), MIRROR, SET, STAMP),
            List.of(error(lookNone), info(ChatText.usage("/wormhole mirror set [<name>] -stamp [nether|end|ocean]"))),
            List.of(error("QQ look at a banner."),
                info(ChatText.usage("/wormhole mirror set [<name>] -stamp [nether|end|ocean]"))));
        check(player -> stamp(player, new Looks(manyLooks(23), WOODLAND, null, true), MIRROR, SET, STAMP),
            List.of(error(lookNone), info(ChatText.usage(lookUsage)), info(value(23) + LOOK_COUNT_TAIL)),
            List.of(error("QQ look at a banner."), info(ChatText.usage(lookUsage)),
                info("QQ " + value(23) + " looks to pick.")));
        check(player -> stamp(player, Looks.of(), MIRROR, SET, STAMP),
            List.of(error(lookNone), info(ChatText.usage(lookUsage))),
            List.of(error("QQ look at a banner."), info(ChatText.usage(lookUsage))));
        check(player -> stamp(player, Looks.of("a".repeat(80)), MIRROR, SET, STAMP),
            List.of(error(lookNone), info(ChatText.usage("/wormhole mirror set [<name>] -stamp [" + "a".repeat(80) + "]"))),
            List.of(error("QQ look at a banner."),
                info(ChatText.usage("/wormhole mirror set [<name>] -stamp [" + "a".repeat(80) + "]"))));
        check(player -> stamp(player, Looks.of("a".repeat(81)), MIRROR, SET, STAMP),
            List.of(error(lookNone), info(ChatText.usage(lookUsage)), info(value(1) + LOOK_COUNT_TAIL)),
            List.of(error("QQ look at a banner."), info(ChatText.usage(lookUsage)),
                info("QQ " + value(1) + " looks to pick.")));
    }

    /** Stamping from the far side refuses a mirror that goes nowhere, suggesting the first look or a placeholder. */
    @Test
    void stampingAMirrorThatGoesNowhereSuggestsALook()
    {
        final String nowhere = MirrorText.quoted(MUSEUM) + " does not go anywhere yet, so there is nothing to look at.";
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of("nether", "end"), MIRROR, SET, MUSEUM, STAMP);
        }, List.of(error(nowhere), info("Point it first, or name a look: "
            + MirrorText.command("/wormhole mirror stamp", MUSEUM + " nether"))),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> goes nowhere."),
                info("QQ point first " + MirrorText.command("/wormhole mirror stamp", MUSEUM + " nether"))));
        check(player -> {
            museumGoingTo(null);
            stamp(player, Looks.of(), MIRROR, SET, MUSEUM, STAMP);
        }, List.of(error(nowhere), info("Point it first, or name a look: "
            + MirrorText.command("/wormhole mirror stamp", MUSEUM + " <look>"))),
            List.of(error("QQ <" + MirrorText.name(MUSEUM) + "> goes nowhere."),
                info("QQ point first " + MirrorText.command("/wormhole mirror stamp", MUSEUM + " <look>"))));
    }

    /** A far side that cannot be read, and no looks to stamp it with. */
    @Test
    void aFarSideThatCannotBeReadAndNoLooksAreRefused()
    {
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether"), MIRROR, SET, MUSEUM, STAMP);
        }, List.of(error(MirrorText.name("far") + " is not loaded, so the far side cannot be read."
            + " Name a look instead, or try again once that world is up.")),
            List.of(error("QQ " + MirrorText.name("far") + " not loaded for stamp.")));
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether").seeing(new MirrorView("FOREST", List.of(DyeColor.RED), false))
                .withoutBiomeLook(), MIRROR, SET, MUSEUM, STAMP);
        }, List.of(error("No mirror looks are loaded, so there is nothing to stamp with." + NO_LOOKS)),
            List.of(error("QQ no looks to stamp.")));
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether").seeing(new MirrorView("FOREST", List.of(DyeColor.RED), false))
                .stampFailing(), MIRROR, SET, MUSEUM, STAMP);
        }, List.of(error(NOT_STAMPED)), List.of(error("QQ not stamped.")));
    }

    private static String shows(final String where)
    {
        return info(MirrorText.quoted(MUSEUM) + " now shows " + where + ".");
    }

    private static String qqShows(final String where)
    {
        return info("QQ <" + MirrorText.name(MUSEUM) + "> shows " + where + ".");
    }

    /** What the far side is called: a room, a biome or the look's own name, with or without its main colour. */
    @Test
    void stampingFromTheFarSideSaysWhereItIsAndItsMainColour()
    {
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether").seeing(new MirrorView("FOREST", List.of(DyeColor.BROWN, DyeColor.RED), true)),
                MIRROR, SET, MUSEUM, STAMP);
        }, List.of(shows("somewhere indoors, mostly " + MirrorText.dye(DyeColor.BROWN))),
            List.of(qqShows("QQ QQ indoors mostly " + MirrorText.dye(DyeColor.BROWN))));
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether").seeing(new MirrorView("FOREST", List.of(), false)),
                MIRROR, SET, MUSEUM, STAMP);
        }, List.of(shows(MirrorText.name("forest"))), List.of(qqShows(MirrorText.name("forest"))));
        check(player -> {
            museumGoingTo(far());
            stamp(player, Looks.of("nether").seeing(new MirrorView("", List.of(DyeColor.CYAN), false)),
                MIRROR, SET, MUSEUM, STAMP);
        }, List.of(shows(MirrorText.name("woodland") + ", mostly " + MirrorText.dye(DyeColor.CYAN))),
            List.of(qqShows("QQ " + MirrorText.name("woodland") + " mostly " + MirrorText.dye(DyeColor.CYAN))));
    }

    private static String rowOf(final String name, final String world, final String showing, final String tail)
    {
        return "  " + MirrorText.name(name) + " -- " + MirrorText.name(world) + " -> " + showing + tail;
    }

    private static String qqRowOf(final String name, final String world, final String showing, final String tail)
    {
        return "QQ " + MirrorText.name(name) + " in " + MirrorText.name(world) + " shows " + showing + tail + ".";
    }

    private static List<String> headerAndSortedRows(final List<String> sent)
    {
        final List<String> rows = new ArrayList<>(sent.subList(1, sent.size()));
        rows.sort(null);
        final List<String> sorted = new ArrayList<>(sent.subList(0, 1));
        sorted.addAll(rows);
        return sorted;
    }

    /** No mirrors, and one that goes nowhere yet. */
    @Test
    void theListSaysWhenThereAreNoMirrorsAndWhenOneGoesNowhere()
    {
        check(player -> run(player, MIRROR, "list"),
            List.of(info("No mirrors yet. Look at a banner and run "
                + MirrorText.command("/wormhole mirror create <name>") + ".")),
            List.of(info("QQ no mirrors " + MirrorText.command("/wormhole mirror create <name>") + ".")));
        check(player -> {
            mirror(MUSEUM, 1, 1);
            run(player, MIRROR, "list");
        }, List.of(info(value(1) + " mirror(s):"), info(rowOf(MUSEUM, WORLD, "nowhere yet", ""))),
            List.of(info("QQ " + value(1) + " mirrors:"), info(qqRowOf(MUSEUM, WORLD, "QQ nowhere", ""))));
    }

    /** Each way a mirror's row can end: its own reflection, a room elsewhere, another mirror, a start, a capture. */
    @Test
    void theListDescribesWhatEachMirrorShows()
    {
        check(player -> {
            mirrorTo("alpha", 1, new Place(WORLD, 1, 63, 1, 0f, 0f));
            MirrorManager.add(mirrorTo("bravo", 5, new Place("far", 100.6, 70.4, 200.5, 0f, 0f)).withStart("alpha"));
            mirrorTo("charlie", 9, new Place("nether", 3, 4, 5, 0f, 0f));
            mirrorTo("delta", 13, null);
            MirrorNetwork.scroll(MirrorManager.byName("charlie"), false);
            run(player, MIRROR, "list");
        }, List.of(info(value(4) + " mirror(s):"),
            info(rowOf("alpha", WORLD, "its own reflection", ", capture world_1_63_1")),
            info(rowOf("bravo", WORLD, MirrorText.name("far") + " at 101, 70, 201", " (starts on alpha), capture far_100_70_200")),
            info(rowOf("charlie", WORLD, MirrorText.name("alpha"), ", capture nether_3_4_5")),
            info(rowOf("delta", WORLD, "nowhere yet", ""))),
            List.of(info("QQ " + value(4) + " mirrors:"),
                info(qqRowOf("alpha", WORLD, "QQ own reflection", " QQ capture world_1_63_1")),
                info(qqRowOf("bravo", WORLD, "QQ " + MirrorText.name("far") + " @ 101/70/201",
                    " QQ start alpha QQ capture far_100_70_200")),
                info(qqRowOf("charlie", WORLD, MirrorText.name("alpha"), " QQ capture nether_3_4_5")),
                info(qqRowOf("delta", WORLD, "QQ nowhere", ""))),
            MirrorCommandMessagesTest::headerAndSortedRows);
    }

    private static QuantumMirror mirrorTo(final String name, final int x, final Place room)
    {
        final QuantumMirror made = new QuantumMirror(name, new BlockPlace(WORLD, x, 64, x), room);
        MirrorManager.add(made);
        return made;
    }
}
