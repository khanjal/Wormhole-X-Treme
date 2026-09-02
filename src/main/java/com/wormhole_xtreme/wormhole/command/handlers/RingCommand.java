/*
 *   Wormhole X-Treme Plugin for Bukkit
 *
 *   Everything a player does to a transport ring that is not walking into one.
 */
package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.ring.BukkitBlockProbe;
import com.wormhole_xtreme.wormhole.model.ring.Ring;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingIndex;
import com.wormhole_xtreme.wormhole.model.ring.RingManager;
import com.wormhole_xtreme.wormhole.model.ring.RingPair;
import com.wormhole_xtreme.wormhole.model.ring.RingOrientation;
import com.wormhole_xtreme.wormhole.model.ring.RingPermissions;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;
import com.wormhole_xtreme.wormhole.model.ring.RingTemplate;
import com.wormhole_xtreme.wormhole.model.ring.RingYamlManager;

/**
 * Everything a player does to a transport ring that is not walking into one.
 *
 * <p>One command with verbs rather than a command per field. Gates grew a separate top-level
 * command for each setting — {@code portalmaterial}, {@code irismaterial},
 * {@code lightmaterial}, {@code wooshdepth} — which is four registry entries, four usage
 * strings and four completers saying the same thing four ways. This stays one entry however
 * many fields rings end up with.
 *
 * <p>Most verbs take an optional pair id. Leaving it off means "the ring I am standing in",
 * which is how these are almost always used: you walk to the ring you want to change and
 * change it. Giving an id means you are somewhere else and thinking about the pair as a
 * whole. For the two per-end settings that distinction decides scope as well — standing in
 * a ring edits that end, naming a pair edits both.
 */
