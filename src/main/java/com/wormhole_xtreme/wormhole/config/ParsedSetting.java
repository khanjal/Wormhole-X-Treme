package com.wormhole_xtreme.wormhole.config;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.utils.ChatColour;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;
import com.wormhole_xtreme.wormhole.utils.Messages;
import com.wormhole_xtreme.wormhole.utils.SignStyle;

/**
 * What a value typed at {@code /wormhole config} turns into, or why it was refused.
 *
 * <p>Pure: nothing here reads or writes a setting, and nothing touches the config file. That
 * is what lets the rules below be tested directly, which matters because the whole point of
 * this class is the cases it says no to, and there is no other way to see them.
 *
 * <p>Settings used to be typed by what was already in them -- a boolean stayed a boolean, a
 * number stayed a number, and <em>anything else was accepted as typed</em>. That last one is
 * the gap this closes. Several settings are stored as text but have a closed set of valid
 * values, and every one of them read back through a getter that quietly substitutes a
 * fallback when it cannot parse what it finds. So
 * {@code /wormhole config ring_default_style banana} answered
 * "RING_DEFAULT_STYLE is now banana." and then rings deployed concurrently forever, because
 * that is what {@code getRingDefaultStyle()} returns when the stored text is not a style. The
 * success message was not merely unhelpful, it was wrong: the setting was never banana, and
 * nothing anywhere ever said so.
 *
 * <p>Accepted values are stored in their canonical form rather than as typed. That is not
 * tidiness: {@link RingStyle#parse} accepts {@code slow} for {@code SEQUENTIAL}, while
 * {@link ConfigManager#getRingDefaultStyle()} reads the stored text back with
 * {@code valueOf}. Storing the word as typed would have made {@code slow} mean
 * {@code CONCURRENT} -- the very bug this class exists to stop, reintroduced by the fix for
 * it. The same goes for {@code LOG_LEVEL}, which {@link Setting#getLevel()} parses
 * case-sensitively and without catching failure.
 *
 * <p>Sound settings are deliberately left as free text. Naming a sound instead of resolving
 * it to a {@code Sound} constant is what lets a resource pack's own sound through, and this
 * plugin cannot know those names. Validating them would be a regression, not a fix.
 */
final class ParsedSetting
{
    private static final String TRUE = "true";

    private static final String FALSE = "false";

    /** The value to store, or null if this was refused. */
    private final Object value;

    /** Why it was refused, in a reader's language, or null if it was accepted. */
    private final Function<CommandSender, String> refusal;

    /**
     * Instantiates a parsed setting.
     *
     * @param value
     *            the value to store
     * @param refusal
     *            why it was refused
     */
    private ParsedSetting(final Object value, final Function<CommandSender, String> refusal)
    {
        this.value = value;
        this.refusal = refusal;
    }

    /**
     * Reads a typed value against the rules for one setting.
     *
     * @param key
     *            which setting is being written
     * @param current
     *            what is in it now, which is what says whether it is a boolean, a number or
     *            text -- every setting arrives with a default, so this is never absent
     * @param typed
     *            the value as typed
     * @return the value to store, or the reason it was refused
     */
    static ParsedSetting read(final ConfigKeys key, final Object current, final String typed)
    {
        // A command always has something to hand here, since it only gets this far once a
        // value has been typed. Treating an absent one as empty keeps every rule below able
        // to assume a string, rather than each having to say so again.
        final String raw = typed == null ? "" : typed;
        final ParsedSetting named = readNamed(key, raw);
        if (named != null)
        {
            return named;
        }
        if (current instanceof Boolean)
        {
            if (!TRUE.equalsIgnoreCase(raw) && !FALSE.equalsIgnoreCase(raw))
            {
                return refused(recipient -> either(recipient, key, TRUE, FALSE, raw));
            }
            return accepted(Boolean.valueOf(raw));
        }
        if (current instanceof Integer)
        {
            try
            {
                return accepted(Integer.valueOf(raw.trim()));
            }
            catch (final NumberFormatException notANumber)
            {
                return refused(recipient -> number(recipient, key, raw));
            }
        }
        if (current instanceof Double)
        {
            try
            {
                return accepted(Double.valueOf(raw.trim()));
            }
            catch (final NumberFormatException notANumber)
            {
                return refused(recipient -> number(recipient, key, raw));
            }
        }
        return readText(key, raw);
    }

