package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * This plugin does not reach into itself by reflection.
 *
 * <p>Reflection is the right tool for another plugin's API, which may or may not be installed
 * and may or may not be the version you compiled against -- Vault is reached that way here,
 * correctly. It is the wrong tool for this plugin's own classes, where it buys nothing and
 * costs the compiler: a rename still compiles, the lookup throws at runtime, and the catch
 * around it swallows the failure. The feature quietly stops working and nobody is told.
 *
 * <p>That had happened once, in the regenerate command, which reflected into
 * {@code StargateDialManager} to call a package-private method. The fix was a public door on
 * the manager. This test is here so the next person reaching for {@code Class.forName} on our
 * own package has to argue with something first.
 */
public class NoSelfReflectionTest
{
    /** Our own package, which nothing here should be looking up by name. */
    private static final String OWN_PACKAGE = "com.wormhole_xtreme";

    private static List<Path> sources() throws IOException
    {
        try (java.util.stream.Stream<Path> walk = Files.walk(Paths.get("src/main/java")))
        {
            final List<Path> found = new ArrayList<Path>();
            for (final Path p : walk.toList())
            {
                if (p.getFileName().toString().endsWith(".java"))
                {
                    found.add(p);
                }
            }
            return found;
        }
    }

    @Test
    public void nothingLooksUpOneOfOurOwnClassesByName() throws IOException
    {
        final List<Path> sources = sources();
        assertTrue(!sources.isEmpty(), "no sources were read, so this proved nothing");

        final List<String> offenders = new ArrayList<String>();
        for (final Path source : sources)
        {
            int lineNumber = 0;
            for (final String line : Files.readAllLines(source))
            {
                lineNumber++;
                if (line.contains("Class.forName") && line.contains(OWN_PACKAGE))
                {
                    offenders.add(source.getFileName() + ":" + lineNumber);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these reach into this plugin's own classes by name, where a rename would compile "
                + "clean and fail at runtime: " + offenders + ". Make the method reachable "
                + "instead and call it.");
    }
}
