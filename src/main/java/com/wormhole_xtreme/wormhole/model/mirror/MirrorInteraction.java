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
            sayUnbound(player, mirror);
            return;
        }
        final Location destination = mirror.destination().toLocation();
        if (destination == null)
        {
            // The name-based world lookup every store in this plugin uses. A world that is not
            // loaded, or one recreated under a different name, both land here.
            say(player, "The far side of this mirror is in "
                + MirrorText.name(mirror.destination().worldName())
                + ", which is not loaded.");
            return;
        }
        final Location safe = WorldUtils.findSafePlayerLocation(destination);
        if (!player.teleport((safe == null) ? destination : safe))
        {
            sayRefused(player, mirror);
        }
    }

    /**
     * Says that something else on the server stopped the trip.
     *
     * <p>A cancelled {@code PlayerTeleportEvent} puts the player back exactly where they were,
     * which on a mirror is the banner they just clicked -- so a refusal nobody reports reads as
     * the mirror opening onto itself. The commonest canceller is a world-access plugin
     * (Multiverse's {@code enforce-access} wants {@code multiverse.access.<world>}); land
     * claims are the other.
     */
    private static void sayRefused(final Player player, final QuantumMirror mirror)
    {
        say(player, "Something else on this server would not let you into "
            + MirrorText.name(mirror.destination().worldName()) + ".");
        say(player, "A world-access or land-claim plugin is the usual reason -- check that you"
            + " are allowed into that world.");
    }

    /**
     * Says a mirror goes nowhere, and how to point it somewhere.
     *
     * <p>"This mirror does not open onto anywhere yet" is true and useless. It is said at the
     * one moment somebody has demonstrated they want this banner to work, standing in front of
     * it -- which is exactly when the next command is worth putting in front of them, spelled
     * out with this mirror's own name so it can be typed as it stands.
     *
     * <p>Only for somebody who could run it. A visitor clicking a half-built mirror gets the
     * plain sentence: handing them two commands they have no permission for would read as the
     * plugin telling them to do something, and they would be right to try.
     *
     * <p>Both routes are offered because they answer different questions. {@code link} is for
     * a banner at the far end, which is what most pairs are; {@code target} is for arriving
     * somewhere with no banner at all, which is the archived-world case the whole feature was
     * built for and the one nobody guesses.
     */
    private static void sayUnbound(final Player player, final QuantumMirror mirror)
    {
        say(player, MirrorText.quoted(mirror.name()) + " does not open onto anywhere yet.");
        if (!WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG))
        {
            return;
        }
        say(player, "Hang a banner where it should lead, look at it, and run:");
        say(player, "  " + MirrorText.command("/wormhole mirror link", mirror.name()));
        say(player, "Or stand where arrivals should land and run:");
        say(player, "  " + MirrorText.command("/wormhole mirror target", mirror.name()));
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
