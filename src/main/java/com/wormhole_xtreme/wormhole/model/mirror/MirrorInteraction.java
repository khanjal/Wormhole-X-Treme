package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What happens when somebody right-clicks a banner that is a mirror.
 *
 * <p>This runs on every right-click of every block on the server, so the first thing it does
 * is the cheapest question it can ask: is this block in the mirror index? For all but a
 * handful of blocks the answer is no and nothing else happens. Everything expensive --
 * permission, world lookup, safe-location correction -- is behind that.
 *
 * <p>Separate from {@code GateInteractionHandler} rather than folded into it. A gate's dial
 * sign and a mirror's banner have nothing in common beyond both being blocks somebody clicks,
 * and the handlers share no state.
 */
public final class MirrorInteraction
{
    /**
     * Every banner material, worked out once.
     *
     * <p>This is what keeps a click on an ordinary block cheap. Building a
     * {@link MirrorBlock} to ask the registry means calling {@code getWorld()} on the block,
     * and that is real work on every right-click of every block on the server -- there is a
     * test, {@code InteractLoggingCostTest}, that fails if this path touches the world. So the
     * block's own type is checked first: a hash lookup against a set built at class-init,
     * which rules out all but a handful of blocks before anything else happens.
     *
     * <p>Derived from {@code Material.values()} by name rather than listed, because the
     * sixteen wall and sixteen freestanding banners are one per dye colour and a new colour
     * should not need editing here. Not {@code Tag.BANNERS}, which would be a registry lookup
     * at a point in startup where this project has been bitten before.
     */
    private static final Set<Material> BANNERS =
        Arrays.stream(Material.values())
            .filter(m -> m.name().endsWith("BANNER"))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));

    /** Static handler only. */
    private MirrorInteraction()
    {
    }

    /**
     * Sends a player through a mirror, if that is what they clicked.
     *
     * @param event
     *            the interact event
     * @return true if this was a mirror and the event should be cancelled
     */
    public static boolean handle(final PlayerInteractEvent event)
    {
        if ((event == null) || (event.getAction() != Action.RIGHT_CLICK_BLOCK))
        {
            return false;
        }
        final Block block = event.getClickedBlock();
        // Type first, deliberately. Anything else -- including building the key to ask the
        // registry -- costs more than this, and almost every click is on a block that is not
        // a banner at all.
        if ((block == null) || !BANNERS.contains(block.getType()))
        {
            return false;
        }
        final QuantumMirror mirror = MirrorManager.at(MirrorBlock.of(block));
        if (mirror == null)
        {
            return false;
        }
        travel(event.getPlayer(), mirror);
        return true;
    }

    /**
     * Takes the player through, or says why not.
     *
     * <p>Every refusal names what is actually wrong, the way a ring's build refusal does. "It
     * did not work" on a block you just clicked is the least useful thing a mirror could say.
     *
     * @param player
     *            whoever clicked
     * @param mirror
     *            the mirror they clicked
     */
    private static void travel(final Player player, final QuantumMirror mirror)
    {
        if (!WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        if (mirror.destination() == null)
        {
            say(player, "This mirror does not open onto anywhere yet.");
            return;
        }
        final Location destination = mirror.destination().toLocation();
        if (destination == null)
        {
            // The name-based world lookup every store in this plugin uses. A world that is not
            // loaded, or one recreated under a different name, both land here.
            say(player, "The far side of this mirror is in " + mirror.destination().worldName()
                + ", which is not loaded.");
            return;
        }
        final Location safe = WorldUtils.findSafePlayerLocation(destination);
        player.teleport((safe == null) ? destination : safe);
    }

    /**
     * Says something to the player, prefixed the way the rest of the plugin prefixes things.
     *
     * <p>Without the header these lines arrive looking like something another plugin said.
     * The permission refusal carries its own header already, so it is passed through as it is.
     */
    private static void say(final Player player, final String message)
    {
        player.sendMessage(ConfigManager.MessageStrings.NORMAL_HEADER + message);
    }
}
