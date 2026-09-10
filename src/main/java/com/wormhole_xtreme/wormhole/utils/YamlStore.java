package com.wormhole_xtreme.wormhole.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Writes a map to a YAML file, atomically.
 *
 * <p>Gates, rings and beam destinations each had their own copy of this: the same
 * {@code DumperOptions}, the same temp file beside the target, the same
 * {@code Files.move(..., ATOMIC_MOVE)}. Three copies of a write path is three places for a
 * storage bug to be fixed in two of.
 *
 * <p>The write goes to {@code <target>.tmp} first and is then moved onto the target, so a
 * write interrupted half way through leaves the previous file intact rather than a truncated
 * one. That matters most for rings, where one file holds every pair in a world, but it is the
 * right shape for all three.
 *
 * <p>UTF-8 explicitly, never the platform default: a gate named with an accent used to come
 * back as {@code Caf?} after a restart, because the file was written in one charset and read
 * back in another.
 */
public final class YamlStore
{
    /** Static helpers only. */
    private YamlStore()
    {
    }

    /**
     * Writes a map to a file, replacing whatever was there.
     *
     * <p>Throws rather than logging, because the three callers say different things about a
     * failed write and one of them has an exception worth attaching. Mechanism here, wording
     * at the call site.
     *
     * @param target
     *            the file to write
     * @param root
     *            the map to write into it
     * @throws IOException
     *             if the file could not be written or moved into place
     */
    public static void write(final File target, final Map<String, Object> root) throws IOException
    {
        final File temp = new File(target.getAbsolutePath() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(temp, StandardCharsets.UTF_8)))
        {
            blockStyle().dump(root, writer);
        }
        Files.move(temp.toPath(), target.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * A YAML writer that produces block style rather than inline braces.
     *
     * <p>These files are meant to be opened and hand-edited by server owners, and
     * {@code {World: world, X: 1.0}} on one line is not that. Two-space indent to match what
     * every other YAML file on a Bukkit server looks like.
     *
     * @return a fresh Yaml, since the class is not thread-safe and these are cheap
     */
    private static Yaml blockStyle()
    {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        return new Yaml(options);
    }
}
