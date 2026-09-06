/*
 * Registry and loader for .shape files. Extracted from StargateHelper.
 */
package com.wormhole_xtreme.wormhole.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;
import com.wormhole_xtreme.wormhole.logic.StargateShapeFactory;

public final class StargateShapeRegistry
{
    private static final ConcurrentHashMap<String, StargateShape> stargateShapes = new ConcurrentHashMap<String, StargateShape>();

    private StargateShapeRegistry() {}

    /** @return the GateShapes directory, the same one {@link #loadShapes()} reads from */
    private static File shapeDirectory()
    {
        return new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "GateShapes" + File.separator);
    }

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
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                        "Ignoring " + legacy + File.separator + shape.getName()
                        + ": a shape of that name is already in use.");
                    continue;
                }
                if (shape.renameTo(moved))
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
                        "Moved gate shape " + shape.getName() + " out of " + legacy
                        + File.separator + "; shapes are read from one folder now.");
                }
                else
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                        "Could not move gate shape " + shape.getName() + " out of " + legacy
                        + File.separator + "; it will not be loaded until it is moved by hand.");
                }
            }
        }
    }

    public static void loadShapes()
    {
        loadShapes(shapeDirectory());
    }

    /**
     * Loads every shape in a given directory, restoring the shipped ones if they are missing.
     *
     * <p>Split out from {@link #loadShapes()} so a test can point it somewhere other than the
     * live plugin folder: {@link #shapeDirectory()} is a fixed path relative to the working
     * directory, and running the real one under test would write into the project.
     *
     * @param directory
     *            the folder to load from, created if it is not there
     */
    static void loadShapes(final File directory)
    {
        if (!ensureDirectory(directory))
        {
            return;
        }
        liftShapesOutOfLegacySubdirectories(directory);
        restoreMissingDefaults(directory);
        readShapesIn(directory);

        if (getStargateShapes().isEmpty())
        {
            getStargateShapes().put("Standard", new StargateShape());
        }
        rebuildKnownStructureMaterials();
        reportShapesWithoutMaterialGroup();
    }

    /**
     * Makes sure the shapes folder is there.
     *
     * <p>mkdirs rather than mkdir: this is two levels below plugins/ and on a first run
     * neither level need exist yet. The result is checked, because failing to create a
     * directory returns false rather than throwing -- the old catch could never fire, and the
     * failure carried on to listFiles() returning null and shapes silently not loading.
     *
     * @return true if there is a directory to work with
     */
    private static boolean ensureDirectory(final File directory)
    {
        if (directory.isDirectory())
        {
            return true;
        }
        boolean created = false;
        try
        {
            created = directory.mkdirs();
        }
        catch (final SecurityException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Not allowed to create " + directory.getPath() + ": " + e.getMessage());
        }
        if (!created && !directory.isDirectory())
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Could not create " + directory.getPath() + "; no gate shapes will be loaded.");
            return false;
        }
        return true;
    }

    /**
     * Writes out any shipped shape the folder does not already have.
     *
     * <p>Geometry only. Palette variants used to live here as near-identical copies
     * (StandardAtlantis, StandardUniverse); they are material groups in config.yml now, which
     * keeps detection cost independent of how many palettes a server offers.
     */
    private static void restoreMissingDefaults(final File directory)
    {
        final String[] defaultShapeNames = {"Standard.shape", "StandardSignDial.shape", "Minimal.shape",
            "MinimalSignDial.shape",
            "Horizontal.shape", "HorizontalSignDial.shape",
            "Even.shape", "EvenSignDial.shape",
            "Large.shape", "Grand.shape", "Massive.shape"};
        for (final String shape : defaultShapeNames)
        {
            final File defaultShapeFile = new File(directory, shape);
            if (!defaultShapeFile.exists())
            {
                restoreDefault(shape, defaultShapeFile);
            }
        }
    }

    /** Copies one shipped shape out of the jar. */
    private static void restoreDefault(final String shape, final File defaultShapeFile)
    {
        try (final InputStream is = WormholeXTreme.class.getResourceAsStream("/GateShapes/" + shape))
        {
            if (is == null)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Default shape resource not found in JAR: " + shape);
                return;
            }
            try (final BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                 final BufferedWriter bw = new BufferedWriter(new FileWriter(defaultShapeFile, StandardCharsets.UTF_8)))
            {
                for (String s = ""; (s = br.readLine()) != null;)
                {
                    bw.write(s);
                    bw.write("\n");
                }
            }
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Restored default shape: " + shape);
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to create default shape file: " + e.getMessage());
        }
    }

    /** Reads every shape file in the folder, registering each under the name it declares. */
    private static void readShapesIn(final File directory)
    {
        final File[] shapeFiles = directory.listFiles(
            (dir, name) -> !name.startsWith(".") && name.endsWith(".shape"));
        if (shapeFiles == null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Could not read " + directory.getPath() + "; no gate shapes will be loaded.");
            return;
        }
        for (final File fi : shapeFiles)
        {
            registerShapeFile(fi);
        }
    }

    /**
     * Reads one shape file and registers what it declares.
     *
     * <p>Per file, and catching more than IOException: the whole folder is read on startup,
     * and a malformed grid throws IllegalArgumentException. One operator's typo used to leave
     * the server with no gate shapes at all, the shipped ones included.
     */
    private static void registerShapeFile(final File fi)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.CONFIG, "Loading shape file: \"" + fi.getName() + "\"");
        try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(fi, StandardCharsets.UTF_8)))
        {
            final ArrayList<String> fileLines = new ArrayList<String>();
            for (String s = ""; (s = bufferedReader.readLine()) != null;)
            {
                fileLines.add(s);
            }
            final StargateShape shape = StargateShapeFactory.createShapeFromFile(fileLines.toArray(new String[fileLines.size()]));
            if (getStargateShapes().containsKey(shape.getShapeName()))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Shape File: " + fi.getName() + " contains shape name: " + shape.getShapeName() + " which already exists. This shape will be unavailable.");
                return;
            }
            getStargateShapes().put(shape.getShapeName(), shape);
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, "Unable to read shape file: " + e.getMessage());
        }
        catch (final RuntimeException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE,
                "Unable to parse shape file " + fi.getName() + ", skipping it: " + e.getMessage());
        }
    }

    /**
     * Re-scans the whole GateShapes directory from scratch, so an admin trying out shape
     * edits does not have to restart the server to see them.
     *
     * <p>Unlike a single-file {@link #reloadShapeFile}, this can just clear the registry and
     * call {@link #loadShapes()} again: {@code loadShapes()}'s "name already exists" skip only
     * exists to guard against two different files in the directory claiming the same name, and
     * an empty map can never trigger it, so every file is read fresh with no special-casing
     * needed here.
     *
     * <p>Gates already built keep whichever {@link StargateShape} instance they already hold a
     * reference to; only shapes resolved after this call see the reloaded versions. That is
     * fine for the workflow this exists for -- testing a shape before building anything real
     * with it -- but this is not a live hot-swap for a shape gates already stand on.
     */
    public static void reloadAllShapes()
    {
        getStargateShapes().clear();
        loadShapes();
    }

    /**
     * Re-reads one shape file from disk and, if it is valid, replaces its entry in the
     * registry -- or adds it, if this is a file nothing had loaded yet. Reused by
     * {@code /wormhole gate shapes reload <name>} so a shape author can check one file at a
     * time without waiting on every other shape to re-parse too.
     *
     * <p>The registry is keyed by the shape's declared {@code Name=}, not its filename. If a
     * reload changes that name, the old key is left exactly as it was -- this does not go
     * hunting for an entry that might now be stale under a name the file no longer claims.
     * {@link #reloadAllShapes()} is the way to clear that kind of drift.
     *
     * @param fileName
     *            the file's name within the GateShapes directory, e.g. {@code "Standard.shape"}
     * @return the validation result; the registry is only changed if it came back valid
     */
    public static ShapeFileValidator.Result reloadShapeFile(final String fileName)
    {
        final String[] lines = readShapeFileLines(fileName);
        if (lines == null)
        {
            return ShapeFileValidator.Result.unreadable("No such file: " + fileName);
        }
        return replaceIfValid(lines);
    }

    /**
     * Checks one shape file the same way {@link #reloadShapeFile} would, without changing the
     * registry either way -- for looking a shape over before deciding it is worth loading, or
     * checking a fix landed without disturbing whatever is already active under that name.
     *
     * @param fileName
     *            the file's name within the GateShapes directory, e.g. {@code "Standard.shape"}
     * @return the validation result
     */
    public static ShapeFileValidator.Result validateShapeFile(final String fileName)
    {
        final String[] lines = readShapeFileLines(fileName);
        if (lines == null)
        {
            return ShapeFileValidator.Result.unreadable("No such file: " + fileName);
        }
        return ShapeFileValidator.validate(lines);
    }

    /** @return the file's lines, or null if it does not exist or could not be read */
    private static String[] readShapeFileLines(final String fileName)
    {
        final File file = new File(shapeDirectory(), fileName);
        if (!file.isFile())
        {
            return null;
        }
        final ArrayList<String> fileLines = new ArrayList<String>();
        try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)))
        {
            for (String s = ""; (s = bufferedReader.readLine()) != null;)
            {
                fileLines.add(s);
            }
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                "Unable to read " + fileName + ": " + e.getMessage());
            return null;
        }
        return fileLines.toArray(new String[fileLines.size()]);
    }

    /**
     * The part of {@link #reloadShapeFile} that has nothing to do with the filesystem:
     * validate, and only touch the registry if that came back clean. Split out so the
     * validate-then-replace decision can be tested against plain lines, the same reason
     * {@link ShapeFileValidator} itself takes lines rather than a file.
     */
    static ShapeFileValidator.Result replaceIfValid(final String[] lines)
    {
        final ShapeFileValidator.Result result = ShapeFileValidator.validate(lines);
        if (result.isValid())
        {
            final StargateShape shape = StargateShapeFactory.createShapeFromFile(lines);
            getStargateShapes().put(shape.getShapeName(), shape);
            rebuildKnownStructureMaterials();
        }
        return result;
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO,
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
    // Immutable snapshot swapped in wholesale; volatile publishes the new reference.
    @SuppressWarnings("java:S3077")
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
