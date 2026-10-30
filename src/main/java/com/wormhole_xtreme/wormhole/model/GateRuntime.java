package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;

import org.bukkit.block.Block;

/**
 * What a gate is doing right now that is not saved: its scheduler task ids, where its animation is,
 * how many chevrons are locked and the order of its sign's targets (#430).
 *
 * <p>Moved out of {@link Stargate} as it was, fields and accessors both; the gate holds one and hands it
 * to the animator, the dial manager and the lifecycle through {@link Stargate#runtime()}.
 */
final class GateRuntime
{
    /** The gate activate scheduler task id. */
    private int gateActivateTaskId;
    /** The gate shutdown scheduler task id. */
    private int gateShutdownTaskId;
    /** The gate after shutdown scheduler task id. */
    private int gateAfterShutdownTaskId;
    /** The animation removing. */
    private boolean gateAnimationRemoving = false;
    /** The current_lighting_iteration. */
    private int gateLightingCurrentIteration = 0;
    /** How many chevrons this dial has locked, for a player who arrives before the wormhole forms. */
    private int gateChevronsLocked = 0;
    /** The Animated blocks. */
    private final ArrayList<Block> gateAnimatedBlocks = new ArrayList<>();

    /**
     * Gets the gate activate task id.
     */
    int getGateActivateTaskId()
    {
        return gateActivateTaskId;
    }

    /**
     * Sets the gate activate task id.
     */
    void setGateActivateTaskId(final int gateActivateTaskId)
    {
        this.gateActivateTaskId = gateActivateTaskId;
    }

    /**
     * Gets the gate shutdown task id.
     */
    int getGateShutdownTaskId()
    {
        return gateShutdownTaskId;
    }

    /**
     * Sets the gate shutdown task id.
     */
    void setGateShutdownTaskId(final int gateShutdownTaskId)
    {
        this.gateShutdownTaskId = gateShutdownTaskId;
    }

    /**
     * Gets the gate after shutdown task id.
     */
    int getGateAfterShutdownTaskId()
    {
        return gateAfterShutdownTaskId;
    }

    /**
     * Sets the gate after shutdown task id.
     */
    void setGateAfterShutdownTaskId(final int gateAfterShutdownTaskId)
    {
        this.gateAfterShutdownTaskId = gateAfterShutdownTaskId;
    }

    /**
     * Checks if is gate animation removing.
     */
    boolean isGateAnimationRemoving()
    {
        return gateAnimationRemoving;
    }

    /**
     * Sets the gate animation removing.
     */
    void setGateAnimationRemoving(final boolean gateAnimationRemoving)
    {
        this.gateAnimationRemoving = gateAnimationRemoving;
    }

    /**
     * Gets the gate lighting current iteration.
     */
    int getGateLightingCurrentIteration()
    {
        return gateLightingCurrentIteration;
    }

    /**
     * Sets the gate lighting current iteration.
     */
    void setGateLightingCurrentIteration(final int gateLightingCurrentIteration)
    {
        this.gateLightingCurrentIteration = gateLightingCurrentIteration;
    }

    /** @return how many chevrons this dial has locked so far */
    int getGateChevronsLocked()
    {
        return gateChevronsLocked;
    }

    /** Sets how many chevrons this dial has locked so far. */
    void setGateChevronsLocked(final int gateChevronsLocked)
    {
        this.gateChevronsLocked = gateChevronsLocked;
    }

    /**
     * Gets the gate animated blocks.
     */
    ArrayList<Block> getGateAnimatedBlocks()
    {
        return gateAnimatedBlocks;
    }
}
