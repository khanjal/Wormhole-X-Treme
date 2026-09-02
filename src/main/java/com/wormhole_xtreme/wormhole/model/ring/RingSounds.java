package com.wormhole_xtreme.wormhole.model.ring;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;

/**
 * What a ring sounds like.
 *
 * <p>Rings are drawn to clients rather than built, so without sound a cycle is a silent
 * animation happening in somebody's floor. The noise is most of what makes it read as
 * machinery.
 *
 * <p>Sounds are named rather than resolved to a {@code Sound} constant, and played through
 * the overload that takes a name. That keeps this working whichever way the sound type is
 * defined in a given API version, and lets a server with a resource pack name its own.
 *
 * <p>Everything here fails quietly. A ring that cannot make a noise should still work: a
 * misspelt sound name, or a world that has gone away mid-cycle, is not worth ending a
 * transport over.
 */
public final class RingSounds
{
    /** The lowest pitch a ring is played at, for the first one out. */
    private static final float LOWEST_PITCH = 0.8f;

    /** How much the pitch climbs per ring. */
    private static final float PITCH_STEP = 0.2f;

    /** Static helpers only. */
    private RingSounds()
    {
    }

    /**
     * Plays the opening, at both ends.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair waking up
     */
    public static void opened(final World world, final RingPair pair)
    {
        atBothEnds(world, pair, ConfigManager.getRingSoundOpen(), 1.0f);
    }

    /**
     * Plays the transport, at both ends.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair firing
     */
    public static void flashed(final World world, final RingPair pair)
    {
        atBothEnds(world, pair, ConfigManager.getRingSoundFlash(), 1.4f);
    }

    /**
     * Plays the closing, at both ends.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair going quiet
     */
    public static void closed(final World world, final RingPair pair)
    {
        atBothEnds(world, pair, ConfigManager.getRingSoundClose(), 1.0f);
    }

    /**
     * Plays a ring for any ring that starts moving on this frame.
     *
     * <p>Once per ring rather than once per frame, and pitched by where that ring sits in the
     * stack: climbing on the way out, falling on the way back. A single sound repeated flat
     * would say a ring moved; the pitch is what says the stack is rising.
     *
     * <p>Each end is asked separately, because the two can deploy in different styles and so
     * start their rings on different frames.
     *
     * @param world
     *            the world the pair is in
     * @param pair
     *            the pair moving
     * @param frame
     *            the frame just drawn
     * @param retracting
     *            true if the rings are going home
     */
    public static void ringMoved(final World world, final RingPair pair, final int frame,
        final boolean retracting)
    {
        final String sound = ConfigManager.getRingSoundRing();
        if (sound.isEmpty())
        {
            return;
        }
        ringMovedAt(world, pair.getEndA(), sound, frame, retracting);
        ringMovedAt(world, pair.getEndB(), sound, frame, retracting);
    }

    /**
     * Plays a ring for one end, if one of its rings starts moving on this frame.
     *
     * @param world
     *            the world
     * @param ring
     *            the end
     * @param sound
     *            the sound name
     * @param frame
     *            the frame just drawn
     * @param retracting
     *            true if the rings are going home
     */
    private static void ringMovedAt(final World world, final Ring ring, final String sound,
        final int frame, final boolean retracting)
    {
        for (int index = 0; index < RingAnimator.RING_COUNT; index++)
        {
            if (startFrame(ring, index, retracting) == frame)
            {
                play(world, centre(ring), sound, pitchFor(index));
            }
        }
    }

    /**
     * The frame one ring starts moving on.
     *
     * <p>Retracting runs the deploy backwards, so the frame a ring leaves on going out is the
     * frame it arrives on coming back, counted from the other end.
     *
     * @param ring
     *            the end
     * @param index
     *            the ring, counting from the first one out
     * @param retracting
     *            true if the rings are going home
     * @return the frame it moves on
     */
    static int startFrame(final Ring ring, final int index, final boolean retracting)
    {
        final int emerges = RingAnimator.emergesOnFrame(ring, ring.getStyle(), index);
        return retracting
            ? (RingAnimator.deployFrames(ring, ring.getStyle()) - 1 - emerges) : emerges;
    }

    /**
     * What pitch one ring is played at.
     *
     * <p>By the order rings leave the pad, not by where they end up. What a player hears is a
     * sequence in time, and each ring going out a step higher than the one before is what
     * makes the stack sound like it is building.
     *
     * <p>Nothing here needs to know which way the rings are travelling. Retracting runs the
     * order backwards -- the last ring out is the first one home -- so the same pitches
     * replayed in reverse fall on their own. Nor does it need to know the orientation: both
     * ends send their first ring first, whichever way that ring is going, so a pair stays in
     * tune with itself.
     *
     * @param index
     *            the ring, counting from the first one out
     * @return the pitch
     */
    static float pitchFor(final int index)
    {
        return LOWEST_PITCH + (PITCH_STEP * index);
    }

    /**
     * Plays a refusal, to the one player it concerns.
     *
     * <p>Heard by them alone rather than by the room. A ring that turns somebody away has not
     * done anything the neighbours need to know about, and a busy pad would otherwise be a
     * noise complaint.
     *
     * @param player
     *            the player being turned away
     */
    public static void refused(final Player player)
    {
        final String sound = ConfigManager.getRingSoundRefused();
        if (!ConfigManager.isRingSoundsEnabled() || sound.isEmpty())
        {
            return;
        }
        com.wormhole_xtreme.wormhole.utils.Sounds.playTo(player, sound,
            ConfigManager.getRingSoundVolume(), 0.7f);
    }

    /**
     * Plays one sound at both ends of a pair.
     *
     * @param world
     *            the world
     * @param pair
     *            the pair
     * @param sound
     *            the sound name
     * @param pitch
     *            the pitch
     */
    private static void atBothEnds(final World world, final RingPair pair, final String sound,
        final float pitch)
    {
        if (sound.isEmpty())
        {
            return;
        }
        play(world, centre(pair.getEndA()), sound, pitch);
        play(world, centre(pair.getEndB()), sound, pitch);
    }

    /**
     * Where a ring is heard from.
     *
     * <p>The middle of the pad at the bottom of its stack, which is where a traveller stands
     * -- and for a ceiling ring, the floor its rings drop to rather than the plane it hangs
     * from.
     *
     * @param ring
     *            the ring
     * @return the sound's location
     */
    private static Location centre(final Ring ring)
    {
        return new Location(null, ring.getAnchorX() + 0.5D, ring.stackBase(),
            ring.getAnchorZ() + 0.5D);
    }

    /**
     * Plays one sound, if sounds are on at all.
     *
     * @param world
     *            the world to play it in
     * @param where
     *            the location, whose world is filled in for it
     * @param sound
     *            the sound name
     * @param pitch
     *            the pitch
     */
    private static void play(final World world, final Location where, final String sound,
        final float pitch)
    {
        if (!ConfigManager.isRingSoundsEnabled())
        {
            return;
        }
        com.wormhole_xtreme.wormhole.utils.Sounds.play(world, where, sound,
            ConfigManager.getRingSoundVolume(), pitch);
    }
}
