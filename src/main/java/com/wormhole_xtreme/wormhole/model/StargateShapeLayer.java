package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The Class StargateShapeLayer.
 */
public class StargateShapeLayer
{

    /** The block positions. */
    private List<Integer[]> layerBlockPositions = new ArrayList<>();

    /**
     * The chevron positions -- cells written {@code [C]} rather than {@code [S]}.
     *
     * <p>Held apart from the frame blocks because the whole point of a chevron cell is that
     * it is built from a different material, and detection works out which palette a gate
     * belongs to by reading the first frame block it finds. A chevron in that list would
     * have a gate fronted with lamps resolve to the lamp palette, or to no palette at all.
     */
    private List<Integer[]> layerChevronPositions = new ArrayList<>();

    /**
     * The {@code [S:C]} cells: chevron positions the frame material is also accepted at.
     *
     * <p>A subset of {@link #layerChevronPositions}, not a separate kind of cell. They draw,
     * light, index and protect exactly as a {@code [C]} does; the only difference is what
     * detection will accept there.
     *
     * <p>{@code [C]} on its own is strict, and has to stay that way -- every shipped shape
     * uses it and gates built to them have the chevron material in those cells. But a shape
     * that gains a chevron position where it used to have plain frame cannot use it: every
     * gate already standing has frame material there and would stop matching its own shape.
     * {@code [S:C]} is for that case, and reads as what it means -- a frame cell that is also
     * a chevron.
     */
    private final List<Integer[]> layerLenientChevronPositions = new ArrayList<>();

    /** The sign position. */
    private int[] layerNameSignPosition = null;

    /** The exit position. */
    private int[] layerPlayerExitPosition = null;

    /** The minecart exit position. */
    private int[] layerMinecartExitPosition = null;

    /** The activation position. */
    private int[] layerActivationPosition = null;

    /** The iris activation position. */
    private int[] layerIrisActivationPosition = null;

    /** The dialer position. */
    private int[] layerDialSignPosition = null;
    /** Position of point that allows gate to be activated via redstone. */
    private int[] layerRedstoneDialActivationPosition = null;
    /** Position of point that allows gate to cycle sign targets via redstone. */
    private int[] layerRedstoneSignActivationPosition = null;

    /** The layer redstone activation position. */
    private int[] layerRedstoneGateActivatedPosition = null;

    /** The light_positions. */
    private List<List<Integer[]>> layerLightPositions = new ArrayList<>();

    /** The positions of woosh. First array is the order to activate them. Inner array is list of points */
    private List<List<Integer[]>> layerWooshPositions = new ArrayList<>();

    /** The water_positions. */
    private List<Integer[]> layerPortalPositions = new ArrayList<>();

    /**
     * Instantiates a new stargate shape layer.
     */
    protected StargateShapeLayer(final String[] layerLines, final int height, final int width)
    {
        final Pattern marker = Pattern.compile("\\[(.+?)\\]");
        for (int i = 0; i < layerLines.length; i++)
        {
            final Matcher m = marker.matcher(layerLines[i]);
            int j = 0;
            while (m.find())
            {
                final Integer[] point = {0, (height - 1 - i), (width - 1 - j)};
                final String[] mods = m.group(1).split(":");
                if (lenientChevron(mods))
                {
                    // S and C together: a chevron the frame material is also accepted at. Not
                    // recorded as a frame cell as well, or the strict chevron check and the
                    // frame check would each demand a different block of the one cell.
                    getLayerChevronPositions().add(point);
                    layerLenientChevronPositions.add(point);
                }
                for (final String mod : mods)
                {
                    if (!lenientChevron(mods) || !isFrameOrChevronMarker(mod))
                    {
                        recordMarker(mod, point);
                    }
                }
                j++;
            }
        }
        logParsedPositions();
    }

    /**
     * Whether a cell's markers are the {@code S}-and-{@code C} pair.
     *
     * @param mods
     *            the cell's colon-separated markers
     * @return true if both are present
     */
    private static boolean lenientChevron(final String[] mods)
    {
        boolean frame = false;
        boolean chevron = false;
        for (final String mod : mods)
        {
            frame |= "S".equalsIgnoreCase(mod);
            chevron |= "C".equalsIgnoreCase(mod);
        }
        return frame && chevron;
    }

    /**
     * @param mod
     *            one marker
     * @return true if it is the {@code S} or the {@code C} of a lenient chevron
     */
    private static boolean isFrameOrChevronMarker(final String mod)
    {
        return "S".equalsIgnoreCase(mod) || "C".equalsIgnoreCase(mod);
    }

