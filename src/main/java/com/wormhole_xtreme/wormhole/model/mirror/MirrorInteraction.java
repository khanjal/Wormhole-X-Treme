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
import org.bukkit.inventory.EquipmentSlot;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.utils.WorldUtils;

/**
 * What happens when somebody clicks a mirror: a right-click chooses where it opens onto, and a
 * punch goes through.
 *
 * <p>This runs on every click of every block on the server, so the first thing it does is the
 * cheapest question it can ask: is this block in the mirror index? For all but a handful of
 * blocks the answer is no and nothing else happens. Everything expensive -- permission, world
 * lookup, safe-location correction -- is behind that.
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
     * and that is real work on every click of every block on the server -- there is a test,
     * {@code InteractLoggingCostTest}, that fails if this path touches the world. So the
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
     * Chooses or travels, if a mirror is what was clicked.
     *
     * @param event
     *            the interact event
     * @return true if this was a mirror and the event should be cancelled
     */
    public static boolean handle(final PlayerInteractEvent event)
    {
        if (event == null)
        {
            return false;
        }
        final Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK) && (action != Action.LEFT_CLICK_BLOCK))
        {
            return false;
        }
        final Block block = event.getClickedBlock();
        if (block == null)
        {
            return false;
        }
        final Player player = event.getPlayer();
        final QuantumMirror mirror = mirrorAt(player, block);
        if (mirror == null)
        {
            return false;
        }
        // A right-click arrives once for each hand. The second is claimed, and does nothing.
        if (event.getHand() == EquipmentSlot.OFF_HAND)
        {
            return true;
        }
        if (action == Action.RIGHT_CLICK_BLOCK)
        {
            choose(player, block, mirror);
        }
        else
        {
            travel(player, mirror);
        }
        // A click the server refused shows the client the real block again, over the view.
        MirrorWindows.resend(player);
        return true;
    }

    /** The mirror a block belongs to: its banner, or the opening of a window the player sees. */
    private static QuantumMirror mirrorAt(final Player player, final Block block)
    {
        // Type first, deliberately. Anything else -- including building the key to ask the
        // registry -- costs more than this, and almost every click is on a block that is not
        // a banner at all.
        if (BANNERS.contains(block.getType()))
        {
            return MirrorManager.at(MirrorBlock.of(block));
        }
        // A window's opening is drawn over a wall, so what was clicked is the wall.
        final QuantumMirror drawn = MirrorWindows.clicked(player, block);
        return (drawn == null) ? null : MirrorManager.byName(drawn.name());
    }

    /** Moves the mirror on to the next one, and says where it opens onto now. */
    private static void choose(final Player player, final Block block, final QuantumMirror mirror)
    {
        if (!WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.USE))
        {
            player.sendMessage(ConfigManager.MessageStrings.PERMISSION_NO.toString());
            return;
        }
        final String before = MirrorNetwork.chosen(mirror).name();
        final String said = MirrorNetwork.scroll(mirror,
            MirrorNetwork.anybodyNear(block.getWorld(), mirror.banner(), player));
        if (said != null)
        {
            hint(player, said);
        }
        // Shown at once, rather than when the sweep next comes round.
        if (!MirrorNetwork.chosen(mirror).name().equals(before))
        {
            final MirrorBlock at = mirror.banner();
            MirrorWindows.redraw(mirror, block.getWorld().getBlockAt(at.x(), at.y(), at.z()));
        }
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
        // Before the permission check, because this is not about who they are: a player who
        // may use mirrors is exactly the one who has just been carried by one, and is standing
        // in the far banner with the click still arriving.
        if (MirrorSettle.settling(player))
        {
            if (MirrorSettle.shouldExplain(player))
            {
                hint(player, "Mirrors settle for a moment after one puts you down. Step away"
                    + " from the banner and try again.");
            }
            return;
        }
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
        if (MirrorNetwork.reflects(mirror))
        {
            hint(player, MirrorText.quoted(mirror.name()) + " is showing its own room. Right-click it"
                + " to choose another mirror.");
            return;
        }
        final QuantumMirror target = MirrorNetwork.chosen(mirror);
        final Location destination = target.destination().toLocation();
        if (destination == null)
        {
            // The name-based world lookup every store in this plugin uses. A world that is not
            // loaded, or one recreated under a different name, both land here.
            say(player, "The far side of this mirror is in "
                + MirrorText.name(target.destination().worldName())
                + ", which is not loaded.");
            return;
        }
        final Location safe = WorldUtils.findSafePlayerLocation(destination);
        if (!player.teleport((safe == null) ? destination : safe))
        {
            sayRefused(player, target);
            return;
        }
        // Only on a trip that actually happened. The far banner is now in front of them, and
        // the click that sent them there may still have another event in it.
        MirrorSettle.arrived(player);
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
    private static void sayRefused(final Player player, final QuantumMirror target)
    {
        say(player, "Something else on this server would not let you into "
            + MirrorText.name(target.destination().worldName()) + ".");
        say(player, "A world-access or land-claim plugin is the usual reason -- check that you"
            + " are allowed into that world.");
    }

    /**
     * Says a mirror has no room, and how to give it one.
     *
     * <p>Only for somebody who could run the command. A visitor gets the plain sentence: handing
     * them a command they have no permission for would read as the plugin telling them to.
     */
    private static void sayUnbound(final Player player, final QuantumMirror mirror)
    {
        say(player, MirrorText.quoted(mirror.name()) + " does not open onto anywhere yet.");
        if (!WXPermissions.checkWXPermissions(player, WXPermissions.PermissionType.CONFIG))
        {
            return;
        }
        say(player, "Look at it and run "
            + MirrorText.command("/wormhole mirror create", mirror.name()) + " to set it up again.");
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

    /**
     * Says something about the mirror itself above the hotbar, where the next line replaces it.
     *
     * <p>Where it opens onto now, that there is nowhere else, to right-click first: a player clicking
     * through a list of mirrors had a chat window full of them.
     */
    private static void hint(final Player player, final String message)
    {
        com.wormhole_xtreme.wormhole.utils.ActionBar.send(player, "§3:: " + message);
    }
}
