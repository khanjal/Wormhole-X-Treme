package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.BlockPlace;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.SignLines;

/**
 * A sign beside a mirror's banner shows the previous, current and next mirror, and a right-click
 * on it moves the mirror on (#280).
 *
 * <p>One sign is read by everybody at once, so it walks the public list only: a private mirror's
 * name written on a wall would be told to every passer-by, which is what private is for not doing.
 */
class MirrorChoosingSignTest
{
    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        MirrorNetwork.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorSignpost.clear();
        MirrorNetwork.clear();
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** A mirror storing its own room, its banner a few blocks along from the last. */
    private static QuantumMirror mirror(final String name, final int x, final MirrorAccess access)
    {
        final QuantumMirror mirror = new QuantumMirror(name, new BlockPlace("world", x, 64, 0),
            new Place("world", x + 0.5, 63, 0.5, 180f, 0f)).withAccess(access);
        MirrorManager.add(mirror);
        return mirror;
    }

    private static QuantumMirror mirror(final String name, final int x)
    {
        return mirror(name, x, MirrorAccess.OPEN);
    }

    /** Somebody who may see private mirrors. */
    private static Player holder()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(MirrorNetwork.SEE_PRIVATE)).thenReturn(true);
        when(player.isOp()).thenReturn(true);
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        return player;
    }

    private Block block(final Material type, final int x, final int y)
    {
        // Made first: a stubbing helper inside another stub's thenReturn throws UnfinishedStubbing.
        final Block stone = block(Material.STONE);
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(0);
        when(block.getRelative(any(BlockFace.class))).thenReturn(stone);
        return block;
    }

    private static Block block(final Material type)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        return block;
    }

    /** A wall sign under a banner, its front's first line written, its front kept for reading back. */
    private SignSide signUnder(final Block banner, final String firstLine, final Block[] made)
        throws ReflectiveOperationException
    {
        final Block sign = block(Material.OAK_WALL_SIGN, banner.getX(), banner.getY() - 1);
        when(sign.getRelative(BlockFace.UP)).thenReturn(banner);
        final Sign state = mock(Sign.class);
        final SignSide front = mock(SignSide.class);
        when(state.getSide(Side.FRONT)).thenReturn(front);
        when(sign.getState()).thenReturn(state);
        SignLines.holdLine(front, 0, firstLine);
        made[0] = sign;
        return front;
    }

    /** Before anybody chooses, the mirror shows its own room and the first click goes to the first mirror. */
    @Test
    void beforeAnythingIsChosenTheSignShowsItsOwnRoomWithTheFirstMirrorNext()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);
        mirror("nether", 8);

        assertArrayEquals(new String[] {"-hub-", "", "Its own room", "farm"}, MirrorChoosingSign.lines(hub));
    }

    /** Once chosen, the sign reads like a dial sign: the one before, the one chosen, the one after, round. */
    @Test
    void aChosenMirrorIsShownBetweenTheOnesBeforeAndAfterItRoundTheList()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);
        mirror("nether", 8);
        mirror("tower", 12);

        MirrorNetwork.scroll(hub, false);

        assertArrayEquals(new String[] {"-hub-", "tower", "»farm«", "nether"}, MirrorChoosingSign.lines(hub));
    }

    /** With only one other, there is nothing either side of it to show. */
    @Test
    void theOnlyOtherMirrorIsShownAloneWithoutNeighbours()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);

        MirrorNetwork.scroll(hub, false);

        assertArrayEquals(new String[] {"-hub-", "", "»farm«", ""}, MirrorChoosingSign.lines(hub));
    }

    /** The only mirror has nothing to choose. */
    @Test
    void theOnlyMirrorsSignSaysThereAreNoOthers()
    {
        final QuantumMirror hub = mirror("hub", 0);

        assertArrayEquals(new String[] {"-hub-", "", "No other mirrors", ""}, MirrorChoosingSign.lines(hub));
    }

    /**
     * A sign never names a private, hidden or other group's mirror, even after somebody allowed a
     * private one turned the banner to it.
     */
    @Test
    void theSignNamesOnlyThePublicListAndBlanksAPrivateChoiceMadeAtTheBanner()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);
        mirror("attic", 8, MirrorAccess.OPEN.withHidden(true));
        mirror("gallery", 12, MirrorAccess.OPEN.withGroup("museum"));
        mirror("aaa-vault", 16, MirrorAccess.OPEN.withRestricted(true));

        // The holder's own list is aaa-vault then farm, so their first right-click opens the vault.
        MirrorNetwork.scroll(hub, List.of(), holder());
        assertEquals("aaa-vault", MirrorNetwork.chosen(hub).name());

        assertArrayEquals(new String[] {"-hub-", "", "", "farm"}, MirrorChoosingSign.lines(hub));
    }

    /** A click on the sign walks the public list even for somebody whose own right-click would list more. */
    @Test
    void clickingTheSignSkipsAPrivateMirrorEvenForSomebodyAllowedIt()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);
        mirror("aaa-vault", 8, MirrorAccess.OPEN.withRestricted(true));

        final String said = MirrorNetwork.scrollPublic(hub, List.of(), holder());

        assertEquals("farm", MirrorNetwork.chosen(hub).name());
        assertTrue(said.contains("(1 of 1)"), "got " + said);
    }

    /** The sweep redraws a sign only when walking away actually turned a mirror back to its own room. */
    @Test
    void settlingSaysWhetherItDroppedAChoice()
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);

        assertFalse(MirrorNetwork.settle(hub, false), "nothing was chosen");
        MirrorNetwork.scroll(hub, false);
        assertFalse(MirrorNetwork.settle(hub, true), "somebody is still at it");
        assertTrue(MirrorNetwork.settle(hub, false));
        assertSame(hub, MirrorNetwork.chosen(hub));
    }

    /** {@code [mirror]}, or its own name as drawn, makes a sign the mirror's; anything else is somebody's notice. */
    @Test
    void aSignIsTheMirrorsOnlyIfWrittenForIt()
    {
        final QuantumMirror hub = mirror("hub", 0);

        assertTrue(MirrorChoosingSign.marks("[Mirror]", hub));
        assertTrue(MirrorChoosingSign.marks("§3-hub-", hub), "the line as the plugin draws it");
        assertFalse(MirrorChoosingSign.marks("Welcome", hub));
        assertFalse(MirrorChoosingSign.marks("-farm-", hub));
    }

    /** A sign written for the mirror and touching its banner is found from the click on it. */
    @Test
    void aMarkedSignUnderABannerIsThatMirrorsAndAnUnmarkedOneIsNot() throws ReflectiveOperationException
    {
        final QuantumMirror hub = mirror("hub", 0);
        final Block banner = block(Material.WHITE_WALL_BANNER, 0, 64);
        final Block[] sign = new Block[1];

        signUnder(banner, "[mirror]", sign);
        assertSame(hub, MirrorChoosingSign.mirrorFor(sign[0]));

        signUnder(banner, "Welcome", sign);
        assertNull(MirrorChoosingSign.mirrorFor(sign[0]));
    }

    /** Clicking anything but a sign costs no look at its neighbours. */
    @Test
    void aClickOnSomethingOtherThanASignDoesNotLookAround()
    {
        mirror("hub", 0);
        final Block stone = block(Material.STONE, 0, 63);

        assertNull(MirrorChoosingSign.mirrorFor(stone));

        verify(stone, never()).getRelative(any(BlockFace.class));
    }

    /**
     * A right-click on the sign moves the mirror on and draws the sign; a punch is left to break it.
     */
    @Test
    void rightClickingTheSignMovesTheMirrorOnAndDrawsItButAPunchIsLeftAlone() throws ReflectiveOperationException
    {
        final QuantumMirror hub = mirror("hub", 0);
        mirror("farm", 4);
        mirror("nether", 8);
        final Block banner = block(Material.WHITE_WALL_BANNER, 0, 64);
        final Block[] sign = new Block[1];
        final SignSide front = signUnder(banner, "[mirror]", sign);
        final Block air = block(Material.AIR);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(0, 64, 0)).thenReturn(banner);
        when(world.getBlockAt(0, 63, 0)).thenReturn(sign[0]);
        final Player player = holder();

        assertFalse(MirrorInteraction.handle(
            new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, null, sign[0], BlockFace.NORTH)));
        assertSame(hub, MirrorNetwork.chosen(hub), "a punch chose nothing");

        assertTrue(MirrorInteraction.handle(
            new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, sign[0], BlockFace.NORTH)));

        assertEquals("farm", MirrorNetwork.chosen(hub).name());
        assertEquals(Arrays.asList("-hub-", "nether", "»farm«", "nether"),
            Arrays.stream(SignLines.writtenTo(front)).limit(4).map(ChatText::plain).toList());
    }
}
