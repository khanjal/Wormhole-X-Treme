package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Clicking a mirror -- a right-click chooses where it opens onto, a punch goes through -- and,
 * mostly, clicking everything that is not one.
 *
 * <p>This handler runs on every click of every block on the server, so the case that matters
 * most is the one that happens millions of times and must do almost nothing. The ordering inside
 * it is a performance decision with a test of its own in {@code InteractLoggingCostTest}, which
 * fails if this path so much as asks a block for its world; the tests here pin the behaviour
 * that ordering has to preserve.
 *
 * <p>What a click says about the mirror itself -- where it opens onto now, that there is nowhere
 * else, to right-click first, to wait a moment after arriving -- goes above the hotbar, where it
 * replaces itself. In chat, a player clicking through a list of mirrors filled the window. What
 * somebody needs to read and act on -- a refused trip, a world that is not loaded -- stays in chat.
 */
class MirrorInteractionTest
{
    private Player player;
    private Player.Spigot hotbar;
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        // Reached as soon as a click lands on a mirror: the permission check logs through the
        // plugin singleton, so without one these tests fail on an NPE from inside WXPermissions
        // rather than on anything they are actually about.
        PluginTestSupport.install(mock(com.wormhole_xtreme.wormhole.WormholeXTreme.class));
        MirrorManager.clear();
        MirrorSettle.clear();
        MirrorNetwork.clear();
        player = mock(Player.class);
        // MirrorSettle keys on the UUID, and an unstubbed mock answers null for it -- which
        // reaches ConcurrentHashMap.get and throws from inside plugin code, in every test that
        // gets as far as travelling. A real Player always has one.
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        // Travel is behind the USE node. Without this the handler refuses on permission and
        // returns before reaching anything below.
        when(player.isOp()).thenReturn(true);
        // Where the hints go. An unstubbed mock answers null here, which the sending code
        // swallows -- so without this a test would read silence off the mock.
        hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    /**
     * What a click says above the hotbar holds the approach line off, so it can be read.
     *
     * <p>"The showing its own room message appears when clicking but is quickly replaced."
     */
    @Test
    void whatAClickSaysAboveTheHotbarHoldsTheApproachLineOff()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        reflecting("museum", banner);

        MirrorInteraction.handle(punch(banner));

