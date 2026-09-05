package com.wormhole_xtreme.wormhole.utils;

/**
 * Marks the window in which the plugin is writing a gate's own redstone blocks.
 *
 * <p>Opening a gate switches the gate's own DHD lever on, and switches its [RA] output lever
 * on after that. Bukkit fires {@code BlockRedstoneEvent} for those writes synchronously, on
 * the thread doing the writing, and for every conductor they light up as well -- so the
 * plugin's own dial arrives back at the redstone listener looking exactly like a player's
 * circuit powering the gate. On a sign gate that read as a second press and dialled the gate
 * a second time, because at that instant the gate is marked active but its target has not
 * been assigned yet, so none of the "already open" checks recognised it.
 *
 * <p>The listener asks {@link #inProgress()} and ignores anything raised inside the window.
 * A depth counter rather than a flag, so nested writes cannot have the inner one clear the
 * guard while the outer is still going; a {@link ThreadLocal} rather than a plain static,
 * because the guard is only meaningful on the thread whose write raised the event.
 */
public final class GateRedstoneWrite
{
    /** How many gate redstone writes this thread is inside. */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> Integer.valueOf(0));

    private GateRedstoneWrite()
    {
    }

    /** Opens a window. Always paired with {@link #end()} in a {@code finally}. */
    public static void begin()
    {
        DEPTH.set(Integer.valueOf(DEPTH.get().intValue() + 1));
    }

    /**
     * Closes a window opened by {@link #begin()}.
     *
     * <p>The depth floors at closed rather than going negative, so an unmatched {@code end()}
     * cannot bank a credit that swallows the next real {@link #begin()} -- which would leave
     * the listener acting on writes it should ignore, with nothing to show why. It cannot
     * repair the other direction: an {@code end()} that belongs to nobody still closes an
     * outer window early. Nothing here can know which {@code begin()} a stray call was meant
     * for, so the pairing is the caller's to keep, and both call sites keep it with
     * {@code try}/{@code finally}.
     */
    public static void end()
    {
        final int remaining = DEPTH.get().intValue() - 1;
        if (remaining > 0)
        {
            DEPTH.set(Integer.valueOf(remaining));
        }
        else
        {
            // Removed rather than set to zero so a pooled thread is not left holding an entry.
            // Underflow lands here too, and removing is what floors it at closed.
            DEPTH.remove();
        }
    }

    /**
     * Whether this thread is currently inside a gate redstone write.
     *
     * @return true if any redstone change seen right now is the plugin's own doing
     */
    public static boolean inProgress()
    {
        return DEPTH.get().intValue() > 0;
    }
}
