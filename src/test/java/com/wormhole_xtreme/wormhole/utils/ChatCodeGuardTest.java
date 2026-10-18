package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every player-facing colour is built by {@link ChatText} (#438), so no other source file
 * writes a colour code by hand.
 *
 * <p>A hand-written code is a palette decision made in one file: a gate name in grey here, a
 * header in a slightly different dark purple there. Keeping the section sign (written
 * literally, as an escape or as a numeric char) in one file is what keeps the colours the same
 * everywhere. Use {@link ChatText}, or {@link ChatColour} for a code that is not in the palette.
 */
class ChatCodeGuardTest
{
    /** The section sign, built here so this file does not itself contain one. */
    private static final char SECTION = (char) 0xA7;

    /**
     * The other ways to write the section sign or to translate an {@code &} code into one, each
     * as a regex over a source line: a unicode escape (any number of {@code u}s, either case),
     * an octal escape, a numeric char, and Bukkit's {@code translateAlternateColorCodes}.
     * Nothing in main uses that last one, so it has no allowlist: a use should come to ChatText.
     */
    private static final List<Pattern> FORMS = List.of(
        Pattern.compile("\\\\u+00[aA]7"),
        Pattern.compile("\\\\0?247"),
        Pattern.compile("\\(char\\)\\s*(0[xX][aA]7|0?247|167)\\b"),
        Pattern.compile("Character\\.toString\\(\\s*(0[xX][aA]7|0?247|167)\\s*\\)"),
        Pattern.compile("translateAlternateColorCodes"));

    /** The files allowed to hold the codes: the palette, and the code table it sits beside. */
    private static final Set<String> HOMES = Set.of("com/wormhole_xtreme/wormhole/utils/ChatText.java",
        "com/wormhole_xtreme/wormhole/utils/ChatColour.java");

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
                if (HOMES.contains(relative))
                {
                    continue;
                }
                final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++)
                {
                    final String line = lines.get(i);
                    if ((line.indexOf(SECTION) >= 0) || FORMS.stream().anyMatch(f -> f.matcher(line).find()))
                    {
                        offences.add(relative + ":" + (i + 1));
                    }
                }
            }
        }
        assertTrue(files > 100, "the walk should open every main source file, opened only " + files
            + " from " + root.toAbsolutePath());
        assertTrue(offences.isEmpty(), "colour codes written by hand outside ChatText -- use ChatText "
            + "or ChatColour: " + offences);
    }
}
