package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Spigot-only API is used only inside the classes that isolate it.
 *
 * <p>The README lists CraftBukkit as supported, but every CI leg compiles against spigot-api,
 * paper-api or purpur-api, all of which carry Spigot's extras. CraftBukkit has no
 * {@code Player.spigot()}, no BungeeCord chat and no {@code org.spigotmc} package, so a new call
 * to any of them passes every check and then throws a {@code LinkageError} on a CraftBukkit
 * server. {@code ActionBar} did exactly that, into ring countdowns and mirror approaches, until
 * it moved its Spigot calls into a nested class and caught the error.
 *
 * <p>Comments and string literals are stripped first: prose naming these APIs is not a use of
 * them, and a class name passed as a string to a presence check cannot fail to link.
 */
class SpigotApiIsIsolatedTest
{
    /** Forms that exist on Spigot and its forks but not on CraftBukkit. */
    private static final Pattern SPIGOT_ONLY = Pattern.compile(
        "\\.\\s*spigot\\s*\\(|\\bnet\\s*\\.\\s*md_5\\b|\\borg\\s*\\.\\s*spigotmc\\b");

    /**
     * Files allowed Spigot API, relative to {@code src/main/java}, each with the class that must
     * hold every use of it other than an import.
     *
     * <p>{@code ActionBar} keeps its calls in the nested {@code SpigotBar} and catches
     * {@code LinkageError} around it, so a call in {@code ActionBar.send} itself would not be
     * covered. {@code LegacyGateDismountListener} is loaded by name, behind a catch of
     * {@code NoClassDefFoundError}.
     */
    private static final Map<String, String> ISOLATED = Map.of(
        "com/wormhole_xtreme/wormhole/utils/ActionBar.java", "SpigotBar",
        "com/wormhole_xtreme/wormhole/LegacyGateDismountListener.java", "LegacyGateDismountListener");

    /** Isolated only while nothing names it in code: a direct reference links it eagerly. */
    private static final String LOADED_BY_NAME = "LegacyGateDismountListener";

    private static final Path ROOT = Paths.get("src/main/java");

    /** Well under the sources there are today, so it trips only on a scan that read almost nothing. */
    private static final int SOURCE_FLOOR = 150;

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s", Pattern.MULTILINE);

    @Test
    void spigotOnlyApiAppearsOnlyInTheClassesThatIsolateIt() throws IOException
    {
        final List<String> offenders = new ArrayList<>();
        final Set<String> isolatedUsers = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> walk = Files.walk(ROOT))
        {
            for (final Path source : walk.filter(p -> p.toString().endsWith(".java")).toList())
            {
                scanned++;
                final String relative = ROOT.relativize(source).toString().replace('\\', '/');
                final String code = stripCommentsAndLiterals(
                    Files.readString(source, StandardCharsets.UTF_8));
                final String holder = ISOLATED.get(relative);
                final int[] body = holder == null ? null : classBody(code, holder);
                final Matcher m = SPIGOT_ONLY.matcher(code);
                while (m.find())
                {
                    if ((holder != null && isImport(code, m.start()))
                        || (body != null && m.start() > body[0] && m.start() < body[1]))
                    {
                        isolatedUsers.add(relative);
                    }
                    else
                    {
                        offenders.add(relative + ":" + lineOf(code, m.start()) + " " + m.group());
                    }
                }
                if (!source.getFileName().toString().equals(LOADED_BY_NAME + ".java")
                    && Pattern.compile("\\b" + LOADED_BY_NAME + "\\b").matcher(code).find())
                {
                    offenders.add(relative + " names " + LOADED_BY_NAME + " outside a string");
                }
            }
        }

