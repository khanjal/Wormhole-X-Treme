package com.wormhole_xtreme.wormhole.model.window;

import static com.wormhole_xtreme.wormhole.model.window.WindowStore.ACTIVE;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.BLIND;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.FULL;
import static com.wormhole_xtreme.wormhole.model.window.WindowStore.VIEWS;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorText;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * What the window drawing says about itself for {@code mirror debug}: a viewer's view in full or in
 * brief, and how each window is being drawn and why (#430).
 *
 * <p>Moved out of {@link Windows} verbatim; public because {@code describe} and {@code summary} are
 * the command's.
 */
public final class WindowDebug
{
    /** Static helpers only. */
    private WindowDebug()
    {
    }

    /**
     * What a player's view is doing, for {@code mirror debug}.
     *
     * @return lines to say
     */
    public static List<String> describe(final Player player)
    {
        final List<String> lines = new ArrayList<>();
        lines.add(MirrorText.heading("your view"));
        lines.add(MirrorText.field("server", ACTIVE.size() + " window(s), " + VIEWS.size() + " viewer(s)"));
        lines.add(MirrorText.field("depth", ConfigManager.getViewDepth() + " from the opening (view-depth)"));
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (BLIND.contains(player.getUniqueId()))
        {
            lines.add(MirrorText.field("views", MirrorText.bad("off for you") + ", mirror debug -on turns them back on"));
        }
        final String fullName = FULL.get(player.getUniqueId());
        if (fullName != null)
        {
            lines.add(MirrorText.field("drawn full", fullName + ", mirror debug on stops that"));
        }
        if (view == null)
        {
            lines.add(MirrorText.field("looking into", "no window"));
            return lines;
        }
        lines.add(MirrorText.field("looking into", String.join(", ", view.mirrors)));
        lines.add(MirrorText.field("drawn", view.drawn.size() + Windows.BLOCKS));
        if (!view.pending.isEmpty())
        {
            lines.add(MirrorText.field("still to send", view.pending.size() + " blocks, " + Windows.streamPerTick + " a tick"));
        }
        lines.add(MirrorText.field("creatures hidden", String.valueOf(view.veiled.size())));
        if (view.undrawable > 0)
        {
            lines.add(MirrorText.field("not drawn over",
                MirrorText.bad(view.undrawable + " block(s) the world would not let be drawn over")));
        }
        for (final String name : view.mirrors)
        {
            final WindowState window = ACTIVE.get(name);
            if (window != null)
            {
                lines.addAll(mirrorLines(view, window, name.equals(fullName)));
            }
        }
        if (view.lastRedraw == null)
        {
            lines.add(MirrorText.field("last redraw", "never"));
        }
        else
        {
            lines.addAll(view.lastRedraw.lines());
        }
        return lines;
    }

    /**
     * What a player's view is doing, a line a mirror and one for the last redraw, for
     * {@code mirror debug} without {@code all}.
     *
     * @return lines to say
     */
    public static List<String> summary(final Player player)
    {
        final List<String> lines = new ArrayList<>();
        if (BLIND.contains(player.getUniqueId()))
        {
            lines.add(MirrorText.field("views", MirrorText.bad("off for you") + ", mirror debug -on turns them back on"));
        }
        final ViewerDrawing view = VIEWS.get(player.getUniqueId());
        if (view == null)
        {
            lines.add(MirrorText.field("view", "you are looking into no window"));
            return lines;
        }
        final String fullName = FULL.get(player.getUniqueId());
        for (final String name : view.mirrors)
        {
            final WindowState window = ACTIVE.get(name);
            if (window != null)
            {
                lines.add(MirrorText.field(name, name.equals(fullName) ? "whole and unlimited for you"
                    : howDrawn(window, view.fixedNames.contains(name))));
            }
        }
        if (view.lastRedraw != null)
        {
            lines.add(view.lastRedraw.brief());
        }
        return lines;
    }

    /** How one window is being drawn for a viewer, and for a clipped one how far its far part stands, for {@code mirror debug}. */
    private static List<String> mirrorLines(final ViewerDrawing view, final WindowState window,
        final boolean full)
    {
        final String name = window.name();
        if (full)
        {
            return List.of(MirrorText.field(name, "whole and unlimited for you, "
                + ((window.full == null) ? 0 : window.full.blocks().size()) + Windows.BLOCKS));
        }
        final boolean fixedForViewer = view.fixedNames.contains(name);
        final List<String> lines = new ArrayList<>();
        lines.add(MirrorText.field(name, howDrawn(window, fixedForViewer)));
        if (window.walkThrough())
        {
            // A gate's view comes in steps, and which step it is on is the first question when it looks short.
            final int box = reachAhead(window);
            final int kept = window.capture.keptReach();
            lines.add(MirrorText.field(name + " capture", "reaches " + ((kept > 0) ? Math.min(box, kept) : box) + " ahead"
                + ((kept > 0) ? MirrorText.bad(", cut from " + box + " to fit") : "") + ", drawn to " + window.depth()
                + " (full " + window.source.fullDepth()
                + "), " + window.capture.secondsOld() + "s old"));
        }
        final CreatureTally tally = view.tallies.get(name);
        if (ConfigManager.isMirrorShowEntities() && (tally != null))
        {
            final int[] held = FarChunkHolds.heldFor(name);
            lines.add(MirrorText.field(name + " creatures", tally.line(window) + ", chunks held " + held[0] + " of "
                + held[1] + " (mirror-entity-load-radius " + ConfigManager.getMirrorEntityLoadRadius() + ")"));
        }
        if (!fixedForViewer)
        {
            final WindowCompose.Far far = view.far.get(name);
            lines.add(MirrorText.field(name + " far part", "judged once per " + WindowCompose.farCellFor(window.border)
                + " blocks of movement, wall " + window.border + " on every side, "
                + ((far == null) ? "not yet" : (far.blocks().size() + " blocks kept"))));
        }
        return lines;
    }

    /** How a window is being drawn for a viewer, and why, for {@code mirror debug}. */
    private static String howDrawn(final WindowState window, final boolean fixedForViewer)
    {
        final Spot gap = WindowWall.gapBeside(window);
        final String clipped = "whole " + toDepth(window) + ", clipped to each eye: ";
        if (gap != null)
        {
            final String what = window.anchor().getWorld().getBlockAt(gap.x(), gap.y(), gap.z())
                .getBlockData().getAsString();
            return clipped + MirrorText.bad("wall within "
                + WindowFace.wallReach() + " open at " + gap.x() + "," + gap.y() + "," + gap.z()) + " (" + what + ")";
        }
        if (!fixedForViewer)
        {
            return clipped + MirrorText.bad("another mirror within twice the depth");
        }
        return MirrorText.good("drawn whole") + " " + toDepth(window) + ", "
            + ((window.fixed == null) ? 0 : window.fixed.size()) + Windows.BLOCKS
            + (FixedBuilds.building(window.name()) ? ", being built again" : "");
    }

    /**
     * How far past its arrival a window's capture reaches, the way a traveller faces.
     *
     * @return blocks, counted to the far edge of the capture's box
     */
    static int reachAhead(final WindowState window)
    {
        final int[] box = window.capture.bounds();
        // From where travellers land, not the window's far block, which a tunnel moves deeper.
        final Place destination = window.source.destination();
        final Spot far = new Spot((int) Math.floor(destination.x()), 0, (int) Math.floor(destination.z()));
        final Spot ahead = window.shape.ahead();
        if (ahead.x() != 0)
        {
            return (ahead.x() > 0) ? (box[3] - far.x()) : (far.x() - box[0]);
        }
        return (ahead.z() > 0) ? (box[5] - far.z()) : (far.z() - box[2]);
    }

    /** How deep a window's held room reaches, and in red when it was cut to fit under the cap. */
    private static String toDepth(final WindowState window)
    {
        if ((window.fixed != null) && (window.fixedDepth < window.fixedFor))
        {
            return MirrorText.bad("cut to depth " + window.fixedDepth + " of " + window.fixedFor + " to fit " + Windows.mostFixedFor(window)
                + Windows.BLOCKS);
        }
        return "to depth " + window.fixedDepth;
    }
}
