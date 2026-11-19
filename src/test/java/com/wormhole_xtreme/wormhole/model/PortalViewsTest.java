package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.window.Capture;
import com.wormhole_xtreme.wormhole.model.window.Place;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;
import com.wormhole_xtreme.wormhole.model.window.WindowSource;

/**
 * Nether portals learning where they go from real trips, and offering the one a player looks into
 * (#514). Get the turn wrong and the far side is shown facing the wrong way; predict a link and a
 * portal shows somewhere nobody can get to; read a block that is not loaded and the view loads a
 * chunk. Mockito seams stand in for the world, the captures and the sweep.
 */
class PortalViewsTest
{
    private static final UUID TRAVELLER = UUID.randomUUID();

    /** Portal blocks by world, then place. */
    private final Map<String, Map<Spot, Axis>> blocks = new HashMap<>();

    private final List<WindowSource> offered = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();
    private final List<String> released = new ArrayList<>();

    private World overworld;
    private World nether;
    private Capture capture;

    private Function<World, NetherPortal.Cells> cellsWas;
    private Function<String, World> worldWas;
    private Function<String, Capture> captureWas;
    private PortalViews.Requester requesterWas;
    private BiConsumer<WindowSource, Capture> offererWas;
    private Consumer<String> releaserWas;

    @BeforeEach
    void setUp()
    {
        PortalViews.clear();
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEWS, true);
        overworld = worldNamed("world");
        nether = worldNamed("World_Nether");
        capture = mock(Capture.class, withSettings().stubOnly());
        cellsWas = PortalViews.cellsIn;
        worldWas = PortalViews.worldNamed;
        captureWas = PortalViews.captureOf;
        requesterWas = PortalViews.requester;
        offererWas = PortalViews.offerer;
        releaserWas = PortalViews.releaser;
        PortalViews.cellsIn = world -> (x, y, z) -> blocks.getOrDefault(world.getName(), Map.of()).get(new Spot(x, y, z));
        PortalViews.worldNamed = name -> "world".equals(name) ? overworld : ("World_Nether".equals(name) ? nether : null);
        PortalViews.captureOf = key -> capture;
        PortalViews.requester = (key, what, arrival, depth, older) ->
        {
            asked.add(key);
            return true;
        };
        PortalViews.offerer = (source, held) -> offered.add(source);
        PortalViews.releaser = released::add;
        // A two by three portal along x in the overworld, at z = 10.
        light("world", Axis.X, 0, 64, 10, 2, 3);
    }

    @AfterEach
    void tearDown()
    {
        PortalViews.cellsIn = cellsWas;
        PortalViews.worldNamed = worldWas;
        PortalViews.captureOf = captureWas;
        PortalViews.requester = requesterWas;
        PortalViews.offerer = offererWas;
        PortalViews.releaser = releaserWas;
        PortalViews.clear();
        ConfigTestSupport.clear();
    }

    private static World worldNamed(final String name)
    {
        final World world = mock(World.class, withSettings().stubOnly());
        when(world.getName()).thenReturn(name);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        final Block block = mock(Block.class, withSettings().stubOnly());
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        return world;
    }

    /** Lights a rectangle of portal blocks: along x in the plane z = plane, or along z in x = plane. */
    private void light(final String world, final Axis axis, final int low, final int y0, final int plane,
        final int width, final int height)
    {
        final Map<Spot, Axis> in = blocks.computeIfAbsent(world, name -> new HashMap<>());
        for (int across = low; across < (low + width); across++)
        {
            for (int y = y0; y < (y0 + height); y++)
            {
                in.put((axis == Axis.X) ? new Spot(across, y, plane) : new Spot(plane, y, across), axis);
            }
        }
    }

    /** One trip: into the overworld portal looking north, out at a nether spot with a yaw. */
    private void travel(final double x, final double y, final double z, final float landedYaw)
    {
        PortalViews.leaving(TRAVELLER, new Location(overworld, 0.5, 64.0, 10.5, 180.0f, 0.0f), 1_000L);
        PortalViews.arrived(TRAVELLER, new Location(nether, x, y, z, landedYaw, 0.0f), 2_000L);
    }

    /** A player whose eye is at a place, looking at the overworld portal's middle. */
    private Player lookingFrom(final double x, final double z)
    {
        final Location eye = new Location(overworld, x, 65.6, z);
        eye.setDirection(new Vector(1.0 - x, 65.5 - 65.6, 10.5 - z));
        final Player player = mock(Player.class, withSettings().stubOnly());
        when(player.getEyeLocation()).thenReturn(eye);
        when(player.hasPermission(anyString())).thenReturn(true);
        return player;
    }

    /** One trip teaches where the portal went, and nothing about where the far portal goes. */
    @Test
    void oneTripLearnsWhereThePortalWentAndNotTheWayBack()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);

        travel(100.5, 70.0, 5.5, 180.0f);

        final PortalViews.Link link = PortalViews.links().get("world 0,64,10 x");
        assertNotNull(link);
        assertEquals("World_Nether 100,70,5 x", link.to().id());
        assertEquals(0, link.turn());
        assertEquals(1, PortalViews.links().size(), "the way back is learnt by going back");
    }

    /** Standing south of the portal, looking in, the far side is the nether portal's north side, a block out. */
    @Test
    void aPlayerInFrontIsOfferedTheFarPortalsMatchingSide()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertEquals(1, offered.size());
        final WindowSource source = offered.get(0);
        assertEquals("portal:world 0,64,10 x south", source.name());
        final Place far = source.destination();
        assertEquals("World_Nether", far.worldName());
        assertEquals(new Spot(100, 70, 4), new Spot((int) Math.floor(far.x()), (int) far.y(), (int) Math.floor(far.z())));
        assertEquals(180.0f, far.yaw(), "looking north out of the far portal");
        assertTrue(source.walkThrough(), "walked into, never barred");
        assertTrue(source.oneAtATime());
        assertTrue(source.holdsFarChunks(), "its far side is held while creatures are shown");
    }

    /** From the other face the view is the far portal's other side. */
    @Test
    void aPlayerOnTheOtherSideSeesTheFarPortalsOtherSide()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(lookingFrom(0.5, 6.0)), 3_000L);

        assertEquals(1, offered.size());
        assertEquals("portal:world 0,64,10 x north", offered.get(0).name());
        assertEquals(0.0f, offered.get(0).destination().yaw(), "looking south out of the far portal");
        assertEquals(6.5, offered.get(0).destination().z());
    }

    /** A far portal on the other axis turns the traveller a quarter, and the view with them. */
    @Test
    void aFarPortalOnTheOtherAxisTurnsTheView()
    {
        light("World_Nether", Axis.Z, 5, 70, 50, 2, 3);
        travel(50.5, 70.0, 5.5, 270.0f);

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertEquals(1, offered.size());
        final Place far = offered.get(0).destination();
        assertEquals(270.0f, far.yaw(), "looking east out of a portal along z");
        assertEquals(51.5, far.x());
        assertEquals(5.5, far.z());
    }

    /** A landing whose turn does not come out square to the far portal teaches nothing, rather than a view at an angle. */
    @Test
    void aLandingThatIsNotSquareToTheFarPortalTeachesNothing()
    {
        light("World_Nether", Axis.Z, 5, 70, 50, 2, 3);

        travel(50.5, 70.0, 5.5, 180.0f);

        assertTrue(PortalViews.links().isEmpty());
    }

    /** Landing anywhere but in a portal -- another plugin moved them -- teaches nothing. */
    @Test
    void aLandingOutsideAPortalTeachesNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);

        travel(200.5, 70.0, 5.5, 180.0f);

        assertTrue(PortalViews.links().isEmpty());
    }

    /** Off, which is the default, a trip teaches nothing. */
    @Test
    void offTheFeatureLearnsNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEWS, false);

        travel(100.5, 70.0, 5.5, 180.0f);

        assertTrue(PortalViews.links().isEmpty());
    }

    /** Turned off after a trip, the portal is offered nothing and its far side is asked for no more: the kill switch. */
    @Test
    void turnedOffALearntPortalIsOfferedAndCapturedNoMore()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        assertEquals(1, PortalViews.links().size(), "learnt while on");
        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEWS, false);

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertTrue(offered.isEmpty());
        assertTrue(asked.isEmpty());
    }

    /** A landing back in the world it left -- some other plugin's doing -- teaches nothing. */
    @Test
    void aLandingInTheSameWorldTeachesNothing()
    {
        light("world", Axis.X, 40, 64, 30, 2, 3);
        PortalViews.leaving(TRAVELLER, new Location(overworld, 0.5, 64.0, 10.5, 180.0f, 0.0f), 1_000L);

        PortalViews.arrived(TRAVELLER, new Location(overworld, 40.5, 64.0, 30.5, 180.0f, 0.0f), 2_000L);

        assertTrue(PortalViews.links().isEmpty());
    }

    /** A far world left off the allow list is never learnt. */
    @Test
    void aWorldOffTheAllowListIsNeverLearnt()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEW_WORLDS, "world, other");

        travel(100.5, 70.0, 5.5, 180.0f);

        assertTrue(PortalViews.links().isEmpty());

        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEW_WORLDS, "WORLD, world_nether");
        travel(100.5, 70.0, 5.5, 180.0f);
        assertEquals(1, PortalViews.links().size(), "both listed, in any case");
    }

    /** A portal broken since the trip drops its link and takes both its faces' views back. */
    @Test
    void aBrokenPortalDropsItsLinkAndItsViews()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        blocks.get("world").remove(new Spot(1, 65, 10));

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertTrue(offered.isEmpty());
        assertTrue(PortalViews.links().isEmpty());
        assertTrue(released.contains("portal:world 0,64,10 x south"));
        assertTrue(released.contains("portal:world 0,64,10 x north"));
    }

    /** The far portal broken, as far as its loaded chunks say, drops the link too. */
    @Test
    void aBrokenFarPortalDropsTheLink()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        blocks.get("World_Nether").remove(new Spot(100, 72, 5));

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertTrue(offered.isEmpty());
        assertTrue(PortalViews.links().isEmpty());
    }

    /** A portal bigger than the largest drawn opening keeps vanilla's look, though its link is learnt. */
    @Test
    void aPortalBiggerThanAViewIsDrawnForStaysPlain()
    {
        blocks.clear();
        light("world", Axis.X, 0, 64, 10, 19, 3);
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(lookingFrom(9.5, 15.0)), 3_000L);

        assertEquals(1, PortalViews.links().size());
        assertTrue(offered.isEmpty());
    }

    /** Fresh off a trip, both far faces are asked for from loaded chunks, nobody looking. */
    @Test
    void aFreshTripAsksForBothFarSides()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(), 3_000L);

        assertEquals(2, asked.size());
        assertTrue(asked.get(0).contains("nether_portal_world_nether_100_70_5_x"), asked.get(0));
        assertNotEquals(asked.get(0), asked.get(1), "a capture for each way out of the far portal");
    }

    /** A minute on, a trip's far side is no longer asked for unless somebody looks. */
    @Test
    void anOldTripIsNoLongerAskedForUnlessLookedAt()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(), 2_000L + PortalViews.ARRIVAL_MILLIS + 1L);

        assertTrue(asked.isEmpty());
    }

    /** No view is offered to a player without the permission. */
    @Test
    void aPlayerWithoutThePermissionIsOfferedNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        final Player player = lookingFrom(0.5, 15.0);
        when(player.hasPermission(PortalSource.PERMISSION)).thenReturn(false);

        PortalViews.offer(List.of(player), 3_000L);

        assertTrue(offered.isEmpty());
    }

    /** A capture not in yet offers nothing: the portal stays plain until there is a far side to draw. */
    @Test
    void noCaptureYetOffersNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        PortalViews.captureOf = key -> null;

        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);

        assertTrue(offered.isEmpty());
        assertFalse(asked.isEmpty(), "it was asked for");
    }

    /** A world unloading forgets the links into it and takes their views back. */
    @Test
    void aWorldUnloadingForgetsItsLinks()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.worldUnloaded("World_Nether");

        assertTrue(PortalViews.links().isEmpty());
        assertTrue(released.contains("portal:world 0,64,10 x south"));
    }

    /** Past mirror-proximity-distance (16 by default) a portal is offered nothing, though in front of it. */
    @Test
    void aPlayerOutOfReachIsOfferedNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);

        PortalViews.offer(List.of(lookingFrom(0.5, 40.0)), 3_000L);

        assertTrue(offered.isEmpty());
        PortalViews.offer(List.of(lookingFrom(0.5, 20.0)), 3_000L);
        assertEquals(1, offered.size(), "ten blocks off it is");
    }

    /** A player in the far world, at the near portal's coordinates, is offered nothing of it. */
    @Test
    void aPlayerInAnotherWorldIsOfferedNothing()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        final Player player = lookingFrom(0.5, 15.0);
        final Location eye = player.getEyeLocation();
        eye.setWorld(nether);

        PortalViews.offer(List.of(player), 3_000L);

        assertTrue(offered.isEmpty());
        assertEquals(1, PortalViews.links().size(), "nor is the portal judged broken by reading the wrong world");
    }

    /**
     * A far world taken off the list after the trip -- a config reload -- is drawn and captured no more,
     * though the portal it leaves from is still listed.
     */
    @Test
    void aFarWorldTakenOffTheListIsNoLongerDrawn()
    {
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);
        travel(100.5, 70.0, 5.5, 180.0f);
        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 3_000L);
        assertEquals(1, offered.size(), "drawn while both are listed");
        offered.clear();
        asked.clear();

        ConfigTestSupport.set(ConfigKeys.NETHER_PORTAL_VIEW_WORLDS, "world");
        PortalViews.offer(List.of(lookingFrom(0.5, 15.0)), 4_000L);

        assertTrue(offered.isEmpty());
        assertTrue(asked.isEmpty(), "nor its far side captured on arrival");
    }

    /**
     * Stepping into a portal with another portal beside it, the trip is from the portal they stand in.
     *
     * <p>Looked for in a fixed order from a corner, the neighbour was found first and its link learnt.
     */
    @Test
    void aTripIsFromThePortalStoodInNotOneBesideIt()
    {
        light("world", Axis.Z, 8, 64, -1, 2, 3);
        light("World_Nether", Axis.X, 100, 70, 5, 2, 3);

        travel(100.5, 70.0, 5.5, 180.0f);

        assertNotNull(PortalViews.links().get("world 0,64,10 x"), "learnt for the portal stood in");
        assertEquals(1, PortalViews.links().size());
    }
}