        assertTrue(scanned >= SOURCE_FLOOR,
            "only " + scanned + " sources were read from " + ROOT.toAbsolutePath()
                + ", so an empty result here proves nothing");
        assertTrue(offenders.isEmpty(),
            "these use Spigot-only API that CraftBukkit lacks, so they throw a LinkageError there: "
                + offenders + ". Move the call into an isolated class that fails safely -- a "
                + "nested class whose caller catches LinkageError, as ActionBar does -- and add "
                + "that class to ISOLATED.");
        assertEquals(new TreeSet<>(ISOLATED.keySet()), isolatedUsers,
            "an ISOLATED entry was not found or no longer uses Spigot API; a stale entry would "
                + "wave through whatever takes its name next, so remove or rename it");
    }

    private static boolean isImport(final String code, final int offset)
    {
        final int lineStart = code.lastIndexOf('\n', offset - 1) + 1;
        final Matcher m = IMPORT.matcher(code).region(lineStart, offset);
        return m.lookingAt();
    }

    /**
     * The offsets of the braces around the named class's body, or null if it is not declared.
     *
     * <p>Counted on stripped code, where every brace left is a real one.
     */
    static int[] classBody(final String code, final String name)
    {
        final Matcher decl = Pattern.compile("\\bclass\\s+" + name + "\\b").matcher(code);
        if (!decl.find())
        {
            return null;
        }
        final int open = code.indexOf('{', decl.end());
        int depth = 0;
        for (int i = open; i >= 0 && i < code.length(); i++)
        {
            if (code.charAt(i) == '{')
            {
                depth++;
            }
            else if (code.charAt(i) == '}' && --depth == 0)
            {
                return new int[] {open, i};
            }
        }
        return null;
    }

    /** Line numbers survive stripping because newlines are kept. */
    private static int lineOf(final String code, final int offset)
    {
        int line = 1;
        for (int i = 0; i < offset; i++)
        {
            if (code.charAt(i) == '\n')
            {
                line++;
            }
        }
        return line;
    }

    /**
     * Blanks comments, string, text-block and char literals, keeping newlines.
     *
     * <p>A scanner rather than a regex, so a quote inside a comment or a comment marker inside a
     * string cannot throw it out of step.
     */
    static String stripCommentsAndLiterals(final String src)
    {
        final StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length())
        {
            final int end;
            if (src.startsWith("//", i))
            {
                end = indexOrEnd(src, "\n", i);
            }
            else if (src.startsWith("/*", i))
            {
                end = indexOrEnd(src, "*/", i + 2) + 2;
            }
            else if (src.startsWith("\"\"\"", i))
            {
                end = closingQuote(src, i + 3, "\"\"\"");
            }
            else if (src.charAt(i) == '"' || src.charAt(i) == '\'')
            {
                end = closingQuote(src, i + 1, String.valueOf(src.charAt(i)));
            }
            else
            {
                out.append(src.charAt(i++));
                continue;
            }
            final int stop = Math.min(end, src.length());
            for (; i < stop; i++)
            {
                out.append(src.charAt(i) == '\n' ? '\n' : ' ');
            }
        }
        return out.toString();
    }

    private static int indexOrEnd(final String src, final String token, final int from)
    {
        final int at = src.indexOf(token, from);
        return at < 0 ? src.length() : at;
    }

    /** The index just past the unescaped closing delimiter. */
    private static int closingQuote(final String src, final int from, final String quote)
    {
        int i = from;
        while (i < src.length())
        {
            if (src.charAt(i) == '\\')
            {
                i += 2;
            }
            else if (src.startsWith(quote, i))
            {
                return i + quote.length();
            }
            else
            {
                i++;
            }
        }
        return src.length();
    }

    @Test
    void commentsAndLiteralsAreBlankedButCodeIsKept()
    {
        final String stripped = stripCommentsAndLiterals(
            "a.spigot(); // b.spigot()\n/* \"x */ c(\"org.spigotmc.X\", '\"', \"\\\"\");\n"
                + "d(\"\"\"\n  net.md_5 \" \n\"\"\"); e.spigot();");

        final Matcher m = SPIGOT_ONLY.matcher(stripped);
        final List<Integer> lines = new ArrayList<>();
        while (m.find())
        {
            lines.add(lineOf(stripped, m.start()));
        }
        assertEquals(List.of(1, 5), lines,
            "only the two real calls should survive stripping, on their original lines: "
                + stripped);
    }
}