    /**
     * Reads a setting whose value is one of a fixed set of names, checked before the type: a
     * config.yml from before dial-spin patterns still holds a boolean there.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the value to store or the reason it was refused, or null for any other setting
     */
    private static ParsedSetting readNamed(final ConfigKeys key, final String raw)
    {
        if (key == ConfigKeys.GATE_DIAL_SPIN)
        {
            final DialSpinPattern pattern =
                DialSpinPattern.parse(raw);
            return (pattern == null)
                ? refused(recipient -> Messages.get(recipient, "config.refuse.dial-spin", key, "CHEVRON", "TOP", "LAP",
                    "FILL", "PEGASUS", "CHASE", "UNIVERSE", "OVERSHOOT", "NONE", quoted(raw)))
                : accepted(pattern.name());
        }
        // Refused rather than stored: an unknown style would sweep as the default and be
        // reported by gate edit as though it were a style (#427).
        if (key == ConfigKeys.GATE_IRIS_ANIMATION)
        {
            final String animation = ConfigManager.parseIrisAnimation(raw);
            return (animation == null)
                ? notOneOf(key, ConfigManager.irisAnimations(), raw)
                : accepted(animation);
        }
        if (key == ConfigKeys.GATE_VIEW)
        {
            final String level = ConfigManager.parseGateView(raw);
            return (level == null)
                ? notOneOf(key, ConfigManager.GATE_VIEWS, raw)
                : accepted(level);
        }
        return null;
    }

    /**
     * Reads a value for a setting stored as text.
     *
     * <p>Switched on the key rather than on some property of the value, because there is
     * nothing about the string {@code "GLOWSTONE"} that says whether it is a material name or
     * the name of a sound in somebody's resource pack. Anything not named here is free text
     * and passes through as typed, which is what every sound setting relies on.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the value to store, or the reason it was refused
     */
    private static ParsedSetting readText(final ConfigKeys key, final String raw)
    {
        final String trimmed = raw.trim();
        switch (key)
        {
            case LOG_LEVEL:
                return readLevel(key, trimmed);
            case SERVER_LANGUAGE:
                return readServerLanguage(key, trimmed);
            case RING_DEFAULT_ACCESS:
                return readAccess(key, trimmed);
            case RING_DEFAULT_STYLE:
                return readStyle(key, trimmed);
            case RING_DEFAULT_LIGHT, RING_DEFAULT_FLASH:
                return readBlock(key, trimmed);
            case SIGN_COLOR_GATE_NAME, SIGN_COLOR_NETWORK, SIGN_COLOR_OWNER, SIGN_COLOR_SELECTED,
                SIGN_COLOR_NEIGHBOUR:
                return readColour(key, trimmed);
            default:
                return accepted(raw);
        }
    }

    /**
     * Reads a logging level.
     *
     * <p>Stored upper case because {@link Setting#getLevel()} parses it back without
     * catching failure and without normalising case, so {@code fine} would be written
     * happily and then throw at every log call afterwards -- inside logging, which is a
     * poor place to be the first to notice.
     *
     * <p>Upper-cased against {@link Locale#ROOT} rather than the server's own locale. A
     * Turkish JVM upper-cases {@code i} to a dotted capital I (U+0130) rather than to
     * {@code I}, so {@code fine} would arrive at {@link Level#parse} carrying a character
     * it has never heard of, and a level that is valid on every other server would be
     * refused on that one. Level names are fixed ASCII and should be folded as ASCII.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the level's canonical name, or the reason it was refused
     */
    private static ParsedSetting readLevel(final ConfigKeys key, final String raw)
    {
        try
        {
            return accepted(Level.parse(raw.toUpperCase(Locale.ROOT)).getName());
        }
        catch (final IllegalArgumentException notALevel)
        {
            return refused(recipient -> Messages.get(recipient, "config.refuse.log-level", key, "SEVERE", "WARNING",
                "INFO", "CONFIG", "FINE", "FINER", "FINEST", "ALL", "OFF", quoted(raw)));
        }
    }

    /**
     * Reads a language code; it need not have a file yet, since a missing one reads as English.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the code in lower case, or the reason it was refused
     */
    private static ParsedSetting readServerLanguage(final ConfigKeys key, final String raw)
    {
        final String code = ConfigManager.parseServerLanguage(raw);
        return (code == null)
            ? refused(recipient -> Messages.get(recipient, "config.refuse.language", key, "en", "de", "pt_br", quoted(raw)))
            : accepted(code);
    }

    /**
     * Reads what a newly built ring pair starts as.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the access mode's name, or the reason it was refused
     */
    private static ParsedSetting readAccess(final ConfigKeys key, final String raw)
    {
        for (final RingAccess access : RingAccess.values())
        {
            if (access.name().equalsIgnoreCase(raw))
            {
                return accepted(access.name());
            }
        }
        return refused(recipient -> either(recipient, key, "PUBLIC", "PRIVATE", raw));
    }

