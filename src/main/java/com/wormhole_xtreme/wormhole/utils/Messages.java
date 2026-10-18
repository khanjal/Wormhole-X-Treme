package com.wormhole_xtreme.wormhole.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Chat text by key, for the server's language, with English beneath it (#26).
 *
 * <p>A template holds the sentence, its colour codes and {@code {0}} to {@code {99}} for its
 * arguments; the call site colours each argument with {@link ChatText} and adds the line's header,
 * so {@code ChatText.error(Messages.get("gate.build.no-shape", ChatText.name(shape)))}. Placeholders
 * are replaced in one pass, so an argument holding {@code {1}} is sent as it is, and an apostrophe or
 * any other brace is plain text, as it would not be under {@code MessageFormat}. A placeholder with
 * no argument is left as written.
 *
 * <p>Each key is looked up in {@code lang/<language>.yml} in the plugin folder, if the owner made one,
 * then in the jar's {@code /lang/<language>.yml}, then in the jar's English, so a stale or partial
 * file cannot lose a key. Nothing is written to the plugin folder. A key the jar's English lacks is
 * sent as the key itself and logged once.
 */
public final class Messages
{
    /** The language shipped, and the one any other falls back to. */
    public static final String DEFAULT_LANGUAGE = "en";

    /** The templates by key; the jar's English alone until {@link #load()} runs. */
    private static volatile Map<String, Template> templates;

    /** Keys already logged as missing, so a message sent every trip logs once. */
    private static final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    /** Static helpers only. */
    private Messages()
    {
    }

    /**
     * The text for a key, its placeholders filled in.
     *
     * @param key
     *            a key from {@code lang/en.yml}, written as a literal at the call site
     * @param args
     *            the arguments, {@code {0}} first, each already coloured
     * @return the text, or the key itself if no language has it
     */
    public static String get(final String key, final Object... args)
    {
        final Template template = templates().get(key);
        if (template == null)
        {
            if (reportedMissing.add(key))
            {
                PluginLog.log(Level.WARNING, "No text for the message " + key + "; it is shown as its key.");
            }
            return key;
        }
        return template.format(args);
    }

    /** Reads the configured language again. */
    public static void load()
    {
        load(ConfigManager.getLanguage(), DataLayout.lang());
    }

    /**
     * Reads one language: the plugin folder's file over the jar's, over the jar's English.
     *
     * @param language
     *            the language code, already checked to be a plain file name
     * @param directory
     *            the plugin folder's {@code lang} folder, which need not exist and is never created
     */
    static void load(final String language, final File directory)
    {
        final Map<String, String> text = readJar(DEFAULT_LANGUAGE);
        final Map<String, String> jar = DEFAULT_LANGUAGE.equals(language) ? Map.of() : readJar(language);
        final File file = new File(directory, language + ".yml");
        if (!DEFAULT_LANGUAGE.equals(language) && jar.isEmpty() && !file.isFile())
        {
            PluginLog.log(Level.WARNING, "There is no lang/" + file.getName() + "; messages are in English.");
        }
        text.putAll(jar);
        text.putAll(readOverlay(file));
        templates = parse(text);
        reportedMissing.clear();
    }

