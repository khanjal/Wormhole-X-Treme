package com.wormhole_xtreme.wormhole.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * Says at startup whether a newer release exists (#461), in the log and to players with
 * {@value #NOTIFY_PERMISSION} as they join. It only ever says so; it never downloads anything.
 *
 * <p>Modrinth is asked first, for the newest release built for this server's Minecraft version;
 * GitHub's latest release is asked only when Modrinth fails or names none.
 */
public final class UpdateCheck implements Listener
{
    /** Who is told on joining. */
    static final String NOTIFY_PERMISSION = "wormhole.update.notify";

    static final String MODRINTH_API = "https://api.modrinth.com/v2/project/wormhole-x-treme/version";

    static final String MODRINTH_PAGE = "https://modrinth.com/plugin/wormhole-x-treme";

    static final String GITHUB_API = "https://api.github.com/repos/khanjal/Wormhole-X-Treme/releases/latest";

    private static final int TIMEOUT_MILLIS = 5000;

    /** Far more than either answer needs; a longer one is cut short and reads as malformed. */
    private static final int MAX_BODY_BYTES = 1 << 20;

    private static final int[] NONE = {};

    private static final int MAX_REASON_CHARS = 120;

    private static final Pattern CONTROL_OR_COLOUR = Pattern.compile("[\\p{Cntrl}§]");

    /** No whitespace, control characters or colour codes reach the log or chat from a remote answer. */
    private static final Pattern SAFE_VERSION = Pattern.compile("[vV]?[0-9A-Za-z.+-]{1,40}");

    private static final Pattern SAFE_GITHUB_PAGE = Pattern.compile("https://github\\.com/[!-~]{1,200}");

    private final String message;

    /** Reads a URL's body; anything but a 2xx answer is an IOException. */
    @FunctionalInterface
    interface Fetcher
    {
        String get(String url) throws IOException;
    }

    /** A release and where to get it. */
    record Release(String version, String url)
    {
    }

    UpdateCheck(final String message)
    {
        this.message = message;
    }

    /**
     * Starts the check off the main thread, if {@code update-check} allows it; never throws.
     *
     * @param plugin
     *            this plugin
     */
    public static void startIfConfigured(final JavaPlugin plugin)
    {
        try
        {
            if (!ConfigManager.isUpdateCheckEnabled())
            {
                return;
            }
            final String running = plugin.getDescription().getVersion();
            final String minecraft = minecraftVersion(plugin.getServer().getBukkitVersion());
            final Fetcher fetcher = http("khanjal/Wormhole-X-Treme/" + running + " (update check)");
            WormholeXTreme.getScheduler().runTaskAsynchronously(plugin,
                () -> check(plugin, fetcher, running, minecraft));
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "Could not start the update check", e);
        }
    }

    /** Runs off the main thread, and goes back to it only when there is something to say. */
    static void check(final JavaPlugin plugin, final Fetcher fetcher, final String running,
        final String minecraft)
    {
        try
        {
            final Optional<Release> newer = findNewer(fetcher, running, minecraft,
                line -> WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, line));
            if (newer.isPresent())
            {
                final Release release = newer.get();
                WormholeXTreme.getScheduler().runTask(plugin, () -> announce(plugin, running, release));
            }
        }
        catch (final Exception | LinkageError e)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, "The update check failed", e);
        }
    }

    private static void announce(final JavaPlugin plugin, final String running, final Release release)
    {
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Wormhole X-Treme " + release.version()
            + " is available (running " + running + "): " + release.url());
        final String line = ConfigManager.MessageStrings.NORMAL_HEADER.toString() + "Wormhole X-Treme "
            + ChatText.value(release.version()) + " is available (running " + running + "): " + release.url();
        plugin.getServer().getPluginManager().registerEvents(new UpdateCheck(line), plugin);
    }

    /**
     * Tells a joining player about the newer release, if they may be told.
     *
     * @param event
     *            the join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();
        if (player.hasPermission(NOTIFY_PERMISSION))
        {
            player.sendMessage(message);
        }
    }

    /**
     * The newest release, if it is newer than the one running.
     *
     * @param fetcher
     *            how to read a URL
     * @param running
     *            this plugin's version
     * @param minecraft
     *            the server's Minecraft version, such as 1.21.8
     * @param note
     *            where to say why nothing was found, for the FINE log
     * @return the newer release, or empty when there is none or nothing could be told
     */
    static Optional<Release> findNewer(final Fetcher fetcher, final String running, final String minecraft,
        final Consumer<String> note)
    {
        Release latest = null;
        try
        {
            final String version = latestFromModrinth(fetcher.get(modrinthUrl(minecraft)), minecraft);
            if (version == null)
            {
                note.accept("Modrinth lists no release for Minecraft " + minecraft + "; trying GitHub.");
            }
            else
            {
                latest = new Release(version, MODRINTH_PAGE);
            }
        }
        catch (final IOException | RuntimeException modrinthFailed)
        {
            note.accept("Modrinth did not answer the update check (" + describe(modrinthFailed) + "); trying GitHub.");
        }
        // A release Modrinth names, newer or not, is final; none may only mean it has not tagged this Minecraft version yet.
        if (latest == null)
        {
            latest = fromGitHub(fetcher, note);
        }
        if ((latest != null) && isNewer(latest.version(), running))
        {
            return Optional.of(latest);
        }
        return Optional.empty();
    }

    private static Release fromGitHub(final Fetcher fetcher, final Consumer<String> note)
    {
        try
        {
            return latestFromGitHub(fetcher.get(GITHUB_API));
        }
        catch (final IOException | RuntimeException gitHubFailed)
        {
            note.accept("GitHub did not answer the update check (" + describe(gitHubFailed) + ").");
            return null;
        }
    }

    /** A failure for the log, short and plain: a parser's message can carry the whole remote body. */
    static String describe(final Exception failure)
    {
        final String message = failure.getMessage();
        if (message == null)
        {
            return failure.getClass().getSimpleName();
        }
        final String plain = CONTROL_OR_COLOUR.matcher(message).replaceAll("");
        return failure.getClass().getSimpleName() + ": "
            + ((plain.length() > MAX_REASON_CHARS) ? (plain.substring(0, MAX_REASON_CHARS) + "...") : plain);
    }

    /** Modrinth's versions for this Minecraft version alone, so a long history stays well under the body cap. */
    static String modrinthUrl(final String minecraft)
    {
        return MODRINTH_API + "?game_versions="
            + URLEncoder.encode("[\"" + minecraft + "\"]", StandardCharsets.UTF_8) + "&include_changelog=false";
    }

    /** Whether remote text is fit to put in the log and chat as a version. */
    static boolean safeVersion(final String version)
    {
        return (version != null) && SAFE_VERSION.matcher(version).matches();
    }

    /**
     * The highest release on Modrinth built for this Minecraft version.
     *
     * @param json
     *            Modrinth's list of versions
     * @param minecraft
     *            the server's Minecraft version
     * @return its version number, or null if none is built for this Minecraft version
     * @throws RuntimeException
     *             if the body is not a JSON array
     */
    static String latestFromModrinth(final String json, final String minecraft)
    {
        final JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        String best = null;
        for (final JsonElement element : versions)
        {
            if (!element.isJsonObject())
            {
                continue;
            }
            final JsonObject entry = element.getAsJsonObject();
            final String number = string(entry, "version_number");
            if ("release".equals(string(entry, "version_type")) && builtFor(entry, minecraft)
                && safeVersion(number) && (numbers(number).length > 0) && ((best == null) || isNewer(number, best)))
            {
                best = number;
            }
        }
        return best;
    }

    private static boolean builtFor(final JsonObject entry, final String minecraft)
    {
        final JsonElement games = entry.get("game_versions");
        if ((games == null) || !games.isJsonArray())
        {
            return false;
        }
        for (final JsonElement game : games.getAsJsonArray())
        {
            if (game.isJsonPrimitive() && game.getAsString().equals(minecraft))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * GitHub's latest release, which already leaves out drafts and prereleases.
     *
     * @param json
     *            GitHub's answer
     * @return the release, or null if it names no tag or GitHub page fit to show
     * @throws RuntimeException
     *             if the body is not a JSON object
     */
    static Release latestFromGitHub(final String json)
    {
        final JsonObject release = JsonParser.parseString(json).getAsJsonObject();
        final String tag = string(release, "tag_name");
        final String page = string(release, "html_url");
        return (safeVersion(tag) && (page != null) && SAFE_GITHUB_PAGE.matcher(page).matches())
            ? new Release(tag, page) : null;
    }

    private static String string(final JsonObject object, final String key)
    {
        final JsonElement value = object.get(key);
        return ((value != null) && value.isJsonPrimitive()) ? value.getAsString() : null;
    }

    /**
     * Whether a version is newer than another; false when either cannot be read.
     *
     * @param candidate
     *            the version that may be newer
     * @param than
     *            the version to compare against
     * @return true only if candidate is strictly newer
     */
    static boolean isNewer(final String candidate, final String than)
    {
        final OptionalInt order = compareVersions(candidate, than);
        return order.isPresent() && (order.getAsInt() > 0);
    }

    /**
     * Orders two versions by number, part by part, so 1.10 is after 1.9 and 1.9 equals 1.9.0.
     * A leading v is ignored, and a qualifier such as -SNAPSHOT puts a version before the same
     * numbers without one.
     *
     * @param a
     *            one version
     * @param b
     *            the other
     * @return negative, zero or positive as a is before, the same as or after b; empty if either
     *         cannot be read
     */
    static OptionalInt compareVersions(final String a, final String b)
    {
        final int[] left = numbers(a);
        final int[] right = numbers(b);
        if ((left.length == 0) || (right.length == 0))
        {
            return OptionalInt.empty();
        }
        for (int i = 0; i < Math.max(left.length, right.length); i++)
        {
            final int order = Integer.compare(part(left, i), part(right, i));
            if (order != 0)
            {
                return OptionalInt.of(order);
            }
        }
        return OptionalInt.of(Boolean.compare(!qualified(a), !qualified(b)));
    }

    private static int part(final int[] parts, final int index)
    {
        return (index < parts.length) ? parts[index] : 0;
    }

    /** The version's numbers, or none if it does not start with dotted numbers. */
    private static int[] numbers(final String version)
    {
        if (version == null)
        {
            return NONE;
        }
        final String[] parts = numeric(version).split("\\.", -1);
        final int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            if (parts[i].isEmpty() || !parts[i].chars().allMatch(c -> (c >= '0') && (c <= '9')))
            {
                return NONE;
            }
            try
            {
                numbers[i] = Integer.parseInt(parts[i]);
            }
            catch (final NumberFormatException tooLong)
            {
                return NONE;
            }
        }
        return numbers;
    }

    /** The version without a leading v, cut at any qualifier. */
    private static String numeric(final String version)
    {
        final String trimmed = version.trim();
        final String bare = (trimmed.startsWith("v") || trimmed.startsWith("V")) ? trimmed.substring(1) : trimmed;
        final int qualifier = qualifierAt(bare);
        return (qualifier < 0) ? bare : bare.substring(0, qualifier);
    }

    private static boolean qualified(final String version)
    {
        return qualifierAt(version.trim()) >= 0;
    }

    private static int qualifierAt(final String version)
    {
        final int dash = version.indexOf('-');
        final int plus = version.indexOf('+');
        if (dash < 0)
        {
            return plus;
        }
        return (plus < 0) ? dash : Math.min(dash, plus);
    }

    /**
     * The Minecraft version in a Bukkit version.
     *
     * @param bukkitVersion
     *            such as 1.21.8-R0.1-SNAPSHOT
     * @return such as 1.21.8
     */
    static String minecraftVersion(final String bukkitVersion)
    {
        final int dash = bukkitVersion.indexOf('-');
        return (dash < 0) ? bukkitVersion : bukkitVersion.substring(0, dash);
    }

    /** Reads a URL over HTTP with short timeouts, off the main thread only. */
    private static Fetcher http(final String userAgent)
    {
        return url -> {
            final HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try
            {
                connection.setConnectTimeout(TIMEOUT_MILLIS);
                connection.setReadTimeout(TIMEOUT_MILLIS);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept",
                    url.startsWith("https://api.github.com/") ? "application/vnd.github+json" : "application/json");
                final int status = connection.getResponseCode();
                if ((status < 200) || (status >= 300))
                {
                    throw new IOException("HTTP " + status + " from " + url);
                }
                return readBody(connection);
            }
            finally
            {
                connection.disconnect();
            }
        };
    }

    private static String readBody(final HttpURLConnection connection) throws IOException
    {
        try (InputStream body = connection.getInputStream())
        {
            return new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
        }
    }
}
