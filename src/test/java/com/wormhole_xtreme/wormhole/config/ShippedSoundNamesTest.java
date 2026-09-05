package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every sound this plugin ships by name has to be shaped like a sound the client can look up.
 *
 * <p>Sound names are text handed straight to the client, deliberately -- that is what lets a
 * server name a resource pack's own sounds in config with no code involved. The price is that
 * nothing checks them: a name the client does not recognise is silent, and silence is exactly
 * what a sound setting looks like when it is turned off on purpose. A typo in a shipped
 * default therefore compiles, starts, dials, and is only noticed by somebody wondering why
 * their gate went quiet.
 *
 * <p>The names cannot be resolved against {@code org.bukkit.Sound} here for the reason
 * {@link com.wormhole_xtreme.wormhole.utils.Sounds} gives: that type is registry-backed on
 * newer API versions and a registry cannot be asked about without a running server. What can
 * be checked without one is the shape, which is where the plausible mistake lives -- pasting
 * in the constant name ({@code ENTITY_PLAYER_SPLASH_HIGH_SPEED}) instead of the sound event's
 * own name, or leaving a stray space or capital in one.
 */
class ShippedSoundNamesTest
{
    /** A resource location: an optional namespace, then a dotted lowercase path. */
    private static final Pattern SOUND_NAME =
        Pattern.compile("(?:[a-z0-9_-]+:)?[a-z0-9_-]+(?:[.][a-z0-9_-]+)+");

    /**
     * The shipped defaults for every setting that names a sound.
     *
     * <p>Picked out by the key rather than listed by hand, so a sound setting added later is
     * covered without anybody remembering to come back here. The sound group also holds a
     * volume, a tick count and an on/off switch, which is what the string test skips.
     */
    private static List<Setting> shippedSoundSettings()
    {
        final List<Setting> sounds = new ArrayList<Setting>();
        for (final Setting setting : DefaultSettings.config)
        {
            if (setting.getName().name().contains("SOUND") && (setting.getValue() instanceof String))
            {
                sounds.add(setting);
            }
        }
        return sounds;
    }

    @Test
    void everySoundThisPluginShipsIsNamedTheWayTheClientNamesOne()
    {
        final List<Setting> sounds = shippedSoundSettings();
        assertTrue(sounds.size() >= 10,
            "no sound defaults were found to check -- the key naming this picks them out by "
                + "must have changed");
        for (final Setting sound : sounds)
        {
            final String name = sound.getStringValue();
            assertTrue(SOUND_NAME.matcher(name).matches(),
                sound.getName().name() + " ships \"" + name + "\", which no client will "
                    + "recognise -- a sound event is a dotted lowercase name, not a constant");
        }
    }
}
