package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The starter files {@code /wormhole lang export} writes (#26).
 *
 * <p>The commented file exists so an owner can reword a few messages without freezing the rest: a
 * copy of English with live lines would keep today's text for every key after an update changed it.
 * So the commented file must read as nothing at all, and uncommenting a line must change that key alone.
 */
class LangExportTest
{
    /** One message: a dotted key, then text in double quotes, on one line. */
    private static final Pattern MESSAGE = Pattern.compile("[a-z0-9.-]+: \"[^\"]*\"");

    @TempDir
    File folder;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Messages.load(Messages.DEFAULT_LANGUAGE, new File(folder, "none"));
        PluginTestSupport.remove();
    }

    private static Map<String, String> jarEnglish() throws IOException
    {
        try (InputStream in = LangExport.class.getResourceAsStream("/lang/en.yml"))
        {
            assertNotNull(in, "lang/en.yml should be bundled");
            return Messages.read(in, "en.yml");
        }
    }

    private static List<String> jarEnglishLines() throws IOException
    {
        try (InputStream in = LangExport.class.getResourceAsStream("/lang/en.yml"))
        {
            assertNotNull(in, "lang/en.yml should be bundled");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    /**
     * The export copies line by line, so it relies on every message being one line. A folded or
     * multi-line value would be half commented out and the file would not parse; this fails first.
     */
    @Test
    void everyEnglishMessageIsOneQuotedLine() throws IOException
    {
        final List<String> messages = jarEnglishLines().stream()
            .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        for (final String line : messages)
        {
            assertTrue(MESSAGE.matcher(line).matches(), "each message in en.yml must be key: \"text\" on one line: " + line);
        }
        assertEquals(jarEnglish().size(), messages.size(), "one line per message, no more and no fewer");
    }

    /** The commented file holds every message, each behind {@code # }, and no live line. */
    @Test
    void theCommentedExportHasEveryMessageCommentedOutAndNoneLive() throws IOException
    {
        final String text = LangExport.render("en", true);
        final List<String> lines = text.lines().toList();

        assertTrue(lines.stream().allMatch(line -> line.isBlank() || line.startsWith("#")),
            "a live line in the commented file would freeze that message: " + text);
        assertNull(new Yaml().load(text), "the commented file should read as no messages at all");
        for (final String message : jarEnglishLines().stream().filter(MESSAGE.asPredicate()).toList())
        {
            assertTrue(lines.contains("# " + message), "each message should be there to uncomment: " + message);
        }
    }

    /** The translation file holds every English message live, word for word. */
    @Test
    void theTranslationExportHasEveryEnglishMessageLive() throws IOException
    {
        final String text = LangExport.render("de", false);

        final Map<String, String> read = Messages.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "de.yml");
        assertEquals(jarEnglish(), read, "every key, with the jar's English as its text");
    }

    /** Both files open by saying how the file is read, and how to apply it. */
    @Test
    void bothExportsOpenWithTheRules() throws IOException
    {
        final String overlay = LangExport.render("en", true);
        final String translation = LangExport.render("de", false);

        assertTrue(overlay.startsWith("# Your own wording for Wormhole X-Treme's en messages"), overlay);
        assertTrue(overlay.contains("A message not in this file\n# uses the plugin's text."), overlay);
        assertTrue(translation.startsWith("# Wormhole X-Treme messages for de, in English"), translation);
        assertTrue(translation.contains("A message missing from\n# this file falls back"), translation);
        assertTrue(overlay.contains("/wormhole config -reload") && translation.contains("/wormhole config -reload"),
            "both should say how to apply an edit");
        assertFalse(overlay.contains("To translate, copy this file"), "the jar's own header is replaced, not doubled");
    }

    /**
     * The commented file, read as the owner's en.yml, sends every message exactly as before; and
     * uncommenting one line changes that message and no other.
     */
    @Test
    void theCommentedExportChangesNothingUntilALineIsUncommented() throws IOException
    {
        final File lang = new File(folder, "lang");
        final Map<String, String> english = jarEnglish();
        assertEquals(LangExport.Result.OVERLAY, LangExport.export(lang, "en"));

        Messages.load("en", lang);
        for (final String key : english.keySet())
        {
            assertEquals(english.get(key), Messages.get(null, key), "the commented file should change nothing: " + key);
        }

        final Path file = new File(lang, "en.yml").toPath();
        final String uncommented = Files.readString(file, StandardCharsets.UTF_8)
            .replace("# compass.none: \"No wormholes to track.\"", "compass.none: \"Nothing to point at.\"");
        Files.writeString(file, uncommented, StandardCharsets.UTF_8);
        Messages.load("en", lang);
        for (final String key : english.keySet())
        {
            final String expected = "compass.none".equals(key) ? "Nothing to point at." : english.get(key);
            assertEquals(expected, Messages.get(null, key), "only the uncommented line should change: " + key);
        }
    }

    /** A file already there is left byte for byte as it was. */
    @Test
    void anExistingFileIsNeverOverwritten() throws IOException
    {
        final File lang = new File(folder, "lang");
        assertTrue(lang.mkdirs());
        final Path mine = new File(lang, "de.yml").toPath();
        Files.writeString(mine, "compass.none: \"Meins.\"\n", StandardCharsets.UTF_8);

        assertEquals(LangExport.Result.EXISTS, LangExport.export(lang, "de"));
        assertEquals("compass.none: \"Meins.\"\n", Files.readString(mine, StandardCharsets.UTF_8));
    }

    /** The folder is made when missing, and a code the jar does not ship gets live English. */
    @Test
    void aMissingFolderIsMadeAndAnUnshippedCodeGetsLiveEnglish() throws IOException
    {
        final File lang = new File(folder, "plugin/lang");

        assertEquals(LangExport.Result.TRANSLATION, LangExport.export(lang, "pt_br"));
        assertEquals(LangExport.render("pt_br", false),
            Files.readString(new File(lang, "pt_br.yml").toPath(), StandardCharsets.UTF_8));
    }

    /** In a classes folder, each lang/*.yml named as a language is listed, and nothing else. */
    @Test
    void aClassesFolderListsItsLanguages() throws IOException
    {
        final Path root = folder.toPath();
        Files.createDirectories(root.resolve("lang"));
        for (final String name : new String[] { "en.yml", "de_de.yml", "Pt-BR.yml", "notes.txt" })
        {
            Files.writeString(root.resolve("lang").resolve(name), "", StandardCharsets.UTF_8);
        }

        assertEquals(List.of("de_de", "en"), LangExport.languagesIn(root));
    }

    /** In the plugin's jar, only the files directly in lang/ are languages. */
    @Test
    void aJarListsTheLanguagesDirectlyInItsLangFolder() throws IOException
    {
        final Path jar = folder.toPath().resolve("plugin.jar");
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file))
        {
            for (final String name : new String[] { "lang/en.yml", "lang/de.yml", "lang/old/fr.yml", "shapes/es.yml" })
            {
                out.putNextEntry(new JarEntry(name));
                out.closeEntry();
            }
        }

        assertEquals(List.of("de", "en"), LangExport.languagesIn(jar));
    }

    /** The running plugin lists English from its own classes, not the test fixtures beside them. */
    @Test
    void theShippedLanguagesAreTheMainResources()
    {
        final List<String> shipped = LangExport.shippedLanguages();
        assertTrue(shipped.contains("en"), "English ships: " + shipped);
        assertFalse(shipped.contains("qq"), "qq is a test fixture, not a shipped language: " + shipped);
    }
}
