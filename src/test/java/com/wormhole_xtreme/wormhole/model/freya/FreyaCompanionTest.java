package com.wormhole_xtreme.wormhole.model.freya;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * One companion per player, and nothing left behind when they go.
 *
 * <p>This is the whole defence against a hidden command becoming a way to fill a world with
 * cats. {@code /wormhole freya} is open to every player by default and takes no cooldown, so
 * the only thing standing between it and a thousand entities is that spawning replaces rather
 * than adds. Written as {@code LIVE.put} without the {@code removeFor} above it, the command
 * would still look correct run once, and every test of the toggle would still pass.
 *
 * <p>The quit path matters for the same reason from the other end. She is spawned per session
 * and marked not to persist, so a player who leaves without her being removed leaves a cat in
 * the world that nobody owns and -- because hiding is per-observer -- nobody can see.
 */
class FreyaCompanionTest
{
    private static final UUID OWNER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private World world;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final Server server = mock(Server.class);
        doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
        when(plugin.getServer()).thenReturn(server);
        PluginTestSupport.install(plugin);
        world = mock(World.class);
        FreyaCompanion.forgetAll();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        PluginTestSupport.remove();
    }

    /**
     * A player standing somewhere, whose world will hand back the given cat.
     *
     * @param id
     *            their player id
     * @param cat
     *            what the world should spawn for them
     * @return the mock player
     */
    private Player playerWith(final UUID id, final Cat cat)
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 1.0));
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(cat);
        return player;
    }

    @Test
    void askingTwiceMovesTheSameCatRatherThanSpawningASecond()
    {
        final Cat first = mock(Cat.class);
        final Player player = playerWith(OWNER, first);

        FreyaCompanion.spawnFor(player);
        assertEquals(1, FreyaCompanion.liveCount(), "one ask, one cat");

        final Cat second = mock(Cat.class);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(second);
        FreyaCompanion.spawnFor(player);

        assertEquals(1, FreyaCompanion.liveCount(),
            "a player has one companion however many times they ask -- this is the only thing "
                + "stopping an open, hidden, cooldown-free command from filling a world");
        verify(first).remove();
    }

    @Test
    void twoPlayersEachGetTheirOwn()
    {
        final Player one = playerWith(OWNER, mock(Cat.class));
        FreyaCompanion.spawnFor(one);
        final Player two = playerWith(OTHER, mock(Cat.class));
        FreyaCompanion.spawnFor(two);

        assertEquals(2, FreyaCompanion.liveCount(), "one each, not one between them");
    }

    @Test
    void sheIsMarkedNotToPersistSoNoWorldEverStoresHer()
    {
        final Cat cat = mock(Cat.class);
        FreyaCompanion.spawnFor(playerWith(OWNER, cat));

        verify(cat).setPersistent(false);
        verify(cat).setInvulnerable(true);
        verify(cat).setCollidable(false);
        // Sound is positional and reaches everyone. An unsilenced cat that only one player
        // can see is a cat the rest of the server can hear, which gives the whole thing away.
        verify(cat).setSilent(true);
        verify(cat).setTamed(true);
        // The following is vanilla tamed-cat AI, so turning AI off would break the one
        // behaviour worth having.
        verify(cat, never()).setAI(false);
    }

    @Test
    void leavingTakesHerWithThem()
    {
        final Cat cat = mock(Cat.class);
        FreyaCompanion.spawnFor(playerWith(OWNER, cat));

        assertTrue(FreyaCompanion.removeFor(OWNER), "there was one to remove");
        verify(cat, times(1)).remove();
        assertEquals(0, FreyaCompanion.liveCount(), "and nothing is still tracked");
        assertFalse(FreyaCompanion.removeFor(OWNER), "removing again is not an error");
    }

    @Test
    void aPlayerWithNowhereToStandGetsNoCatRatherThanAnException()
    {
        final Player nowhere = mock(Player.class);
        when(nowhere.getUniqueId()).thenReturn(OWNER);
        when(nowhere.getLocation()).thenReturn(null);

        assertNull(FreyaCompanion.spawnFor(nowhere),
            "a cosmetic command must fail quietly, not throw out of a join handler");
        assertEquals(0, FreyaCompanion.liveCount(), "and nothing is tracked for them");
    }

    @Test
    void onlyTheTrackedCatsCountAsCompanions()
    {
        final Cat hers = mock(Cat.class);
        FreyaCompanion.spawnFor(playerWith(OWNER, hers));
        final Cat someoneElsesPet = mock(Cat.class);

        assertTrue(FreyaCompanion.isCompanion(hers), "the one we spawned is ours");
        assertFalse(FreyaCompanion.isCompanion(someoneElsesPet),
            "a player's own black cat they happened to call Freya is still their cat, and "
                + "must stay pettable and killable like any other");
    }

    @Test
    void shuttingDownTakesEveryCompanionAway()
    {
        final Cat one = mock(Cat.class);
        FreyaCompanion.spawnFor(playerWith(OWNER, one));
        final Cat two = mock(Cat.class);
        FreyaCompanion.spawnFor(playerWith(OTHER, two));

        FreyaCompanion.removeAll();

        assertEquals(0, FreyaCompanion.liveCount(), "nothing tracked after a shutdown");
        verify(one).remove();
        verify(two).remove();
    }
}
