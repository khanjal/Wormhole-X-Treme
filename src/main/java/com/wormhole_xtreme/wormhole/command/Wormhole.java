package com.wormhole_xtreme.wormhole.command;

import java.util.Arrays;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.logic.StargateHelper;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.PermissionsManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;
// HelpSupport removed

/**
 * The Class Wormhole.
 * 
 * @author alron
 */
public class Wormhole implements CommandExecutor
{

    /**
     * Do activate timeout.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doActivateTimeout(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            try
            {
                final int timeout = Integer.parseInt(args[1]);
                if ((timeout >= 10) && (timeout <= 60))
                {
                    ConfigManager.setTimeoutActivate(timeout);
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "activate_timeout set to: " + ConfigManager.getTimeoutActivate());
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid activate_timeout: " + args[1]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
                    return false;
                }
            }
            catch (final NumberFormatException e)
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid activate_timeout: " + args[1]);
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
                return false;
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current activate_timeout is: " + ConfigManager.getTimeoutActivate());
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid timeout is between 10 and 60 seconds.");
        }
        return true;
    }

    /**
     * Do cooldown.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doCooldown(final CommandSender sender, final String[] args)
    {
        if ((args.length >= 2) && isValidGroupName(args[1]))
        {
            if (args.length == 3)
            {
                try
                {
                    final int timeout = Integer.parseInt(args[2]);
                    if ((timeout >= 15) && (timeout <= 3600))
                    {
                        doCooldownGroup(args[1], true, timeout);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole cooldown time set to: " + args[2]);
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid cooldown time: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid cooldown time: " + args[2]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current cooldown time is: " + doCooldownGroup(args[1], false, 0));
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
            }
        }
        else if ((args.length == 2) && CommandUtilities.isBoolean(args[1]))
        {
            ConfigManager.setUseCooldownEnabled(Boolean.valueOf(args[1].toLowerCase()));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole use cooldowns set to: " + args[1].toLowerCase());
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Command: /wormhole cooldown [false|true|group] <time>");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid groups are 'one', 'two', and 'three'.");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid cooldown times are between 15 and 3600 seconds.");
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole use cooldowns currently enabled: " + ConfigManager.isUseCooldownEnabled());
        }
        return true;
    }

    /**
     * Do cooldown group.
     * 
     * @param groupName
     *            the group name
     * @param set
     *            the set
     * @param timeoutValue
     *            the timeout value
     * @return the int
     */
    private static int doCooldownGroup(final String groupName, final boolean set, final int timeoutValue)
    {
        int group = 0;
        int oldValue = 0;
        if (groupName.equalsIgnoreCase("one"))
        {
            group = 1;
        }
        else if (groupName.equalsIgnoreCase("two"))
        {
            group = 2;
        }
        else if (groupName.equalsIgnoreCase("three"))
        {
            group = 3;
        }
        switch (group)
        {
            case 1 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupOne();
                    ConfigManager.setUseCooldownGroupOne(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupOne();
            case 2 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupTwo();
                    ConfigManager.setUseCooldownGroupTwo(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupTwo();
            case 3 :
                if (set)
                {
                    oldValue = ConfigManager.getUseCooldownGroupThree();
                    ConfigManager.setUseCooldownGroupThree(timeoutValue);
                }
                return set
                    ? oldValue
                    : ConfigManager.getUseCooldownGroupThree();
            default :
                return -1;
        }
    }

    /**
     * Do custom.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doCustom(final CommandSender sender, final String[] args)
    {
        if ((args.length == 2) || (args.length == 3))
        {
            if (args[1].equalsIgnoreCase("-all") && (args.length == 3) && CommandUtilities.isBoolean(args[2]))
            {
                for (final Stargate stargate : StargateManager.getAllGates())
                {
                    setGateCustomAll(stargate, args[2].equalsIgnoreCase("true")
                        ? true
                        : false);
                }
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "All stargates with valid shapes have been set to custom mode: " + args[2]);
                return true;
            }
            else if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (args.length == 3)
                {
                    if (CommandUtilities.isBoolean(args[2]))
                    {
                        if (stargate.getGateShape() != null)
                        {
                            setGateCustomAll(stargate, args[2].equalsIgnoreCase("true")
                                ? true
                                : false);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargate is custom: " + stargate.isGateCustom());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "No gate shape to base custom data off of!");
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Make sure the proper shape file is available!");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid boolean option: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Stargate is custom: " + stargate.isGateCustom());
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid boolean options are: true and false");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole custom [stargate|-all] <boolean>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            return false;
        }

    }

    /**
     * Do iris material.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doIrisMaterial(final CommandSender sender, final String[] args)
    {
        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        Material m = null;
                        try
                        {
                            m = Material.valueOf(args[2].trim().toUpperCase());
                        }
                        catch (final Exception e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Exception on iris material" + e.getMessage());
                        }

                        if ((m != null) && ((m == Material.DIAMOND_BLOCK) || (m == Material.GLASS) || (m == Material.IRON_BLOCK) || (m == Material.BEDROCK) || (m == Material.STONE) || (m == Material.LAPIS_BLOCK)))
                        {
                            stargate.setGateCustomIrisMaterial(m);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " iris material set to: " + stargate.getGateCustomIrisMaterial());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Iris Material: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK, and LAPIS_BLOCK");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " iris material is currently: " + stargate.getGateCustomIrisMaterial());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK, and LAPIS_BLOCK");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole irismaterial [stargate] <material>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK, and LAPIS_BLOCK");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole irismaterial [stargate] <material>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: STONE, DIAMOND_BLOCK, GLASS, IRON_BLOCK, BEDROCK, and LAPIS_BLOCK");
            return false;
        }
    }

    private static boolean doLightMaterial(final CommandSender sender, final String[] args)
    {
        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        Material m = null;
                        try
                        {
                            m = Material.valueOf(args[2].trim().toUpperCase());
                        }
                        catch (final Exception e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Exception on light material" + e.getMessage());
                        }

                        if ((m != null) && ((m == Material.GLOWSTONE) || (m == Material.REDSTONE_ORE)))
                        {
                            stargate.setGateCustomLightMaterial(m);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " light material set to: " + stargate.getGateCustomLightMaterial());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Light Material: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, REDSTONE_ORE");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " light material is currently: " + stargate.getGateCustomLightMaterial());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: GLOWSTONE, GLOWING_REDSTONE_ORE");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole lightmaterial [stargate] <material>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, GLOWING_REDSTONE_ORE");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole lightmaterial [stargate] <material>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: GLOWSTONE, GLOWING_REDSTONE_ORE");
            return false;
        }
    }

    /**
     * Do owner.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doOwner(final CommandSender sender, final String[] args)
    {
        if (args.length >= 2)
        {
            final Stargate s = StargateManager.getStargate(args[1]);
            if (s != null)
            {
                if (args.length == 3)
                {
                    final String newOwnerName = args[2];
                    // Try to resolve a UUID: online player first, then offline player cache
                    Player onlineTarget = Bukkit.getPlayerExact(newOwnerName);
                    if (onlineTarget != null)
                    {
                        s.setGateOwner(onlineTarget.getUniqueId().toString());
                        s.setGateOwnerName(onlineTarget.getName());
                    }
                    else
                    {
                        // getOfflinePlayer by name queries usercache (may return unknown UUID if name never joined)
                        final org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(newOwnerName);
                        if (offline.hasPlayedBefore() || offline.isOnline())
                        {
                            s.setGateOwner(offline.getUniqueId().toString());
                            s.setGateOwnerName(offline.getName() != null ? offline.getName() : newOwnerName);
                        }
                        else
                        {
                            // Player unknown to this server: store name as legacy fallback
                            s.setGateOwner(newOwnerName);
                            s.setGateOwnerName(newOwnerName);
                        }
                    }
                    s.setupGateSign(true);
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Now owned by: " + s.getGateOwnerName());
                }
                else if (args.length == 2)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Gate: " + s.getGateName() + " Owned by: " + s.getGateOwnerName());
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }
        return true;
    }

    /**
     * Do perms.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     */
    private static void doPerms(final CommandSender sender, final String[] args)
    {
        if (CommandUtilities.playerCheck(sender))
        {
            final Player p = (Player) sender;
            PermissionsManager.handlePermissionRequest(p, args);
        }
    }

    /**
     * Do Portal Material.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doPortalMaterial(final CommandSender sender, final String[] args)
    {
        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        Material m = null;
                        try
                        {
                            m = Material.valueOf(args[2].trim().toUpperCase());
                        }
                        catch (final Exception e)
                        {
                            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, "Caught Exception on portal material" + e.getMessage());
                        }

                        if ((m != null) && ((m == Material.LAVA) || (m == Material.WATER) || (m == Material.AIR) || (m == Material.NETHER_PORTAL)))
                        {
                            stargate.setGateCustomPortalMaterial(m);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " portal material set to: " + stargate.getGateCustomPortalMaterial());
                        }
                        else
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Portal Material: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " portal material is currently: " + stargate.getGateCustomPortalMaterial());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole portalmaterial [stargate] <material>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole portalmaterial [stargate] <material>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid materials are: WATER, LAVA, AIR, NETHER_PORTAL");
            return false;
        }
    }

    /**
     * Do redstone.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRedstone(final CommandSender sender, final String[] args)
    {
        if ((args.length == 2) || (args.length == 3))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (args.length == 3)
                {
                    if (CommandUtilities.isBoolean(args[2]))
                    {
                        stargate.setGateRedstonePowered(Boolean.valueOf(args[2].trim().toLowerCase()));
                        if (stargate.isGateRedstonePowered())
                        {
                            stargate.setupRedstone(true);
                        }
                        else
                        {
                            stargate.setupRedstone(false);
                        }
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " is redstone powered: " + stargate.isGateRedstonePowered());
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid boolean option: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " is redstone powered: " + stargate.isGateRedstonePowered());
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid boolean options are: true and false");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole redstone [stargate] <boolean>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid boolean options are: true and false");
            return false;
        }
    }

    /**
     * Do regenerate.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRegenerate(final CommandSender sender, final String[] args)
    {
        if (args.length >= 2)
        {
            final Stargate s = StargateManager.getStargate(args[1]);
            if (s != null)
            {
                if ((s.getGateShape() != null) && StargateHelper.isStargateShape(s.getGateShape().getShapeName()))
                {
                    // Shape format (2D/3D) is determined at load time; no runtime upgrade needed.
                }
                s.toggleDialLeverState(true);
                if ((s.getGateIrisDeactivationCode() != null) && (s.getGateIrisDeactivationCode().length() > 0))
                {
                    s.setupIrisLever(true);
                }
                if (s.isGateRedstonePowered())
                {
                    s.setupRedstone(true);
                }
                s.setupGateSign(true);
                if (s.isGateSignPowered() && s.getGateDialSignBlock() != null)
                {
                    try
                    {
                        final Class<?> dialManagerClass = Class.forName("com.wormhole_xtreme.wormhole.model.StargateDialManager");
                        final java.lang.reflect.Method teleportSignClicked = dialManagerClass.getDeclaredMethod("teleportSignClicked", Stargate.class, boolean.class);
                        teleportSignClicked.setAccessible(true);
                        teleportSignClicked.invoke(null, s, true);
                    }
                    catch (final Throwable ignore) {}
                }
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Regenerating Gate: " + s.getGateName());
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.constructNameInvalid.toString() + "\"" + args[1] + "\"");
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.gateNotSpecified.toString());
            return false;
        }
        return true;
    }

    /**
     * Do restrict.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doRestrict(final CommandSender sender, final String[] args)
    {
        if ((args.length >= 2) && isValidGroupName(args[1]))
        {
            if (args.length == 3)
            {
                try
                {
                    final int gateCount = Integer.parseInt(args[2]);
                    if ((gateCount >= 1) && (gateCount <= 200))
                    {
                        doCooldownGroup(args[1], true, gateCount);
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole build restriction count: " + args[2]);
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Build restriction count: " + args[2]);
                        sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid restriction values are between 1 and 200.");
                    }
                }
                catch (final NumberFormatException e)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid restriction count: " + args[2]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid restriction values are between 1 and 200.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current restriction count is: " + doRestrictionGroup(args[1], false, 0));
                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid restriction values are between 1 and 200.");
            }
        }
        else if ((args.length == 2) && CommandUtilities.isBoolean(args[1]))
        {
            ConfigManager.setBuildRestrictionEnabled(Boolean.valueOf(args[1].toLowerCase()));
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole build count restrictions set to: " + args[1].toLowerCase());
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Build restriction feature has been removed. Use Vault/LuckPerms for permissions.");
        }
        return true;
    }

    /**
     * Do restriction group.
     * 
     * @param groupName
     *            the group name
     * @param set
     *            the set
     * @param gateCount
     *            the gate count
     * @return the int
     */
    private static int doRestrictionGroup(final String groupName, final boolean set, final int gateCount)
    {
        // Build restriction groups removed; feature deprecated.
        return -1;
    }

    /**
     * Do shutdown timeout.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doShutdownTimeout(final CommandSender sender, final String[] args)
    {
        if (args.length == 2)
        {
            try
            {
                final int timeout = Integer.parseInt(args[1]);
                if ((timeout > -1) && (timeout <= 60))
                {
                    ConfigManager.setTimeoutShutdown(timeout);
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "shutdown_timeout set to: " + ConfigManager.getTimeoutShutdown());
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid shutdown_timeout: " + args[1]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
                    return false;
                }
            }
            catch (final NumberFormatException e)
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid shutdown_timeout: " + args[1]);
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
                return false;
            }
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Current shutdown_timeout is: " + ConfigManager.getTimeoutShutdown());
            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid timeout is between 0 and 60 seconds.");
        }
        return true;
    }

    /**
     * Do simple permissions.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    /**
     * Do woosh depth.
     * 
     * @param sender
     *            the sender
     * @param args
     *            the args
     * @return true, if successful
     */
    private static boolean doWooshDepth(final CommandSender sender, final String[] args)
    {
        if ((args.length == 3) || (args.length == 2))
        {
            if (StargateManager.isStargate(args[1]))
            {
                final Stargate stargate = StargateManager.getStargate(args[1]);
                if (stargate.isGateCustom())
                {
                    if (args.length == 3)
                    {
                        try
                        {
                            final int wooshDepth = Integer.parseInt(args[2].trim());
                            if ((wooshDepth >= 0) && (wooshDepth <= 5))
                            {
                                stargate.setGateCustomWooshDepth(wooshDepth);
                                stargate.setGateCustomWooshDepthSquared(wooshDepth * wooshDepth);
                                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " woosh depth set to: " + stargate.getGateCustomWooshDepth());
                            }
                            else
                            {
                                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid woosh depth: " + args[2]);
                                sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                            }
                        }
                        catch (final NumberFormatException e)
                        {
                            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid woosh depth: " + args[2]);
                            sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                        }
                    }
                    else
                    {
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + args[1] + " woosh depth is currently: " + stargate.getGateCustomWooshDepth());
                        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid depth: 0 - 5");
                    }
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Stargate is not in custom mode. Set it with the '/wormhole custom' command");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.targetInvalid.toString());
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole wooshdepth [stargate] <depth>");
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid depth: 0 - 5");
            }
            return true;
        }
        else
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Command: /wormhole wooshdepth [stargate] <depth>");
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid depth: 0 - 5");
            return false;
        }
    }

