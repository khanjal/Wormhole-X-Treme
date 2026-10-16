package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every player-facing colour is built by {@link ChatText} (#438), so no other source file
 * writes a colour code by hand.
 *
 * <p>A hand-written code is a palette decision made in one file: a gate name in grey here, a
 * header in a slightly different dark purple there. Keeping the section sign (written
 * literally or as a {@code §} escape) in one file is what keeps the colours the same
 * everywhere. Use {@link ChatText}, or {@code ChatColor} for a code that is not in the palette.
 */
class ChatCodeGuardTest
{
    /** The section sign, built here so this file does not itself contain one. */
    private static final char SECTION = (char) 0xA7;

    /** A unicode escape of the section sign, in either case, as written in Java source. */
    private static final Pattern ESCAPE = Pattern.compile("\\\\u00[aA]7");

    /** The one file allowed to hold the codes. */
    private static final String HOME = "com/wormhole_xtreme/wormhole/utils/ChatText.java";

    @Test
    void noSourceFileOutsideChatTextWritesAColourCodeByHand() throws IOException
    {
        final Path root = Paths.get("src", "main", "java");
        final List<String> offences = new ArrayList<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(root))
        {
            for (final Path file : walk.filter(p -> p.toString().endsWith(".java")).toList())
            {
                files++;
                final String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.equals(HOME))
                {
                    continue;
                }
                final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++)
                {
                    final String line = lines.get(i);
                    if ((line.indexOf(SECTION) >= 0) || ESCAPE.matcher(line).find())
                    {
                        offences.add(relative + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(files > 100, "the walk should open every main source file, opened only " + files
            + " from " + root.toAbsolutePath());
        assertTrue(offences.isEmpty(), "colour codes written by hand outside ChatText -- use ChatText "
            + "or ChatColor: " + offences);
    }
}
