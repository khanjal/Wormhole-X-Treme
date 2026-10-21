package com.wormhole_xtreme.wormhole.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * Chat text by key, in the language of whoever reads it (#26).
 *
 * <p>A template holds the sentence, its colour codes and {@code {0}} to {@code {99}} for its
 * arguments; the call site colours each argument with {@link ChatText} and adds the line's header,
 * so {@code ChatText.error(Messages.get(sender, "gate.build.no-shape", ChatText.name(shape)))}.
 * Placeholders are replaced in one pass, so an argument holding {@code {1}} is sent as it is, and an
 * apostrophe or any other brace is plain text, as it would not be under {@code MessageFormat}. A
 * placeholder with no argument is left as written.
 *
 * <p>A player reads their client's language: {@code de_de} looks in {@code de_de}, then {@code de},
 * then {@code server-language}, then English, key by key. Anyone else, and a player whose client
 * names no usable language, starts at {@code server-language}. A language is the jar's
 * {@code /lang/<code>.yml} with the owner's {@code lang/<code>.yml} from the plugin folder over it,
 * if they made one; loading writes nothing there. A key the jar's English lacks is sent as the key itself
 * and logged once.
 */
public final class Messages
{
    /** The language shipped, and the one any other falls back to. */
    public static final String DEFAULT_LANGUAGE = "en";

    /** Most client languages remembered at once; past it the memory is cleared, so a client cannot grow it. */
    static final int MOST_REMEMBERED = 256;

    /** Longest client language read; {@code de_de} is five. */
    private static final int LONGEST_LOCALE = 16;

    /** A language with no file: neither in the jar nor in the plugin folder. */
    private static final Map<String, Template> NONE = Map.of();

    /** What the last load read; the jar's English alone until {@link #load()} runs. */
    private static final AtomicReference<Catalog> catalog = new AtomicReference<>();

    /** Keys already logged as missing, so a message sent every trip logs once. */
    private static final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    /** Static helpers only. */
    private Messages()
    {
    }

    /**
     * The text for a key, in the recipient's language, its placeholders filled in.
     *
     * @param recipient
     *            who will read it; a player reads their client's language, anyone else and null the
     *            server's
     * @param key
     *            a key from {@code lang/en.yml}, written as a literal at the call site
     * @param args
     *            the arguments, {@code {0}} first, each already coloured
     * @return the text, or the key itself if no language has it
     */
    public static String get(final CommandSender recipient, final String key, final Object... args)
    {
        for (final Map<String, Template> language : catalog().chainFor(recipient))
        {
            final Template template = language.get(key);
            if (template != null)
            {
                return template.format(args);
            }
        }
        if (reportedMissing.add(key))
        {
            PluginLog.log(Level.WARNING, "No text for the message " + key + "; it is shown as its key.");
        }
        return key;
    }

    /** Reads the languages again, the plugin folder's lang files and {@code server-language} with them. */
    public static void load()
    {
        load(ConfigManager.getServerLanguage(), DataLayout.lang());
    }

    /**
     * Reads the languages again.
     *
     * @param serverLanguage
     *            the server's language code, already checked to be a plain file name
     * @param directory
     *            the plugin folder's {@code lang} folder, which need not exist and is never created
     */
    static void load(final String serverLanguage, final File directory)
    {
        final Catalog loaded = new Catalog(readOverlays(directory), serverLanguage);
        if (!DEFAULT_LANGUAGE.equals(serverLanguage) && (loaded.serverChain.size() < 2))
        {
            PluginLog.log(Level.WARNING, "There is no lang/" + serverLanguage + ".yml; messages are in English.");
        }
        catalog.set(loaded);
        reportedMissing.clear();
    }

    /**
     * A client's language as a code to look up, or null for one that is not usable.
     *
     * <p>The client sends it, so it is trusted for nothing: lower case, letters and {@code _} only,
     * and short, so it can never name a path.
     *
     * @param raw
     *            the client's locale as the server reports it, such as {@code de_DE}
     * @return the code, such as {@code de_de}, or null
     */
    public static String sanitise(final String raw)
    {
        if ((raw == null) || raw.isEmpty() || (raw.length() > LONGEST_LOCALE))
        {
            return null;
        }
        final String code = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < code.length(); i++)
        {
            final char c = code.charAt(i);
            if (((c < 'a') || (c > 'z')) && (c != '_'))
            {
                return null;
            }
        }
        return ((code.charAt(0) == '_') || (code.charAt(code.length() - 1) == '_')) ? null : code;
    }

    /** @return the catalog, the jar's English alone on first use when nothing has loaded */
    private static Catalog catalog()
    {
        final Catalog loaded = catalog.get();
        if (loaded != null)
        {
            return loaded;
        }
        // A load that lands meanwhile wins: only an empty reference takes this one.
        catalog.compareAndSet(null, new Catalog(Map.of(), DEFAULT_LANGUAGE));
        return catalog.get();
    }

    /** @return how many client languages are remembered now, for tests */
    static int remembered()
    {
        return catalog().chains.size();
    }

    /** @return how many language codes have been looked up, found or not, for tests */
    static int lookedUp()
    {
        return catalog().languages.size();
    }

    /** @return each lang file in the plugin folder whose name is a language code, read; none if there is no folder */
    private static Map<String, Map<String, String>> readOverlays(final File directory)
    {
        final File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null)
        {
            return Map.of();
        }
        final Map<String, Map<String, String>> overlays = new HashMap<>();
        for (final File file : files)
        {
            final String name = file.getName();
            final String code = sanitise(name.substring(0, name.length() - ".yml".length()));
            if ((code != null) && file.isFile())
            {
                overlays.put(code, readOverlay(file));
            }
        }
        return overlays;
    }

    /** @return one lang file from the plugin folder, empty if it cannot be read */
    private static Map<String, String> readOverlay(final File file)
    {
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

    /**
     * The languages read since the last load: each one parsed once, and each client language's chain
     * of them remembered, never anything per player.
     */
    private static final class Catalog
    {
        /** The owner's lang files from the plugin folder, read at load; nothing else looks there. */
        private final Map<String, Map<String, String>> overlays;

        /** Each language code looked up, the jar's file with the owner's over it, or {@link #NONE}. */
        private final Map<String, Map<String, Template>> languages = new ConcurrentHashMap<>();

        /** Each client language seen, to the languages it reads in order. */
        private final Map<String, List<Map<String, Template>>> chains = new ConcurrentHashMap<>();

        /** What the console, and a player with no usable language, reads. */
        private final List<Map<String, Template>> serverChain;

        private final Map<String, Template> english;

        Catalog(final Map<String, Map<String, String>> overlays, final String serverLanguage)
        {
            this.overlays = overlays;
            this.english = language(DEFAULT_LANGUAGE);
            this.serverChain = chainOf(serverLanguage, List.of());
        }

        /** @return the languages a recipient reads, in order */
        List<Map<String, Template>> chainFor(final CommandSender recipient)
        {
            if (!(recipient instanceof Player player))
            {
                return serverChain;
            }
            final String code = sanitise(PlayerLocales.of(player));
            if (code == null)
            {
                return serverChain;
            }
            final List<Map<String, Template>> known = chains.get(code);
            if (known != null)
            {
                return known;
            }
            if (chains.size() >= MOST_REMEMBERED)
            {
                chains.clear();
                languages.values().removeIf(language -> language == NONE);
            }
            final List<Map<String, Template>> chain = chainOf(code);
            chains.put(code, chain);
            return chain;
        }

        /** @return a code's languages: itself, its part before {@code _}, the server's, then English */
        private List<Map<String, Template>> chainOf(final String code)
        {
            return chainOf(code, serverChain);
        }

        private List<Map<String, Template>> chainOf(final String code, final List<Map<String, Template>> server)
        {
            final List<Map<String, Template>> chain = new ArrayList<>(4);
            add(chain, language(code));
            final int underscore = code.indexOf('_');
            if (underscore > 0)
            {
                add(chain, language(code.substring(0, underscore)));
            }
            server.forEach(language -> add(chain, language));
            add(chain, english);
            return List.copyOf(chain);
        }

        private static void add(final List<Map<String, Template>> chain, final Map<String, Template> language)
        {
            // By identity: two languages that happen to hold the same text are still two.
            if ((language != NONE) && chain.stream().noneMatch(held -> held == language))
            {
                chain.add(language);
            }
        }

        /** @return a language's text from the jar, empty if it has none, as a map the caller may add to */
        private static Map<String, String> readJar(final String code)
        {
            try (InputStream in = Messages.class.getResourceAsStream("/lang/" + code + ".yml"))
            {
                if (in != null)
                {
                    return read(in, code + ".yml");
                }
            }
            catch (final IOException | YAMLException e)
            {
                PluginLog.log(Level.SEVERE, "Could not read the bundled " + code + " messages. " + e);
            }
            return new HashMap<>();
        }

        /** @return each text parsed once into its template */
        private static Map<String, Template> parse(final Map<String, String> text)
        {
            final Map<String, Template> parsed = new HashMap<>();
            text.forEach((key, value) -> parsed.put(key, Template.parse(value)));
            return Map.copyOf(parsed);
        }

        /** @return a code's templates, read once: the jar's file, the owner's over it, or {@link #NONE} */
        private Map<String, Template> language(final String code)
        {
            return languages.computeIfAbsent(code, c -> {
                final Map<String, String> text = readJar(c);
                text.putAll(overlays.getOrDefault(c, Map.of()));
                return text.isEmpty() ? NONE : parse(text);
            });
        }
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
