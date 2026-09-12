package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * The line a mirror shows to whoever is looking at it.
 *
 * <p>This replaced a line sent once, on crossing into the proximity radius, and the two tests
 * that matter most here are the ones that pin down why. An action bar entry fades after about
 * three seconds, so a message sent on arrival is gone by the time somebody is stood in front of
 * the banner deciding whether to click it -- the one moment it is worth having. So the line is
 * re-sent every sweep for as long as the player keeps looking, which is the opposite of what
 * the crossing version did and is asserted as such.
 *
 * <p>The other half is that it must ask the <em>player</em> what they are looking at rather
 * than asking every mirror who is near it. A corridor puts somebody within eight blocks of
 * several mirrors at once; each would win the action bar in turn and the result would flicker.
 * Nothing here can prove the absence of flicker directly, but the cost test below pins the
 * shape that prevents it: the pass never walks the mirrors looking for players.
 */
class MirrorSignpostTest
{
    /** Where saves go, so no test writes a mirror file into the repository. */
    @TempDir
    File dataFolder;

    private World world;
    private Player player;
    private Player.Spigot hotbar;
    private Block banner;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        banner = mock(Block.class);
        when(banner.getType()).thenReturn(Material.WHITE_WALL_BANNER);
        when(banner.getWorld()).thenReturn(world);
        when(banner.getX()).thenReturn(10);
        when(banner.getY()).thenReturn(64);
        when(banner.getZ()).thenReturn(10);

        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        // Where the line actually goes. An unstubbed mock answers null here, which the sending
        // code catches and swallows -- so without this a test would read silence off the mock
        // and call it silence from the code.
        hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /**
     * Looking at a mirror says what it is and where it goes.
     *
     * <p>The far world especially: the banner is showing what the destination looks like, so
     * the one thing a player standing in front of it cannot work out is which world that is.
     */
    @Test
    void lookingAtAMirrorNamesItAndWhereItGoes()
    {
        boundMirror();
        lookingAt(banner);

        sweep();

        final List<String> said = shown();
        assertEquals(1, said.size(), "one look, one line: " + said);
        assertTrue(said.get(0).contains("museum"), "it should name the mirror: " + said);
        assertTrue(said.get(0).contains("nether"), "and the world it opens onto: " + said);
    }

    /**
     * It keeps saying it while you keep looking.
     *
     * <p>The whole reason this exists. The crossing version said it once and let it fade, so
     * the message was reliably absent at the moment of deciding to click. Three sweeps with
     * the player still looking must produce three lines, not one.
     */
    @Test
    void itKeepsSayingItForAsLongAsYouKeepLooking()
    {
        boundMirror();
        lookingAt(banner);

        sweep();
        sweep();
        sweep();

        assertEquals(3, shown().size(),
            "an action bar line fades, so staying on screen means being re-sent");
    }

    /** Look away and it stops, rather than following you round. */
    @Test
    void lookingAwaySaysNothing()
    {
        boundMirror();
        lookingAt(null);

        sweep();

        assertTrue(shown().isEmpty(), "nothing is being looked at, so there is nothing to name");
    }

    /** An ordinary banner somebody hung for decoration is not a mirror and says nothing. */
    @Test
    void lookingAtABannerThatIsNotAMirrorSaysNothing()
    {
        lookingAt(banner);

        sweep();

        assertTrue(shown().isEmpty(), "an unbound banner is just a banner");
    }

    /**
     * A mirror that goes nowhere keeps quiet.
     *
     * <p>Announcing one would be the plugin telling whoever glanced at a half-built banner
     * about somebody else's unfinished work. Clicking it already says what to do, to the one
     * person who asked.
     */
    @Test
    void aMirrorThatGoesNowhereSaysNothing()
    {
        // A second, working mirror in the same world, and it is load-bearing. Without one the
        // world never enters the set this pass walks, so the sweep stops before it looks at
        // anybody -- and the test would pass without the unpointed case ever being reached.
        // A mutation removing the destination check survived exactly that way.
        MirrorManager.add(new QuantumMirror("working", new MirrorBlock("world", 99, 64, 99),
            new MirrorPoint("nether", 0, 64, 0, 0f, 0f)));
        MirrorManager.add(new QuantumMirror("museum", MirrorBlock.of(banner), null));
        lookingAt(banner);

        sweep();

        assertTrue(shown().isEmpty(), "an unpointed mirror has nothing to announce");
    }

