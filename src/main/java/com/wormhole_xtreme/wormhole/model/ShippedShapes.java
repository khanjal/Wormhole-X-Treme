package com.wormhole_xtreme.wormhole.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The bundled gate shapes, and which of a server's copies differ from this version's.
 *
 * <p>A shape file is written once and never overwritten, since an admin may have edited it, so
 * an upgraded server keeps the old ones. Each that differs is named in the log for the admin to
 * decide on; nothing here changes a file.
 */
final class ShippedShapes
{
    /** The bundled shapes, written out when missing. */
    static final List<String> NAMES = List.of("Standard.shape", "StandardSignDial.shape", "Minimal.shape",
        "MinimalSignDial.shape", "Horizontal.shape", "HorizontalSignDial.shape",
        "Large.shape", "Grand.shape", "Massive.shape");

    private ShippedShapes() {}

    /**
     * Logs each bundled shape whose copy in the folder differs from this version's.
     *
     * @param directory
     *            the gate shapes folder
     * @return how many differ
     */
    static int reportDiffering(final File directory)
    {
        int differing = 0;
        for (final String name : NAMES)
        {
            final File file = new File(directory, name);
            final String bundled = bundled(name);
            if (!file.isFile() || (bundled == null))
            {
                continue;
            }
            try
            {
                if (!normalised(Files.readString(file.toPath(), StandardCharsets.UTF_8)).equals(bundled))
                {
                    differing++;
                    WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Gate shape " + name
                        + " differs from this version's. If you did not edit it, delete it and restart to take the new one.");
                }
            }
            catch (final IOException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, "Could not read gate shape " + name, e);
            }
        }
        return differing;
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
