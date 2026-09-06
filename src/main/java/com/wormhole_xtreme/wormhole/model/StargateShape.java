package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The Class StargateShape.
 */
public class StargateShape
{

    /** The shape name. */
    private String shapeName = "Standard";

    /** The stargate_positions. */
    private int[][] shapeStructurePositions = {{0, 2, 0}, {0, 3, 0}, {0, 4, 0}, {0, 1, 1}, {0, 5, 1}, {0, 0, 2},
        {0, 6, 2}, {0, 6, 3}, {0, 0, 3}, {0, 0, 4}, {0, 6, 4}, {0, 5, 5}, {0, 1, 5}, {0, 2, 6}, {0, 3, 6}, {0, 4, 6}};

    /** The sign_position. */
    private int[] shapeSignPosition = {0, 3, 6};

    /** The enter_position. */
    private int[] shapeEnterPosition = {0, 0, 3};

    /** The light_positions. */
    private int[] shapeLightPositions = {3, 4, 11, 12};

    /** The water_positions. */
    private int[][] shapePortalPositions = {{0, 2, 1}, {0, 3, 1}, {0, 4, 1}, {0, 1, 2}, {0, 2, 2}, {0, 3, 2},
        {0, 4, 2}, {0, 5, 2}, {0, 1, 3}, {0, 2, 3}, {0, 3, 3}, {0, 4, 3}, {0, 5, 3}, {0, 1, 4}, {0, 2, 4}, {0, 3, 4},
        {0, 4, 4}, {0, 5, 4}, {0, 2, 5}, {0, 3, 5}, {0, 4, 5}};

    /** The reference_vector, this vector always points up for calculating cross product. */
    private int[] shapeReferenceVector = {0, 1, 0};

    /** [0] = Left - / Right + [1] = Up + / Down - [2] = Forward + / Backward -. */
    private int[] shapeToGateCorner = {1, -1, 4};

    /** The woosh_depth. */
    private int shapeWooshDepth = 0;

    /** The square of the woosh_depth, used in comparisions with squared distance. */
    private int shapeWooshDepthSquared = 0;

    /** The portal material. */
    private Material shapePortalMaterial = Material.WATER;

    /** The iris material. */
    private Material shapeIrisMaterial = Material.STONE;

    /** The stargate material. */
    private Material shapeStructureMaterial = Material.OBSIDIAN;

    /** The active material. */
    private Material shapeLightMaterial = Material.GLOWSTONE;

    /** The sign material used for name and dial signs. */
    private Material shapeSignMaterial = Material.OAK_WALL_SIGN;

    /**
     * What an unlit chevron is built from, or null if this shape has no distinct chevrons.
     *
     * <p>Null rather than a default, because there is no safe default to pick: every gate
     * standing in every world today has frame material at its chevron positions, so a shape
     * that has not asked for distinct chevrons must go on accepting exactly what it accepted
     * before. This is also why it needs no {@code explicit} flag like the materials above --
     * a non-null value already means the file named one.
     */
    private Material shapeChevronMaterial = null;

    /**
     * Materials this shape's file named outright, as opposed to inheriting the defaults
     * above. A shape that asks for a glass iris means it — a horizontal gate is meant to
     * be seen through — so an explicit value outranks whatever palette the gate resolves
     * to. Anything the file leaves unsaid is the palette's to fill in.
     */
    private boolean explicitPortalMaterial = false;
    private boolean explicitIrisMaterial = false;
    private boolean explicitStructureMaterial = false;
    private boolean explicitLightMaterial = false;
    private boolean explicitSignMaterial = false;

    /**
     * Material groups this shape may be built from, lowercased. Empty means every
     * configured group is accepted, which is the default and what most shapes want:
     * geometry and palette are independent, so any shape can be built in any palette.
     * A shape restricts this only when a palette would make it ambiguous against
     * another shape with the same frame layout.
     */
    private final java.util.Set<String> shapeMaterialGroups = new java.util.HashSet<String>();

    /** The shape woosh ticks. */
    private int shapeWooshTicks = 3;

    /** The shape light ticks. */
    private int shapeLightTicks = 3;

    /**
     * Instantiates a new stargate shape.
     */
    public StargateShape()
    {
//        setShapeWooshDepth(3);
//        setShapeWooshDepthSquared(9);
    }

    /**
     * Instantiates a new stargate shape.
     * 
     * @param file_data
     *            the file_data
     */
    /** One [X] marker in a shape row. */
    private static final Pattern MARKER = Pattern.compile("(\\[.*?\\])");

