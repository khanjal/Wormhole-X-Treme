package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The Class Stargate3DShape.
 */
public class Stargate3DShape extends StargateShape
{
    /**
     * Resolves a material name the same way a shape file's {@code PORTAL_MATERIAL} /
     * {@code IRIS_MATERIAL} / {@code STARGATE_MATERIAL} / {@code ACTIVE_MATERIAL} /
     * {@code SIGN_MATERIAL} lines are resolved when the file is actually loaded --
     * including the legacy {@code STATIONARY_WATER}/{@code STATIONARY_LAVA} aliases pre-1.13
     * shape files still use. Public so {@code ShapeFileValidator} checks a name against
     * exactly what this parser accepts rather than a stricter reading of its own that would
     * reject a shape the game loads fine.
     *
     * @param name the material name as written in the shape file
     * @return the resolved material, or null if nothing matches
     */
    public static Material parseMaterialName(final String name) {
        if (name == null) return null;
        final String n = name.trim().toUpperCase(Locale.ROOT);
        switch (n) {
            case "STATIONARY_WATER":
                return Material.WATER;
            case "STATIONARY_LAVA":
                return Material.LAVA;
            default:
                try { return Material.valueOf(n); } catch (final IllegalArgumentException e) { return null; }
        }
    }
    /**
     * Layers of the 3D shape. Layers go from 1 - 10
     */
    private final ArrayList<StargateShapeLayer> shapeLayers = new ArrayList<StargateShapeLayer>();

    /** The activation_layer. */
    private int shapeActivationLayer = -1;

    /** The sign_layer. */
    private int shapeSignLayer = -1;

    private boolean shapeRedstoneActivated = false;

