/*
 * Registry and loader for .shape files. Extracted from StargateHelper.
 */
package com.wormhole_xtreme.wormhole.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.StargateShapeFactory;

public final class StargateShapeRegistry
{
    private static final ConcurrentHashMap<String, StargateShape> stargateShapes = new ConcurrentHashMap<String, StargateShape>();

    private StargateShapeRegistry() {}

    public static StargateShape getStargateShape(final String name)
    {
        if (getStargateShapes().containsKey(name))
        {
            return getStargateShapes().get(name);
        }

        return null;
    }

    public static ConcurrentHashMap<String, StargateShape> getStargateShapes()
    {
        return stargateShapes;
    }

    public static boolean isStargateShape(final String name)
    {
        return getStargateShapes().containsKey(name);
    }

    /**
     * Moves shapes up out of the old {@code 3d} and {@code 2d} subdirectories.
     *
     * <p>Shapes were once split into those two folders. They are read from one flat
     * directory now, so anything still sitting in a subfolder is simply not seen — a server
     * upgrading with custom shapes in {@code GateShapes/3d/} would find them silently gone,
     * with no error to explain it and their gates undetectable.
     *
     * <p>Files are moved rather than copied, and a shape already present at the top level
     * wins: that one is what has been loading, and overwriting it with an older copy from a
     * subfolder would undo whatever the owner had changed. The emptied folders are left
     * where they are, since removing directories is not this method's business.
     *
     * @param directory
     *            the flat GateShapes directory
     */
    private static void liftShapesOutOfLegacySubdirectories(final File directory)
    {
        for (final String legacy : new String[] { "3d", "2d" })
        {
            final File subdirectory = new File(directory, legacy);
            if (!subdirectory.isDirectory())
            {
                continue;
            }
            final File[] shapes = subdirectory.listFiles();
            if (shapes == null)
            {
                continue;
            }
            for (final File shape : shapes)
            {
                if (!shape.isFile() || !shape.getName().endsWith(".shape"))
                {
                    continue;
                }
                final File moved = new File(directory, shape.getName());
                if (moved.exists())
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                        "Ignoring " + legacy + File.separator + shape.getName()
                        + ": a shape of that name is already in use.");
                    continue;
                }
                if (shape.renameTo(moved))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                        "Moved gate shape " + shape.getName() + " out of " + legacy
                        + File.separator + "; shapes are read from one folder now.");
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false,
                        "Could not move gate shape " + shape.getName() + " out of " + legacy
                        + File.separator + "; it will not be loaded until it is moved by hand.");
                }
            }
        }
    }

    public static void loadShapes()
    {
        final File directory = new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "GateShapes" + File.separator);

        if (!directory.isDirectory())
        {
            // mkdirs rather than mkdir: this is two levels below plugins/ and on a first
            // run neither level need exist yet. And the result is checked, because failing
            // to create a directory returns false rather than throwing - so the old catch
            // could never fire, and the failure carried on to listFiles() returning null
            // and shapes silently not loading.
            boolean created = false;
            try
            {
                created = directory.mkdirs();
            }
            catch (final SecurityException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false,
                    "Not allowed to create " + directory.getPath() + ": " + e.getMessage());
            }
            if (!created && !directory.isDirectory())
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false,
                    "Could not create " + directory.getPath() + "; no gate shapes will be loaded.");
                return;
            }
        }

        liftShapesOutOfLegacySubdirectories(directory);

        final FilenameFilter filenameFilter = new FilenameFilter()
        {
            @Override
            public boolean accept(final File dir, final String name)
            {
                return !name.startsWith(".") && name.endsWith(".shape");
            }
        };

        // Geometry only. Palette variants used to live here as near-identical copies
        // (StandardAtlantis, StandardUniverse); they are material groups in config.yml now,
        // which keeps detection cost independent of how many palettes a server offers.
        final String[] defaultShapeNames = {"Standard.shape", "StandardSignDial.shape", "Minimal.shape",
            "MinimalSignDial.shape",
            "Horizontal.shape", "HorizontalSignDial.shape",
            "MinimalSignDialRedstone.shape"};
        for (final String shape : defaultShapeNames)
        {
            final File defaultShapeFile = new File(directory, shape);
            if (!defaultShapeFile.exists())
            {
                try (final InputStream is = WormholeXTreme.class.getResourceAsStream("/GateShapes/" + shape))
                {
                    if (is == null)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Default shape resource not found in JAR: " + shape);
                        continue;
                    }
                    try (final BufferedReader br = new BufferedReader(new InputStreamReader(is));
                         final BufferedWriter bw = new BufferedWriter(new FileWriter(defaultShapeFile)))
                    {
                        for (String s = ""; (s = br.readLine()) != null;)
                        {
                            bw.write(s);
                            bw.write("\n");
                        }
                    }
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "Restored default shape: " + shape);
                }
                catch (final IOException e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to create default shape file: " + e.getMessage());
                }
            }
        }

        final File[] shapeFiles = directory.listFiles(filenameFilter);
        for (final File fi : shapeFiles)
        {
            if (fi.getName().contains(".shape"))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, false, "Loading shape file: \"" + fi.getName() + "\"");
                BufferedReader bufferedReader = null;
                try
                {
                    final ArrayList<String> fileLines = new ArrayList<String>();
                    bufferedReader = new BufferedReader(new FileReader(fi));
                    for (String s = ""; (s = bufferedReader.readLine()) != null;)
                    {
                        fileLines.add(s);
                    }
                    bufferedReader.close();

                    final StargateShape shape = StargateShapeFactory.createShapeFromFile(fileLines.toArray(new String[fileLines.size()]));

                    if (getStargateShapes().containsKey(shape.getShapeName()))
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Shape File: " + fi.getName() + " contains shape name: " + shape.getShapeName() + " which already exists. This shape will be unavailable.");
                    }
                    else
                    {
                        getStargateShapes().put(shape.getShapeName(), shape);
                    }
                }
                catch (final FileNotFoundException e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to read shape file: " + e.getMessage());
                }
                catch (final IOException e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to read shape file: " + e.getMessage());
                }
                finally
                {
                    try
                    {
                        if (bufferedReader != null)
                        {
                            bufferedReader.close();
                        }
                    }
                    catch (final IOException e)
                    {
                        WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, e.getMessage());
                    }
                }
                WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, false, "Completed loading shape file: \"" + fi.getName() + "\"");
            }
        }

        if (getStargateShapes().size() == 0)
        {
            getStargateShapes().put("Standard", new StargateShape());
        }

        rebuildKnownStructureMaterials();
        reportShapesWithoutMaterialGroup();
    }

    /**
     * Surfaces palettes the loaded shapes imply but config.yml does not define, and — when
     * {@code gate-material-groups-autodiscover} is on — writes them into config.yml so the
     * admin can edit them and reuse them across other shapes.
     *
     * <p>A shape whose palette is not picked up still works: it keeps the materials named
     * in its own file. Discovery only makes that palette reusable.
     */
    private static void reportShapesWithoutMaterialGroup()
    {
        final java.util.List<MaterialGroup> discovered =
            MaterialGroupRegistry.discoverUndeclaredGroups(getStargateShapes().values());
        if (discovered.isEmpty())
        {
            return;
        }

        if (!com.wormhole_xtreme.wormhole.config.ConfigManager.isGateMaterialGroupsAutodiscover())
        {
            final java.util.List<String> names = new java.util.ArrayList<String>();
            for (final MaterialGroup g : discovered)
            {
                names.add(g.getName() + "=" + g.getStructureMaterial());
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "Gate shapes imply material groups not in config.yml: " + names
                + ". Auto-discovery is off, so they were not added; those shapes keep their own materials.");
            return;
        }

        // Register in memory first so the palettes work this run, not just after a restart.
        for (final MaterialGroup g : discovered)
        {
            MaterialGroupRegistry.registerDiscoveredGroup(g);
        }
        com.wormhole_xtreme.wormhole.config.ConfigManager.appendDiscoveredMaterialGroups(discovered);
        rebuildKnownStructureMaterials();
    }

    /** Frame materials any loaded shape declares. Replaced wholesale on load. */
    private static volatile java.util.Set<org.bukkit.Material> knownStructureMaterials = java.util.Collections.emptySet();

    /**
     * Gets every frame material a loaded shape declares.
     *
     * <p>Lets a caller reject a candidate gate position with one block read instead of a
     * geometry scan per shape: if the block a lever is mounted on is not a frame material
     * for any shape, no shape can match there.
     *
     * @return an unmodifiable set of frame materials
     */
    public static java.util.Set<org.bukkit.Material> getKnownStructureMaterials()
    {
        return knownStructureMaterials;
    }

    private static void rebuildKnownStructureMaterials()
    {
        final java.util.Set<org.bukkit.Material> materials = new java.util.HashSet<org.bukkit.Material>();
        for (final StargateShape shape : getStargateShapes().values())
        {
            if (shape.getShapeStructureMaterial() != null)
            {
                materials.add(shape.getShapeStructureMaterial());
            }
        }
        knownStructureMaterials = java.util.Collections.unmodifiableSet(materials);
    }
}