    /** @return the plugin folder's file for a language, empty if there is none or it cannot be read */
    private static Map<String, String> readOverlay(final File file)
    {
        if (!file.isFile())
        {
            return Map.of();
        }
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            return read(in, file.getName());
        }
        catch (final IOException | YAMLException e)
        {
            PluginLog.log(Level.WARNING, "Could not read lang/" + file.getName() + "; it is ignored. " + e);
            return Map.of();
        }
    }

    /** @return a language's file in the jar, or null if the jar has none */
    private static InputStream bundled(final String language)
    {
        return Messages.class.getResourceAsStream("/lang/" + language + ".yml");
    }

    /** @return the templates, the jar's English parsed on first use when nothing has loaded */
    private static Map<String, Template> templates()
    {
        Map<String, Template> loaded = templates;
        if (loaded == null)
        {
            loaded = parse(readJar(DEFAULT_LANGUAGE));
            templates = loaded;
        }
        return loaded;
    }

    /** @return a language's text from the jar, empty if it has none, as a map the caller may add to */
    private static Map<String, String> readJar(final String language)
    {
        try (InputStream in = bundled(language))
        {
            if (in != null)
            {
                return read(in, language + ".yml");
            }
        }
        catch (final IOException | YAMLException e)
        {
            PluginLog.log(Level.SEVERE, "Could not read the bundled " + language + " messages. " + e);
        }
        return new HashMap<>();
    }

    /**
     * Reads a flat key-to-text file; anything that is not text is skipped, and said once.
     *
     * @param in
     *            the file's contents
     * @param name
     *            its name, for the log
     * @return the text by key
     */
    static Map<String, String> read(final InputStream in, final String name)
    {
        final Map<String, String> text = new HashMap<>();
        final Object loaded = new Yaml().load(in);
        if (!(loaded instanceof Map<?, ?> map))
        {
            if (loaded != null)
            {
                PluginLog.log(Level.WARNING, "lang/" + name + " is not a list of messages; it is ignored.");
            }
            return text;
        }
        int skipped = 0;
        for (final Map.Entry<?, ?> entry : map.entrySet())
        {
            if (entry.getValue() instanceof String template)
            {
                text.put(String.valueOf(entry.getKey()), template);
            }
            else
            {
                skipped++;
            }
        }
        if (skipped > 0)
        {
            PluginLog.log(Level.WARNING, skipped + " entries in lang/" + name
                + " are not text in quotes; those messages are in English.");
        }
        return text;
    }

    /** @return each text parsed once into its template */
    private static Map<String, Template> parse(final Map<String, String> text)
    {
        final Map<String, Template> parsed = new HashMap<>();
        text.forEach((key, value) -> parsed.put(key, Template.parse(value)));
        return Map.copyOf(parsed);
    }

    /**
     * A message split once into its literal runs and the placeholders between them.
     *
     * <p>{@code literals} has one more entry than {@code slots}: literal, slot, literal, and so on.
     */
    static final class Template
    {
        private final String[] literals;

        private final int[] slots;

        /** Each placeholder as written, sent back when its argument is missing. */
        private final String[] written;

        /** The length of the literal runs together, to size the output. */
        private final int literalLength;

        private Template(final List<String> literals, final List<Integer> slots, final List<String> written)
        {
            this.literals = literals.toArray(new String[0]);
            this.slots = slots.stream().mapToInt(Integer::intValue).toArray();
            this.written = written.toArray(new String[0]);
            this.literalLength = literals.stream().mapToInt(String::length).sum();
        }

        /**
         * Splits a text at each {@code {n}}, n one or two digits; any other brace is literal.
         *
         * @param text
         *            the template as written
         * @return the parsed template
         */
        static Template parse(final String text)
        {
            final List<String> literals = new ArrayList<>();
            final List<Integer> slots = new ArrayList<>();
            final List<String> written = new ArrayList<>();
            int start = 0;
            int i = 0;
            while (i < text.length())
            {
                final int end = placeholderEnd(text, i);
                if (end < 0)
                {
                    i++;
                    continue;
                }
                literals.add(text.substring(start, i));
                slots.add(Integer.parseInt(text.substring(i + 1, end - 1)));
                written.add(text.substring(i, end));
                start = end;
                i = end;
            }
            literals.add(text.substring(start));
            return new Template(literals, slots, written);
        }

        /** @return the index just past a placeholder starting at {@code i}, or -1 if none does */
        private static int placeholderEnd(final String text, final int i)
        {
            if (text.charAt(i) != '{')
            {
                return -1;
            }
            int j = i + 1;
            while ((j < text.length()) && (j - i <= 2) && (text.charAt(j) >= '0') && (text.charAt(j) <= '9'))
            {
                j++;
            }
            return ((j > i + 1) && (j < text.length()) && (text.charAt(j) == '}')) ? j + 1 : -1;
        }

        /**
         * Fills the placeholders in.
         *
         * @param args
         *            the arguments, possibly fewer than the placeholders, or null
         * @return the text
         */
        String format(final Object[] args)
        {
            if (slots.length == 0)
            {
                return literals[0];
            }
            final int count = (args == null) ? 0 : args.length;
            final StringBuilder out = new StringBuilder(literalLength + (16 * slots.length));
            for (int k = 0; k < slots.length; k++)
            {
                out.append(literals[k]);
                if (slots[k] < count)
                {
                    out.append(args[slots[k]]);
                }
                else
                {
                    out.append(written[k]);
                }
            }
            return out.append(literals[slots.length]).toString();
        }
    }
}
