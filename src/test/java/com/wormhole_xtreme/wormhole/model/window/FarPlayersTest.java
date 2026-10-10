package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSource;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which players in a far room a viewer is shown (#296, step 2).
 *
 * <p>Every refusal here keeps somebody off a screen they should not be on: a staff member in vanish
 * at the destination appearing in a gate, a spectator floating through the wall, the viewer seeing
 * themselves through a mirror, or an NPC plugin's fake player copied as a person.
 */
class FarPlayersTest
{
    private static final Place ARRIVAL = new Place("far", 100.5, 70.0, -20.5, 0.0f, 0.0f);

    private World far;
    private WindowState mirror;
    private WindowState gate;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        StandIns.removeEverything();
        far = mock(World.class);
        when(far.getName()).thenReturn("far");
        final WindowShape shape = WindowShape.of(new BlockPlace("world", 10, 64, 10), BlockFace.NORTH, ARRIVAL);
        final List<Spot> open = List.of(new Spot(10, 63, 11), new Spot(10, 64, 11));
        final Capture capture = new Capture.Builder("far", true, new Capture.Box(60, 54, -61, 81, 81, 81), mock(BlockData.class))
            .build();
        mirror = new WindowState(new MirrorSource(new QuantumMirror("museum", new BlockPlace("world", 10, 64, 10), ARRIVAL),
            mock(Block.class), shape, open, 16), capture);
        gate = new WindowState(new GateSource("gate:Abydos", mock(Block.class), shape, open, ARRIVAL, "Chulak", 16), capture);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        StandIns.removeEverything();
        PluginTestSupport.remove();
    }

    /** A player online, alive, seen by default, on foot and in survival is copied. */
    @Test
    void aPlayerOnlineAliveOnFootAndSeenIsCopied()
    {
        assertTrue(FarPlayers.copied(farPlayer()));
    }

    /**
     * Each thing that hides a player, or makes them not a person to copy, keeps them off: changed one
     * at a time from a player who is copied.
     */
    @Test
    void anNpcAnOfflineDeadInvisibleSpectatingRidingHiddenOrStandInPlayerIsNotCopied()
    {
        final List<Consumer<Player>> hides = List.of(
            player -> when(player.hasMetadata(FarPlayers.NPC)).thenReturn(true),
            player -> when(player.isOnline()).thenReturn(false),
            player -> when(player.isDead()).thenReturn(true),
            player -> when(player.isValid()).thenReturn(false),
            player -> when(player.isInvisible()).thenReturn(true),
            player -> when(player.getGameMode()).thenReturn(GameMode.SPECTATOR),
            player -> when(player.isInsideVehicle()).thenReturn(true),
            player -> when(player.isVisibleByDefault()).thenReturn(false),
            player -> when(player.getScoreboardTags()).thenReturn(Set.of(StandIns.TAG)));
        for (int which = 0; which < hides.size(); which++)
        {
            final Player player = farPlayer();
            hides.get(which).accept(player);
            assertFalse(FarPlayers.copied(player), "refusal " + which + " lets the player through");
        }
        assertFalse(FarPlayers.copied(mock(Zombie.class)), "a mob is not a player");
    }

    /**
     * The viewer is never shown themselves, through a mirror or a gate, while another player standing
     * beside them in the far room is.
     */
    @Test
    void theViewerIsNeverShownThemselvesThroughAMirrorOrAGate()
    {
        final Player viewer = farPlayer();
        final Player other = farPlayer();
        when(viewer.canSee(any(Player.class))).thenReturn(true);
        for (final WindowState window : List.of(mirror, gate))
        {
            assertEquals(CreatureTally.Skip.YOU, FarPlayers.whyNot(viewer, viewer, window), window.name());
            assertNull(FarPlayers.whyNot(viewer, other, window), "another player shows through " + window.name());
            assertFalse(FarPlayers.showsTheViewer(window));
        }
    }

    /** A player the viewer cannot see, as a vanish plugin's hidePlayer leaves them, is not shown. */
    @Test
    void aPlayerTheViewerCannotSeeIsNotShown()
    {
        final Player viewer = farPlayer();
        final Player vanished = farPlayer();
        when(viewer.canSee(vanished)).thenReturn(false);
        final Player seen = farPlayer();
        when(viewer.canSee(seen)).thenReturn(true);

        assertEquals(CreatureTally.Skip.PLAYER_HIDDEN, FarPlayers.whyNot(viewer, vanished, gate));
        assertNull(FarPlayers.whyNot(viewer, seen, gate));
        assertFalse(FarPlayers.stillShown(viewer, vanished, gate));
        assertTrue(FarPlayers.stillShown(viewer, seen, gate));
    }

    /**
     * Players are read from the far room only with {@code mirror-show-players} on; with it off one is
     * counted with the other creatures not copied, and never asked anything a player is asked.
     */
    @Test
    void playersAreReadFromTheFarRoomOnlyWhenShown()
    {
        final Chunk chunk = mock(Chunk.class);
        when(chunk.isEntitiesLoaded()).thenReturn(true);
        when(far.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(far.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        final Player player = farPlayer();
        when(far.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of(player));
        final int[] box = { 96, 66, -24, 104, 75, -18 };

        final int[] tally = new int[3];
        assertEquals(List.of(), FarCreatures.inRoom(far, box, tally, false), "off: no player");
        assertEquals(1, tally[2], "counted as not copied");
        verify(player, never()).isOnline();

        assertEquals(List.of(player), FarCreatures.inRoom(far, box, new int[3], true), "on: the player");

        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertEquals(List.of(), FarCreatures.inRoom(far, box, new int[3], true), "on, but a spectator is still not read");
    }

    /**
     * Players and mobs share one cap of twenty, nearest the eye first: a player nearer than twenty
     * zombies is shown in place of the furthest of them, and one further off is not shown at all.
     */
    @Test
    void playersShareTheCapOfTwentyWithMobsNearestFirst()
    {
        final Location eye = new Location(null, 0.0, 0.0, 0.0);
        final List<FarCreatures.Wanted> candidates = new ArrayList<>();
        for (int i = 1; i <= FarCreatures.MOST_PER_VIEWER; i++)
        {
            candidates.add(wantedAt(mock(Zombie.class), i));
        }
        final Player near = farPlayer();
        candidates.add(wantedAt(near, 0.5));
        final Player away = farPlayer();
        candidates.add(wantedAt(away, 100.0));

        final List<FarCreatures.Wanted> chosen = FarCreatures.choose(candidates, eye, FarCreatures.MOST_PER_VIEWER,
            wanted -> true);

        assertEquals(FarCreatures.MOST_PER_VIEWER, chosen.size());
        assertEquals(near, chosen.get(0).original(), "the nearest first, a player among the mobs");
        assertTrue(chosen.stream().noneMatch(wanted -> wanted.original() == away), "the furthest is past the cap");
    }

    /** Off when the setting is missing, as on an upgraded server's config.yml; on only when set. */
    @Test
    void showingPlayersIsOffUnlessSet()
    {
        ConfigTestSupport.clear();
        assertFalse(ConfigManager.isMirrorShowPlayers(), "missing");
        ConfigTestSupport.loadDefaults();
        assertFalse(ConfigManager.isMirrorShowPlayers(), "the default");
        ConfigTestSupport.set(ConfigKeys.MIRROR_SHOW_PLAYERS, true);
        assertTrue(ConfigManager.isMirrorShowPlayers());
    }

    /** {@code mirror debug} says how many of those found were players, and why a player was not shown. */
    @Test
    void theDebugLineCountsPlayersAndSaysWhyOneWasNotShown()
    {
        final CreatureTally tally = new CreatureTally();
        tally.found = 3;
        tally.players = 2;
        tally.shown = 1;
        tally.skip(CreatureTally.Skip.YOU);
        tally.skip(CreatureTally.Skip.PLAYER_HIDDEN);

        assertEquals("3 found (2 player(s)), 1 you, 1 player(s) hidden from you; 1 shown", tally.line(gate));
    }

    private FarCreatures.Wanted wantedAt(final Entity original, final double apart)
    {
        when(original.getUniqueId()).thenReturn(UUID.randomUUID());
        return new FarCreatures.Wanted(original, new Location(null, apart, 0.0, 0.0), gate);
    }

    /** A player standing in the far room, as a server hands one over. */
    private Player farPlayer()
    {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        when(player.isVisibleByDefault()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(new Location(far, 100.5, 70.0, -20.5));
        return player;
    }
}
