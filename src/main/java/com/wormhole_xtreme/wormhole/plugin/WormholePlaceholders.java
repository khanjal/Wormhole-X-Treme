package com.wormhole_xtreme.wormhole.plugin;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * The PlaceholderAPI expansion, registering {@code %wormhole_...%}.
 *
 * <p>Deliberately thin. This class extends a PlaceholderAPI type, so loading it at all
 * requires PlaceholderAPI on the classpath -- which is why {@link PlaceholderSupport} names it
 * only after finding PlaceholderAPI, and why everything worth testing lives in
 * {@link PlaceholderValues} instead, where neither PlaceholderAPI nor a server is needed.
 *
 * <p>The placeholders are:
 *
 * <ul>
 * <li>{@code %wormhole_gates_total%}</li>
 * <li>{@code %wormhole_gates_open%}</li>
 * <li>{@code %wormhole_gates_owned%}</li>
 * <li>{@code %wormhole_nearest_gate%}</li>
 * </ul>
 */
public class WormholePlaceholders extends PlaceholderExpansion
{
    @Override
    public String getIdentifier()
    {
        return "wormhole";
    }

    @Override
    public String getAuthor()
    {
        return "Khan Jal";
    }

    @Override
    public String getVersion()
    {
        return WormholeXTreme.getThisPlugin().getDescription().getVersion();
    }

    /**
     * Keeps the expansion registered across a PlaceholderAPI reload.
     *
     * <p>Without this PlaceholderAPI drops the expansion on {@code /papi reload} and only an
     * expansion downloaded from its own cloud would come back. Ours arrives with the plugin,
     * so nothing would re-register it until the next server restart.
     *
     * @return true, always
     */
    @Override
    public boolean persist()
    {
        return true;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params)
    {
        if (player == null)
        {
            return PlaceholderValues.resolve(params, null, null, null);
        }
        // An OfflinePlayer has a name and a UUID but no position, so the two gate counts
        // answer for somebody who has logged off and the nearest gate does not.
        final Player online = player.getPlayer();
        final Location where = (online == null) ? null : online.getLocation();
        // getUniqueId() is never null on an OfflinePlayer -- Bukkit builds one from the
        // name when it has nothing else -- so there is nothing to guard here.
        return PlaceholderValues.resolve(params, player.getUniqueId().toString(),
            player.getName(), where);
    }
}