    /**
     * Checks if is valid group name.
     * 
     * @param groupName
     *            the group name
     * @return true, if is valid group name
     */
    private static boolean isValidGroupName(final String groupName)
    {
        return groupName.equalsIgnoreCase("one") || groupName.equalsIgnoreCase("two") || groupName.equalsIgnoreCase("three");
    }

    /**
     * Sets the gate custom all.
     * 
     * @param stargate
     *            the stargate
     * @param customEnabled
     *            the custom enabled
     */
    private static void setGateCustomAll(final Stargate stargate, final boolean customEnabled)
    {
        if (stargate.getGateShape() != null)
        {
            if (customEnabled)
            {
                stargate.setGateCustom(true);
                if (stargate.getGateCustomIrisMaterial() == null)
                {
                    stargate.setGateCustomIrisMaterial(stargate.getGateShape().getShapeIrisMaterial());
                }
                if (stargate.getGateCustomLightMaterial() == null)
                {
                    stargate.setGateCustomLightMaterial(stargate.getGateShape().getShapeLightMaterial());
                }
                if (stargate.getGateCustomPortalMaterial() == null)
                {
                    stargate.setGateCustomPortalMaterial(stargate.getGateShape().getShapePortalMaterial());
                }
                if (stargate.getGateCustomStructureMaterial() == null)
                {
                    stargate.setGateCustomStructureMaterial(stargate.getGateShape().getShapeStructureMaterial());
                }
                if (stargate.getGateCustomLightTicks() == -1)
                {
                    stargate.setGateCustomLightTicks(stargate.getGateShape().getShapeLightTicks());
                }
                if (stargate.getGateCustomWooshTicks() == -1)
                {
                    stargate.setGateCustomWooshTicks(stargate.getGateShape().getShapeWooshTicks());
                }
                if (stargate.getGateCustomWooshDepth() == -1)
                {
                    stargate.setGateCustomWooshDepth(stargate.getGateShape().getShapeWooshDepth());
                }
                if (stargate.getGateCustomWooshDepthSquared() == -1)
                {
                    stargate.setGateCustomWooshDepthSquared(stargate.getGateShape().getShapeWooshDepthSquared());
                }
            }
            else
            {
                stargate.setGateCustom(false);
            }
        }
        else
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.FINE, false, stargate.getGateName() + " has no valid shape file. Unable to enable custom.");
        }
    }

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        try
        {
            if (CommandUtilities.playerCheck(sender)
                ? WXPermissions.checkWXPermissions((Player) sender, PermissionType.CONFIG)
                : true)
            {
                final String[] a = CommandUtilities.commandEscaper(args);
                if (a.length > 4)
                {
                    return false;
                }
                if (a.length == 0)
                {
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Wormhole admin/config command (use /wormhole <subcommand>)");
                    sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "Valid commands: owner, perms, portalmaterial, irismaterial, lightmaterial, shutdown_timeout, activate_timeout, simple, regenerate, redstone, wooshdepth, cooldown, restrict, custom.");
                    return true;
                }
                if (a[0].equalsIgnoreCase("owner"))
                {
                    return doOwner(sender, a);
                }
                else if (a[0].equalsIgnoreCase("perm") || a[0].equalsIgnoreCase("perms"))
                {
                    doPerms(sender, a);
                }
                else if (a[0].equalsIgnoreCase("portalmaterial"))
                {
                    return doPortalMaterial(sender, a);
                }
                else if (a[0].equalsIgnoreCase("irismaterial"))
                {
                    return doIrisMaterial(sender, a);
                }
                else if (a[0].equalsIgnoreCase("timeout") || a[0].equalsIgnoreCase("shutdown_timeout"))
                {
                    return doShutdownTimeout(sender, a);
                }
                else if (a[0].equalsIgnoreCase("activate_timeout"))
                {
                    return doActivateTimeout(sender, a);
                }
                
                else if (a[0].equalsIgnoreCase("regenerate") || a[0].equalsIgnoreCase("regen"))
                {
                    return doRegenerate(sender, a);
                }
                else if (a[0].equalsIgnoreCase("list"))
                {
                    // Route to existing WXList executor for backwards-compatible listing
                    return new WXList().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("build"))
                {
                    return new Build().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("complete"))
                {
                    return new Complete().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("refresh"))
                {
                    return new Refresh().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("compass"))
                {
                    return new Compass().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("force"))
                {
                    return new Force().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("go"))
                {
                    return new Go().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("idc"))
                {
                    return new WXIDC().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("remove"))
                {
                    return new WXRemove().onCommand(sender, command, label, Arrays.copyOfRange(a, 1, a.length));
                }
                else if (a[0].equalsIgnoreCase("redstone"))
                {
                    return doRedstone(sender, a);
                }
                else if (a[0].equalsIgnoreCase("custom"))
                {
                    return doCustom(sender, a);
                }
                else if (a[0].equalsIgnoreCase("lightmaterial"))
                {
                    return doLightMaterial(sender, a);
                }
                else if (a[0].equalsIgnoreCase("wooshdepth"))
                {
                    return doWooshDepth(sender, a);
                }
                else if (a[0].equalsIgnoreCase("cooldown"))
                {
                    return doCooldown(sender, a);
                }
                else if (a[0].equalsIgnoreCase("restrict"))
                {
                    return doRestrict(sender, a);
                }
                else
                {
                    sender.sendMessage(ConfigManager.MessageStrings.requestInvalid.toString() + ": " + a[0]);
                    sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Valid commands are 'owner', 'perms', 'portalmaterial', 'irismaterial', 'lightmaterial', 'shutdown_timeout', 'activate_timeout', 'simple', 'regenerate', 'redstone', 'wooshdepth', 'cooldown', 'restrict', & 'custom'.");
                }
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            }
            return true;
        }
        catch (final Throwable t)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, "Error executing /wormhole command: " + t.getMessage());
            if (CommandUtilities.playerCheck(sender))
            {
                ((Player) sender).sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            else
            {
                sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "An internal error occurred. Check server logs.");
            }
            return true;
        }
    }
}