    /** The material keys a shape file may carry, each against the setting it fills. */
    private static final java.util.Map<String, java.util.function.BiConsumer<StargateShape, Material>> MATERIAL_KEYS =
        java.util.Map.of(
            "PORTAL_MATERIAL", StargateShape::setShapePortalMaterial,
            "IRIS_MATERIAL", StargateShape::setShapeIrisMaterial,
            "STARGATE_MATERIAL", StargateShape::setShapeStructureMaterial,
            "ACTIVE_MATERIAL", StargateShape::setShapeLightMaterial,
            "CHEVRON_MATERIAL", StargateShape::setShapeChevronMaterial,
            "SIGN_MATERIAL", StargateShape::setShapeSignMaterial);

    /** Which slot of the corner offset each BUTTON_ key fills. */
    private static final java.util.Map<String, Integer> BUTTON_AXES =
        java.util.Map.of("BUTTON_RIGHT", 0, "BUTTON_UP", 1, "BUTTON_AWAY", 2);

    public StargateShape(final String[] file_data)
    {
        setShapeSignPosition(new int[]{});
        setShapeEnterPosition(new int[]{});

        final ArrayList<Integer[]> blockPositions = new ArrayList<Integer[]>();
        final ArrayList<Integer[]> portalPositions = new ArrayList<Integer[]>();
        final ArrayList<Integer> lightPositions = new ArrayList<Integer>();

        for (int i = 0; i < file_data.length; i++)
        {
            final String line = file_data[i];

            if (line.contains("Name="))
            {
                shapeName = line.split("=")[1];
                WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Begin parsing shape: \"" + shapeName + "\"");
            }
            else if (line.equals("GateShape="))
            {
                final int[] grid = measureGrid(file_data, i + 1);
                parseGrid(file_data, i + 1, grid, blockPositions, portalPositions, lightPositions);
            }
            else
            {
                applySetting(line);
            }
        }

        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Stargate Sign Position: \"" + Arrays.toString(getShapeSignPosition()) + "\"");
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Stargate Enter Position: \"" + Arrays.toString(getShapeEnterPosition()) + "\"");
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Stargate Button Position [Left/Right,Up/Down,Forward/Back]: \"" + Arrays.toString(getShapeToGateCorner()) + "\"");

        setShapePortalPositions(toPoints(portalPositions));
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Stargate Portal Positions: \"" + Arrays.deepToString(getShapePortalPositions()) + "\"");

        final int[] tempLightPositions = new int[lightPositions.size()];
        for (int i = 0; i < lightPositions.size(); i++)
        {
            tempLightPositions[i] = lightPositions.get(i);
        }
        setShapeLightPositions(tempLightPositions);
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Light Material Positions: \"" + Arrays.toString(getShapeLightPositions()) + "\"");

        setShapeStructurePositions(toPoints(blockPositions));
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Stargate Material Positions: \"" + Arrays.deepToString(getShapeStructurePositions()) + "\"");
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Finished parsing shape: \"" + shapeName + "\"");

        setShapeWooshDepthSquared(getShapeWooshDepth() * getShapeWooshDepth());
    }

    /**
     * Measures the grid that follows a {@code GateShape=} line.
     *
     * @param from
     *            the first row of the grid
     * @return the height and the width, in that order
     */
    private int[] measureGrid(final String[] fileData, final int from)
    {
        int height = 0;
        int width = 0;
        int index = from;
        while (fileData[index].startsWith("["))
        {
            if (width <= 0)
            {
                final Matcher m = MARKER.matcher(fileData[index]);
                while (m.find())
                {
                    width++;
                }
            }
            height++;
            index++;
        }

        if ((height <= 0) || (width <= 0))
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to parse custom gate due to incorrect height or width: \"" + shapeName + "\"");
            throw new IllegalArgumentException("Unable to parse custom gate due to incorrect height or width: \"" + shapeName + "\"");
        }
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Shape: \"" + shapeName + "\"" + " Height: \"" + Integer.toString(height) + "\"" + " Width: \"" + Integer.toString(width) + "\"");
        return new int[] {height, width};
    }

    /**
     * Reads every marker in the grid into the collections the shape is built from.
     *
     * <p>Version one's alphabet is its own: {@code O} is a structure block, {@code P} the
     * portal, {@code S} the sign and {@code E} the entry point. A light is {@code L} on a
     * block that is also {@code O}, and is recorded as that block's number rather than as
     * coordinates.
     */
    private void parseGrid(final String[] fileData, final int from, final int[] grid,
        final ArrayList<Integer[]> blockPositions, final ArrayList<Integer[]> portalPositions,
        final ArrayList<Integer> lightPositions)
    {
        final int height = grid[0];
        final int width = grid[1];
        int numBlocks = 0;
        int index = from;
        while (fileData[index].startsWith("["))
        {
            final Matcher m = MARKER.matcher(fileData[index]);
            int j = 0;
            while (m.find())
            {
                final String block = m.group(0);
                final Integer[] point = {0, (height - 1 - (index - from)), (width - 1 - j)};
                if (block.contains("O"))
                {
                    numBlocks++;
                    blockPositions.add(point);
                }
                else if (block.contains("P"))
                {
                    portalPositions.add(point);
                }

                if (block.contains("S"))
                {
                    setShapeSignPosition(toPoint(point));
                }
                if (block.contains("E"))
                {
                    setShapeEnterPosition(toPoint(point));
                }
                if (block.contains("L") && block.contains("O"))
                {
                    lightPositions.add(numBlocks - 1);
                }
                j++;
            }
            index++;
        }
    }

    /**
     * Applies one {@code KEY=value} line that is neither the name nor the grid.
     *
     * <p>An unrecognised line is ignored, which is what lets a shape file carry comments and
     * keys written for a later version of the plugin.
     */
    private void applySetting(final String line)
    {
        for (final java.util.Map.Entry<String, Integer> axis : BUTTON_AXES.entrySet())
        {
            if (line.contains(axis.getKey()))
            {
                // Read back, changed, and set again: the getter hands out a clone, so writing
                // through it went nowhere and every BUTTON_ line was silently dropped.
                final int[] corner = getShapeToGateCorner();
                corner[axis.getValue()] = Integer.parseInt(line.split("=")[1]);
                setShapeToGateCorner(corner);
                return;
            }
        }
        if (line.contains("WOOSH_DEPTH"))
        {
            setShapeWooshDepth(Integer.parseInt(line.split("=")[1]));
            return;
        }
        for (final java.util.Map.Entry<String, java.util.function.BiConsumer<StargateShape, Material>> key
            : MATERIAL_KEYS.entrySet())
        {
            if (line.contains(key.getKey()) && (line.split("=").length > 1))
            {
                final Material m = parseMaterialName(line.split("=")[1]);
                if (m != null)
                {
                    key.getValue().accept(this, m);
                }
                return;
            }
        }
    }

    /**
     * Reads a material name from a shape file, tolerating one this server does not know.
     *
     * <p>Shapes outlive the Minecraft versions they were written for, so an unreadable name
     * means fall back to the palette rather than refuse the whole shape.
     *
     * @param name
     *            the name as written in the file
     * @return the material, or null if this server has no such block
     */
    public static Material parseMaterialName(final String name)
    {
        if (name == null)
        {
            return null;
        }
        try
        {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException notOnThisServer)
        {
            return null;
        }
    }

    private static int[] toPoint(final Integer[] point)
    {
        return new int[] {point[0], point[1], point[2]};
    }

    private static int[][] toPoints(final ArrayList<Integer[]> points)
    {
        final int[][] out = new int[points.size()][3];
        for (int i = 0; i < points.size(); i++)
        {
            out[i] = toPoint(points.get(i));
        }
        return out;
    }

    /**
     * Gets the shape enter position.
     * 
     * @return the shape enter position
     */
    public int[] getShapeEnterPosition()
    {
        return shapeEnterPosition.clone();
    }

    /**
     * Gets the shape iris material.
     * 
     * @return the shape iris material
     */
    public Material getShapeIrisMaterial()
    {
        return shapeIrisMaterial;
    }

    /**
     * Gets the shape active material.
     * 
     * @return the shape active material
     */
    public Material getShapeLightMaterial()
    {
        return shapeLightMaterial;
    }

    /**
     * Gets the material an unlit chevron is built from.
     *
     * @return the chevron material, or null if this shape has no distinct chevrons
     */
    public Material getShapeChevronMaterial()
    {
        return shapeChevronMaterial;
    }

    /**
     * Sets the material an unlit chevron is built from.
     *
     * @param shapeChevronMaterial
     *            the chevron material
     */
    public void setShapeChevronMaterial(final Material shapeChevronMaterial)
    {
        this.shapeChevronMaterial = shapeChevronMaterial;
    }

    /**
     * Gets the shape light positions.
     * 
     * @return the shape light positions
     */
    public int[] getShapeLightPositions()
    {
        return shapeLightPositions.clone();
    }

    /**
     * Gets the shape light ticks.
     * 
     * @return the shape light ticks
     */
    public int getShapeLightTicks()
    {
        return shapeLightTicks;
    }

    /**
     * Gets the shape name.
     * 
     * @return the shape name
     */
    public String getShapeName()
    {
        return shapeName;
    }

    /**
     * Gets the shape portal material.
     * 
     * @return the shape portal material
     */
    public Material getShapePortalMaterial()
    {
        return shapePortalMaterial;
    }

    /**
     * Gets the shape water positions.
     * 
     * @return the shape water positions
     */
    public int[][] getShapePortalPositions()
    {
        return shapePortalPositions.clone();
    }

    /**
     * Gets the shape reference vector.
     * 
     * @return the shape reference vector
     */
    public int[] getShapeReferenceVector()
    {
        return shapeReferenceVector.clone();
    }

    /**
     * Gets the shape sign position.
     * 
     * @return the shape sign position
     */
    public int[] getShapeSignPosition()
    {
        return shapeSignPosition.clone();
    }

    /**
     * Gets the shape structure material.
     * 
     * @return the shape structure material
     */
    /** @return true if the shape file named a portal material outright */
    public boolean hasExplicitPortalMaterial() { return explicitPortalMaterial; }

    /** @return true if the shape file named an iris material outright */
    public boolean hasExplicitIrisMaterial() { return explicitIrisMaterial; }

    /** @return true if the shape file named a structure material outright */
    public boolean hasExplicitStructureMaterial() { return explicitStructureMaterial; }

    /** @return true if the shape file named an active/light material outright */
    public boolean hasExplicitLightMaterial() { return explicitLightMaterial; }

    /** @return true if the shape file named a sign material outright */
    public boolean hasExplicitSignMaterial() { return explicitSignMaterial; }

    /**
     * Checks whether this shape may be built from the named material group.
     *
     * @param groupName
     *            the group name, case-insensitive
     * @return true if the shape declares no restriction, or names this group
     */
    public boolean acceptsMaterialGroup(final String groupName)
    {
        if (shapeMaterialGroups.isEmpty())
        {
            return true;
        }
        return groupName != null && shapeMaterialGroups.contains(groupName.toLowerCase(Locale.ROOT));
    }

    /**
     * Restricts this shape to a comma-separated list of material group names.
     * An empty or blank list clears the restriction.
     *
     * @param csv
     *            the comma-separated group names from the shape file
     */
    public void setShapeMaterialGroups(final String csv)
    {
        shapeMaterialGroups.clear();
        if (csv == null)
        {
            return;
        }
        for (final String part : csv.split(","))
        {
            final String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty())
            {
                shapeMaterialGroups.add(trimmed);
            }
        }
    }

    public Material getShapeStructureMaterial()
    {
        return shapeStructureMaterial;
    }

    /**
     * Gets the shape structure positions.
     * 
     * @return the shape structure positions
     */
    public int[][] getShapeStructurePositions()
    {
        return shapeStructurePositions.clone();
    }

    /**
     * Gets the shape to gate corner.
     * 
     * @return the shape to gate corner
     */
    public int[] getShapeToGateCorner()
    {
        return shapeToGateCorner.clone();
    }

    /**
     * Gets the shape woosh depth.
     * 
     * @return the shape woosh depth
     */
    public int getShapeWooshDepth()
    {
        return shapeWooshDepth;
    }

    /**
     * Gets the shape woosh depth squared.
     * 
     * @return the shape woosh depth squared
     */
    public int getShapeWooshDepthSquared()
    {
        return shapeWooshDepthSquared;
    }

    /**
     * Gets the shape woosh ticks.
     * 
     * @return the shape woosh ticks
     */
    public int getShapeWooshTicks()
    {
        return shapeWooshTicks;
    }

    /**
     * Sets the shape enter position.
     * 
     * @param shapeEnterPosition
     *            the new shape enter position
     */
    public void setShapeEnterPosition(final int[] shapeEnterPosition)
    {
        this.shapeEnterPosition = shapeEnterPosition.clone();
    }

    /**
     * Sets the shape iris material.
     * 
     * @param shapeIrisMaterial
     *            the new shape iris material
     */
    public void setShapeIrisMaterial(final Material shapeIrisMaterial)
    {
        this.shapeIrisMaterial = shapeIrisMaterial;
        explicitIrisMaterial = true;
    }

    /**
     * Sets the shape active material.
     * 
     * @param shapeLightMaterial
     *            the new shape light material
     */
    public void setShapeLightMaterial(final Material shapeLightMaterial)
    {
        this.shapeLightMaterial = shapeLightMaterial;
        explicitLightMaterial = true;
    }

    /**
     * Sets the shape light positions.
     * 
     * @param shapeLightPositions
     *            the new shape light positions
     */
    public void setShapeLightPositions(final int[] shapeLightPositions)
    {
        this.shapeLightPositions = shapeLightPositions.clone();
    }

    /**
     * Sets the shape light ticks.
     * 
     * @param shapeLightTicks
     *            the new shape light ticks
     */
    public void setShapeLightTicks(final int shapeLightTicks)
    {
        this.shapeLightTicks = shapeLightTicks;
    }

    /**
     * Sets the shape name.
     * 
     * @param shapeName
     *            the new shape name
     */
    public void setShapeName(final String shapeName)
    {
        this.shapeName = shapeName;
    }

    /**
     * Sets the shape portal material.
     * 
     * @param shapePortalMaterial
     *            the new shape portal material
     */
    public void setShapePortalMaterial(final Material shapePortalMaterial)
    {
        this.shapePortalMaterial = shapePortalMaterial;
        explicitPortalMaterial = true;
    }

    /**
     * Sets the shape water positions.
     * 
     * @param shapePortalPositions
     *            the new shape portal positions
     */
    public void setShapePortalPositions(final int[][] shapePortalPositions)
    {
        this.shapePortalPositions = shapePortalPositions.clone();
    }

    /**
     * Sets the shape reference vector.
     * 
     * @param shapeReferenceVector
     *            the new shape reference vector
     */
    public void setShapeReferenceVector(final int[] shapeReferenceVector)
    {
        this.shapeReferenceVector = shapeReferenceVector.clone();
    }

    /**
     * Sets the shape sign position.
     * 
     * @param shapeSignPosition
     *            the new shape sign position
     */
    public void setShapeSignPosition(final int[] shapeSignPosition)
    {
        this.shapeSignPosition = shapeSignPosition.clone();
    }

    /**
     * Sets the shape structure material.
     * 
     * @param shapeStructureMaterial
     *            the new shape structure material
     */
    public void setShapeStructureMaterial(final Material shapeStructureMaterial)
    {
        this.shapeStructureMaterial = shapeStructureMaterial;
        explicitStructureMaterial = true;
    }

    public Material getShapeSignMaterial()
    {
        return shapeSignMaterial;
    }

    public void setShapeSignMaterial(final Material shapeSignMaterial)
    {
        this.shapeSignMaterial = shapeSignMaterial;
        explicitSignMaterial = true;
    }

    /**
     * Sets the shape structure positions.
     * 
     * @param shapeStructurePositions
     *            the new shape structure positions
     */
    public void setShapeStructurePositions(final int[][] shapeStructurePositions)
    {
        this.shapeStructurePositions = shapeStructurePositions.clone();
    }

    /**
     * Sets the shape to gate corner.
     * 
     * @param shapeToGateCorner
     *            the new shape to gate corner
     */
    public void setShapeToGateCorner(final int[] shapeToGateCorner)
    {
        this.shapeToGateCorner = shapeToGateCorner.clone();
    }

    /**
     * Sets the shape woosh depth.
     * 
     * @param shapeWooshDepth
     *            the new shape woosh depth
     */
    public void setShapeWooshDepth(final int shapeWooshDepth)
    {
        this.shapeWooshDepth = shapeWooshDepth;
    }

    /**
     * Sets the shape woosh depth squared.
     * 
     * @param shapeWooshDepthSquared
     *            the new shape woosh depth squared
     */
    public void setShapeWooshDepthSquared(final int shapeWooshDepthSquared)
    {
        this.shapeWooshDepthSquared = shapeWooshDepthSquared;
    }

    /**
     * Sets the shape woosh ticks.
     * 
     * @param shapeWooshTicks
     *            the new shape woosh ticks
     */
    public void setShapeWooshTicks(final int shapeWooshTicks)
    {
        this.shapeWooshTicks = shapeWooshTicks;
    }
}
