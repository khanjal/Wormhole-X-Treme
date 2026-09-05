package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * No file in this plugin is read or written in whatever charset the host happens to default to.
 *
 * <p>{@code new FileWriter(f)} encodes with {@link java.nio.charset.Charset#defaultCharset()},
 * which on Java 17 still comes from {@code file.encoding} -- JEP 400 made UTF-8 the default in
 * 18, not 17, and this plugin targets 17. Meanwhile SnakeYAML decodes as UTF-8, and
 * {@code Files.readAllLines(Path)} decodes as UTF-8. So a charset-less writer and any of the
 * readers around it disagree on every machine whose default is not UTF-8.
 *
 * <p>A Minecraft server in a minimal container with a POSIX/C locale is exactly such a machine,
 * and the default there is effectively ASCII. A gate whose name carried an accent saved on
 * that box came back with a {@code ?} in place of it: written as one byte, read as UTF-8,
 * replaced. The same held for
 * owner names, iris deactivation codes, and any comment or value an admin typed into
 * {@code config.yml}. Nothing logged a warning, because at no point did anything fail -- each
 * half did exactly what it was told, in a different charset from the other half.
 *
 * <p>The fix was mechanical: name UTF-8 at all 27 sites. This test is what keeps it that way,
 * because the broken form is shorter to type than the correct one and reads as perfectly
 * ordinary Java. It runs on every platform, including the UTF-8 ones where the bug itself is
 * invisible, which is the point -- the round-trip tests next to it can only fail on a host
 * that reproduces the conditions, and CI is not such a host.
 */
public class PlatformCharsetIsNeverUsedTest
{
    /**
     * Forms that fall back to the platform charset when not given one.
     *
     * <p>The first four are constructors, and have taken a {@code Charset} since Java 11. The
     * fifth is {@link String#getBytes()}, whose charset-taking overload has been there since
     * Java 6. Either way there has always been a correct form to move to, and none of the five
     * has a legitimate charset-less use.
     *
     * <p>Matched without the {@code new}, which the first draft of this test included and
     * which let two real offenders through: {@code ConfigurationYAML} spelled them
     * {@code new java.io.FileWriter(...)}, and a guard that only recognises the unqualified
     * form is a guard that rewards writing it the long way.
     */
    private static final String[] PLATFORM_DEFAULTED = {
        "FileWriter(",
        "FileReader(",
        "InputStreamReader(",
        "OutputStreamWriter(",
        ".getBytes()",
    };

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

    /** Prose mentioning one of these forms is not a use of it. */
    private static boolean isComment(final String line)
    {
        final String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    /**
     * Naming a charset anywhere on the line is taken as naming it for this call.
     *
     * <p>Deliberately loose. It matches {@code StandardCharsets.UTF_8} and the fully qualified
     * {@code java.nio.charset.StandardCharsets.UTF_8} both, and it would also accept a line
     * that mentions a charset for some unrelated reason. A guard that occasionally lets one
     * through is worth more here than one that cries wolf and gets deleted.
     */
    private static boolean namesACharset(final String line)
    {
        return line.contains("Charset");
    }

    @Test
    public void noFileIsReadOrWrittenInThePlatformCharset() throws IOException
    {
        final List<Path> sources = sources();
        assertTrue(!sources.isEmpty(), "no sources were read, so this proved nothing");

        final List<String> offenders = new ArrayList<String>();
        for (final Path source : sources)
        {
            int lineNumber = 0;
            for (final String line : Files.readAllLines(source, StandardCharsets.UTF_8))
            {
                lineNumber++;
                if (isComment(line) || namesACharset(line))
                {
                    continue;
                }
                for (final String form : PLATFORM_DEFAULTED)
                {
                    if (line.contains(form))
                    {
                        offenders.add(source.getFileName() + ":" + lineNumber + " " + form);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these encode or decode in the host's default charset, so what they write is not "
                + "what the readers around them expect on any machine that is not already "
                + "UTF-8: " + offenders + ". Pass StandardCharsets.UTF_8 -- every one of these "
                + "has had a Charset overload since Java 11.");
    }

    /**
     * Bytes are decoded with a named charset.
     *
     * <p>Split from the constructors above because {@code new String(...)} is ambiguous by
     * shape: over a {@code char[]} it is charset-free and fine, over a {@code byte[]} it is the
     * platform default. The variable name is what separates them, which is a heuristic and
     * admitted as one -- it holds for this codebase, where every such call takes something
     * named {@code ...Bytes} or {@code raw}.
     *
     * <p>The case that made this worth pinning is in {@code GateSerializer}: it wrote the iris
     * deactivation code with an explicit {@code getBytes("UTF8")} and read it back with a bare
     * {@code new String(idcBytes)}. Both halves live in one file, forty lines apart, and they
     * disagreed -- a gate whose code contained anything outside ASCII could not be opened by
     * the code that opened it, on the servers where that mattered.
     */
    @Test
    public void bytesAreDecodedWithANamedCharset() throws IOException
    {
        final List<String> offenders = new ArrayList<String>();
        for (final Path source : sources())
        {
            int lineNumber = 0;
            for (final String line : Files.readAllLines(source, StandardCharsets.UTF_8))
            {
                lineNumber++;
                if (isComment(line) || namesACharset(line) || !line.contains("new String("))
                {
                    continue;
                }
                if (line.contains("Bytes)") || line.contains("raw)"))
                {
                    offenders.add(source.getFileName() + ":" + lineNumber);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
            "these decode bytes in the host's default charset, which is not the charset they "
                + "were written in: " + offenders + ". Pass StandardCharsets.UTF_8 as the "
                + "second argument.");
    }
}
