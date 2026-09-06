package com.wormhole_xtreme.wormhole.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions;
import com.wormhole_xtreme.wormhole.permissions.WXPermissions.PermissionType;

/**
 * The Class WXIDC.
 * 
 * @author alron
 */
public class WXIDC implements CommandExecutor
{

    /* (non-Javadoc)
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args)
    {
        return CommandUtilities.runCommandSafe(sender, new java.util.concurrent.Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                final String[] a = CommandUtilities.commandEscaper(args);
                if (a.length < 1)
                {
                    return false;
                }
                handleIdc(sender, a);
                return true;
            }
        });
    }

    /** Reports or changes one gate's iris deactivation code. */
    private static void handleIdc(final CommandSender sender, final String[] a)
    {
        // Asked for once and checked, rather than isStargate followed by getStargate: the
        // registry is a concurrent map, so between two lookups the answer can change.
        final Stargate s = StargateManager.getStargate(a[0]);
        if (s == null)
        {
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Invalid Stargate: " + a[0]);
            return;
        }
        if (s.isGateSignPowered() || (s.getGateIrisLeverBlock() == null))
        {
            // Nothing to unlock, so a code set here would never be asked for.
            sender.sendMessage(ConfigManager.MessageStrings.errorHeader.toString() + "Iris not available for sign powered stargates or gates without an iris activation block.");
            return;
        }
        if (!mayChangeCode(sender, s))
        {
            sender.sendMessage(ConfigManager.MessageStrings.permissionNo.toString());
            return;
        }
        if (a.length >= 2)
        {
            setCode(s, a[1]);
        }
        // Always shown, whether or not anything was changed.
        sender.sendMessage(ConfigManager.MessageStrings.normalHeader.toString() + "IDC for gate: " + s.getGateName() + " is:" + s.getGateIrisDeactivationCode());
    }

    /**
     * Whether this sender may change the code.
     *
     * <p>The gate's own owner, or an admin holding the config node. The console is neither
     * and may do it anyway, having no player to be an owner of anything.
     */
    private static boolean mayChangeCode(final CommandSender sender, final Stargate s)
    {
        if (!CommandUtilities.playerCheck(sender))
        {
            return true;
        }
        final Player player = (Player) sender;
        return WXPermissions.checkWXPermissions(player, PermissionType.CONFIG) || s.isOwner(player);
    }

    /**
     * Sets the code, or clears it when asked to.
     *
     * <p>{@code -clear} is an instruction rather than a code: storing it literally would
     * leave a gate whose iris opens for anyone who typed the word.
     */
    private static void setCode(final Stargate s, final String value)
    {
        if ("-clear".equals(value))
        {
            StargateManager.removeBlockIndex(s.getGateIrisLeverBlock());
            s.setIrisDeactivationCode("");
            return;
        }
        s.setIrisDeactivationCode(value);
        // The lever has to be findable again, since a code makes it meaningful.
        StargateManager.addBlockIndex(s.getGateIrisLeverBlock(), s);
    }

}
