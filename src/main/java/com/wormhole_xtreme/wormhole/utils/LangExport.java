package com.wormhole_xtreme.wormhole.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.List;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Starter lang files for {@code /wormhole lang export}, the only thing that writes to the plugin
 * folder's {@code lang/} (#26).
 *
 * <p>A language the jar ships is exported with every message commented out, so the file changes
 * nothing until a line is uncommented, and what stays commented keeps following the jar across updates.
 * Any other code gets the English, live, to translate. The jar's file is copied line by line, keeping
 * its order and quoting, which works because every message in it is one line.
 */
public final class LangExport
{
    /** What an export did. */
    public enum Result
    {
        /** A shipped language, every message commented out. */
        OVERLAY,
        /** English to translate, every message live. */
        TRANSLATION,
        /** The file was there already and is untouched. */
        EXISTS
    }

    private static final String YML = ".yml";

    private static final String LANG = "lang/";

    private static final String OVERLAY_HEADER = """
        # Your own wording for Wormhole X-Treme's %s messages, read over the plugin's.
        #
        # Every message is commented out, so it follows the plugin's text, updates included. To reword
        # one, remove the "# " in front of it and change the text in quotes. A message not in this file
        # uses the plugin's text. Run /wormhole config -reload after editing.
        """;

    private static final String TRANSLATION_HEADER = """
        # Wormhole X-Treme messages for %s, in English until translated.
        #
        # Translate the text in quotes and keep each {0}, {1} and § colour code. A message missing from
        # this file falls back to the plugin's text, English last. Run /wormhole config -reload after
        # editing. Guide: docs/guide/TRANSLATING.md
        """;

    /** Static helpers only. */
    private LangExport()
    {
    }

    /**
     * Writes a starter file, never over one that exists.
     *
     * @param directory
     *            the plugin folder's {@code lang} folder, created if missing
     * @param code
     *            a language code, already sanitised
     * @return what was written, or that nothing was
     * @throws IOException
     *             if the file could not be written
     */
    public static Result export(final File directory, final String code) throws IOException
    {
        final boolean shipped = LangExport.class.getResource(jarPath(code)) != null;
        final String text = render(code, shipped);
        Files.createDirectories(directory.toPath());
        try
        {
            Files.writeString(new File(directory, code + YML).toPath(), text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (final FileAlreadyExistsException e)
        {
            return Result.EXISTS;
        }
        return shipped ? Result.OVERLAY : Result.TRANSLATION;
    }

    /**
     * The file's text: a header, then the jar's file for the code if commented, its English if not.
     *
     * @param code
     *            the language code
     * @param commented
     *            true to comment out each message of the code's own file
     * @return the text
     * @throws IOException
     *             if the jar's file is missing or could not be read
     */
    static String render(final String code, final boolean commented) throws IOException
    {
        final String source = commented ? code : Messages.DEFAULT_LANGUAGE;
        final StringBuilder out = new StringBuilder(String.format(commented ? OVERLAY_HEADER : TRANSLATION_HEADER, code));
        try (InputStream in = LangExport.class.getResourceAsStream(jarPath(source)))
        {
            if (in == null)
            {
                throw new IOException("the plugin has no " + source + YML);
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean header = true;
            for (String line = reader.readLine(); line != null; line = reader.readLine())
            {
                // The jar's own header is replaced by the one above.
                header = header && (line.isBlank() || line.startsWith("#"));
                if (!header)
                {
                    final boolean message = !line.isBlank() && !line.startsWith("#");
                    out.append('\n').append((commented && message) ? "# " + line : line);
                }
            }
        }
        return out.append('\n').toString();
    }

    /**
     * The languages the jar ships, for tab completion, listed once from the jar or classes folder this
     * class was loaded from.
     *
     * @return the codes, sorted; English alone if the jar cannot be listed
     */
    public static List<String> shippedLanguages()
    {
        return Shipped.CODES;
    }

    /**
     * @param codeSource
     *            the plugin's jar, or a classes folder as in tests
     * @return the codes of the {@code lang/*.yml} it holds, sorted
     * @throws IOException
     *             if it cannot be read
     */
    static List<String> languagesIn(final Path codeSource) throws IOException
    {
        final TreeSet<String> codes = new TreeSet<>();
        if (Files.isDirectory(codeSource))
        {
            try (Stream<Path> files = Files.list(codeSource.resolve(LANG)))
            {
                files.forEach(file -> addCode(codes, file.getFileName().toString()));
            }
        }
        else
        {
            try (JarFile jar = new JarFile(codeSource.toFile()))
            {
                // A name in a folder below lang/ holds a / and is no code.
                jar.stream().map(JarEntry::getName).filter(name -> name.startsWith(LANG))
                    .forEach(name -> addCode(codes, name.substring(LANG.length())));
            }
        }
        return List.copyOf(codes);
    }

    private static void addCode(final TreeSet<String> codes, final String fileName)
    {
        if (fileName.endsWith(YML))
        {
            final String code = Messages.sanitise(fileName.substring(0, fileName.length() - YML.length()));
            if (code != null)
            {
                codes.add(code);
            }
        }
    }

    private static String jarPath(final String code)
    {
        return "/" + LANG + code + YML;
    }

    /** The jar's languages, listed on first use. */
    private static final class Shipped
    {
        static final List<String> CODES = find();

        private Shipped()
        {
        }

        private static List<String> find()
        {
            try
            {
                final CodeSource source = LangExport.class.getProtectionDomain().getCodeSource();
                if (source != null)
                {
                    return languagesIn(Paths.get(source.getLocation().toURI()));
                }
            }
            catch (final IOException | URISyntaxException | RuntimeException e)
            {
                PluginLog.log(Level.FINE, "Could not list the bundled languages; offering English. " + e);
            }
            return List.of(Messages.DEFAULT_LANGUAGE);
        }
    }
}
