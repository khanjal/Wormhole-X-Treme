package com.wormhole_xtreme.wormhole.command.handlers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * Handler for the three per-gate material overrides.
 *
 * <p>This was three classes -- {@code PortalMaterialCommand}, {@code IrisMaterialCommand} and
 * {@code LightMaterialCommand} -- of 97 lines each, identical but for a material whitelist, a
 * display noun, and one getter/setter pair. Each of them also spelled its whitelist out twice,
 * once as a chain of {@code ==} comparisons and again as English in three or four separate
 * message strings, with nothing keeping the two in step.
 *
 * <p>They had already drifted. The iris variant printed its valid-material line under
 * {@code normalHeader} in two of the four places the other two used {@code errorHeader}, so the
 * same advice arrived in a different colour depending on which command you had typed and how
 * you had got it wrong. That is the failure mode copying a file produces: not a dramatic bug,
 * just a slow loss of the property that the three behave alike.
 *
 * <p>Here the set in {@link Kind} is the only statement of what a material may be. The
 * membership test reads it, the message that lists the options is generated from it, and so is
 * tab completion -- which none of the three offered before, because a hand-written list of
 * materials in the completer would have been a fourth copy to keep in step.
 */
public class MaterialCommand implements SubCommand
{

    /**
     * One of the three materials a gate can override, and everything that differs about it.
     */
    public enum Kind
    {
        /** What the gate interior shows while a wormhole is open. */
        PORTAL("portalmaterial", "portal",
            materials(Material.WATER, Material.LAVA, Material.AIR, Material.NETHER_PORTAL),
            Stargate::getGateCustomPortalMaterial, Stargate::setGateCustomPortalMaterial),

        /** What the iris is made of when engaged. */
        IRIS("irismaterial", "iris",
            materials(Material.STONE, Material.DIAMOND_BLOCK, Material.GLASS, Material.IRON_BLOCK,
                Material.BEDROCK, Material.LAPIS_BLOCK),
            Stargate::getGateCustomIrisMaterial, Stargate::setGateCustomIrisMaterial),

        /** What the light blocks become while the gate is active. */
        LIGHT("lightmaterial", "light",
            materials(Material.GLOWSTONE, Material.REDSTONE_ORE),
            Stargate::getGateCustomLightMaterial, Stargate::setGateCustomLightMaterial);

        /** The subcommand as it is typed. */
        private final String command;

        /** The noun used in messages, lower case: "portal", "iris", "light". */
        private final String noun;

        /** Every material this override accepts. Iteration order is the order listed to players. */
        private final Set<Material> allowed;

        /** Reads the gate's current override. */
        private final Function<Stargate, Material> getter;

        /** Writes the gate's override. */
        private final BiConsumer<Stargate, Material> setter;

        Kind(final String command, final String noun, final Set<Material> allowed,
            final Function<Stargate, Material> getter, final BiConsumer<Stargate, Material> setter)
        {
            this.command = command;
            this.noun = noun;
            this.allowed = allowed;
            this.getter = getter;
            this.setter = setter;
        }

        /**
         * The materials this override accepts, in the order players are shown them.
         *
         * @return an unmodifiable set
         */
        public Set<Material> allowed()
        {
            return allowed;
        }

        /**
         * The subcommand as it is typed, e.g. {@code irismaterial}.
         *
         * @return the subcommand name
         */
        public String command()
        {
            return command;
        }

        /**
         * The material names this override accepts, for tab completion.
         *
         * @return the names, in the order players are shown them
         */
        public List<String> allowedNames()
        {
            return allowed.stream().map(Material::name).collect(Collectors.toList());
        }

        // Package-private rather than private: MaterialCommandTest sets through one kind and
        // reads back through all three, which is what catches an entry wired to another
        // kind's accessor -- the one mistake folding three classes into this enum could make
        // that still compiles. Nothing outside the test needs them.
        /**
         * Reads this override off a gate.
         *
         * @return the getter
         */
        Function<Stargate, Material> getter()
        {
            return getter;
        }

        /**
         * Writes this override onto a gate.
         *
         * @return the setter
         */
        BiConsumer<Stargate, Material> setter()
        {
            return setter;
        }

        /** Keeps the declared order, so the listed advice reads the same way every time. */
        private static Set<Material> materials(final Material... values)
        {
            return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
        }
    }

    /** Which override this instance edits. */
    private final Kind kind;

    /**
     * Instantiates a handler for one of the three material overrides.
     *
     * @param kind
     *            which override to edit
     */
    public MaterialCommand(final Kind kind)
    {
        this.kind = kind;
    }

    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        // Gate management was never actually gated: none of these commands checked a
        // permission at all, so any player able to run /wormhole could reconfigure or
        // reassign any gate on the server. wormhole.config is what an admin already needs
        // for /wormhole config, so it is reused here rather than inventing a second
        // admin-only node that would mean the same thing.
        if ((sender instanceof Player)
            && !WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return true;
        }

        if ((args.length != 2) && (args.length != 3))
        {
            usage(sender);
            return false;
        }

        if (!StargateManager.isStargate(args[1]))
        {
            sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
            usage(sender);
            return true;
        }

        final Stargate stargate = StargateManager.getStargate(args[1]);
        if (!stargate.isGateCustom())
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
            return true;
        }

        if (args.length == 2)
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
                + args[1] + " " + kind.noun + " material is currently: " + kind.getter.apply(stargate));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + validMaterials());
            return true;
        }

        final Material material = parse(args[2]);
        if ((material == null) || !kind.allowed.contains(material))
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
                + "Invalid " + kind.noun + " material: " + args[2]);
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + validMaterials());
            return true;
        }

        kind.setter.accept(stargate, material);
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString()
            + args[1] + " " + kind.noun + " material set to: " + kind.getter.apply(stargate));
        return true;
    }

    /**
     * Reads a material name, tolerantly.
     *
     * <p>An unknown name is not an error worth logging. It is a player typing, and the caller
     * reports it to them along with the list of what would have worked -- which is more use
     * than the FINE-level log line the three previous copies each wrote instead.
     *
     * @param input
     *            what the player typed
     * @return the material, or null if there is no such name
     */
    private static Material parse(final String input)
    {
        try
        {
            return Material.valueOf(input.trim().toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException notAMaterial)
        {
            return null;
        }
    }

    /**
     * The advice line, generated from the same set the check uses.
     *
     * @return the sentence listing every accepted material
     */
    private String validMaterials()
    {
        return "Valid materials are: " + String.join(", ", kind.allowedNames());
    }

    /**
     * Tells the sender how the command is spelled and what it takes.
     *
     * @param sender
     *            who asked
     */
    private void usage(final CommandSender sender)
    {
        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString()
            + "Command: /wormhole " + kind.command + " <gate> [material]");
        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + validMaterials());
    }

}
