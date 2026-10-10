package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.GateSource;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorSource;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What a mirror and a gate each tell the window drawing about themselves (#522).
 *
 * <p>A gate used to be drawn as a stand-in mirror, with a flag on the window to step round what a
 * mirror does. Those answers come from the source now: a mirror answering like a gate could no longer
 * be clicked through, and a gate answering like a mirror would be barred and send a punch to a mirror
 * that is not there.
 */
class WindowSourceTest
{
    private static final Place ROOM = new Place("far", 100.5, 70.0, 200.5, 0.0f, 0.0f);

    private static final Place CHOSEN = new Place("far2", 300.5, 70.0, -20.5, 90.0f, 0.0f);

    private final WindowShape shape = WindowShape.through(new Spot(10, 64, 20), new Spot(0, 0, -1), ROOM, 1, 2);

    private final List<Spot> open = List.of(new Spot(10, 64, 20), new Spot(10, 65, 20));

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install();
        ConfigTestSupport.clear();
        ConfigTestSupport.set(ConfigKeys.MIRROR_VIEW_DEPTH, 24);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    @Test
    void aMirrorIsKnownByItsNameMeasuredFromItsBannerAndPunchedThrough()
    {
        final QuantumMirror museum = new QuantumMirror("museum", new BlockPlace("world", 10, 64, 19), ROOM);
        final Block banner = mock(Block.class);

        final MirrorSource source = new MirrorSource(museum, banner, shape, open);

        assertEquals("museum", source.name());
        assertSame(banner, source.anchor(), "distances are measured from its banner");
        assertEquals(ROOM, source.destination());
        assertFalse(source.walkThrough(), "a mirror's opening is barred and punched through");
        assertSame(museum, source.punchTarget(), "a punch travels the mirror itself");
        assertEquals(24, source.depth(), "drawn to mirror-view-depth as set, not its default of 160");
    }

    /** The mirror chosen at a banner lends its room, but the window is still the banner's own mirror. */
    @Test
    void aMirrorShowingTheRoomChosenAtItKeepsItsOwnName()
    {
        final QuantumMirror museum = new QuantumMirror("museum", new BlockPlace("world", 10, 64, 19), ROOM);
        final QuantumMirror showing = museum.withDestination(CHOSEN);

        final MirrorSource source = new MirrorSource(showing, mock(Block.class), shape, open);

        assertEquals("museum", source.name(), "known by the banner's mirror, as the sweep's maps are keyed");
        assertEquals(CHOSEN, source.destination(), "and opens onto the room chosen at it");
        assertSame(showing, source.punchTarget(), "a punch travels to what it shows");
    }

    @Test
    void aGateIsWalkedThroughAndAPunchAtItTravelsNothing()
    {
        final Block anchor = mock(Block.class);

        final GateSource gate = new GateSource("gate:Abydos", anchor, shape, open, ROOM, "Chulak", 16);

        assertEquals("gate:Abydos", gate.name(), "under its prefix, which no mirror can be called");
        assertSame(anchor, gate.anchor());
        assertSame(open, gate.open());
        assertTrue(gate.walkThrough(), "a gate's opening is walked into, never barred");
        assertNull(gate.punchTarget(), "a punch at a gate's view goes nowhere");
        assertEquals(16, gate.depth(), "drawn to its first step until a deeper capture is in");
    }
}
