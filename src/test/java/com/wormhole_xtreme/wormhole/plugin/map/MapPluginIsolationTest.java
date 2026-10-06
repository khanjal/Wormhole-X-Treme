package com.wormhole_xtreme.wormhole.plugin.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Each map plugin is named only by its own provider (#236, #530).
 *
 * <p>Map plugins are optional, and a class that names one of their types fails to load on a
 * server without it. The coordinator and the plugin's main class are loaded on every server, so
 * a map plugin's type reaching either -- an import, a field, a catch -- would stop the plugin
 * loading wherever that map is not installed; and a provider naming another map's types would
 * stop its own map working wherever the other is missing. The test suite has the map APIs on its
 * classpath, so no other test would notice.
 */
class MapPluginIsolationTest
{
    private static final Path MAP = Path.of("src/main/java/com/wormhole_xtreme/wormhole/plugin/map");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*+\"");
    private static final Path MAIN = Path.of("src/main/java/com/wormhole_xtreme/wormhole/WormholeXTreme.java");

    /** Each map plugin's packages, by the one file allowed to name them. */
    private static final Map<String, List<String>> OWNED = Map.of(
        "DynmapMapProvider.java", List.of("org.dynmap"),
        "BlueMapMapProvider.java", List.of("de.bluecolored", "com.flowpowered"),
        "SquaremapMapProvider.java", List.of("xyz.jpenilla.squaremap"),
        "Pl3xMapMapProvider.java", List.of("net.pl3x"));

    @Test
    void eachMapPluginIsNamedOnlyByItsOwnProvider() throws IOException
    {
        final List<Path> files = new ArrayList<>();
        try (Stream<Path> listed = Files.list(MAP))
        {
            listed.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        files.add(MAIN);
        // A floor, so a wrong working directory or a moved package fails rather than passing on nothing.
        assertTrue(files.size() >= 10, "expected the map package and WormholeXTreme.java, read " + files);

        final List<String> offenders = new ArrayList<>();
        int providers = 0;
        for (final Path file : files)
        {
            final List<String> own = OWNED.getOrDefault(file.getFileName().toString(), List.of());
            if (!own.isEmpty())
            {
                providers++;
            }
            offenders.addAll(offenders(file, own));
        }

        assertEquals(OWNED.size(), providers, "every provider should have been read, read " + files);
        assertTrue(offenders.isEmpty(), "A map plugin is named outside its own provider: " + offenders);
    }

    /** The lines of one file that name a map plugin's package other than its own. */
    private static List<String> offenders(final Path file, final List<String> own) throws IOException
    {
        final List<String> foreign = OWNED.values().stream().flatMap(List::stream).filter(p -> !own.contains(p)).toList();
        final List<String> found = new ArrayList<>();
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++)
        {
            // Outside string literals: the coordinator looks each map up by name, which loads nothing.
            final String code = STRING.matcher(lines.get(i)).replaceAll("\"\"");
            if (foreign.stream().anyMatch(code::contains))
            {
                found.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
            }
        }
        return found;
    }
}
