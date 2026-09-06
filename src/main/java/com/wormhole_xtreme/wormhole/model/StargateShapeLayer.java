package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
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
    private ArrayList<Integer[]> layerBlockPositions = new ArrayList<Integer[]>();

    /**
     * The chevron positions -- cells written {@code [C]} rather than {@code [S]}.
     *
     * <p>Held apart from the frame blocks because the whole point of a chevron cell is that
     * it is built from a different material, and detection works out which palette a gate
     * belongs to by reading the first frame block it finds. A chevron in that list would
     * have a gate fronted with lamps resolve to the lamp palette, or to no palette at all.
     */
    private ArrayList<Integer[]> layerChevronPositions = new ArrayList<Integer[]>();

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
    private ArrayList<ArrayList<Integer[]>> layerLightPositions = new ArrayList<ArrayList<Integer[]>>();

    /** The positions of woosh. First array is the order to activate them. Inner array is list of points */
    private ArrayList<ArrayList<Integer[]>> layerWooshPositions = new ArrayList<ArrayList<Integer[]>>();

    /** The water_positions. */
    private ArrayList<Integer[]> layerPortalPositions = new ArrayList<Integer[]>();

    /**
     * Instantiates a new stargate shape layer.
     * 
     * @param layerLines
     *            the layer lines
     * @param height
     *            the height
     * @param width
     *            the width
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
                for (final String mod : m.group(1).split(":"))
                {
                    record(mod, point);
                }
                j++;
            }
        }
        logParsedPositions();
    }

    /**
     * Applies one marker to this layer.
     *
     * <p>A block can carry several, which is why this is called per colon-separated part
     * rather than once per bracket.
     */
    private void record(final String mod, final Integer[] point)
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
    private static void addToWave(final ArrayList<ArrayList<Integer[]>> waves, final String mod,
                                  final Integer[] point, final String label)
    {
        final int order = mod.contains("#") ? Integer.parseInt(mod.split("#")[1]) : 1;
        while (waves.size() <= order)
        {
            waves.add(null);
        }
        if (waves.get(order) == null)
        {
            waves.set(order, new ArrayList<Integer[]>());
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
     * 
     * @return the layer activation position
     */
    public int[] getLayerActivationPosition()
    {
        return layerActivationPosition != null
            ? layerActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer block positions.
     * 
     * @return the layer block positions
     */
    public ArrayList<Integer[]> getLayerBlockPositions()
    {
        return layerBlockPositions;
    }

    /**
     * Gets the layer chevron positions.
     *
     * @return the layer chevron positions
     */
    public ArrayList<Integer[]> getLayerChevronPositions()
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
     * 
     * @return the layer iris activation position
     */
    public int[] getLayerIrisActivationPosition()
    {
        return layerIrisActivationPosition != null
            ? layerIrisActivationPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer light positions.
     * 
     * @return the layer light positions
     */
    public ArrayList<ArrayList<Integer[]>> getLayerLightPositions()
    {
        return layerLightPositions;
    }

    /**
     * Gets the layer minecart exit position.
     * 
     * @return the layer minecart exit position
     */
    public int[] getLayerMinecartExitPosition()
    {
        return layerMinecartExitPosition != null
            ? layerMinecartExitPosition.clone()
            : new int[]{};
    }

    /**
     * Gets the layer sign position.
     * 
     * @return the layer sign position
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
     * 
     * @return the layer portal positions
     */
    public ArrayList<Integer[]> getLayerPortalPositions()
    {
        return layerPortalPositions;
    }

    /**
     * Gets the layer redstone activation position.
     * 
     * @return the layer redstone activation position
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
     * 
     * @return the layer woosh positions
     */
    public ArrayList<ArrayList<Integer[]>> getLayerWooshPositions()
    {
        return layerWooshPositions;
    }

    /**
     * Sets the layer activation position.
     * 
     * @param layerActivationPosition
     *            the new layer activation position
     */
    public void setLayerActivationPosition(final int[] layerActivationPosition)
    {
        this.layerActivationPosition = layerActivationPosition.clone();
    }

    /**
     * Sets the layer block positions.
     * 
     * @param layerBlockPositions
     *            the new layer block positions
     */
    public void setLayerBlockPositions(final ArrayList<Integer[]> layerBlockPositions)
    {
        this.layerBlockPositions = layerBlockPositions;
    }

    /**
     * Sets the layer dialer position.
     * 
     * @param layerDialerPosition
     *            the new layer dialer position
     */
    public void setLayerDialSignPosition(final int[] layerDialSignPosition)
    {
        this.layerDialSignPosition = layerDialSignPosition.clone();
    }

    /**
     * Sets the layer iris activation position.
     * 
     * @param layerIrisActivationPosition
     *            the new layer iris activation position
     */
    public void setLayerIrisActivationPosition(final int[] layerIrisActivationPosition)
    {
        this.layerIrisActivationPosition = layerIrisActivationPosition.clone();
    }

    /**
     * Sets the layer light positions.
     * 
     * @param layerLightPositions
     *            the new layer light positions
     */
    public void setLayerLightPositions(final ArrayList<ArrayList<Integer[]>> layerLightPositions)
    {
        this.layerLightPositions = layerLightPositions;
    }

    /**
     * Sets the layer minecart exit position.
     * 
     * @param layerMinecartExitPosition
     *            the new layer minecart exit position
     */
    public void setLayerMinecartExitPosition(final int[] layerMinecartExitPosition)
    {
        this.layerMinecartExitPosition = layerMinecartExitPosition.clone();
    }

    /**
     * Sets the layer sign position.
     * 
     * @param layerSignPosition
     *            the new layer sign position
     */
    public void setLayerNameSignPosition(final int[] layerNameSignPosition)
    {
        this.layerNameSignPosition = layerNameSignPosition.clone();
    }

    /**
     * Sets the layer exit position.
     * 
     * @param layerPlayerExitPosition
     *            the new layer player exit position
     */
    public void setLayerPlayerExitPosition(final int[] layerPlayerExitPosition)
    {
        this.layerPlayerExitPosition = layerPlayerExitPosition.clone();
    }

    /**
     * Sets the layer portal positions.
     * 
     * @param layerPortalPositions
     *            the new layer portal positions
     */
    public void setLayerPortalPositions(final ArrayList<Integer[]> layerPortalPositions)
    {
        this.layerPortalPositions = layerPortalPositions;
    }

    /**
     * Sets the layer redstone activation position.
     * 
     * @param layerRedstoneDialActivationPosition
     *            the new layer redstone dial activation position
     */
    public void setLayerRedstoneDialActivationPosition(final int[] layerRedstoneDialActivationPosition)
    {
        this.layerRedstoneDialActivationPosition = layerRedstoneDialActivationPosition.clone();
    }

    /**
     * Sets the layer redstone activation position.
     * 
     * @param layerRedstoneActivationPosition
     *            the new layer redstone activation position
     */
    public void setLayerRedstoneGateActivatedPosition(final int[] layerRedstoneGateActivatedPosition)
    {
        this.layerRedstoneGateActivatedPosition = layerRedstoneGateActivatedPosition.clone();
    }

    /**
     * Sets the layer redstone dialer activation position.
     * 
     * @param layerRedstoneSignActivationPosition
     *            the new layer redstone sign activation position
     */
    public void setLayerRedstoneSignActivationPosition(final int[] layerRedstoneSignActivationPosition)
    {
        this.layerRedstoneSignActivationPosition = layerRedstoneSignActivationPosition.clone();
    }

    /**
     * Sets the layer woosh positions.
     * 
     * @param layerWooshPositions
     *            the new layer woosh positions
     */
    public void setLayerWooshPositions(final ArrayList<ArrayList<Integer[]>> layerWooshPositions)
    {
        this.layerWooshPositions = layerWooshPositions;
    }
}
