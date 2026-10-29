package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which far creatures a viewer is shown through the windows they see, as stand-ins, and the chunks held
 * so they show on the first look (#430).
 *
 * <p>Moved out of {@link Windows} verbatim, but for the members Windows and its tests call being
 * package-private.
 */
final class WindowCreatures
{
    /** Static helpers only. */
    private WindowCreatures()
    {
    }

    /** Moves every viewer's stand-ins after their creatures: the follow task's pass, while any are shown. */
    static void followStandIns()
    {
        for (final ViewerDrawing view : VIEWS.values())
        {
            followStandIns(view);
        }
        if (StandIns.count() == 0)
        {
            StandIns.stopFollowing();
        }
    }

    /**
     * Holds the area in front of each watched window's far side loaded, so its creatures show on the
     * first look (#296): every window being drawn for a viewer, nearest its viewers first, while
     * creatures are shown and {@code mirror-entity-load-radius} is above 0.
     */
    static void holdFarAreas(final long now)
    {
        final int radius = ConfigManager.isMirrorShowEntities() ? ConfigManager.getMirrorEntityLoadRadius() : 0;
        final Map<String, Double> nearest = (radius > 0) ? watchedWindows() : Map.of();
        final List<String> order = new ArrayList<>(nearest.keySet());
        order.sort(Comparator.<String>comparingDouble(nearest::get).thenComparing(Comparator.naturalOrder()));
        final Map<String, List<FarChunkHolds.Area>> watched = new LinkedHashMap<>();
        for (final String name : order)
        {
            final Place destination = ACTIVE.get(name).source.destination();
            // A far world not loaded has nothing to hold, and nothing is asked of it.
            if (Bukkit.getWorld(destination.worldName()) != null)
            {
                watched.put(name, FarChunkHolds.ahead(destination, radius));
            }
        }
        try
        {
            FarChunkHolds.settle(watched, now);
        }
        catch (final Exception | LinkageError failed)
        {
            StandIns.failedOnce("Could not hold the chunks in front of a view's far side", failed);
        }
    }

    /** Every window being drawn for a viewer, with how near its nearest viewer's eye was, squared. */
    private static Map<String, Double> watchedWindows()
    {
        final Map<String, Double> nearest = new HashMap<>();
        for (final ViewerDrawing view : VIEWS.values())
        {
            for (final String name : view.mirrors)
            {
                final WindowState window = ACTIVE.get(name);
                if ((window != null) && !window.shape.mirrored())
                {
                    nearest.merge(name, apartFrom(window, view), Math::min);
                }
            }
        }
        return nearest;
    }

    /** How far a viewer's last drawn eye was from a window, squared; far off for one never drawn. */
    private static double apartFrom(final WindowState window, final ViewerDrawing view)
    {
        if (view.lastRedraw == null)
        {
            return Double.MAX_VALUE;
        }
        final Spot eye = view.lastRedraw.eye();
        return Windows.fromBanner(window.anchor(), eye.x(), eye.y(), eye.z());
    }

    /** Moves one viewer's stand-ins after their creatures, and drops them if that fails. */
    static void followStandIns(final ViewerDrawing view)
    {
        if (view.standIns.isEmpty())
        {
            return;
        }
        try
        {
            StandIns.follow(view);
        }
        catch (final Exception | LinkageError failed)
        {
            StandIns.failedOnce("Could not move the stand-ins shown through a view", failed);
            dropQuietly(view);
        }
    }

    /**
     * Shows a viewer the creatures in the far rooms they are looking into, as stand-ins, when the
     * setting is on; takes away any they have otherwise.
     */
    static void showStandIns(final Player player, final ViewerDrawing view, final Location eye,
        final List<WindowState> seeing, final WindowCompose.Wholes wholes, final long now)
    {
        try
        {
            if (!ConfigManager.isMirrorShowEntities() || player.isDead())
            {
                if (!view.standIns.isEmpty())
                {
                    StandIns.removeAll(view);
                }
                return;
            }
            StandIns.show(player, view, standInsFor(player, view, eye, seeing, wholes), now);
        }
        catch (final Exception | LinkageError failed)
        {
            // Cosmetic: the blocks of the view, and every other viewer, must still be drawn.
            StandIns.failedOnce("Could not show the creatures in a view's far room", failed);
            dropQuietly(view);
        }
    }

    /** Takes a viewer's stand-ins away after a failure, without letting a second one escape. */
    private static void dropQuietly(final ViewerDrawing view)
    {
        try
        {
            StandIns.removeAll(view);
        }
        catch (final Exception | LinkageError alsoFailed)
        {
            // Already logged once; the stray sweep tries again.
            view.standIns.clear();
        }
    }

    /**
     * The far creatures a viewer sees through these windows, and where: inside a room as drawn, at
     * most {@link FarCreatures#MOST_PER_VIEWER}, nearest first.
     */
    private static List<FarCreatures.Wanted> standInsFor(final Player player, final ViewerDrawing view,
        final Location eye, final List<WindowState> seeing, final WindowCompose.Wholes wholes)
    {
        final Map<WindowState, Windows.Whole> fixed = wholes.whole();
        final Set<Long> allOpen = new HashSet<>();
        seeing.forEach(window -> allOpen.addAll(window.openKeys));
        final Map<String, WindowState> byName = new HashMap<>();
        seeing.forEach(window -> byName.put(window.name(), window));
        // A window whose room is not held this redraw draws nothing new: its stand-ins stay as they are, and none are added.
        final Set<WindowState> drawn = new HashSet<>(fixed.keySet());
        drawn.addAll(wholes.clipped().keySet());
        final Predicate<FarCreatures.Wanted> kept =
            wanted -> !drawn.contains(wanted.window()) || WindowVeil.stillSeen(eye, wanted.here(), seeing, fixed);
        final List<FarCreatures.Wanted> inRoom = new ArrayList<>();
        view.tallies.clear();
        for (final WindowState window : Windows.nearestFirst(seeing, eye))
        {
            // A reflection's room is the viewer's own, which is a later step of #296.
            if (!window.shape.mirrored())
            {
                final CreatureTally tally = new CreatureTally();
                view.tallies.put(window.name(), tally);
                inRoomThrough(player, view, window, byName, kept, inRoom, tally);
            }
        }
        // The costly view test runs nearest first and stops at the cap: a farm far off costs a sort, not a projection each.
        final List<FarCreatures.Wanted> chosen = FarCreatures.choose(inRoom, eye, FarCreatures.MOST_PER_VIEWER,
            FarCreatures.keepOrShow(view.standIns.keySet(), kept,
                wanted -> drawn.contains(wanted.window()) && WindowVeil.inAnyView(eye, wanted.here(), seeing, fixed, allOpen)));
        chosen.forEach(wanted -> view.tallies.get(wanted.window().name()).shown++);
        return chosen;
    }

    /**
     * Adds the far creatures this viewer may see that stand inside one window's room as drawn.
     *
     * <p>A creature already shown stays with the window it was shown through while that window is
     * still seen, so two windows that both see it do not hand it back and forth; and only a new one
     * must stand on drawn floor, so one stepping over a gap is not taken away and shown again.
     */
    private static void inRoomThrough(final Player player, final ViewerDrawing view, final WindowState window,
        final Map<String, WindowState> seeing, final Predicate<FarCreatures.Wanted> kept,
        final List<FarCreatures.Wanted> inRoom, final CreatureTally tally)
    {
        for (final Entity creature : farCreatures(window))
        {
            if (creature.isValid())
            {
                tally.found++;
                if (creature instanceof Player)
                {
                    tally.players++;
                }
                final CreatureTally.Skip why = whyNot(player, view, window, seeing, kept, creature);
                if (why == null)
                {
                    inRoom.add(new FarCreatures.Wanted(creature, FarCreatures.hereOf(view.world, window.shape,
                        creature.getLocation()), window));
                }
                else
                {
                    tally.skip(why);
                }
            }
        }
    }

    /**
     * Why a far creature is not offered through this window, or null if it is.
     *
     * @param seeing
     *            the windows the viewer sees, by name
     * @param kept
     *            whether a creature already shown is kept where it would stand
     */
    static CreatureTally.Skip whyNot(final Player player, final ViewerDrawing view, final WindowState window,
        final Map<String, WindowState> seeing, final Predicate<FarCreatures.Wanted> kept, final Entity creature)
    {
        if (creature instanceof Player other)
        {
            final CreatureTally.Skip unseen = FarPlayers.whyNot(player, other, window);
            if (unseen != null)
            {
                return unseen;
            }
        }
        else if (!visibleTo(player, view, creature))
        {
            return CreatureTally.Skip.HIDDEN;
        }
        final StandIns.StandIn held = view.standIns.get(creature.getUniqueId());
        final Location far = creature.getLocation();
        if ((held != null) && keptThroughAnother(view, held, window, seeing, kept, far))
        {
            return CreatureTally.Skip.OTHER_WINDOW;
        }
        if (!StandIns.inRoom(window, FarCreatures.hereOf(view.world, window.shape, far), held != null))
        {
            return CreatureTally.Skip.OUT_OF_ROOM;
        }
        // A flying, swimming or climbing creature has no floor to stand on, and is shown floating, as it is.
        if ((held == null) && !StandIns.floats(creature)
            && !StandIns.onDrawnFloor(view, window, far, StandIns.floorReach(creature)))
        {
            return CreatureTally.Skip.NO_FLOOR;
        }
        return null;
    }

    /**
     * Whether a shown creature is still kept through the window it was shown through, so it is not
     * also offered through this one; if that window would let it go, this one may take it over at once.
     */
    private static boolean keptThroughAnother(final ViewerDrawing view, final StandIns.StandIn held,
        final WindowState window, final Map<String, WindowState> seeing, final Predicate<FarCreatures.Wanted> kept,
        final Location far)
    {
        final WindowState holding = seeing.get(held.window.name());
        if ((holding == null) || holding.name().equals(window.name()))
        {
            return false;
        }
        final Location there = FarCreatures.hereOf(view.world, holding.shape, far);
        return StandIns.inRoom(holding, there, true) && kept.test(new FarCreatures.Wanted(held.original, there, holding));
    }

    /**
     * Whether a viewer may see a far creature: not hidden from them, unless the hiding is this view's
     * own veil, which a mirror onto the room behind its own wall would otherwise show empty.
     *
     * @return true if it may be shown to them
     */
    static boolean visibleTo(final Player player, final ViewerDrawing view, final Entity creature)
    {
        return player.canSee(creature) || view.veiled.containsKey(creature.getUniqueId());
    }

    /** The creatures in a window's far room, read once for the window as this sweep offered it. */
    private static List<Entity> farCreatures(final WindowState window)
    {
        if (window.creatures == null)
        {
            final World far = Bukkit.getWorld(window.capture.worldName());
            final int[] box = (far == null) ? FarCreatures.NOWHERE
                : FarCreatures.roomBox(window.shape, window.depth(), window.capture.bounds());
            final int[] tally = new int[3];
            window.creatures = (box.length == 0) ? List.of()
                : FarCreatures.inRoom(far, box, tally, ConfigManager.isMirrorShowPlayers());
            window.notLoaded = tally[0];
            window.entitiesNotLoaded = tally[1];
            window.notCopied = tally[2];
        }
        return window.creatures;
    }
}
