package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorPoint;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * Binding a banner, pointing it, and the rules that refuse.
 *
 * <p>The command layer is where a mistake is most visible to an admin and least visible to the
 * rest of the suite: nothing else calls these methods, so an error here compiles, passes every
 * other test, and only shows up as a banner that does not work.
 *
 * <p>The refusals get as much attention as the successes. A mirror that quietly does the wrong
 * thing is worse than one that says why it will not -- and the cross-world rule in particular
 * is a deliberate restriction, which means a server owner will meet it while trying to do
 * something reasonable and needs to be told which setting relaxes it.
 */
class MirrorCommandTest
{
    /**
     * Where the command's saves go.
     *
     * <p>Every verb that changes a mirror calls saveAll, so without this the plugin mock's
     * unstubbed data folder sends the file somewhere relative -- which meant these tests were
     * writing a real data/mirror.yml into whatever directory the suite was run from.
     */
    @TempDir
    File dataFolder;

    private Player player;
    private World here;
    private Location standing;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        MirrorManager.clear();

        here = mock(World.class);
        when(here.getName()).thenReturn("world");
        standing = new Location(here, 10.0, 64.0, 10.0, 0.0f, 0.0f);

        player = mock(Player.class);
        when(player.getLocation()).thenReturn(standing);
        // Every verb is behind the config node, so without this the command refuses before
        // reaching any of them and every test below asserts on a permission message instead
        // of on what it is actually about.
        when(player.isOp()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private Block banner(final Material type)
    {
        final Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.getWorld()).thenReturn(here);
        when(block.getX()).thenReturn(1);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(1);
        return block;
    }

    private static boolean run(final CommandSender sender, final String... args)
    {
        return new MirrorCommand().execute(sender, args);
    }

    /** Naming the banner you are looking at is the first half of binding one. */
    @Test
    void setNamesTheBannerThePlayerIsLookingAt()
    {
        final Block wallBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(wallBanner);

        assertTrue(run(player, "mirror", "set", "museum"));

        final QuantumMirror mirror = MirrorManager.byName("museum");
        assertNotNull(mirror, "the mirror should exist after set");
        assertEquals(new MirrorBlock("world", 1, 64, 1), mirror.banner());
        assertNull(mirror.destination(), "set names a banner; it does not point it anywhere");
    }

    /**
     * Pointing a banner at a block that is not one refuses, and says what it is instead.
     *
     * <p>"That did not work" would leave an admin looking for the wrong problem -- most likely
     * assuming they were out of range rather than off-target.
     */
    @Test
    void setRefusesABlockThatIsNotABanner()
    {
        final Block notABanner = banner(Material.STONE);
        when(player.getTargetBlockExact(6)).thenReturn(notABanner);

        run(player, "mirror", "set", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("not a banner"));
    }

    /** Looking at nothing is its own message, since the fix is different. */
    @Test
    void setRefusesWhenLookingAtNothing()
    {
        when(player.getTargetBlockExact(6)).thenReturn(null);

        run(player, "mirror", "set", "museum");

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("within six blocks"));
    }

    /** The second half: where you stand becomes where arrivals land. */
    @Test
    void targetPointsAMirrorAtWhereThePlayerStands()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("snapshot", 0, 64, 0), null));

        assertTrue(run(player, "mirror", "target", "museum"));

        final MirrorPoint destination = MirrorManager.byName("museum").destination();
        assertNotNull(destination);
        assertEquals("world", destination.worldName());
        assertEquals(10.0, destination.x());
    }

    /**
     * A mirror whose two ends share a world is refused, and the message names the setting.
     *
     * <p>This is the rule an admin is most likely to hit while doing something perfectly
     * reasonable -- two points in one world is what a beam place is for -- so the refusal has
     * to say both which worlds clashed and what to change if they meant it.
     */
    @Test
    void targetRefusesWhenBothEndsAreInOneWorld()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "target", "museum");

        assertNull(MirrorManager.byName("museum").destination(),
            "the refusal has to leave the mirror as it was");
        verify(player, atLeastOnce()).sendMessage(contains("mirror-allow-same-world"));
    }

    /** And is allowed once an admin turns the setting on. */
    @Test
    void targetAllowsOneWorldWhenTheSettingSaysSo()
    {
        ConfigTestSupport.set(
            com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys.MIRROR_ALLOW_SAME_WORLD,
            true);
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "target", "museum");

        assertNotNull(MirrorManager.byName("museum").destination(),
            "the setting exists precisely so this case can be allowed");
    }

    /** Naming a mirror that does not exist says so rather than creating one. */
    @Test
    void targetRefusesAnUnknownName()
    {
        run(player, "mirror", "target", "nothing-by-that-name");

        verify(player, atLeastOnce()).sendMessage(contains("no mirror called"));
    }

    /** A mirror cannot open onto itself; the arrival would be where you already are. */
    @Test
    void linkRefusesAMirrorPointedAtItself()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "link", "museum", "MUSEUM");

        verify(player, atLeastOnce()).sendMessage(contains("cannot open onto itself"));
    }

    /** Removing gives the banner back. */
    @Test
    void removeForgetsTheMirror()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        assertTrue(run(player, "mirror", "remove", "museum"));

        assertNull(MirrorManager.byName("museum"));
        verify(player, atLeastOnce()).sendMessage(contains("ordinary banner again"));
    }

    @Test
    void removeSaysSoWhenThereIsNothingToRemove()
    {
        run(player, "mirror", "remove", "museum");

        verify(player, atLeastOnce()).sendMessage(contains("no mirror called"));
    }

    /** An empty list says how to make one rather than printing nothing. */
    @Test
    void listOnAServerWithNoMirrorsExplainsHowToMakeOne()
    {
        assertTrue(run(player, "mirror", "list"));

        verify(player, atLeastOnce()).sendMessage(contains("No mirrors yet"));
    }

    /** A mirror that has not been pointed lists as such rather than being hidden. */
    @Test
    void listShowsAMirrorThatGoesNowhereYet()
    {
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 0, 64, 0), null));

        run(player, "mirror", "list");

        verify(player, atLeastOnce()).sendMessage(contains("nowhere yet"));
    }

    /**
     * set and target need a player, because both depend on where somebody is.
     *
     * <p>Run from the console they would have no banner to look at and nowhere to stand, so
     * they say that rather than throwing a ClassCastException into the server log.
     */
    @Test
    void theVerbsThatNeedAPlayerSayWhenRunFromTheConsole()
    {
        final CommandSender console = mock(CommandSender.class);

        assertTrue(run(console, "mirror", "set", "museum"));
        assertTrue(run(console, "mirror", "target", "museum"));

        verify(console, atLeastOnce()).sendMessage(contains("has to be run in game"));
    }

    /** An unknown verb, and no verb at all, both get the usage. */
    @Test
    void anUnknownVerbGetsTheUsage()
    {
        assertTrue(run(player, "mirror", "frobnicate"));
        assertTrue(run(player, "mirror"));

        verify(player, atLeastOnce()).sendMessage(contains("Usage: /wormhole mirror"));
    }

    /** A verb that needs a name and was not given one says which form it wanted. */
    @Test
    void aVerbMissingItsNameGetsThatVerbsUsage()
    {
        final Block anyBanner = banner(Material.WHITE_WALL_BANNER);
        when(player.getTargetBlockExact(6)).thenReturn(anyBanner);

        assertTrue(run(player, "mirror", "set"));

        verify(player, atLeastOnce()).sendMessage(contains("set <name>"));
    }
}
