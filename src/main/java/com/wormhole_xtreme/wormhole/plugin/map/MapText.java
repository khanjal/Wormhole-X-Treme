package com.wormhole_xtreme.wormhole.plugin.map;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.BeamMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.GateMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.MirrorMark;
import com.wormhole_xtreme.wormhole.plugin.map.MapSnapshot.RingMark;

/**
 * What every web map says about a mark, and the layers and icons it is drawn with.
 *
 * <p>Names no map plugin's types, so every provider can use it whichever maps are installed.
 * Each map shows a mark's description as HTML, so every name in it is escaped.
 */
final class MapText
{
    /** Layer id for gates, their openings and the lines between dialled pairs. */
    static final String GATES = "wormhole.gates";

    /** Layer id for ring ends and the lines between pairs. */
    static final String RINGS = "wormhole.rings";

    /** Layer id for public beam destinations. */
    static final String BEAMS = "wormhole.beams";

    /** Layer id for quantum mirrors. */
    static final String MIRRORS = "wormhole.mirrors";

    /** The gates layer's name in a map's layer list. */
    static final String GATES_LABEL = "Stargates";

    /** The rings layer's name. */
    static final String RINGS_LABEL = "Transport rings";

    /** The beams layer's name. */
    static final String BEAMS_LABEL = "Beam destinations";

    /** The mirrors layer's name. */
    static final String MIRRORS_LABEL = "Quantum mirrors";

    /** Colour of a gate's opening and of a dialled pair's line: the logo's horizon cyan. */
    static final int GATE_COLOUR = 0x37B0D8;

    /** Colour of the line between a ring pair's ends: the logo's ring stone. */
    static final int RING_COLOUR = 0x9AA5B1;

    /** Line opacity for everything drawn. */
    static final double LINE_OPACITY = 0.8;

    /** Fill opacity for a gate's opening. */
    static final double FILL_OPACITY = 0.35;

    /** Line weight for everything drawn. */
    static final int LINE_WEIGHT = 3;

    /** Width and height of every icon, in pixels. */
    static final int ICON_SIZE = 16;

    /** Icon of a gate with a wormhole open. */
    static final String GATE_OPEN_ICON = "gate.png";

    /** Icon of a gate with no wormhole. */
    static final String GATE_IDLE_ICON = "gate-idle.png";

    /** Icon of a ring end. */
    static final String RINGS_ICON = "rings.png";

    /** Icon of a public beam destination. */
    static final String BEAM_ICON = "beam.png";

    /** Icon of a quantum mirror. */
    static final String MIRROR_ICON = "mirror.png";

    /** A line break in a description. */
    private static final String BREAK = "<br/>";

    /** Static helpers only. */
    private MapText()
    {
    }

    /**
     * A gate's description: its name, and its network and owner when it has them.
     *
     * @param gate
     *            the gate
     * @return the markup
     */
    static String gate(final GateMark gate)
    {
        final StringBuilder html = new StringBuilder(heading(gate.name()));
        if (gate.network() != null)
        {
            html.append(BREAK).append("Network: ").append(escape(gate.network()));
        }
        if (gate.owner() != null)
        {
            html.append(BREAK).append("Owner: ").append(escape(gate.owner()));
        }
        return html.toString();
    }

    /**
     * A ring end's description: its name, how the pair reads, and its owner when it has one.
     *
     * @param ring
     *            the ring end
     * @return the markup
     */
    static String ring(final RingMark ring)
    {
        final StringBuilder html = new StringBuilder(heading(ring.name()));
        html.append(BREAK).append(escape(ring.pair()));
        if (ring.owner() != null)
        {
            html.append(BREAK).append("Owner: ").append(escape(ring.owner()));
        }
        return html.toString();
    }

    /**
     * A public beam destination's description.
     *
     * @param beam
     *            the destination
     * @return the markup
     */
    static String beam(final BeamMark beam)
    {
        return heading(beam.name()) + BREAK + "Beam destination";
    }

    /**
     * A quantum mirror's description.
     *
     * @param mirror
     *            the mirror
     * @return the markup
     */
    static String mirror(final MirrorMark mirror)
    {
        return heading(mirror.name()) + BREAK + "Quantum mirror";
    }

    /**
     * A name in bold, escaped.
     *
     * @param text
     *            the name
     * @return the markup
     */
    static String heading(final String text)
    {
        return "<b>" + escape(text) + "</b>";
    }

    /**
     * Escapes text for a map's HTML, so a gate or player name cannot add markup.
     *
     * @param text
     *            the text
     * @return it, safe to put in HTML
     */
    static String escape(final String text)
    {
        final StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++)
        {
            final char c = text.charAt(i);
            switch (c)
            {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Reads one of the icons shipped in the jar.
     *
     * @param file
     *            its name, under {@code dynmap/} in the jar
     * @return the image
     * @throws IOException
     *             if it is missing or unreadable
     */
    static BufferedImage icon(final String file) throws IOException
    {
        try (InputStream in = MapText.class.getResourceAsStream("/dynmap/" + file))
        {
            if (in == null)
            {
                throw new IOException("Map icon " + file + " is missing from the jar");
            }
            final BufferedImage image = ImageIO.read(in);
            if (image == null)
            {
                throw new IOException("Map icon " + file + " is not an image");
            }
            return image;
        }
    }
}
