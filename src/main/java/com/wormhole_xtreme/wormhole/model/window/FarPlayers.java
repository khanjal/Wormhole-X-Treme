package com.wormhole_xtreme.wormhole.model.window;

import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Which players standing in a window's far room a viewer is shown (#296, step 2), with
 * {@code mirror-show-players} on.
 *
 * <p>Showing who is in the destination room is what a window does, but it is also information: a
 * player the viewer could not see standing beside them is never shown through a window either.
 */
final class FarPlayers
{
    /** The metadata key Citizens and its kind set on a player that is an NPC. */
    static final String NPC = "NPC";

    /** The kinds of window a viewer is shown themselves through: none in this step. */
    private static final Set<Class<? extends WindowSource>> SHOWN_TO_THEMSELVES = Set.of();

    /** Static methods only. */
    private FarPlayers()
    {
    }

    /**
     * Whether a viewer is shown their own stand-in through this window: never yet. Step 4 of #296
     * turns this on for a gate facing back at its viewer, and nothing else need change.
     *
     * @param window
     *            the window
     * @return true if the viewer may see themselves through it
     */
    static boolean showsTheViewer(final WindowState window)
    {
        return SHOWN_TO_THEMSELVES.contains(window.source.getClass());
    }

    /**
     * Whether a player in a far room may be shown to anybody: online and alive, not an NPC, not
     * invisible, a spectator or hidden by default, and riding nothing, since a seated rider cannot be
     * copied yet.
     *
     * @param entity
     *            an entity in a far room
     * @return true for a player who may be given a stand-in
     */
    static boolean copied(final Entity entity)
    {
        return (entity instanceof Player player) && player.isOnline() && !player.hasMetadata(NPC) && player.isValid()
            && !player.isDead() && !player.isInvisible() && (player.getGameMode() != GameMode.SPECTATOR)
            && !player.isInsideVehicle() && player.isVisibleByDefault() && !StandIns.isStandIn(player);
    }

    /**
     * Why a far player is not shown to this viewer, or null if they may be.
     *
     * @param viewer
     *            who looks
     * @param other
     *            a player in the far room
     * @param window
     *            the window they would show through
     * @return {@link CreatureTally.Skip#YOU} for the viewer themselves, {@link CreatureTally.Skip#PLAYER_HIDDEN}
     *         for one hidden from the viewer, as a vanish plugin hides one; null otherwise
     */
    static CreatureTally.Skip whyNot(final Player viewer, final Player other, final WindowState window)
    {
        if (other.getUniqueId().equals(viewer.getUniqueId()) && !showsTheViewer(window))
        {
            return CreatureTally.Skip.YOU;
        }
        return viewer.canSee(other) ? null : CreatureTally.Skip.PLAYER_HIDDEN;
    }

    /**
     * Whether a far player's stand-in may stay, asked on every follow: one who has since vanished,
     * gone invisible or into spectator, mounted something or left is taken away at once.
     *
     * @param viewer
     *            who is shown the stand-in
     * @param other
     *            the player it stands in for
     * @param window
     *            the window it shows through
     * @return true if it may stay
     */
    static boolean stillShown(final Player viewer, final Player other, final WindowState window)
    {
        return copied(other) && (whyNot(viewer, other, window) == null);
    }
}