public class RingCommand implements SubCommand
{
    /* (non-Javadoc)
     * @see com.wormhole_xtreme.wormhole.command.SubCommand#execute(org.bukkit.command.CommandSender, java.lang.String[])
     */
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("Transport rings are built and edited in the world, so this is a player command.");
            return true;
        }
        final Player player = (Player) sender;
        final String verb = (args.length > 1) ? args[1].toLowerCase() : "help";

        if ("create".equals(verb))
        {
            return create(player);
        }
        if ("cancel".equals(verb))
        {
            return cancel(player);
        }
        if ("list".equals(verb))
        {
            return list(player);
        }
        if ("remove".equals(verb))
        {
            return remove(player, args);
        }
        if ("edit".equals(verb))
        {
            return edit(player, args);
        }
        if ("allow".equals(verb) || "deny".equals(verb))
        {
            return allowOrDeny(player, args, "allow".equals(verb));
        }
        if ("owner".equals(verb))
        {
            return transferOwner(player, args);
        }
        return help(player);
    }

    /**
     * Builds the ring the player is standing in, or pairs it with the one they built before.
     *
     * @param player
     *            the builder
     * @return true, the command was handled
     */
    private static boolean create(final Player player)
    {
        if (!RingPermissions.has(player, RingPermissions.BUILD))
        {
            player.sendMessage("You may not build transport rings.");
            return true;
        }

        final RingTemplate.Result found = RingTemplate.detect(
            new BukkitBlockProbe(player.getWorld()),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ(),
            ConfigManager.getRingReach(),
            ConfigManager.getRingDefaultLight());
        if (!found.isSuccess())
        {
            player.sendMessage(explain(found.getFailure()));
            return true;
        }

        final Ring ring = found.getRing();
        final String world = player.getWorld().getName();
        final RingManager.Refusal refusal =
            RingManager.checkPlacement(ring, world, ConfigManager.getRingMinSeparation());
        if (refusal != null)
        {
            player.sendMessage(explain(refusal));
            return true;
        }
        if (touchesGate(player, ring))
        {
            player.sendMessage("That circle overlaps a stargate. Rings and gates cannot share blocks.");
            return true;
        }

        final RingManager.PendingRing waiting = RingManager.getPending(player.getUniqueId());
        if (waiting == null)
        {
            return holdFirstEnd(player, ring, world);
        }
        return completePair(player, waiting, ring, world);
    }

    /**
     * Takes the first end and waits for its partner.
     *
     * @param player
     *            the builder
     * @param ring
     *            the end just read
     * @param world
     *            the world it is in
     * @return true, the command was handled
     */
    private static boolean holdFirstEnd(final Player player, final Ring ring, final String world)
    {
        final int quota = ConfigManager.getRingMaxPairsPerPlayer();
        if ((quota > 0) && !RingPermissions.has(player, RingPermissions.UNLIMITED)
            && (RingManager.countPairsOwnedBy(player.getUniqueId().toString()) >= quota))
        {
            // Checked here rather than at the second end, so nobody builds two rings and
            // only then finds out they were never going to be allowed the pair.
            player.sendMessage("You already have " + quota + " ring pairs, which is the limit.");
            return true;
        }
        RingManager.setPending(player.getUniqueId(), ring, world);
        consumeTemplate(player, ring);
        player.sendMessage("First ring registered in " + ring.getRingMaterial()
            + ". Lay the other one and run this again to pair them.");
        player.sendMessage("Run /wormhole ring cancel to put this one back.");
        return true;
    }

    /**
     * Joins a waiting end to the one just built.
     *
     * @param player
     *            the builder
     * @param waiting
     *            the end built first
     * @param ring
     *            the end just read
     * @param world
     *            the world the second end is in
     * @return true, the command was handled
     */
    private static boolean completePair(final Player player, final RingManager.PendingRing waiting,
        final Ring ring, final String world)
    {
        if (!waiting.getWorldName().equals(world))
        {
            // Said here rather than discovered later. Rings do not cross worlds, and finding
            // that out after laying a second circle of slabs is a poor way to learn it.
            player.sendMessage("Both ends have to be in the same world. Your first ring is in "
                + waiting.getWorldName() + ", and this one is in " + world + ".");
            player.sendMessage("Run /wormhole ring cancel to give up on that one.");
            return true;
        }
        final int maxDistance = ConfigManager.getRingMaxLinkDistance();
        if ((maxDistance > 0)
            && (waiting.getRing().anchorDistanceSquared(ring) > ((long) maxDistance * maxDistance)))
        {
            player.sendMessage("Those two rings are further apart than this server allows ("
                + maxDistance + " blocks).");
            return true;
        }

        final RingPair pair = new RingPair(RingManager.newId(), world, waiting.getRing(), ring);
        pair.setOwner(player.getUniqueId().toString());
        pair.setOwnerName(player.getName());
        pair.setCreated(System.currentTimeMillis());
        pair.setAccess(ConfigManager.getRingDefaultAccess());
        waiting.getRing().setStyle(ConfigManager.getRingDefaultStyle());
        ring.setStyle(ConfigManager.getRingDefaultStyle());

        RingManager.clearPending(player.getUniqueId());
        consumeTemplate(player, ring);
        RingManager.addPair(pair, ConfigManager.getRingReach());
        RingYamlManager.saveWorld(world);

        player.sendMessage("Ring pair " + pair.getId() + " is live. Step into either end.");
        player.sendMessage("It is " + pair.getAccess()
            + (pair.getAccess() == RingAccess.PRIVATE
                ? " — use /wormhole ring allow <player> to let others in." : "."));
        return true;
    }

    /**
     * Clears the slabs a ring was laid out in.
     *
     * <p>The template is scaffolding, not structure: once the ring is registered the circle
     * comes up and the floor looks as it did. Only blocks that are still the slab the ring
     * was read from are touched, so anything changed in between is left where it is.
     *
     * @param player
     *            the builder, whose world this is
     * @param ring
     *            the ring whose template to clear
     */
    private static void consumeTemplate(final Player player, final Ring ring)
    {
        for (final int[] block : ring.perimeterBlocks())
        {
            final org.bukkit.block.Block at = player.getWorld().getBlockAt(block[0], block[1], block[2]);
            if (at.getType() == ring.getRingMaterial())
            {
                at.setType(Material.AIR, false);
            }
        }
    }

    /**
     * Puts a template back, for a first end somebody has given up on.
     *
     * @param player
     *            the builder
     * @param ring
     *            the ring to lay out again
     */
    private static void restoreTemplate(final Player player, final Ring ring)
    {
        final boolean top = ring.getOrientation() == RingOrientation.CEILING;
        for (final int[] block : ring.perimeterBlocks())
        {
            final org.bukkit.block.Block at = player.getWorld().getBlockAt(block[0], block[1], block[2]);
            if (at.getType() != Material.AIR)
            {
                // Something is there now. Putting the slab back would destroy it, and the
                // player can lay one more slab far more easily than they can undo that.
                continue;
            }
            final org.bukkit.block.data.BlockData data = ring.getRingMaterial().createBlockData();
            if (data instanceof org.bukkit.block.data.type.Slab)
            {
                final org.bukkit.block.data.type.Slab slab = (org.bukkit.block.data.type.Slab) data;
                slab.setType(top
                    ? org.bukkit.block.data.type.Slab.Type.TOP
                    : org.bukkit.block.data.type.Slab.Type.BOTTOM);
                at.setBlockData(slab, false);
            }
        }
    }

    /**
     * Whether a ring would sit on top of a stargate.
     *
     * <p>Gates and rings both act on the move path and both animate their own blocks, so
     * they are never allowed to share ground. Gates were built first, so rings give way.
     *
     * @param player
     *            the builder, whose world this is
     * @param ring
     *            the ring being placed
     * @return true if it touches gate blocks
     */
    private static boolean touchesGate(final Player player, final Ring ring)
    {
        for (final int[] block : ring.perimeterBlocks())
        {
            if (com.wormhole_xtreme.wormhole.model.StargateManager.isBlockInGate(
                player.getWorld().getBlockAt(block[0], block[1], block[2])))
            {
                return true;
            }
        }
        for (final int[] block : ring.interiorBlocks())
        {
            if (com.wormhole_xtreme.wormhole.model.StargateManager.isBlockInGate(
                player.getWorld().getBlockAt(block[0], block[1], block[2])))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Says why a circle of slabs was not accepted.
     *
     * <p>Each reason gets its own sentence. Telling somebody looking straight at their ring
     * that no ring was found would send them hunting the wrong problem entirely.
     *
     * @param failure
     *            what detection objected to
     * @return something the player can act on
     */
    private static String explain(final RingTemplate.Failure failure)
    {
        if (failure == RingTemplate.Failure.MIXED_MATERIALS)
        {
            return "That ring is built from more than one kind of slab. Use just one — "
                + "whichever you pick is what the rings will be made of.";
        }
        if (failure == RingTemplate.Failure.MIXED_HALVES)
        {
            return "Some of those slabs rest on the floor and others hang from the ceiling. "
                + "A ring has to be one or the other.";
        }
        if (failure == RingTemplate.Failure.INTERIOR_NOT_CLEAR)
        {
            return "That circle is filled in. Lay only the ring itself and leave the middle "
                + "clear — that is where people stand.";
        }
        return "No ring of slabs here. Lay a circle of slabs and stand inside it.";
    }

    /**
     * Says why a ring may not go where it was asked for.
     *
     * @param refusal
     *            what placement objected to
     * @return something the player can act on
     */
    private static String explain(final RingManager.Refusal refusal)
    {
        if (refusal == RingManager.Refusal.TOO_CLOSE)
        {
            return "There is another ring close by. Move this one further away and try again.";
        }
        if (refusal == RingManager.Refusal.OVERLAPS_RING)
        {
            return "That overlaps another ring. Two rings cannot share ground, whatever "
                + "height they are at.";
        }
        return "That ring cannot go there.";
    }

    /**
     * Throws away a half-built pair.
     *
     * @param player
     *            the builder
     * @return true, the command was handled
     */
    private static boolean cancel(final Player player)
    {
        final RingManager.PendingRing waiting = RingManager.clearPending(player.getUniqueId());
        if (waiting == null)
        {
            player.sendMessage("You have no half-built ring pair.");
            return true;
        }
        // The slabs were taken when that end was registered, so giving up has to give them
        // back. Losing a circle of slabs for changing your mind would be a mean way to
        // learn how this works.
        if (player.getWorld().getName().equals(waiting.getWorldName()))
        {
            restoreTemplate(player, waiting.getRing());
            player.sendMessage("Forgotten, and the slabs are back where you laid them.");
            return true;
        }
        player.sendMessage("Forgotten. Its slabs were in " + waiting.getWorldName()
            + ", so they have not been put back — go there and cancel from inside it to get them.");
        return true;
    }

    /**
     * Lists the pairs this player owns.
     *
     * @param player
     *            the player
     * @return true, the command was handled
     */
    private static boolean list(final Player player)
    {
        final String uuid = player.getUniqueId().toString();
        int shown = 0;
        for (final RingPair pair : RingManager.getAllPairs())
        {
            if (!pair.isOwnedBy(uuid) && !RingPermissions.has(player, RingPermissions.ADMIN))
            {
                continue;
            }
            player.sendMessage(pair.describe() + " — " + pair.getWorldName() + ", "
                + pair.getAccess() + ", " + pair.getEndA().getStyle() + "/"
                + pair.getEndB().getStyle());
            shown++;
        }
        if (shown == 0)
        {
            player.sendMessage("You have no transport rings.");
        }
        return true;
    }

    /**
     * Removes a pair, both ends at once.
     *
     * @param player
     *            the player
     * @param args
     *            the command arguments
     * @return true, the command was handled
     */
    private static boolean remove(final Player player, final String[] args)
    {
        final RingPair pair = target(player, args, 2);
        if (pair == null)
        {
            return true;
        }
        if (!RingPermissions.mayManage(player, pair))
        {
            player.sendMessage("That is not your ring pair.");
            return true;
        }
        RingManager.removePair(pair, ConfigManager.getRingReach());
        RingYamlManager.saveWorld(pair.getWorldName());
        player.sendMessage("Removed both ends of " + pair.getId() + ".");
        return true;
    }

    /**
     * Changes one setting on a ring or a pair.
     *
     * @param player
     *            the player
     * @param args
     *            the command arguments
     * @return true, the command was handled
     */
    private static boolean edit(final Player player, final String[] args)
    {
        // "edit <field> <value>" acts on the ring underfoot; "edit <id> <field> <value>"
        // names a pair. Which form was typed is decided by whether the first word is an id.
        final RingPair named = (args.length > 2) ? RingManager.getPair(args[2]) : null;
        final int fieldAt = (named != null) ? 3 : 2;
        final RingPair pair = (named != null) ? named : standingIn(player);
        if (pair == null)
        {
            player.sendMessage("Stand in a ring, or name a pair by its id.");
            return true;
        }
        if (args.length <= (fieldAt + 1))
        {
            player.sendMessage("Usage: /wormhole ring edit [id] <ring|light|name|access|style> <value>");
            return true;
        }
        if (!RingPermissions.mayManage(player, pair))
        {
            player.sendMessage("That is not your ring pair.");
            return true;
        }

        final String field = args[fieldAt].toLowerCase();
        final String value = join(args, fieldAt + 1);
        // Naming a pair means both ends; standing in one means that end only. Materials are
        // per end precisely so a base and a mine can each look like where they are.
        final Ring only = (named != null) ? null : endUnderfoot(player);

        if ("ring".equals(field))
        {
            return setRingMaterial(player, pair, only, value);
        }
        if ("light".equals(field))
        {
            return setLightMaterial(player, pair, only, value);
        }
        if ("name".equals(field))
        {
            if (only == null)
            {
                // Naming both ends the same would defeat the point: the name exists so a
                // traveller can be told where they are going, which differs by end.
                player.sendMessage("Stand in the ring you want to name — naming a pair by id "
                    + "would call both ends the same thing.");
                return true;
            }
            only.setName(value);
            return saved(player, pair, value.isEmpty()
                ? "Name cleared." : ("This ring is now " + value + "."));
        }
        if ("access".equals(field))
        {
            try
            {
                pair.setAccess(RingAccess.valueOf(value.toUpperCase()));
            }
            catch (final IllegalArgumentException e)
            {
                player.sendMessage("Access is public or private.");
                return true;
            }
            return saved(player, pair, "Access set to " + pair.getAccess() + ".");
        }
        if ("style".equals(field))
        {
            final RingStyle chosen = RingStyle.parse(value);
            if (chosen == null)
            {
                player.sendMessage("Style is fast (rings climb together) or slow "
                    + "(one at a time). 'concurrent' and 'sequential' work too.");
                return true;
            }
            if (only != null)
            {
                only.setStyle(chosen);
            }
            else
            {
                pair.getEndA().setStyle(chosen);
                pair.getEndB().setStyle(chosen);
            }
            return saved(player, pair, "Style set to " + chosen + ".");
        }
        player.sendMessage("Fields are: ring, light, name, access, style.");
        return true;
    }

    /**
     * Sets the travelling slab material on one end or both.
     *
     * @param player
     *            the player
     * @param pair
     *            the pair
     * @param only
     *            the single end to change, or null for both
     * @param value
     *            the material name
     * @return true, the command was handled
     */
    private static boolean setRingMaterial(final Player player, final RingPair pair,
        final Ring only, final String value)
    {
        final Material material = Material.matchMaterial(value);
        if (!Ring.isUsableAsRing(material))
        {
            // Refused rather than accepted quietly: the rise is built out of slab halves,
            // and a full block would cost the animation its half-block movement, which is
            // the whole visual effect.
            player.sendMessage("The travelling ring has to be a slab — that is what lets it "
                + "move half a block at a time.");
            return true;
        }
        if (only != null)
        {
            only.setRingMaterial(material);
        }
        else
        {
            pair.getEndA().setRingMaterial(material);
            pair.getEndB().setRingMaterial(material);
        }
        return saved(player, pair, "Ring material set to " + material + ".");
    }

    /**
     * Sets the countdown light material on one end or both.
     *
     * @param player
     *            the player
     * @param pair
     *            the pair
     * @param only
     *            the single end to change, or null for both
     * @param value
     *            the material name
     * @return true, the command was handled
     */
    private static boolean setLightMaterial(final Player player, final RingPair pair,
        final Ring only, final String value)
    {
        final Material material = Material.matchMaterial(value);
        if ((material == null) || !material.isBlock())
        {
            player.sendMessage("That is not a block.");
            return true;
        }
        if (only != null)
        {
            only.setLightMaterial(material);
        }
        else
        {
            pair.getEndA().setLightMaterial(material);
            pair.getEndB().setLightMaterial(material);
        }
        return saved(player, pair, "Light material set to " + material + ".");
    }

    /**
     * Adds or removes somebody from a private pair's allow list.
     *
     * @param player
     *            the player
     * @param args
     *            the command arguments
     * @param allowing
     *            true to allow, false to deny
     * @return true, the command was handled
     */
    private static boolean allowOrDeny(final Player player, final String[] args, final boolean allowing)
    {
        if (args.length < 3)
        {
            player.sendMessage("Usage: /wormhole ring " + (allowing ? "allow" : "deny") + " <player> [id]");
            return true;
        }
        final RingPair pair = target(player, args, 3);
        if (pair == null)
        {
            return true;
        }
        if (!RingPermissions.mayManage(player, pair))
        {
            player.sendMessage("That is not your ring pair.");
            return true;
        }
        final OfflinePlayer subject = findPlayer(args[2]);
        if (subject == null)
        {
            player.sendMessage("No player called " + args[2] + " has been on this server.");
            return true;
        }
        final String uuid = subject.getUniqueId().toString();
        if (allowing)
        {
            return saved(player, pair, pair.allow(uuid)
                ? (args[2] + " may now use " + pair.getId() + ".")
                : (args[2] + " could already use it."));
        }
        return saved(player, pair, pair.deny(uuid)
            ? (args[2] + " may no longer use " + pair.getId() + ".")
            : (args[2] + " was not on the list."));
    }

    /**
     * Hands a pair to somebody else.
     *
     * <p>Written for the case where staff build rings for a player, but it works for a gift
     * between players too. The quota is checked against the recipient, because otherwise it
     * could be walked around entirely by having somebody else build and hand over.
     *
     * @param player
     *            the player
     * @param args
     *            the command arguments
     * @return true, the command was handled
     */
    private static boolean transferOwner(final Player player, final String[] args)
    {
        if (args.length < 3)
        {
            player.sendMessage("Usage: /wormhole ring owner <player> [id]");
            return true;
        }
        final RingPair pair = target(player, args, 3);
        if (pair == null)
        {
            return true;
        }
        if (!RingPermissions.mayManage(player, pair))
        {
            player.sendMessage("That is not your ring pair to give away.");
            return true;
        }
        final OfflinePlayer subject = findPlayer(args[2]);
        if (subject == null)
        {
            player.sendMessage("No player called " + args[2] + " has been on this server.");
            return true;
        }
        final String uuid = subject.getUniqueId().toString();
        if (pair.isOwnedBy(uuid))
        {
            player.sendMessage(args[2] + " already owns that pair.");
            return true;
        }

        final int quota = ConfigManager.getRingMaxPairsPerPlayer();
        if ((quota > 0) && (RingManager.countPairsOwnedBy(uuid) >= quota))
        {
            player.sendMessage(args[2] + " already has " + quota + " ring pairs, which is the limit.");
            return true;
        }

        pair.setOwner(uuid);
        pair.setOwnerName(subject.getName() == null ? args[2] : subject.getName());
        // The previous owner is not quietly kept on the allow list. Staff building a ring
        // for somebody should not be left with standing access to it afterwards, and a
        // player who wants to keep using one they gave away can be added back by its new
        // owner — which is their call to make, not ours.
        return saved(player, pair, "Handed " + pair.getId() + " to " + args[2]
            + (pair.getAccess() == RingAccess.PRIVATE
                ? ". It is private, so you no longer have access to it yourself." : "."));
    }

    /**
     * Finds the pair a command should act on.
     *
     * @param player
     *            the player
     * @param args
     *            the command arguments
     * @param idAt
     *            where an id would be, if one was given
     * @return the pair, or null after telling the player why not
     */
    private static RingPair target(final Player player, final String[] args, final int idAt)
    {
        if (args.length > idAt)
        {
            final RingPair named = RingManager.getPair(args[idAt]);
            if (named == null)
            {
                player.sendMessage("There is no ring pair called " + args[idAt] + ".");
            }
            return named;
        }
        final RingPair here = standingIn(player);
        if (here == null)
        {
            player.sendMessage("Stand in a ring, or name a pair by its id.");
        }
        return here;
    }

    /**
     * The pair whose ring the player is standing in.
     *
     * <p>The same index lookup the move path makes, so this costs nothing.
     *
     * @param player
     *            the player
     * @return the pair, or null
     */
    private static RingPair standingIn(final Player player)
    {
        final RingIndex.RingEnd end = endAt(player);
        return (end == null) ? null : end.getPair();
    }

    /**
     * The exact end the player is standing in.
     *
     * @param player
     *            the player
     * @return the ring, or null
     */
    private static Ring endUnderfoot(final Player player)
    {
        final RingIndex.RingEnd end = endAt(player);
        return (end == null) ? null : end.getRing();
    }

    /**
     * Looks the player's feet up in the ring index.
     *
     * @param player
     *            the player
     * @return what is there, or null
     */
    private static RingIndex.RingEnd endAt(final Player player)
    {
        return RingIndex.volumeAt(player.getWorld().getName(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ());
    }

    /**
     * Writes the change out and says what happened.
     *
     * @param player
     *            the player to tell
     * @param pair
     *            the pair that changed
     * @param message
     *            what to say
     * @return true, the command was handled
     */
    private static boolean saved(final Player player, final RingPair pair, final String message)
    {
        RingYamlManager.saveWorld(pair.getWorldName());
        player.sendMessage(message);
        return true;
    }

    /**
     * Finds a player by name, whether or not they are online.
     *
     * @param name
     *            the name typed
     * @return the player, or null if the server has never seen them
     */
    @SuppressWarnings("deprecation")
    private static OfflinePlayer findPlayer(final String name)
    {
        final Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null)
        {
            return online;
        }
        final OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(name);
        // getOfflinePlayer invents a profile for a name nobody has ever used, so having
        // played before is the only way to tell a real absent player from a typo.
        return ((offline != null) && offline.hasPlayedBefore()) ? offline : null;
    }

    /**
     * Joins the rest of the arguments into one value.
     *
     * @param args
     *            the command arguments
     * @param from
     *            where the value starts
     * @return the value, possibly with spaces in it
     */
    private static String join(final String[] args, final int from)
    {
        final StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++)
        {
            if (out.length() > 0)
            {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    /**
     * Prints what this command can do.
     *
     * @param player
     *            the player
     * @return true, the command was handled
     */
    private static boolean help(final Player player)
    {
        player.sendMessage("/wormhole ring create — lay a circle of slabs, stand in it, run this twice to pair");
        player.sendMessage("/wormhole ring cancel — forget a half-built pair");
        player.sendMessage("/wormhole ring list — your pairs");
        player.sendMessage("/wormhole ring remove [id] — remove both ends");
        player.sendMessage("/wormhole ring edit [id] <ring|light|name|access|style> <value>");
        player.sendMessage("/wormhole ring allow|deny <player> [id] — who may use a private pair");
        player.sendMessage("/wormhole ring owner <player> [id] — hand a pair to somebody else");
        return true;
    }

    /**
     * A UUID from a string, or null when it is not one.
     *
     * @param text
     *            the text
     * @return the UUID, or null
     */
    static UUID parseUuid(final String text)
    {
        try
        {
            return UUID.fromString(text);
        }
        catch (final IllegalArgumentException e)
        {
            return null;
        }
    }
}
