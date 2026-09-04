package com.wormhole_xtreme.wormhole.utils;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Plays a named sound, carefully.
 *
 * <p>Sounds are named rather than resolved to a {@code Sound} constant, and played through the
 * overloads that take a name. Two reasons. That type has been moving toward a registry-backed
 * one across recent API versions, and a registry cannot be asked about before a server has
 * finished starting -- the trap that once killed a class's initialisation here. And a name
 * passes straight through to the client, so a server with a resource pack can name its own
 * sounds in config with no code involved.
 *
 * <p>Nothing here throws. A sound is decoration: a misspelt name, or a world that has gone
 * away mid-animation, is not worth interrupting a transport or a wormhole over. An unknown
 * name is silent, which is what the client does with one anyway.
 */
public final class Sounds
{
    /** The quietest and loudest Minecraft will actually play. */
    private static final float MIN_PITCH = 0.5f;

    /** The quietest and loudest Minecraft will actually play. */
    private static final float MAX_PITCH = 2.0f;

    /** Static helpers only. */
    private Sounds()
    {
    }

    /**
     * Plays a sound in the world, for whoever is near enough to hear it.
     *
     * @param world
     *            the world to play it in
     * @param where
     *            where it comes from
     * @param sound
     *            the sound name; empty plays nothing
     * @param volume
     *            the volume, which is also the audible range
     * @param pitch
     *            the pitch, clamped to what will play
     */
    public static void play(final World world, final Location where, final String sound,
        final float volume, final float pitch)
    {
        if ((world == null) || (where == null) || (sound == null) || sound.isEmpty())
        {
            return;
        }
        try
        {
            final Location at = where.clone();
            at.setWorld(world);
            world.playSound(at, sound, SoundCategory.BLOCKS, volume, clamp(pitch));
        }
        catch (final RuntimeException ignored)
        {
            // Decoration. Nothing a player could usefully be told about.
        }
    }

    /**
     * Plays a sound for one player, heard by them alone.
     *
     * @param player
     *            who hears it
     * @param sound
     *            the sound name; empty plays nothing
     * @param volume
     *            the volume
     * @param pitch
     *            the pitch, clamped to what will play
     */
    public static void playTo(final Player player, final String sound, final float volume,
        final float pitch)
    {
        if ((player == null) || (sound == null) || sound.isEmpty())
        {
            return;
        }
        try
        {
            player.playSound(player.getLocation(), sound, SoundCategory.BLOCKS, volume,
                clamp(pitch));
        }
        catch (final RuntimeException ignored)
        {
            // As above.
        }
    }

    /**
     * Stops a named, looping sound for everyone currently in a world.
     *
     * <p>{@code World} has no {@code stopSound} of its own -- only {@code Player} does, so
     * unlike {@link #play}, which Bukkit broadcasts to whoever is in range on its own, stopping
     * one has to be told to each player individually. There is no range to narrow that to
     * either: a stop is a client-side "you are no longer playing this," not a positional event,
     * so a player who never actually heard the original sound just receives a harmless no-op.
     *
     * @param world
     *            the world to stop it in; nothing happens if null
     * @param sound
     *            the sound name; empty does nothing
     */
    public static void stopForEveryoneIn(final World world, final String sound)
    {
        if ((world == null) || (sound == null) || sound.isEmpty())
        {
            return;
        }
        for (final Player player : world.getPlayers())
        {
            try
            {
                player.stopSound(sound, SoundCategory.BLOCKS);
            }
            catch (final RuntimeException ignored)
            {
                // As above -- decoration, not worth interrupting a shutdown over.
            }
        }
    }

    /**
     * Holds a pitch to what Minecraft will play.
     *
     * <p>Out of range, the client plays the nearest end rather than refusing -- so a sequence
     * meant to climb would quietly flatten at the top instead. Clamping here means that
     * flattening is at least the same everywhere, and the tests that pin each sequence inside
     * the range are checking something real rather than something already lost.
     *
     * @param pitch
     *            the wanted pitch
     * @return a pitch that will play
     */
    public static float clamp(final float pitch)
    {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
    }
}
