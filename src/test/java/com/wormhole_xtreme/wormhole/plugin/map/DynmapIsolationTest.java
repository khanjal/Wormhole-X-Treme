package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Only {@link DynmapMapProvider} names Dynmap (#236).
 *
 * <p>Dynmap is optional, and a class that names one of its types fails to load on a server
 * without it. The coordinator and the plugin's main class are loaded on every server, so a
 * Dynmap type reaching either -- an import, a field, a catch -- would stop the plugin loading
 * wherever Dynmap is not installed. The test suite always has Dynmap's API on its classpath,
 * so no other test would notice.
 */
class DynmapIsolationTest
{
    private static final Path MAP = Path.of("src/main/java/com/wormhole_xtreme/wormhole/plugin/map");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*+\"");
    private static final Path MAIN = Path.of("src/main/java/com/wormhole_xtreme/wormhole/WormholeXTreme.java");

    @Test
    void nothingButTheDynmapProviderNamesDynmap() throws IOException
    {
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> listed = Files.list(MAP))
        {
            listed.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        files.add(MAIN);
        // A floor, so a wrong working directory or a moved package fails rather than passing on nothing.
        assertTrue(files.size() >= 8, "expected the map package and WormholeXTreme.java, read " + files);

        final List<String> offenders = new ArrayList<>();
        int read = 0;
        for (final Path file : files)
        {
            if (file.getFileName().toString().equals("DynmapMapProvider.java"))
            {
                continue;
            }
            read++;
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++)
            {
                // Outside string literals: the coordinator looks Dynmap up by name, which loads nothing.
                if (STRING.matcher(lines.get(i)).replaceAll("\"\"").contains("org.dynmap"))
                {
                    offenders.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }

        assertEquals(files.size() - 1, read, "every file but the provider should have been read");
        assertTrue(offenders.isEmpty(), "Dynmap is named outside DynmapMapProvider: " + offenders);
    }
}
