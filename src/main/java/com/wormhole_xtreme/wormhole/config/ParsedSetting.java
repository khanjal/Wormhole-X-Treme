package com.wormhole_xtreme.wormhole.config;

import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

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
    /** The value to store, or null if this was refused. */
    private final Object value;

    /** Why it was refused, or null if it was accepted. */
    private final String refusal;

    /**
     * Instantiates a parsed setting.
     *
     * @param value
     *            the value to store
     * @param refusal
     *            why it was refused
     */
    private ParsedSetting(final Object value, final String refusal)
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
        if (current instanceof Boolean)
        {
            if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw))
            {
                return refused(key + " is true or false, not \"" + raw + "\".");
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
                return refused(key + " is a number, not \"" + raw + "\".");
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
                return refused(key + " is a number, not \"" + raw + "\".");
            }
        }
        return readText(key, raw);
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
            case RING_DEFAULT_ACCESS:
                return readAccess(key, trimmed);
            case RING_DEFAULT_STYLE:
                return readStyle(key, trimmed);
            case RING_DEFAULT_MATERIAL:
                return readSlab(key, trimmed);
            case RING_DEFAULT_LIGHT:
            case RING_DEFAULT_FLASH:
                return readBlock(key, trimmed);
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
            return refused(key + " is SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST, "
                + "ALL or OFF, not \"" + raw + "\".");
        }
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
        return refused(key + " is PUBLIC or PRIVATE, not \"" + raw + "\".");
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
            return refused(key + " is CONCURRENT or SEQUENTIAL, not \"" + raw
                + "\". Fast and slow work too.");
        }
        return accepted(style.name());
    }

    /**
     * Reads a material a travelling ring could be made of.
     *
     * @param key
     *            which setting is being written
     * @param raw
     *            the value as typed
     * @return the material's name, or the reason it was refused
     */
    private static ParsedSetting readSlab(final ConfigKeys key, final String raw)
    {
        final Material material = Material.matchMaterial(raw);
        if (!Ring.isUsableAsRing(material))
        {
            // The same refusal /wormhole ring edit ring gives, and for the same reason: the
            // rise is drawn out of slab halves, so a full block would cost the animation the
            // half-block movement that is the whole effect.
            return refused(key + " is the name of a slab, not \"" + raw
                + "\" -- a ring moves half a block at a time, which is what a slab can do.");
        }
        return accepted(material.name());
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
            return refused(key + " is the name of a block, not \"" + raw + "\".");
        }
        return accepted(material.name());
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
     *            what to tell whoever typed it
     * @return the refused result
     */
    private static ParsedSetting refused(final String message)
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
     * Why the value was refused.
     *
     * @return the message, or null if it was accepted
     */
    String getRefusal()
    {
        return refusal;
    }
}