    /**
     * Looking at an ordinary block costs nothing beyond noticing it is not a banner.
     *
     * <p>Every player who is in a world with a mirror goes through here every sweep, whatever
     * they happen to be looking at -- which is usually stone. Building a lookup key means
     * asking the block for its world, so the type is checked first and a stone block never
     * gets that far. Verifying the absence is the point: it is the only way to tell "answered
     * no cheaply" from "answered no after hashing a key". The same guard, and the same test,
     * as the click path in {@code MirrorInteractionTest}.
     */
    @Test
    void lookingAtAnOrdinaryBlockDoesNotEvenBuildAKey()
    {
        boundMirror();
        final Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        lookingAt(stone);

        sweep();

        assertTrue(shown().isEmpty(), "stone is not a mirror");
        verify(stone, never()).getWorld();
    }

    /** Turned off is turned off, and it does not even look. */
    @Test
    void turningTheSettingOffStopsItBeforeItLooksAtAnybody()
    {
        ConfigTestSupport.set(ConfigManager.ConfigKeys.MIRROR_APPROACH_MESSAGE, false);
        boundMirror();
        lookingAt(banner);

        sweep();

        assertTrue(shown().isEmpty(), "switched off means switched off");
        verify(player, never()).getTargetBlockExact(anyInt());
    }

    /**
     * A player in a world with no mirrors is never asked what they are looking at.
     *
     * <p>The guard that keeps this affordable on a server where the mirrors live in one world
     * and most of the players do not. Asserting the absence of the call is the only way to
     * tell "answered no cheaply" from "answered no after a ray trace per player per second".
     */
    @Test
    void aPlayerInAWorldWithNoMirrorsIsNotEvenAsked()
    {
        boundMirror();
        final World elsewhere = mock(World.class);
        when(elsewhere.getName()).thenReturn("somewhere_else");
        when(player.getWorld()).thenReturn(elsewhere);

        sweep();

        verify(player, never()).getTargetBlockExact(anyInt());
    }

    /**
     * The pass never walks the mirrors looking for who is near them.
     *
     * <p>This is the shape that stops a corridor flickering, and the reason the change made the
     * plugin cheaper rather than dearer. The old version asked every mirror who was within
     * range of it, which is a distance check per player per mirror; this asks each player one
     * question whatever the mirror count. If it ever starts measuring distances again, the
     * corridor problem comes back with it.
     */
    @Test
    void itAsksThePlayerRatherThanMeasuringFromEveryMirror()
    {
        boundMirror();
        lookingAt(banner);

        sweep();

        verify(world, never()).getPlayers();
        verify(player, never()).getLocation();
    }

    /** A mirror on the banner block, pointing at another world. */
    private void boundMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", MirrorBlock.of(banner),
            new MirrorPoint("nether", 0, 64, 0, 0f, 0f)));
    }

    /** What the player's crosshair is on, or null for thin air. */
    private void lookingAt(final Block block)
    {
        when(player.getTargetBlockExact(anyInt())).thenReturn(block);
    }

    /** One pass, with a server that knows who is online. */
    private void sweep()
    {
        try (final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class))
        {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            MirrorSignpost.tick();
        }
    }

    /** Everything that has landed on the player's action bar so far, in order. */
    private List<String> shown()
    {
        final ArgumentCaptor<BaseComponent> said = ArgumentCaptor.forClass(BaseComponent.class);
        verify(hotbar, atLeast(0)).sendMessage(eq(ChatMessageType.ACTION_BAR), said.capture());
        final List<String> lines = new ArrayList<>();
        said.getAllValues().forEach(component -> lines.add(component.toPlainText()));
        return lines;
    }
}
