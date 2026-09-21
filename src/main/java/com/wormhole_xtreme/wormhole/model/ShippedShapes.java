package com.wormhole_xtreme.wormhole.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Brings the bundled gate shapes on a server up to this version, unless someone edited them.
 *
 * <p>Shapes are written out once and never overwritten, so a server that upgraded kept the old
 * geometry and light order for good. A copy that matches, line for line, a version some release
 * shipped was never touched by hand, and is replaced; anything else is left alone.
 */
final class ShippedShapes
{
    /** The bundled shapes, written out when missing and updated when untouched. */
    static final List<String> NAMES = List.of("Standard.shape", "StandardSignDial.shape", "Minimal.shape",
        "MinimalSignDial.shape", "Horizontal.shape", "HorizontalSignDial.shape",
        "Large.shape", "Grand.shape", "Massive.shape");

    /** The list of every shipped version, as {@code <file> <sha-256>} lines. */
    static final String SHIPPED_LIST = "/shapes/shipped-shapes.txt";

    /** What a replaced copy is renamed to, beside the new one. */
    static final String BACKUP_SUFFIX = ".old";

    /** Where the new copy is written before it takes the old one's place. */
    static final String INCOMING_SUFFIX = ".new";

    private ShippedShapes() {}

    /**
     * Replaces each bundled shape the folder holds as some earlier release wrote it.
     *
     * @param directory
     *            the gate shapes folder
     */
    static void updateUntouched(final File directory)
    {
        final Set<String> shipped = shippedVersions();
        if (shipped.isEmpty())
        {
            return;
        }
        for (final String name : NAMES)
        {
            updateOne(directory, name, shipped);
        }
    }

    /**
     * Replaces one bundled shape if the folder holds it as some earlier release wrote it.
     *
     * <p>The new copy is written beside it first and only then swapped in, so a write that fails
     * -- a full disk, a file held open on Windows -- leaves the old one where it was rather than
     * already moved aside with nothing in its place.
     */
    private static void updateOne(final File directory, final String name, final Set<String> shipped)
    {
        final File file = new File(directory, name);
        final String current = bundled(name);
        if (!file.isFile() || (current == null))
        {
            return;
        }
        final java.nio.file.Path incoming = new File(directory, name + INCOMING_SUFFIX).toPath();
        try
        {
            final String onDisk = hash(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (onDisk.equals(hash(current)))
            {
                return;
            }
            if (!shipped.contains(name + " " + onDisk))
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Gate shape " + name
                    + " has been edited, so it was left as it is. Delete it and restart to get this version's.");
                return;
            }
            Files.writeString(incoming, current, StandardCharsets.UTF_8);
            Files.move(file.toPath(), new File(directory, name + BACKUP_SUFFIX).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
            Files.move(incoming, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Updated gate shape " + name
                + " to this version; the old one is kept as " + name + BACKUP_SUFFIX + ".");
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not update gate shape " + name, e);
            try
            {
                Files.deleteIfExists(incoming);
            }
            catch (final IOException ignore)
            {
                // Best effort: a stray .new file is harmless, and the next start tries again.
            }
        }
    }

    /** @return the bundled copy of a shape with LF line endings, or null if the jar lacks it */
    static String bundled(final String name)
    {
        try (final InputStream is = WormholeXTreme.class.getResourceAsStream("/shapes/gate/" + name))
        {
            return (is == null) ? null : normalised(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
        catch (final IOException e)
        {
            return null;
        }
    }

    /** @return every {@code <file> <sha-256>} line the list holds */
    static Set<String> shippedVersions()
    {
        final Set<String> versions = new HashSet<>();
        try (final InputStream is = WormholeXTreme.class.getResourceAsStream(SHIPPED_LIST))
        {
            if (is == null)
            {
                return versions;
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (!line.isBlank() && !line.startsWith("#"))
                {
                    versions.add(line.trim());
                }
            }
        }
        catch (final IOException e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not read the shipped shape list", e);
        }
        return versions;
    }

    /** @return the SHA-256 of a shape's text, line endings aside */
    static String hash(final String text)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(normalised(text).getBytes(StandardCharsets.UTF_8)));
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is missing from this Java", e);
        }
    }

    /** @return the text with every line ended by a single LF, as the plugin writes a shape */
    static String normalised(final String text)
    {
        final StringBuilder out = new StringBuilder(text.length());
        try (final BufferedReader reader = new BufferedReader(new StringReader(text)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                out.append(line).append('\n');
            }
        }
        catch (final IOException e)
        {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }
}
