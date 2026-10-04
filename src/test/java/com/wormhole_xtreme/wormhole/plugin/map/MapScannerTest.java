package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;
import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamPoint;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.Footprint;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.LineMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * What the web map is told to show (#236).
 *
 * <p>The scan is the part of the map feature that decides anything, and it decides things a
 * server admin would be upset to get wrong: a dialled pair drawn as two lines on top of each
 * other, a line that leads the eye straight to a gate its owner hid behind an iris, or a
 * layer the operator switched off still showing. Every map plugin draws what this returns,
 * so it is tested here once, without any of them.
 */
class MapScannerTest
{
    private final World overworld = world("world");
    private final World nether = world("world_nether");

    private static World world(final String name)
    {
        final World w = mock(World.class);
        when(w.getName()).thenReturn(name);
        return w;
    }

    /** A gate with a two-block-wide opening standing at x 10-11, y 64-66, z 20. */
    private Stargate gate(final String name, final World world)
    {
        final Stargate gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn(name);
        when(gate.getGateWorld()).thenReturn(world);
        when(gate.getGatePortalBounds()).thenReturn(new BoundingBox(10, 64, 20, 12, 67, 21));
        return gate;
    }

    /**
     * Both ends of a formed wormhole. Only the gate that dialled holds a target, as
     * {@code StargateDialManager.connect} leaves them.
     */
    private static void dial(final Stargate from, final Stargate to)
    {
        formed(from);
        when(from.getGateTarget()).thenReturn(to);
        formed(to);
    }

    private static void formed(final Stargate gate)
    {
        when(gate.isGateActive()).thenReturn(true);
        when(gate.isGatePortalOpen()).thenReturn(true);
    }

    private static MapSnapshot scan(final List<Stargate> gates, final MapLayers layers)
    {
        return MapScanner.scan(gates, List.of(), List.of(), List.of(), layers);
    }

    @Test
    void aGateCarriesItsNetworkAndOwnerOntoItsMark()
    {
        final Stargate abydos = gate("Abydos", overworld);
        final StargateNetwork network = mock(StargateNetwork.class);
        when(network.getNetworkName()).thenReturn("Milky Way");
        when(abydos.getGateNetwork()).thenReturn(network);
        when(abydos.getGateOwnerName()).thenReturn("Daniel");

        final GateMark mark = scan(List.of(abydos), MapLayers.ALL).gates().get("abydos");

        assertNotNull(mark, "a gate should be marked under its lower-cased name");
        assertEquals("Abydos", mark.name(), "the label keeps the name as the owner wrote it");
        assertEquals("world", mark.world());
        assertEquals("Milky Way", mark.network(), "the network is what a viewer dials by");
        assertEquals("Daniel", mark.owner());
    }

    @Test
    void aGateWithPortalBlocksIsMarkedAtTheirCentreAndDrawnAsTheirSpan()
    {
        final GateMark mark = scan(List.of(gate("Abydos", overworld)), MapLayers.ALL).gates().get("abydos");

        assertEquals(11.0, mark.x(), 1e-9, "the point sits in the middle of the opening");
        assertEquals(65.5, mark.y(), 1e-9);
        assertEquals(20.5, mark.z(), 1e-9);
        // The bounds already reach past the last block, so the area is drawn from them as they are;
        // adding a block again would draw every gate one block too wide.
        assertEquals(new Footprint(10, 20, 12, 21, 64, 67), mark.footprint());
    }

    @Test
    void aGateWithNoPortalBlocksIsMarkedAtItsTeleportSpotWithNoArea()
    {
        final Stargate chulak = gate("Chulak", overworld);
        when(chulak.getGatePortalBounds()).thenReturn(null);
        when(chulak.getGatePlayerTeleportLocation()).thenReturn(new Location(overworld, 1.5, 70, -3.5));

        final GateMark mark = scan(List.of(chulak), MapLayers.ALL).gates().get("chulak");

        assertNotNull(mark, "a gate with no portal blocks is still a gate on the map");
        assertEquals(1.5, mark.x(), 1e-9);
        assertEquals(70.0, mark.y(), 1e-9);
        assertEquals(-3.5, mark.z(), 1e-9);
        assertNull(mark.footprint(), "with nothing to measure there is no area to draw");
    }

    @Test
    void aGateWithNoWorldIsLeftOff()
    {
        final MapSnapshot snapshot = scan(List.of(gate("Lost", null), gate("Found", overworld)), MapLayers.ALL);

        assertEquals(List.of("found"), List.copyOf(snapshot.gates().keySet()),
            "a map marker needs a world; the gate in one is still shown");
    }

    @Test
    void aDialledPairIsOneLineNotTwo()
    {
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        when(chulak.getGatePortalBounds()).thenReturn(new BoundingBox(100, 64, 200, 102, 67, 201));
        // Dialling sets a target on the dialler only; both ends holding one is defended against
        // anyway, so a pair never draws two lines on top of each other.
        dial(abydos, chulak);
        when(chulak.getGateTarget()).thenReturn(abydos);

        final MapSnapshot snapshot = scan(List.of(abydos, chulak), MapLayers.ALL);

        assertEquals(1, snapshot.gateLinks().size(),
            "both ends reporting the other is one line, not two drawn on top of each other");
        final LineMark line = snapshot.gateLinks().get("abydos|chulak");
        assertNotNull(line, "the line is keyed on the two names in order, whichever end was seen first");
        assertEquals(11.0, line.x1(), 1e-9);
        assertEquals(101.0, line.x2(), 1e-9);
    }

    @Test
    void anIdleGateIsMarkedShutAndAFormedOneOpen()
    {
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        formed(chulak);

        final MapSnapshot snapshot = scan(List.of(abydos, chulak), MapLayers.ALL);

        assertFalse(snapshot.gates().get("abydos").open());
        assertTrue(snapshot.gates().get("chulak").open());
    }

    @Test
    void aGateStillDiallingIsShownShutWithNoLine()
    {
        // Active from the first chevron, but nothing to travel through until the kawoosh
        // settles; the lit icon and the line wait for that, and appear together.
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        when(abydos.isGateActive()).thenReturn(true);
        when(abydos.getGateTarget()).thenReturn(chulak);
        when(chulak.isGateActive()).thenReturn(true);

        final MapSnapshot snapshot = scan(List.of(abydos, chulak), MapLayers.ALL);

        assertFalse(snapshot.gates().get("abydos").open(), "dialling is not open");
        assertFalse(snapshot.gates().get("chulak").open(), "nor is the gate being dialled");
        assertTrue(snapshot.gateLinks().isEmpty());
        assertEquals(2, snapshot.gates().size(), "both are still on the map");
    }

    @Test
    void theFarEndOfAWormholeIsOpenThoughOnlyTheDiallerHoldsATarget()
    {
        // Dialling sets a target on the gate that dialled and not on the one it reached, so
        // the far end's state is read from the dialler as well as from itself.
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        formed(abydos);
        when(abydos.getGateTarget()).thenReturn(chulak);

        final MapSnapshot snapshot = scan(List.of(abydos, chulak), MapLayers.ALL);

        assertTrue(snapshot.gates().get("abydos").open());
        assertTrue(snapshot.gates().get("chulak").open(), "a wormhole is open at both ends");
        assertEquals(1, snapshot.gateLinks().size(), "and the pair still has its line");
    }

    @Test
    void aHiddenIrisGateDoesNotLightTheGateItIsDialledTo()
    {
        // The far end reads as open from the gate that dialled it. If that gate is hidden
        // behind its iris, lighting the far end would say somebody hidden is dialling in.
        final Stargate earth = gate("Earth", overworld);
        final Stargate abydos = gate("Abydos", overworld);
        when(earth.getGateIrisDeactivationCode()).thenReturn("GDO");
        formed(earth);
        when(earth.getGateTarget()).thenReturn(abydos);

        final MapSnapshot hidden = scan(List.of(earth, abydos), new MapLayers(true, false, true, true, true));
        final MapSnapshot shown = scan(List.of(earth, abydos), MapLayers.ALL);

        assertFalse(hidden.gates().get("abydos").open(), "not lit by a gate the map is not showing");
        assertTrue(shown.gates().get("abydos").open(), "but lit when that gate is shown");
    }

    /** A gate behind an iris dialled to an ordinary one, both ends formed, as a server sees it. */
    private Stargate[] irisPair(final boolean irisGateDials)
    {
        final Stargate hidden = gate("Earth", overworld);
        final Stargate visible = gate("Abydos", overworld);
        when(hidden.getGateIrisDeactivationCode()).thenReturn("GDO");
        formed(hidden);
        formed(visible);
        if (irisGateDials)
        {
            when(hidden.getGateTarget()).thenReturn(visible);
        }
        else
        {
            when(visible.getGateTarget()).thenReturn(hidden);
        }
        return new Stargate[] {hidden, visible};
    }

    private static final MapLayers IRIS_HIDDEN = new MapLayers(true, false, true, true, true);

    @Test
    void aGateDialledFromAHiddenIrisGateStaysIdleThoughItsOwnWormholeFormed()
    {
        // Found on a test server: the far end forms its own wormhole, so it lit itself and
        // told anyone looking that a hidden gate was connected to it.
        final MapSnapshot snapshot = scan(List.of(irisPair(true)), IRIS_HIDDEN);

        assertEquals(List.of("abydos"), List.copyOf(snapshot.gates().keySet()), "the ordinary gate is still shown");
        assertFalse(snapshot.gates().get("abydos").open(), "but not as open");
        assertTrue(snapshot.gateLinks().isEmpty());
    }

    @Test
    void aGateDiallingAHiddenIrisGateStaysIdle()
    {
        final MapSnapshot snapshot = scan(List.of(irisPair(false)), IRIS_HIDDEN);

        assertEquals(List.of("abydos"), List.copyOf(snapshot.gates().keySet()), "the ordinary gate is still shown");
        assertFalse(snapshot.gates().get("abydos").open(), "but not as open");
        assertTrue(snapshot.gateLinks().isEmpty());
    }

    @Test
    void aShutHiddenGateRememberingATargetDoesNotDarkenIt()
    {
        // A gate keeps its last target after it shuts. That is no wormhole, so the gate it
        // names, open to somewhere else, must still show open.
        final Stargate earth = gate("Earth", overworld);
        final Stargate abydos = gate("Abydos", overworld);
        when(earth.getGateIrisDeactivationCode()).thenReturn("GDO");
        when(earth.getGateTarget()).thenReturn(abydos);
        formed(abydos);

        final MapSnapshot snapshot = scan(List.of(earth, abydos), IRIS_HIDDEN);

        assertTrue(snapshot.gates().get("abydos").open());
    }

    @Test
    void withIrisGatesShownBothEndsOfEitherPairAreOpenWithTheirLine()
    {
        for (final boolean irisGateDials : new boolean[] {true, false})
        {
            final MapSnapshot snapshot = scan(List.of(irisPair(irisGateDials)), MapLayers.ALL);

            assertTrue(snapshot.gates().get("earth").open(), "iris gate dials: " + irisGateDials);
            assertTrue(snapshot.gates().get("abydos").open(), "iris gate dials: " + irisGateDials);
            assertEquals(1, snapshot.gateLinks().size(), "iris gate dials: " + irisGateDials);
        }
    }

    @Test
    void anIdleGateHasNoLineWhateverTargetItRemembers()
    {
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        when(abydos.getGateTarget()).thenReturn(chulak);

        final MapSnapshot snapshot = scan(List.of(abydos, chulak), MapLayers.ALL);

        assertTrue(snapshot.gateLinks().isEmpty(), "a line means the wormhole is open now");
        assertFalse(snapshot.gates().get("chulak").open(),
            "nor is the gate it last dialled open because of it");
    }

    @Test
    void aDialledPairAcrossWorldsHasNoLine()
    {
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate netu = gate("Netu", nether);
        dial(abydos, netu);

        final MapSnapshot snapshot = scan(List.of(abydos, netu), MapLayers.ALL);

        assertEquals(2, snapshot.gates().size(), "both gates are still shown");
        assertTrue(snapshot.gateLinks().isEmpty(), "a map line is drawn in one world");
    }

    @Test
    void anIrisGateAndItsLineAreHiddenWhenIrisGatesAreNot()
    {
        final Stargate earth = gate("Earth", overworld);
        final Stargate abydos = gate("Abydos", overworld);
        when(earth.getGateIrisDeactivationCode()).thenReturn("GDO");
        // The shown gate holds the target, so a line from it is what is being refused.
        dial(abydos, earth);

        final MapSnapshot snapshot = scan(List.of(earth, abydos),
            new MapLayers(true, false, true, true, true));

        assertEquals(List.of("abydos"), List.copyOf(snapshot.gates().keySet()),
            "the gate with an iris is hidden and the other still shown");
        assertTrue(snapshot.gateLinks().isEmpty(),
            "a line from the open gate would lead straight to the hidden one");
    }

    @Test
    void anIrisGateIsShownWhenIrisGatesAre()
    {
        final Stargate earth = gate("Earth", overworld);
        final Stargate abydos = gate("Abydos", overworld);
        when(earth.getGateIrisDeactivationCode()).thenReturn("GDO");
        dial(earth, abydos);

        final MapSnapshot snapshot = scan(List.of(earth, abydos), MapLayers.ALL);

        assertNotNull(snapshot.gates().get("earth"));
        assertEquals(1, snapshot.gateLinks().size());
    }

    @Test
    void aBlankIrisCodeIsNoIris()
    {
        final Stargate earth = gate("Earth", overworld);
        when(earth.getGateIrisDeactivationCode()).thenReturn("  ");

        assertNotNull(scan(List.of(earth), new MapLayers(true, false, true, true, true)).gates().get("earth"),
            "a gate whose code was cleared has no iris to keep secret");
    }

    @Test
    void aRingPairIsTwoEndsAndOneLine()
    {
        final Ring down = ring(0, 60, 0, "Mine");
        final Ring up = ring(10, 90, 10, "");
        final RingPair pair = new RingPair("r1", "world", down, up);
        pair.setOwnerName("Sam");

        final MapSnapshot snapshot = MapScanner.scan(List.of(), List.of(pair), List.of(), List.of(), MapLayers.ALL);

        final RingMark a = snapshot.rings().get("r1:a");
        final RingMark b = snapshot.rings().get("r1:b");
        assertNotNull(a);
        assertNotNull(b);
        assertEquals("Mine", a.name());
        assertEquals(pair.describe(), b.name(), "an end with no name of its own is called by the pair");
        assertEquals("Sam", a.owner());
        assertEquals(0.5, a.x(), 1e-9, "the anchor block's centre");
        assertEquals(1, snapshot.ringLinks().size());
        assertEquals(10.5, snapshot.ringLinks().get("r1").x2(), 1e-9);
    }

    private static Ring ring(final int x, final int y, final int z, final String name)
    {
        final Ring ring = mock(Ring.class);
        when(ring.getAnchorX()).thenReturn(x);
        when(ring.getAnchorY()).thenReturn(y);
        when(ring.getAnchorZ()).thenReturn(z);
        when(ring.getName()).thenReturn(name);
        return ring;
    }

    @Test
    void beamsComeOnlyFromTheDestinationsGiven()
    {
        final BeamDestination market = new BeamDestination("Market",
            new BeamPoint("world", 5, 64, 6, 0f, 0f), null);

        final MapSnapshot snapshot = MapScanner.scan(List.of(), List.of(), List.of(market), List.of(), MapLayers.ALL);

        assertEquals(1, snapshot.beams().size());
        assertEquals("Market", snapshot.beams().get("market").name());
    }

    @Test
    void aMirrorIsMarkedAtItsBanner()
    {
        final QuantumMirror hall = new QuantumMirror("Hall", new MirrorBlock("world", 3, 65, -7), null);

        final MirrorMark mark = MapScanner.scan(List.of(), List.of(), List.of(), List.of(hall), MapLayers.ALL)
            .mirrors().get("hall");

        assertNotNull(mark);
        assertEquals("Hall", mark.name());
        assertEquals("world", mark.world());
        assertEquals(3.5, mark.x(), 1e-9, "the banner block's centre");
        assertEquals(65.5, mark.y(), 1e-9);
        assertEquals(-6.5, mark.z(), 1e-9);
    }

    /** One of everything, for the layer tests. */
    private MapSnapshot everything(final MapLayers layers)
    {
        final Stargate abydos = gate("Abydos", overworld);
        final Stargate chulak = gate("Chulak", overworld);
        dial(abydos, chulak);
        final RingPair pair = new RingPair("r1", "world", ring(0, 60, 0, "A"), ring(9, 60, 9, "B"));
        final BeamDestination market = new BeamDestination("Market", new BeamPoint("world", 5, 64, 6, 0f, 0f), null);
        final QuantumMirror hall = new QuantumMirror("Hall", new MirrorBlock("world", 3, 65, -7), null);
        return MapScanner.scan(List.of(abydos, chulak), List.of(pair), List.of(market), List.of(hall), layers);
    }

    @Test
    void gatesSwitchedOffTakeTheirLinesWithThemAndLeaveTheRest()
    {
        final MapSnapshot snapshot = everything(new MapLayers(false, true, true, true, true));

        assertTrue(snapshot.gates().isEmpty());
        assertTrue(snapshot.gateLinks().isEmpty(), "the lines live in the gates layer");
        assertEquals(2, snapshot.rings().size(), "the other layers are still filled");
        assertEquals(1, snapshot.beams().size());
        assertEquals(1, snapshot.mirrors().size());
    }

    @Test
    void ringsSwitchedOffLeaveTheRest()
    {
        final MapSnapshot snapshot = everything(new MapLayers(true, true, false, true, true));

        assertTrue(snapshot.rings().isEmpty());
        assertTrue(snapshot.ringLinks().isEmpty());
        assertEquals(2, snapshot.gates().size(), "the other layers are still filled");
    }

    @Test
    void beamsSwitchedOffLeaveTheRest()
    {
        final MapSnapshot snapshot = everything(new MapLayers(true, true, true, false, true));

        assertTrue(snapshot.beams().isEmpty());
        assertEquals(1, snapshot.mirrors().size(), "the other layers are still filled");
    }

    @Test
    void mirrorsSwitchedOffLeaveTheRest()
    {
        final MapSnapshot snapshot = everything(new MapLayers(true, true, true, true, false));

        assertTrue(snapshot.mirrors().isEmpty());
        assertEquals(1, snapshot.beams().size(), "the other layers are still filled");
    }

    @Test
    void anUnchangedServerScansToAnEqualPicture()
    {
        // equals is the whole change detector: if two scans of the same state differed, the
        // map would be redrawn every few seconds for nothing.
        assertEquals(everything(MapLayers.ALL), everything(MapLayers.ALL));
    }
}
