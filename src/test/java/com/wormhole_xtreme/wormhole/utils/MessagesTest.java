package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Chat text read by key from {@code lang/} over the bundled English (#26).
 *
 * <p>What a translation can get wrong is what these guard: a placeholder moved, an apostrophe or a
 * brace in the sentence, an argument that looks like a placeholder, a key left out, a language with
 * no file. Each must still send a sentence, never an exception or a raw key that English has.
 */
class MessagesTest
{
    @TempDir
    File lang;

    private WormholeXTreme plugin;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Messages.load(Messages.DEFAULT_LANGUAGE, lang);
        PluginTestSupport.remove();
    }

    private void write(final String name, final String yaml) throws Exception
    {
        Files.writeString(new File(lang, name).toPath(), yaml, StandardCharsets.UTF_8);
    }

    private static String format(final String template, final Object... args)
    {
        return Messages.Template.parse(template).format(args);
    }

    /** A translation that puts its arguments the other way round gets them the other way round. */
    @Test
    void placeholdersMayComeInAnyOrder()
    {
        assertEquals("b then a", format("{1} then {0}", "a", "b"));
        assertEquals("a, a", format("{0}, {0}", "a"), "a placeholder may be used twice");
    }

    /** A colour code in the template is sent as written, not stripped and reapplied. */
    @Test
    void colourCodesInATemplateAreKept()
    {
        assertEquals("§ecompass§7 here", format("§ecompass§7 {0}", "here"));
    }

    /**
     * Apostrophes and braces are plain text. Under MessageFormat {@code can't} loses its apostrophe and
     * a lone brace throws, which every English sentence with "it's" would have run into.
     */
    @Test
    void apostrophesAndOtherBracesAreText()
    {
        assertEquals("can't {x} {} {123} { 0} x", format("can't {x} {} {123} { 0} {0}", "x"));
        assertEquals("ends with {", format("ends with {"));
        assertEquals("{1", format("{1"));
    }

    /** An argument holding a placeholder is sent as typed, not expanded by a second pass: a gate called {1}. */
    @Test
    void anArgumentThatLooksLikeAPlaceholderIsNotExpanded()
    {
        assertEquals("{1} and b", format("{0} and {1}", "{1}", "b"));
    }

    /** A placeholder with no argument stays as written rather than throwing or vanishing. */
    @Test
    void aPlaceholderWithNoArgumentIsLeftAsWritten()
    {
        assertEquals("a and {1}", format("{0} and {1}", "a"));
        assertEquals("{0}", format("{0}", (Object[]) null));
    }

    /** A key the chosen language leaves out is sent in English, as the issue asked: never the key. */
    @Test
    void aKeyMissingFromATranslationIsSentInEnglish() throws Exception
    {
        write("de.yml", "compass.none: \"Keine Wurmlöcher.\"\n");

        Messages.load("de", lang);

        assertEquals("Keine Wurmlöcher.", Messages.get("compass.none"));
        assertEquals("Compass back to normal: it points at world spawn again.", Messages.get("compass.reset"));
    }

    /** An edited en.yml on disk that has lost a key still sends that key's text: the jar's English is underneath. */
    @Test
    void anEditedEnglishCopyCannotLoseAKey() throws Exception
    {
        write("en.yml", "compass.none: \"Nothing to point at.\"\n");

        Messages.load("en", lang);

        assertEquals("Nothing to point at.", Messages.get("compass.none"));
        assertEquals("Compass back to normal: it points at world spawn again.", Messages.get("compass.reset"));
    }

    /** A language with no file is English, said once in the log. */
    @Test
    void aLanguageWithNoFileIsEnglish()
    {
        Messages.load("xx", lang);

        assertEquals("No wormholes to track.", Messages.get("compass.none"));
        verify(plugin).prettyLog(eq(Level.WARNING), contains("xx.yml"));
    }

    /** A file that is not YAML, or holds a value that is not text, costs those messages, never the rest. */
    @Test
    void anUnreadableTranslationIsEnglish() throws Exception
    {
        write("de.yml", "compass.none: [\n");
        Messages.load("de", lang);
        assertEquals("No wormholes to track.", Messages.get("compass.none"));

        write("de.yml", "compass.none: no\ncompass.reset: \"Zurück.\"\n");
        Messages.load("de", lang);
        assertEquals("No wormholes to track.", Messages.get("compass.none"), "YAML reads a bare no as false");
        assertEquals("Zurück.", Messages.get("compass.reset"));
    }

    /** A key in no language returns itself, logs once however often it is sent, and never throws. */
    @Test
    void aKeyNoLanguageHasIsItsOwnTextAndLogsOnce()
    {
        Messages.load("en", lang);

        assertDoesNotThrow(() -> Messages.get("no.such.key", "a"));
        assertEquals("no.such.key", Messages.get("no.such.key"));
        verify(plugin, times(1)).prettyLog(eq(Level.WARNING), contains("no.such.key"));
    }

    /** The bundled English is copied out for translators, and an edited copy is never overwritten. */
    @Test
    void theBundledEnglishIsWrittenOutOnlyWhenMissing() throws Exception
    {
        final File en = new File(lang, "en.yml");
        Messages.writeMissing(lang);
        assertTrue(Files.readString(en.toPath(), StandardCharsets.UTF_8).contains("compass.none:"),
            "the copy should be the bundled file");

        write("en.yml", "compass.none: \"Mine.\"\n");
        Messages.writeMissing(lang);
        assertEquals("compass.none: \"Mine.\"\n", Files.readString(en.toPath(), StandardCharsets.UTF_8),
            "an edited en.yml must survive a restart");
        verify(plugin, never()).prettyLog(eq(Level.WARNING), anyString());
    }

    /**
     * The converted lines read exactly as the code built them before they moved to en.yml: colours,
     * spacing and full stops included. Extraction must never reword.
     */
    @Test
    void theConvertedLinesReadAsTheyDidBefore()
    {
        Messages.load("en", lang);
        final String gate = ChatText.name("Abydos");
        final String at = ChatText.value("0 -60 0");
        assertEquals("Built " + gate + " at " + at + " in " + ChatText.name("world") + ". Opening centred on "
            + ChatText.value("1.5 2.5 3.5") + "; arrivals at " + ChatText.value("1.5 2.0 4.5") + ".",
            Messages.get("gate.build.built", gate, at, ChatText.name("world"), ChatText.value("1.5 2.5 3.5"),
                ChatText.value("1.5 2.0 4.5")));
        assertEquals("Carry a plain " + ChatText.material("compass")
            + " to see it; lodestone and recovery compasses follow their own targets.", Messages.get("compass.plain-only"));
        assertEquals("Compass set to wormhole " + gate + ".", Messages.get("compass.set", gate));
        assertEquals("part of it would be below the world's floor at " + ChatText.value("-64") + ".",
            Messages.get("gate.build.below-floor", ChatText.value("-64")));
        assertEquals("Facing must be " + ChatText.command("north") + ", " + ChatText.command("south") + ", "
            + ChatText.command("east") + " or " + ChatText.command("west") + ", not " + ChatText.command("up") + ".",
            Messages.get("gate.build.facing", ChatText.command("north"), ChatText.command("south"),
                ChatText.command("east"), ChatText.command("west"), ChatText.command("up")));
        assertEquals("Unknown option " + ChatText.command("x") + "; only " + ChatText.command("net=") + " and "
            + ChatText.command("idc=") + " follow the facing.",
            Messages.get("gate.build.unknown-option", ChatText.command("x"), ChatText.command("net="),
                ChatText.command("idc=")));
        assertEquals("Not built: why", Messages.get("gate.build.not-built", "why"));
    }
}