        assertTrue(hints().stream().anyMatch(line -> line.contains("own room")), "got " + hints());
        assertTrue(MirrorSignpost.held(player), "the next sweep must not speak over it");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorSignpost.clear();
        MirrorManager.clear();
        MirrorSettle.clear();
        MirrorNetwork.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Block block(final Material type, final int x)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(0);
        return block;
    }

    /** A mirror on this banner storing its own room, which is what a new mirror does. */
    private QuantumMirror reflecting(final String name, final Block banner)
    {
        final QuantumMirror mirror = new QuantumMirror(name, MirrorBlock.of(banner),
            new MirrorPoint("world", banner.getX() + 0.5, 63, 0.5, 180f, 0f));
        MirrorManager.add(mirror);
        return mirror;
    }

    private PlayerInteractEvent click(final Block block)
    {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP);
    }

    private PlayerInteractEvent punch(final Block block)
    {
        return new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, block, BlockFace.UP);
    }

    /** Everything that has landed above the player's hotbar so far, in order. */
    private List<String> hints()
    {
        final ArgumentCaptor<BaseComponent> said = ArgumentCaptor.forClass(BaseComponent.class);
        verify(hotbar, atLeast(0)).sendMessage(eq(ChatMessageType.ACTION_BAR), said.capture());
        final List<String> lines = new ArrayList<>();
        said.getAllValues().forEach(component -> lines.add(component.toPlainText()));
        return lines;
    }

    /** How many hints so far said this. */
    private long hinted(final String text)
    {
        return hints().stream().filter(line -> line.contains(text)).count();
    }

    /**
     * An ordinary block is not a mirror, and finding that out must not touch the world.
     *
     * <p>The whole-server hot path. Asking the registry means building a key, and building a
     * key means {@code getWorld()} -- so the block's type is checked first and a stone block
     * never gets that far. Verifying the absence here is the point: it is the only way to tell
     * "answered no cheaply" from "answered no expensively".
     */
    @Test
    void clickingAnOrdinaryBlockIsNotAMirrorAndDoesNotAskItsWorld()
    {
        final Block stone = block(Material.STONE, 5);

        assertFalse(MirrorInteraction.handle(click(stone)));
        assertFalse(MirrorInteraction.handle(punch(stone)));

        verify(stone, never()).getWorld();
        verify(player, never()).teleport(any(Location.class));
    }

    /** A banner that nobody has made a mirror is still just a banner. */
    @Test
    void punchingABannerThatIsNotAMirrorDoesNothing()
    {
        assertFalse(MirrorInteraction.handle(punch(block(Material.WHITE_WALL_BANNER, 5))));

        verify(player, never()).teleport(any(Location.class));
    }

    /**
     * Both banner families are recognised.
     *
     * <p>A mirror can no longer be made on a post, but one made before that rule still answers
     * its clicks rather than being silently ignored.
     */
    @Test
    void aFreestandingBannerOfAnyColourIsRecognised()
    {
        final Block banner = block(Material.MAGENTA_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(punch(banner)), "a mirror claims its own click");
    }

    /**
     * A mirror with no room says so in chat, and says how to give it one.
     *
     * <p>Only a mirror from before the network is like this; making one stores its room. It is a
     * line to act on, with a command in it, so it is not one that should fade.
     */
    @Test
    void punchingAMirrorWithNoRoomSaysHowToSetItUp()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(punch(banner)), "a mirror claims its own click");
        verify(player, never()).teleport(any(Location.class));
        verify(player, atLeastOnce()).sendMessage(contains("does not open onto anywhere yet"));
        verify(player, atLeastOnce()).sendMessage(contains(
            MirrorText.COMMAND_COLOUR + "/wormhole mirror create " + MirrorText.NAME_COLOUR + "Museum"));
    }

    /**
     * A visitor who could not run that command is not given it.
     *
     * <p>Simple mode on purpose: it is the arrangement where a player may travel but not
     * configure, which is exactly the split being tested.
     */
    @Test
    void aPlayerWhoCannotConfigureIsNotToldToRunCommands()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        when(player.isOp()).thenReturn(false);
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner), null));

        assertTrue(MirrorInteraction.handle(punch(banner)), "a mirror still claims its click");
        verify(player, atLeastOnce()).sendMessage(contains("does not open onto anywhere yet"));
        verify(player, never()).sendMessage(contains("/wormhole mirror"));
    }

    /**
     * Punching a mirror that shows its own room goes nowhere, and says above the hotbar what to do.
     *
     * <p>A reflection is not somewhere to go. Stepping into it would put you back where you
     * stand, which reads as a mirror that does not work.
     */
    @Test
    void punchingAMirrorShowingItsOwnRoomSaysToChooseFirst()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        reflecting("Museum", banner);

        assertTrue(MirrorInteraction.handle(punch(banner)));

        verify(player, never()).teleport(any(Location.class));
        assertEquals(1, hinted("Right-click"), "said above the hotbar: " + hints());
        verify(player, never()).sendMessage(any(String.class));
    }

    /** Right-clicking the only mirror there is says, above the hotbar, that there is nowhere else. */
    @Test
    void rightClickingTheOnlyMirrorSaysThereAreNoOthers()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        reflecting("Museum", banner);

        assertTrue(MirrorInteraction.handle(click(banner)));

        assertEquals(1, hinted("No other mirrors found"), "said above the hotbar: " + hints());
        verify(player, never()).sendMessage(any(String.class));
        verify(player, never()).teleport(any(Location.class));
    }

    /**
     * A right-click moves the mirror on to the next one, and a punch then goes there.
     *
     * <p>Where it lands is the other mirror's room: in front of its banner, not in its wall.
     */
    @Test
    void rightClickingChoosesTheNextMirrorAndPunchingGoesThere()
    {
        final Block here = block(Material.WHITE_WALL_BANNER, 5);
        final Block there = block(Material.WHITE_WALL_BANNER, 9);
        final QuantumMirror museum = reflecting("Museum", here);
        reflecting("Library", there);
        when(player.teleport(any(Location.class))).thenReturn(true);

        assertTrue(MirrorInteraction.handle(click(here)));
        assertEquals(1, hinted("opens onto"), "said above the hotbar: " + hints());
        assertEquals("Library", MirrorNetwork.chosen(museum).name());

        travelTo(here, "world");

        final ArgumentCaptor<Location> landed = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(landed.capture());
        assertEquals(9.5, landed.getValue().getX(), 0.001, "in front of Library's banner");
        assertEquals(63.0, landed.getValue().getY(), 0.001, "level with the bottom of its opening");
    }

    /**
     * A right-click arrives once for each hand, and only the first moves the mirror.
     *
     * <p>Otherwise one press would skip a mirror every time, and with two mirrors it would land
     * straight back where it started.
     */
    @Test
    void aFollowingPetGoesThroughTheMirrorWithItsOwner() throws Exception
    {
        final Block here = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(here),
            new MirrorPoint("museum_world", 9, 64, 0, 0, 0)));
        com.wormhole_xtreme.wormhole.PetTestSupport.standsWhereTeleported(player, new Location(world, 5.5, 64.0, 1.5));
        final org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        com.wormhole_xtreme.wormhole.PluginTestSupport.scheduler(scheduler);
        final org.bukkit.entity.Wolf wolf = mock(org.bukkit.entity.Wolf.class);
        when(wolf.isTamed()).thenReturn(true);
        when(wolf.getOwner()).thenReturn(player);
        when(wolf.getUniqueId()).thenReturn(UUID.randomUUID());
        when(wolf.teleport(any(Location.class))).thenReturn(true);
        when(wolf.getLocation()).thenReturn(new Location(world, 7.5, 64.0, 1.5));
        when(player.getNearbyEntities(org.mockito.ArgumentMatchers.anyDouble(),
            org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
            .thenReturn(List.of(wolf));
        try
        {
            travelTo(here, "museum_world");
            com.wormhole_xtreme.wormhole.PetTestSupport.runEscorts(scheduler);
        }
        finally
        {
            com.wormhole_xtreme.wormhole.PluginTestSupport.scheduler(null);
        }

        final ArgumentCaptor<Location> owner = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(owner.capture());
        verify(wolf).teleport(owner.getValue());
    }

    @Test
    void theOffHandHalfOfARightClickDoesNothing()
    {
        final Block here = block(Material.WHITE_WALL_BANNER, 5);
        final QuantumMirror museum = reflecting("Museum", here);
        reflecting("Library", block(Material.WHITE_WALL_BANNER, 9));

        assertTrue(MirrorInteraction.handle(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            null, here, BlockFace.UP, EquipmentSlot.OFF_HAND)), "the off hand's half is still claimed");

        assertEquals("Museum", MirrorNetwork.chosen(museum).name(), "and changes nothing");
        assertTrue(hints().isEmpty(), "nor says anything: " + hints());
        verify(player, never()).sendMessage(any(String.class));
    }

    /**
     * A mirror whose far side is in an unloaded world names the world, in chat.
     *
     * <p>The most likely way a working mirror stops working: the archive world it opens onto is
     * not started this session.
     */
    @Test
    void punchingAMirrorIntoAnUnloadedWorldNamesTheWorld()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner),
            new MirrorPoint("a_world_nobody_started", 0, 64, 0, 0, 0)));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld("a_world_nobody_started")).thenReturn(null);

            assertTrue(MirrorInteraction.handle(punch(banner)), "the mirror still claims the click");
        }

        verify(player, never()).teleport(any(Location.class));
        verify(player, atLeastOnce()).sendMessage(contains("a_world_nobody_started"));
    }

    /**
     * A trip another plugin cancels is reported in chat, not swallowed.
     *
     * <p>A cancelled {@code PlayerTeleportEvent} leaves the player exactly where they were, and
     * where they were is the mirror they just punched -- which reads as a mirror that did
     * nothing, with nothing to say who had refused or why.
     */
    @Test
    void aMirrorWhoseTeleportAnotherPluginCancelledSaysSo()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner),
            new MirrorPoint("museum_world", 0, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(false);

        travelTo(banner, "museum_world");

        verify(player, atLeastOnce()).sendMessage(contains("would not let you into"));
        verify(player, atLeastOnce()).sendMessage(contains("world-access or land-claim"));
    }

    /** A trip that goes through says nothing at all. */
    @Test
    void aMirrorThatTravelsDoesNotComplainAboutBeingRefused()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner),
            new MirrorPoint("museum_world", 0, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(true);

        travelTo(banner, "museum_world");

        verify(player, atLeastOnce()).teleport(any(Location.class));
        verify(player, never()).sendMessage(contains("would not let you into"));
    }

    /**
     * Punches a mirror with its destination world loaded.
     *
     * <p>{@link MirrorPoint} resolves its world through {@link Bukkit}, so a mirror that actually
     * travels can only be exercised with the static standing in. The world is a bare mock: the
     * safe-location search gets null for every block and falls back to the stored point.
     */
    private void travelTo(final Block banner, final String worldName)
    {
        final World destination = mock(World.class);
        when(destination.getName()).thenReturn(worldName);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(() -> Bukkit.getWorld(worldName)).thenReturn(destination);

            assertTrue(MirrorInteraction.handle(punch(banner)), "the mirror claims its click");
        }
    }

    /**
     * The mirror you arrive at does not send you straight back.
     *
     * <p>A mirror puts the player in front of the destination banner; a punch still being
     * delivered then lands on that one and returns them. Asserting exactly one teleport is what
     * distinguishes "the settle works" from "nothing travels at all".
     */
    @Test
    void theMirrorYouArriveAtDoesNotFireStraightBack()
    {
        final Block here = block(Material.WHITE_WALL_BANNER, 5);
        final Block there = block(Material.WHITE_WALL_BANNER, 9);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(here),
            new MirrorPoint("museum_world", 9, 64, 0, 0, 0)));
        MirrorManager.add(new QuantumMirror("Library", MirrorBlock.of(there),
            new MirrorPoint("museum_world", 5, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(true);

        travelTo(here, "museum_world");
        travelTo(there, "museum_world");

        verify(player, times(1)).teleport(any(Location.class));
        assertEquals(1, hinted("settle for a moment"), "said above the hotbar: " + hints());
    }

    /** The explanation is said once per arrival, not once per repeat. */
    @Test
    void theSettleIsExplainedOnceNotOnEveryRepeat()
    {
        final Block here = block(Material.WHITE_WALL_BANNER, 5);
        final Block there = block(Material.WHITE_WALL_BANNER, 9);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(here),
            new MirrorPoint("museum_world", 9, 64, 0, 0, 0)));
        MirrorManager.add(new QuantumMirror("Library", MirrorBlock.of(there),
            new MirrorPoint("museum_world", 5, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(true);

        travelTo(here, "museum_world");
        travelTo(there, "museum_world");
        travelTo(there, "museum_world");
        travelTo(there, "museum_world");

        assertEquals(1, hinted("settle for a moment"), "once, however many repeats: " + hints());
    }

    /** A trip that never happened does not shut the mirror behind it. */
    @Test
    void aRefusedTripDoesNotStartTheSettle()
    {
        final Block banner = block(Material.WHITE_WALL_BANNER, 5);
        MirrorManager.add(new QuantumMirror("Museum", MirrorBlock.of(banner),
            new MirrorPoint("museum_world", 0, 64, 0, 0, 0)));
        when(player.teleport(any(Location.class))).thenReturn(false);

        travelTo(banner, "museum_world");
        travelTo(banner, "museum_world");

        verify(player, times(2)).teleport(any(Location.class));
        assertEquals(0, hinted("settle for a moment"));
    }

    /** Clicking the air has no block to look up. */
    @Test
    void clickingTheAirIsNotAMirror()
    {
        assertFalse(MirrorInteraction.handle(new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_AIR, null, null, BlockFace.UP)));
    }

    /** Nothing to handle at all. */
    @Test
    void aNullEventIsNotAMirror()
    {
        assertFalse(MirrorInteraction.handle(null));
    }
}
