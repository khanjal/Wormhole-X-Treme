package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.Sounds;

/**
 * The three sounds beaming makes: a power-up as the sequence begins, a departure where the
 * traveller leaves, and an arrival where they land.
 *
 * <p>Names and volume are read from {@code ConfigManager} live, the same way ring sounds are,
 * rather than fixed constants -- an admin can retune or silence beaming without a restart.
 * Chosen defaults are deliberately distinct from the ring palette ({@code block.beacon.*},
 * {@code block.piston.extend}) so the two mechanics do not sound alike by default, and from
 * vanilla's own existing teleport sounds so nothing had to be invented.
 *
 * <p>Played through {@link Sounds}, which never throws -- a sound is decoration, not worth
 * failing a beam over.
 */
public final class BeamSounds
{
    private BeamSounds() {}

    /**
     * Played where the traveller is standing, at the moment the sequence begins.
     * {@code block.respawn_anchor.charge} is vanilla's own "something is building up" sound
     * by default, so nothing had to be invented for it.
     *
     * @param origin where the sequence started
     */
    public static void playCharge(final Location origin)
    {
        play(origin, ConfigManager.getBeamSoundCharge());
    }

    /**
     * Played where the traveller was standing, before they leave -- audible to them and to
     * anyone nearby watching them go.
     *
     * @param origin where they departed from
     */
    public static void playDepart(final Location origin)
    {
        play(origin, ConfigManager.getBeamSoundDepart());
    }

    /**
     * Played where the traveller lands, after they arrive -- audible to them and to anyone
     * already standing there.
     *
     * @param destination where they arrived
     */
    public static void playArrive(final Location destination)
    {
        play(destination, ConfigManager.getBeamSoundArrive());
    }

    private static void play(final Location location, final String sound)
    {
        if ((location == null) || !ConfigManager.isBeamSoundsEnabled())
        {
            return;
        }
        Sounds.play(location.getWorld(), location, sound, ConfigManager.getBeamSoundVolume(), 1.0f);
    }
}