    /**
     * Reads how a ring stack deploys.
     *
     * <p>Takes the friendlier words {@code /wormhole ring edit style} takes, so the two
     * places a style can be set do not disagree about what a style is called.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the style's canonical name, or the reason it was refused
     */
    private static ParsedSetting readStyle(final ConfigKeys key, final String raw)
    {
        final RingStyle style = RingStyle.parse(raw);
        if (style == null)
        {
            return refused(recipient -> Messages.get(recipient, "config.refuse.style", key, "CONCURRENT", "SEQUENTIAL",
                quoted(raw), "Fast", "slow"));
        }
        return accepted(style.name());
    }

    /**
     * Reads a material a ring pad can light up as.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the material's name, or the reason it was refused
     */
    private static ParsedSetting readBlock(final ConfigKeys key, final String raw)
    {
        final Material material = Material.matchMaterial(raw);
        if (!MaterialUtils.isBlockOrUnknown(material))
        {
            return refused(recipient -> Messages.get(recipient, "config.refuse.block", key, quoted(raw)));
        }
        return accepted(material.name());
    }

    /**
     * Reads a sign colour, which a sign would otherwise quietly draw in its default.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the colour's name, or the reason it was refused
     */
    private static ParsedSetting readColour(final ConfigKeys key, final String raw)
    {
        final ChatColour colour = SignStyle.colorNamed(raw);
        if (colour == null)
        {
            return notOneOf(key, SignStyle.colorNames(), raw);
        }
        return accepted(colour.name());
    }

    /** Refuses a value that is none of a setting's names, listing them. */
    private static ParsedSetting notOneOf(final ConfigKeys key, final List<String> names, final String raw)
    {
        return refused(recipient -> Messages.get(recipient, "config.refuse.one-of", key,
            String.join(Messages.get(recipient, "config.refuse.separator"), names), quoted(raw)));
    }

    /** @return that a setting is one of two words, not what was typed */
    private static String either(final CommandSender recipient, final ConfigKeys key, final String one,
        final String other, final Object raw)
    {
        return Messages.get(recipient, "config.refuse.either", key, one, other, quoted(raw));
    }

    /** @return that a setting is a number, not what was typed */
    private static String number(final CommandSender recipient, final ConfigKeys key, final Object raw)
    {
        return Messages.get(recipient, "config.refuse.number", key, quoted(raw));
    }

    /**
     * Why a value in config.yml is not of its setting's kind, as {@link ConfigurationYAML#kindOf} names it.
     *
     * @param recipient
     *            who will read it
     * @param key
     *            the setting
     * @param kind
     *            the setting's default, whose type is its kind
     * @param written
     *            the value in the file
     * @return the refusal
     */
    static String notOfKind(final CommandSender recipient, final ConfigKeys key, final Object kind,
        final Object written)
    {
        if (kind instanceof Boolean)
        {
            return either(recipient, key, TRUE, FALSE, written);
        }
        if (kind instanceof Integer)
        {
            return Messages.get(recipient, "config.refuse.whole-number", key, quoted(written));
        }
        return number(recipient, key, written);
    }

    /** @return what was typed, in the quotes a refusal shows it in */
    private static String quoted(final Object raw)
    {
        return "\"" + raw + "\"";
    }

    /**
     * A value that may be stored.
     *
     * @param value
     *            what to store
     * @return the accepted result
     */
    private static ParsedSetting accepted(final Object value)
    {
        return new ParsedSetting(value, null);
    }

    /**
     * A value that may not be stored.
     *
     * @param message
     *            what to tell whoever typed it, in their language
     * @return the refused result
     */
    private static ParsedSetting refused(final Function<CommandSender, String> message)
    {
        return new ParsedSetting(null, message);
    }

    /**
     * Whether this value may be stored.
     *
     * @return true if it was accepted
     */
    boolean isAccepted()
    {
        return refusal == null;
    }

    /**
     * The value to store.
     *
     * @return the value, or null if this was refused
     */
    Object getValue()
    {
        return value;
    }

    /**
     * Why the value was refused, in the server's language.
     *
     * @return the message, or null if it was accepted
     */
    String getRefusal()
    {
        return getRefusal(null);
    }

    /**
     * Why the value was refused.
     *
     * @param recipient
     *            who will read it; null for the server's language
     * @return the message, or null if it was accepted
     */
    String getRefusal(final CommandSender recipient)
    {
        return (refusal == null) ? null : refusal.apply(recipient);
    }
}
