package com.wormhole_xtreme.wormhole;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.wormhole_xtreme.wormhole.model.GateSpatialIndex;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * Fails a test class that leaves gates in {@link StargateManager}'s shared statics.
 *
 * <p>Those statics outlive the class that wrote to them -- the suite runs in a single fork --
 * so a gate left behind is inherited by every class that runs afterwards. The symptom is a test
 * that passes alone and fails in the suite, usually an assertion on a count that comes back
 * bigger than the gates the test itself built. Thirty-odd classes were leaking this way at
 * once, and the only reason the suite stayed green is that no test had yet asserted on an
 * absolute count.
 *
 * <p>Registered for every class through {@code META-INF/services}, so it cannot be forgotten
 * the way an {@code @AfterEach} can. It cleans up as well as failing: leaving the mess in place
 * would fail the next class too, and the offender is the one worth naming.
 *
 * <p>The fix for a failure here is one line -- {@code PluginTestSupport.forgetAllGates()} in an
 * {@code @AfterEach}.
 */
public final class GateStateGuard implements AfterAllCallback
{
    @Override
    public void afterAll(final ExtensionContext context) throws Exception
    {
        final List<String> left = new ArrayList<>();
        // Cleaning in a finally so a renamed static does not also leave the mess behind. Every
        // later class would otherwise fail on this class's gates as well as the rename.
        try
        {
            collectLeftovers(left);
        }
        finally
        {
            PluginTestSupport.forgetAllGates();
        }
        if (!left.isEmpty())
        {
            throw new AssertionError(context.getDisplayName() + " left " + left.size()
                + " thing(s) in the shared gate statics, which the next test class"
                + " inherits: " + summarise(left)
                + ". Call PluginTestSupport.forgetAllGates() in an @AfterEach.");
        }
    }

    /**
     * Names everything still held, across every static {@code forgetAllGates} empties.
     *
     * <p>These have to stay in step: something the teardown clears but this does not look at is
     * a leak that gets tidied away without the class that caused it ever being told.
     */
    private static void collectLeftovers(final List<String> into) throws ReflectiveOperationException
    {
        for (final Stargate gate : StargateManager.getOpenGates())
        {
            into.add("open gate " + gate.getGateName());
        }
        for (final Stargate gate : StargateManager.getAllGatesUnsorted())
        {
            into.add("registered gate " + gate.getGateName());
        }
        describe("network", PrivateStatics.of(StargateManager.class, "stargateNetworks"), into);
        describe("world block index", PrivateStatics.of(StargateManager.class, "gateBlocksByWorld"), into);
        describe("incomplete gate", PrivateStatics.of(StargateManager.class, "incompleteStargates"), into);
        describe("activated gate", PrivateStatics.of(StargateManager.class, "activatedStargates"), into);
        describe("builder shape", PrivateStatics.of(StargateManager.class, "playerBuilders"), into);
        describe("indexed block", PrivateStatics.of(GateSpatialIndex.class, "index"), into);
    }

    /**
     * Adds one entry per leftover key in a map-shaped static.
     */
    private static void describe(final String what, final Object value, final List<String> into)
    {
        if (value instanceof java.util.Map)
        {
            for (final Object key : ((java.util.Map<?, ?>) value).keySet())
            {
                into.add(what + " " + key);
            }
        }
    }

    /**
     * The first few leftovers, so a failure message stays readable when a class leaks dozens.
     */
    private static String summarise(final List<String> left)
    {
        if (left.size() <= 8)
        {
            return left.toString();
        }
        return left.subList(0, 8) + " and " + (left.size() - 8) + " more";
    }
}