    /**
     * Applies one marker to this layer.
     *
     * <p>A block can carry several, which is why this is called per colon-separated part
     * rather than once per bracket.
     */
    private void recordMarker(final String mod, final Integer[] point)
    {
        if (mod.equalsIgnoreCase("S"))
        {
            getLayerBlockPositions().add(point);
            return;
        }
        if (mod.equalsIgnoreCase("P"))
        {
            getLayerPortalPositions().add(point);
            return;
        }
        if (mod.equalsIgnoreCase("C"))
        {
            getLayerChevronPositions().add(point);
            return;
        }
        if (recordSinglePosition(mod, point))
        {
            return;
        }
        if (mod.contains("L") || mod.contains("l"))
        {
            addToWave(getLayerLightPositions(), mod, point, "Light Material Position");
            return;
        }
        if (mod.contains("W") || mod.contains("w"))
        {
            addToWave(getLayerWooshPositions(), mod, point, "Woosh Position");
        }
    }

    /**
     * Records a marker that names one block rather than collecting many.
     *
     * @return true if the marker was one of these
     */
    private boolean recordSinglePosition(final String mod, final Integer[] point)
    {
        final int[] p = {point[0], point[1], point[2]};
        switch (mod.toUpperCase(Locale.ROOT))
        {
            case "N": setLayerNameSignPosition(p); return true;
            case "EP": setLayerPlayerExitPosition(p); return true;
            case "EM": setLayerMinecartExitPosition(p); return true;
            case "A": setLayerActivationPosition(p); return true;
            case "D": setLayerDialSignPosition(p); return true;
            case "IA": setLayerIrisActivationPosition(p); return true;
            case "RA": setLayerRedstoneGateActivatedPosition(p); return true;
            case "RD": setLayerRedstoneDialActivationPosition(p); return true;
            case "RS": setLayerRedstoneSignActivationPosition(p); return true;
            default: return false;
        }
    }

