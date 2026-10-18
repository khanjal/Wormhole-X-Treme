package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The keys the code asks {@link Messages} for and the keys {@code lang/en.yml} holds are the same set (#26).
 *
 * <p>A key the code uses that en.yml lacks is sent to players as the bare key; a key en.yml holds that
 * nothing uses is a sentence translators work on for nothing. Both are typos nobody sees until a
 * player does. So every call site must name its key as a string literal, which is what lets this read
 * them: a key built at run time or held in a constant is refused here.
 */
class MessageKeysGuardTest
{
    private static final String CALL = "Messages.get(";

    /** Messages itself names the call in its Javadoc, so it is not a call site. */
    private static final String HOME = "com/wormhole_xtreme/wormhole/utils/Messages.java";

    @Test
    void everyKeyTheCodeUsesIsInEnglishAndEveryEnglishKeyIsUsed() throws IOException
    {
        final Path root = Paths.get("src", "main", "java");
        final Set<String> used = new TreeSet<>();
        final List<String> notLiteral = new ArrayList<>();
        int files = 0;
        int calls = 0;
        try (Stream<Path> walk = Files.walk(root))
        {
            for (final Path file : walk.filter(p -> p.toString().endsWith(".java")).toList())
            {
                files++;
                final String relative = root.relativize(file).toString().replace('\\', '/');
                final String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains("import static com.wormhole_xtreme.wormhole.utils.Messages"))
                {
                    notLiteral.add(relative + ": a static import hides its calls from this test");
                }
                if (HOME.equals(relative))
                {
                    continue;
                }
                for (int at = source.indexOf(CALL); at >= 0; at = source.indexOf(CALL, at + 1))
                {
                    calls++;
                    final String key = literalKey(source, at + CALL.length());
                    if (key == null)
                    {
                        notLiteral.add(relative + ":" + lineOf(source, at));
                    }
                    else
                    {
                        used.add(key);
                    }
                }
            }
        }
        assertTrue(files > 100, "the walk should open every main source file, opened only " + files
            + " from " + root.toAbsolutePath());
        assertTrue(calls >= 25, "phase 1 converted over twenty-five messages, found " + calls + " calls");
        assertTrue(notLiteral.isEmpty(), "each Messages.get must name its key as a string literal: " + notLiteral);

        final Set<String> english = new TreeSet<>(englishKeys());
        final Set<String> missing = new TreeSet<>(used);
        missing.removeAll(english);
        final Set<String> unused = new TreeSet<>(english);
        unused.removeAll(used);
        assertEquals(Set.of(), missing, "keys the code uses that lang/en.yml lacks; players would see the key");
        assertEquals(Set.of(), unused, "keys in lang/en.yml nothing uses; remove them or use them");
    }

    /** @return the literal key starting after any spaces at {@code from}, or null if that is not a lone literal */
    private static String literalKey(final String source, final int from)
    {
        int i = from;
        while ((i < source.length()) && Character.isWhitespace(source.charAt(i)))
        {
            i++;
        }
        if ((i >= source.length()) || (source.charAt(i) != '"'))
        {
            return null;
        }
        final int close = source.indexOf('"', i + 1);
        if (close < 0)
        {
            return null;
        }
        final String key = source.substring(i + 1, close);
        int j = close + 1;
        while ((j < source.length()) && Character.isWhitespace(source.charAt(j)))
        {
            j++;
        }
        // A key followed by + is built, not named; a backslash would be an escape this does not read.
        final boolean lone = (j < source.length()) && ((source.charAt(j) == ',') || (source.charAt(j) == ')'));
        return (lone && !key.isEmpty() && (key.indexOf('\\') < 0)) ? key : null;
    }

    private static int lineOf(final String source, final int at)
    {
        int line = 1;
        for (int i = 0; i < at; i++)
        {
            line += (source.charAt(i) == '\n') ? 1 : 0;
        }
        return line;
    }

    private static Set<String> englishKeys() throws IOException
    {
        try (InputStream in = MessageKeysGuardTest.class.getResourceAsStream("/lang/en.yml"))
        {
            assertNotNull(in, "lang/en.yml should be bundled");
            final Map<?, ?> map = (Map<?, ?>) new Yaml().load(in);
            final Set<String> keys = new TreeSet<>();
            map.keySet().forEach(k -> keys.add(String.valueOf(k)));
            assertTrue(keys.size() >= 25, "lang/en.yml should hold the phase 1 messages, holds " + keys.size());
            return keys;
        }
    }
}