    /**
     * Instantiates a new stargate3 d shape.
     * 
     * @param fileLines
     *            the file lines
     */
    public Stargate3DShape(final String[] fileLines)
    {
        setShapeSignPosition(new int[]{});
        setShapeEnterPosition(new int[]{});

        // 1. scan all lines for lines beginning with [  - that is the height of the gate
        int height = 0;
        int width = 0;
        int wooshDepth = 0;
        for (int i = 0; i < fileLines.length; i++)
        {
            final String line = fileLines[i];

            if (line.startsWith("#"))
            {
                continue;
            }

            if (line.contains("Name="))
            {
                setShapeName(line.split("=")[1]);
                WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Begin parsing shape: \"" + getShapeName() + "\"");
            }
            else if (line.equals("GateShape="))
            {
                int index = i;
                // Find start of first line
                while ( !fileLines[index].startsWith("["))
                {
                    index++;
                }

                while (fileLines[index].startsWith("["))
                {
                    if (width <= 0)
                    {
                        final Pattern p = Pattern.compile("(\\[.*?\\])");
                        final Matcher m = p.matcher(fileLines[index]);
                        while (m.find())
                        {
                            width++;
                        }
                    }

                    height++;
                    index++;
                }

                // At this point we should know the height and width
                if ((height <= 0) || (width <= 0))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to parse custom gate due to incorrect height or width: \"" + getShapeName() + "\"");
                    throw new IllegalArgumentException("Unable to parse custom gate due to incorrect height or width: \"" + getShapeName() + "\"");
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Shape: \"" + getShapeName() + "\"" + " Height: \"" + Integer.toString(height) + "\"" + " Width: \"" + Integer.toString(width) + "\"");
                }
            }
            else if (line.startsWith("Layer"))
            {
                // 1. get layer #
                final int layer = Integer.valueOf(line.trim().split("[#=]")[1]);

                // 2. add each line that starts with [ to a new string[]
                i++;
                final String[] layerLines = new String[height];
                int line_index = 0;
                while (fileLines[i].startsWith("[") || fileLines[i].startsWith("#"))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Layer=" + layer + " i=" + i + " line_index=" + line_index + " Line=" + fileLines[i]);
                    layerLines[line_index] = fileLines[i];
                    i++;

                    if (fileLines[i].startsWith("#"))
                    {
                        continue;
                    }

                    line_index++;
                }

                // 3. call constructor
                final StargateShapeLayer ssl = new StargateShapeLayer(layerLines, height, width);
                // bad hack to make sure list is big enough :(
                while (getShapeLayers().size() <= layer)
                {
                    getShapeLayers().add(null);
                }
                getShapeLayers().set(layer, ssl);

                if (ssl.getLayerActivationPosition().length > 0)
                {
                    setShapeActivationLayer(layer);
                }
                if (ssl.getLayerDialSignPosition().length > 0)
                {
                    setShapeSignLayer(layer);
                }
                if ((ssl.getLayerPlayerExitPosition() != null) && (ssl.getLayerPlayerExitPosition().length == 3))
                {
                    // This is only so we know it has been set or not and can warn players
                    setShapeEnterPosition(ssl.getLayerPlayerExitPosition());
                }
                if (ssl.getLayerWooshPositions().size() > 0)
                {
                    wooshDepth++;
                }
            }
            else if (line.contains("PORTAL_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapePortalMaterial(m);
            }
            else if (line.contains("IRIS_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapeIrisMaterial(m);
            }
            else if (line.contains("STARGATE_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapeStructureMaterial(m);
            }
            else if (line.contains("ACTIVE_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapeLightMaterial(m);
            }
            else if (line.contains("CHEVRON_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapeChevronMaterial(m);
            }
            else if (line.contains("SIGN_MATERIAL=") && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null) setShapeSignMaterial(m);
            }
            else if (line.contains("LIGHT_TICKS=") && (line.split("=").length > 1))
            {
                setShapeLightTicks(Integer.valueOf(line.split("=")[1]));
            }
            else if (line.contains("WOOSH_TICKS=") && (line.split("=").length > 1))
            {
                setShapeWooshTicks(Integer.valueOf(line.split("=")[1]));
            }
            else if (line.startsWith("REDSTONE_ACTIVATED=") && (line.split("=").length > 1))
            {
                setShapeRedstoneActivated(Boolean.valueOf(line.split("=")[1]));
            }
            else if (line.startsWith("MATERIAL_GROUPS=") && (line.split("=").length > 1))
            {
                setShapeMaterialGroups(line.split("=")[1]);
            }
        }

        setShapeWooshDepth(wooshDepth > 0
            ? wooshDepth
            : 0);
        setShapeWooshDepthSquared(getShapeWooshDepth() * getShapeWooshDepth());

        if (getShapeEnterPosition().length != 3)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Shape: \"" + getShapeName() + "\" does not have an enterance/exit point for players to teleport in. This will cause errors.");
            throw new IllegalArgumentException("Shape: \"" + getShapeName() + "\" does not have an enterance point for players to teleport in. This will cause errors.");
        }

        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Finished parsing shape: \"" + getShapeName() + "\"");
    }

    /**
     * Gets the shape activation layer.
     * 
     * @return the shape activation layer
     */
    public int getShapeActivationLayer()
    {
        return shapeActivationLayer;
    }

    /**
     * Gets the shape layers.
     * 
     * @return the shape layers
     */
    public ArrayList<StargateShapeLayer> getShapeLayers()
    {
        return shapeLayers;
    }

    /**
     * Gets the shape sign layer.
     * 
     * @return the shape sign layer
     */
    public int getShapeSignLayer()
    {
        return shapeSignLayer;
    }

    /**
     * Checks if is shape redstone activated.
     * 
     * @return true, if is shape redstone activated
     */
    public boolean isShapeRedstoneActivated()
    {
        return shapeRedstoneActivated;
    }

    /**
     * Sets the shape activation layer.
     * 
     * @param shapeActivationLayer
     *            the new shape activation layer
     */
    private void setShapeActivationLayer(final int shapeActivationLayer)
    {
        this.shapeActivationLayer = shapeActivationLayer;
    }

    /**
     * Sets the shape redstone activated.
     * 
     * @param shapeRedstoneActivated
     *            the new shape redstone activated
     */
    private void setShapeRedstoneActivated(final boolean shapeRedstoneActivated)
    {
        this.shapeRedstoneActivated = shapeRedstoneActivated;
    }

    /**
     * Sets the shape sign layer.
     * 
     * @param shapeSignLayer
     *            the new shape sign layer
     */
    private void setShapeSignLayer(final int shapeSignLayer)
    {
        this.shapeSignLayer = shapeSignLayer;
    }
}
