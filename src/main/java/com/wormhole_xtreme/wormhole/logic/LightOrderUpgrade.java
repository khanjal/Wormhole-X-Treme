package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;

/**
 * Rebuilds every gate's chevron light order from its shape when the server starts.
 *
 * <p>A gate saves the order it was built with, so a shape whose order changed left every gate
 * already standing lighting the old way until someone ran {@code /wormhole regen -all}. This is
 * that same rebuild, run once gates are loaded: it reads no blocks, and a gate whose frame no
 * longer fits its shape keeps the order it has.
 */
public final class LightOrderUpgrade
{
    /** Most gate names the log line lists before the rest are only counted. */
    private static final int NAMES_LISTED = 20;

    private LightOrderUpgrade() {}

    /**
     * Rebuilds and saves each gate whose light order is out of date.
     *
     * @param gates
     *            the gates just loaded
     * @return how many were rebuilt
     */
    public static int rebuildAll(final Collection<Stargate> gates)
    {
        int rebuilt = 0;
        final List<String> doesNotFit = new ArrayList<>();
        for (final Stargate gate : gates)
        {
            try
            {
                switch (GateRederivation.rebuildLightOrder(gate))
                {
                    case REBUILT ->
                    {
                        StargateDBManager.saveStargate(gate);
                        rebuilt++;
                    }
                    case DOES_NOT_FIT -> doesNotFit.add(gate.getGateName());
                    default -> { /* up to date, or nothing to read an order from */ }
                }
            }
            // One odd gate must not stop the rest, or the server starting.
            catch (final RuntimeException e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING,
                    "Could not rebuild the light order of gate " + gate.getGateName(), e);
            }
        }
        report(rebuilt, doesNotFit);
        return rebuilt;
    }

    private static void report(final int rebuilt, final List<String> doesNotFit)
    {
        if (rebuilt > 0)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, "Rebuilt the chevron light order of "
                + rebuilt + ((rebuilt == 1) ? " gate" : " gates") + " from " + ((rebuilt == 1) ? "its" : "their")
                + " shape.");
        }
        if (!doesNotFit.isEmpty())
        {
            final List<String> sorted = new ArrayList<>(doesNotFit);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            final int more = sorted.size() - NAMES_LISTED;
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, sorted.size()
                + ((sorted.size() == 1) ? " gate keeps its" : " gates keep their")
                + " old light order because the frame no longer fits the shape; /wormhole regen <gate> -shape <shape>"
                + " can fix one: " + String.join(", ", sorted.subList(0, Math.min(NAMES_LISTED, sorted.size())))
                + ((more > 0) ? ", and " + more + " more." : "."));
        }
    }
}
