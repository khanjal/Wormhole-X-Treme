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

    public static void loadShapes()
    {
        final File directory = new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "GateShapes" + File.separator);

        if ( !directory.exists())
        {
            try
            {
                directory.mkdir();
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to make directory: " + e.getMessage());
            }
        }

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
                try (final InputStream is = WormholeXTreme.class.getResourceAsStream("/GateShapes/3d/" + shape))
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
     * Notes any shape whose frame material matches no configured material group.
     *
     * <p>Such a shape works fine — it keeps its own materials and simply never picks up a
     * palette — so this is information, not a warning. It exists because that fact is
     * otherwise invisible: an admin who adds a custom blackstone shape has no way to know
     * a matching {@code gate-material-groups} entry would let them reuse it across shapes.
     *
     * <p>Nothing is written to config.yml. Editing a server's configuration on its behalf
     * is not this plugin's call to make.
     */
    private static void reportShapesWithoutMaterialGroup()
    {
        final java.util.Set<org.bukkit.Material> unmatched = new java.util.LinkedHashSet<org.bukkit.Material>();
        for (final org.bukkit.Material m : getKnownStructureMaterials())
        {
            if (MaterialGroupRegistry.getGroupByStructureMaterial(m) == null)
            {
                unmatched.add(m);
            }
        }
        if (!unmatched.isEmpty())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false,
                "Shape frame materials with no matching material group: " + unmatched
                + ". Gates built from these keep the materials named in their .shape file."
                + " Add a gate-material-groups entry in config.yml to reuse the palette across shapes.");
        }
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
