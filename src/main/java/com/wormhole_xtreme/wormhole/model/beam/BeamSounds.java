package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.Location;

import com.wormhole_xtreme.wormhole.utils.Sounds;

/**
 * The three sounds beaming makes: a power-up as the sequence begins, a departure where the
 * traveller leaves, and an arrival where they land.
 *
 * <p>Hardcoded for now rather than run through {@code ConfigManager}'s settings machinery --
 * that is real plumbing of its own (a config key, a default, an entry in
 * {@code settingNames()}), worth doing once the sound choices themselves have been tried and
 * kept rather than before committing to them. Chosen deliberately distinct from the ring
 * palette ({@code block.beacon.*}, {@code block.piston.extend}) so the two mechanics do not
 * sound alike, and from vanilla's own existing teleport sounds so nothing has to be invented.
 *
 * <p>Played through {@link Sounds}, which never throws -- a sound is decoration, not worth
 * failing a beam over.
 */
public final class BeamSounds
{
    private static final float VOLUME = 1.0f;
    private static final float PITCH = 1.0f;

    private BeamSounds() {}

    /**
     * Played where the traveller is standing, at the moment the sequence begins -- a
     * power-up rather than a whoosh, since this now runs ahead of a charge-and-glow phase
     * rather than firing instantly. {@code block.respawn_anchor.charge} is vanilla's own
     * "something is building up" sound, so nothing had to be invented for it.
     *
     * @param origin where the sequence started
     */
    public static void playCharge(final Location origin)
    {
        if (origin == null)
        {
            return;
        }
        Sounds.play(origin.getWorld(), origin, "block.respawn_anchor.charge", VOLUME, PITCH);
    }

    /**
     * Played where the traveller was standing, before they leave -- audible to them and to
     * anyone nearby watching them go.
     *
     * @param origin where they departed from
     */
    public static void playDepart(final Location origin)
    {
        if (origin == null)
        {
            return;
        }
        Sounds.play(origin.getWorld(), origin, "entity.enderman.teleport", VOLUME, PITCH);
    }

    /**
     * Played where the traveller lands, after they arrive -- audible to them and to anyone
     * already standing there.
     *
     * @param destination where they arrived
     */
    public static void playArrive(final Location destination)
    {
        if (destination == null)
        {
            return;
        }
        Sounds.play(destination.getWorld(), destination, "entity.shulker.teleport", VOLUME, PITCH);
    }
}
