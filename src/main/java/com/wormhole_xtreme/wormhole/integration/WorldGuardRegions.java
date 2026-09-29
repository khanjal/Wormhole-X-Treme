package com.wormhole_xtreme.wormhole.integration;

import java.util.EnumMap;
import java.util.Map;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import com.wormhole_xtreme.wormhole.integration.RegionFlags.Action;

/**
 * The one class that names WorldGuard; loaded only once WorldGuard is known to be installed.
 */
final class WorldGuardRegions
{
    /** Static helpers only. */
    private WorldGuardRegions()
    {
    }

    /**
     * Registers both flags and answers region questions with them.
     *
     * @return the check to hand {@link RegionFlags}
     */
    static RegionFlags.Check register()
    {
        final FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        final Map<Action, StateFlag> flags = new EnumMap<>(Action.class);
        for (final Action action : Action.values())
        {
            flags.put(action, claim(registry, action));
        }
        return (player, at, action) -> {
            final LocalPlayer who = WorldGuardPlugin.inst().wrapPlayer(player);
            final WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();
            if (platform.getSessionManager().hasBypass(who, BukkitAdapter.adapt(at.getWorld())))
            {
                return true;
            }
            return platform.getRegionContainer().createQuery()
                .testState(BukkitAdapter.adapt(at), who, flags.get(action));
        };
    }

    /**
     * The flag for this action: registered new, or the one already under its name.
     *
     * <p>Already there after a {@code /reload}, which keeps WorldGuard's registry but not this
     * plugin's classes, or when another plugin chose the same name.
     *
     * @param registry
     *            WorldGuard's flag registry
     * @param action
     *            which flag
     * @return the flag to query
     * @throws IllegalStateException
     *             if the name is taken by a flag that is not a state flag allowing by default
     */
    static StateFlag claim(final FlagRegistry registry, final Action action)
    {
        final StateFlag existing = existing(registry, action);
        if (existing != null)
        {
            return existing;
        }
        final StateFlag flag = new StateFlag(action.flagName(), true);
        try
        {
            registry.register(flag);
            return flag;
        }
        catch (final FlagConflictException e)
        {
            final StateFlag raced = existing(registry, action);
            if (raced == null)
            {
                throw new IllegalStateException("WorldGuard refused the " + action.flagName() + " flag", e);
            }
            return raced;
        }
    }

    /** The allow-by-default state flag already under this action's name, or null if there is none. */
    private static StateFlag existing(final FlagRegistry registry, final Action action)
    {
        final Flag<?> found = registry.get(action.flagName());
        if (found == null)
        {
            return null;
        }
        if (!(found instanceof StateFlag state))
        {
            throw new IllegalStateException("Another plugin registered " + action.flagName() + " as "
                + found.getClass().getSimpleName() + ", not a state flag");
        }
        // One that denies by default would close every gate in every region that never set it.
        if (state.getDefault() != StateFlag.State.ALLOW)
        {
            throw new IllegalStateException("Another plugin registered " + action.flagName()
                + " denying by default");
        }
        return state;
    }
}
