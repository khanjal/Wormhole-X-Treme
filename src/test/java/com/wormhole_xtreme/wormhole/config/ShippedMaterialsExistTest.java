package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Every material this plugin ships by name has to exist in the Minecraft version it targets.
 *
 * <p>Materials named in `config.yml` and in the shape files are text, resolved at runtime.
 * The compiler never sees them, so a material that Minecraft renames or removes compiles
 * perfectly and then fails on a live server — a palette that silently resolves to nothing,
 * or a gate that cannot be detected because its frame material no longer exists.
 *
 * <p>That risk arrives with every Minecraft version bump, and a bump is exactly when nobody
 * is looking at the shipped defaults. This checks them against whatever API the build is
 * compiled against, so raising the target version is what runs the test.
 */
public class ShippedMaterialsExistTest
{
    /** A run of capitals, digits and underscores: how a material name is written. */
    private static final Pattern CANDIDATE = Pattern.compile("\\b[A-Z][A-Z0-9_]{2,}\\b");

    /**
     * Words that look like materials but are this plugin's own vocabulary.
     *
     * <p>Listed explicitly rather than guessed at, so a genuine material never gets skipped
     * by a rule that was trying to be clever.
     */
    private static final List<String> NOT_MATERIALS = java.util.Arrays.asList(
        "MATERIAL_GROUPS", "PORTAL_MATERIAL", "IRIS_MATERIAL", "STARGATE_MATERIAL",
        "ACTIVE_MATERIAL", "SIGN_MATERIAL", "LIGHT_TICKS", "WOOSH_TICKS",
        "REDSTONE_ACTIVATED", "TRUE", "FALSE", "WALL_SIGN");

    private static List<Path> shippedResources() throws IOException
    {
        final List<Path> files = new ArrayList<Path>();
        final Path config = Paths.get("src/main/resources/config.yml");
        if (Files.exists(config))
        {
            files.add(config);
        }
        final Path shapes = Paths.get("src/main/resources/GateShapes");
        if (Files.isDirectory(shapes))
        {
            for (final Path p : Files.list(shapes).toList())
            {
                if (p.getFileName().toString().endsWith(".shape"))
                {
                    files.add(p);
                }
            }
        }
        return files;
    }

    @Test
    public void everyMaterialNamedInShippedFilesResolves() throws Exception
    {
        final java.util.Set<String> unknown = new TreeSet<String>();
        final java.util.Set<String> checked = new TreeSet<String>();

        for (final Path file : shippedResources())
        {
            for (final String line : Files.readAllLines(file))
            {
                // Comment lines describe materials that may be examples rather than
                // defaults, and an example naming something from another version is not a
                // shipped default that will fail.
                final String code = line.trim();
                if (code.startsWith("#"))
                {
                    continue;
                }
                final Matcher m = CANDIDATE.matcher(code);
                while (m.find())
                {
                    final String name = m.group();
                    if (NOT_MATERIALS.contains(name))
                    {
                        continue;
                    }
                    checked.add(name);
                    if (Material.matchMaterial(name) == null)
                    {
                        unknown.add(name + "  (in " + file.getFileName() + ")");
                    }
                }
            }
        }

        assertFalse(checked.isEmpty(), "no material names were found, so this proved nothing");
        assertTrue(unknown.isEmpty(),
            "shipped files name materials that do not exist in the targeted Minecraft version: " + unknown);
    }

    @Test
    public void theMaterialsTheCodeFallsBackOnStillExist()
    {
        // The built-in defaults, used when a shape and a palette both say nothing. These are
        // referenced as enum constants so the compiler does check them — this is here so
        // that the set is written down in one place, and so a future replacement of the
        // constants with names does not lose the check.
        for (final Material material : new Material[] {
            Material.OBSIDIAN, Material.WATER, Material.STONE, Material.GLOWSTONE,
            Material.OAK_WALL_SIGN, Material.AIR, Material.LEVER, Material.STONE_BUTTON })
        {
            assertNotNull(Material.matchMaterial(material.name()),
                material + " is a built-in default and must resolve by name");
        }
    }
}