    /**
     * Adds a block to one wave of an animation, padding the list up to that wave.
     *
     * <p>The number indexes the list directly, so wave 3 on its own leaves 0, 1 and 2 empty
     * rather than shifting everything down. Lights and wooshes differ only in which list they
     * fill and what they are called in the log.
     */
    private static void addToWave(final List<List<Integer[]>> waves, final String mod,
                                  final Integer[] point, final String label)
    {
        final int order = mod.contains("#") ? Integer.parseInt(mod.split("#")[1]) : 1;
        while (waves.size() <= order)
        {
            waves.add(null);
        }
        if (waves.get(order) == null)
        {
            waves.set(order, new ArrayList<>());
        }
        waves.get(order).add(point);
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG,
            label + " (Order:" + order + " Position:" + Arrays.toString(point) + ")");
    }

    /** Reports the single-block positions this layer ended up with. */
    private void logParsedPositions()
    {
        log("Stargate Sign Position", getLayerNameSignPosition());
        log("Stargate Player Exit Position", getLayerPlayerExitPosition());
        log("Stargate Minecart Exit Position", getLayerMinecartExitPosition());
        log("Stargate Activation Position", getLayerActivationPosition());
        log("Stargate Iris Activation Position", getLayerIrisActivationPosition());
        log("Stargate Dial Sign Position", getLayerDialSignPosition());
        log("Stargate Redstone Dial Activation Position", getLayerRedstoneDialActivationPosition());
        log("Stargate Redstone Sign Activation Position", getLayerRedstoneSignActivationPosition());
        log("Stargate Redstone Gate Activated Position", getLayerRedstoneGateActivatedPosition());
    }

    private static void log(final String what, final int[] position)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG,
            what + ": \"" + Arrays.toString(position) + "\"");
    }

    /**
     * Gets the layer activation position.
     */
    public int[] getLayerActivationPosition()
    {
        return layerActivationPosition != null
            ? layerActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer block positions.
     */
    public List<Integer[]> getLayerBlockPositions()
    {
        return layerBlockPositions;
    }

    /**
     * The chevron cells the frame material is also accepted at.
     *
     * @return the {@code [S:C]} positions, a subset of {@link #getLayerChevronPositions()}
     */
    public List<Integer[]> getLayerLenientChevronPositions()
    {
        return layerLenientChevronPositions;
    }

    /**
     * Gets the layer chevron positions.
     */
    public List<Integer[]> getLayerChevronPositions()
    {
        return layerChevronPositions;
    }

    /**
     * Gets the layer dialer position.
     * 
     * @return the layer dialer position
     */
    public int[] getLayerDialSignPosition()
    {
        return layerDialSignPosition != null
            ? layerDialSignPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer iris activation position.
     */
    public int[] getLayerIrisActivationPosition()
    {
        return layerIrisActivationPosition != null
            ? layerIrisActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer light positions.
     */
    public List<List<Integer[]>> getLayerLightPositions()
    {
        return layerLightPositions;
    }

    /**
     * Gets the layer minecart exit position.
     */
    public int[] getLayerMinecartExitPosition()
    {
        return layerMinecartExitPosition != null
            ? layerMinecartExitPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer sign position.
     */
    public int[] getLayerNameSignPosition()
    {
        return layerNameSignPosition != null
            ? layerNameSignPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer enter position.
     * 
     * @return the layer enter position
     */
    public int[] getLayerPlayerExitPosition()
    {
        return layerPlayerExitPosition != null
            ? layerPlayerExitPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer portal positions.
     */
    public List<Integer[]> getLayerPortalPositions()
    {
        return layerPortalPositions;
    }

    /**
     * Gets the layer redstone activation position.
     */
    public int[] getLayerRedstoneDialActivationPosition()
    {
        return layerRedstoneDialActivationPosition != null
            ? layerRedstoneDialActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer redstone activation position.
     * 
     * @return the layer redstone activation position
     */
    public int[] getLayerRedstoneGateActivatedPosition()
    {
        return layerRedstoneGateActivatedPosition != null
            ? layerRedstoneGateActivatedPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer redstone dialer activation position.
     * 
     * @return the layer redstone dialer activation position
     */
    public int[] getLayerRedstoneSignActivationPosition()
    {
        return layerRedstoneSignActivationPosition != null
            ? layerRedstoneSignActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer woosh positions.
     */
    public List<List<Integer[]>> getLayerWooshPositions()
    {
        return layerWooshPositions;
    }

    /**
     * Sets the layer activation position.
     */
    public void setLayerActivationPosition(final int[] layerActivationPosition)
    {
        this.layerActivationPosition = layerActivationPosition.clone();
    }

    /**
     * Sets the layer block positions.
     */
    public void setLayerBlockPositions(final List<Integer[]> layerBlockPositions)
    {
        this.layerBlockPositions = layerBlockPositions;
    }

    /**
     * Sets the layer dialer position.
     */
    public void setLayerDialSignPosition(final int[] layerDialSignPosition)
    {
        this.layerDialSignPosition = layerDialSignPosition.clone();
    }

    /**
     * Sets the layer iris activation position.
     */
    public void setLayerIrisActivationPosition(final int[] layerIrisActivationPosition)
    {
        this.layerIrisActivationPosition = layerIrisActivationPosition.clone();
    }

    /**
     * Sets the layer light positions.
     */
    public void setLayerLightPositions(final List<List<Integer[]>> layerLightPositions)
    {
        this.layerLightPositions = layerLightPositions;
    }

    /**
     * Sets the layer minecart exit position.
     */
    public void setLayerMinecartExitPosition(final int[] layerMinecartExitPosition)
    {
        this.layerMinecartExitPosition = layerMinecartExitPosition.clone();
    }

    /**
     * Sets the layer sign position.
     */
    public void setLayerNameSignPosition(final int[] layerNameSignPosition)
    {
        this.layerNameSignPosition = layerNameSignPosition.clone();
    }

    /**
     * Sets the layer exit position.
     */
    public void setLayerPlayerExitPosition(final int[] layerPlayerExitPosition)
    {
        this.layerPlayerExitPosition = layerPlayerExitPosition.clone();
    }

    /**
     * Sets the layer portal positions.
     */
    public void setLayerPortalPositions(final List<Integer[]> layerPortalPositions)
    {
        this.layerPortalPositions = layerPortalPositions;
    }

    /**
     * Sets the layer redstone activation position.
     */
    public void setLayerRedstoneDialActivationPosition(final int[] layerRedstoneDialActivationPosition)
    {
        this.layerRedstoneDialActivationPosition = layerRedstoneDialActivationPosition.clone();
    }

    /**
     * Sets the layer redstone activation position.
     */
    public void setLayerRedstoneGateActivatedPosition(final int[] layerRedstoneGateActivatedPosition)
    {
        this.layerRedstoneGateActivatedPosition = layerRedstoneGateActivatedPosition.clone();
    }

    /**
     * Sets the layer redstone dialer activation position.
     */
    public void setLayerRedstoneSignActivationPosition(final int[] layerRedstoneSignActivationPosition)
    {
        this.layerRedstoneSignActivationPosition = layerRedstoneSignActivationPosition.clone();
    }

    /**
     * Sets the layer woosh positions.
     */
    public void setLayerWooshPositions(final List<List<Integer[]>> layerWooshPositions)
    {
        this.layerWooshPositions = layerWooshPositions;
    }
}
