package com.wormhole_xtreme.wormhole.command.handlers;

import java.io.File;
import java.io.IOException;

import org.bukkit.command.CommandSender;

import com.wormhole_xtreme.wormhole.command.CommandHandlerUtils;
import com.wormhole_xtreme.wormhole.command.SubCommand;
import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.DataLayout;
import com.wormhole_xtreme.wormhole.utils.LangExport;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * {@code /wormhole lang export [code]}: a starter lang file in the plugin folder, never over one there (#26).
 *
 * <p>The code is read as {@code server-language} is, so {@code pt-BR} is {@code pt_br}, and one that is
 * not a code creates nothing, not even the folder.
 */
public class LangCommand implements SubCommand
{
    /** The one verb. */
    public static final String EXPORT = "export";

    private static final String RELOAD = "/wormhole config " + ConfigCommand.RELOAD;

    // Bukkit reads the boolean as "handled"; every path here has handled it.
    @SuppressWarnings("java:S3516")
    @Override
    public boolean execute(final CommandSender sender, final String[] args)
    {
        if (CommandHandlerUtils.lacksConfigPermission(sender))
        {
            return true;
        }
        if ((args.length < 2) || (args.length > 3) || !EXPORT.equalsIgnoreCase(args[1]))
        {
            sender.sendMessage(ChatText.info(Messages.get(sender, "lang.usage",
                ChatText.usageBody("/wormhole lang export [code]"))));
            return true;
        }
        final String code = (args.length == 3) ? ConfigManager.parseServerLanguage(args[2]) : Messages.DEFAULT_LANGUAGE;
        if ((code != null) && LangExport.isReserved(code))
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "lang.reserved-code", ChatText.command(args[2]))));
            return true;
        }
        if (code == null)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "lang.bad-code", ChatText.command(args[2]),
                ChatText.value("de"), ChatText.value("pt_br"))));
            return true;
        }
        final File directory = DataLayout.lang();
        final String file = new File(directory, code + ".yml").getPath();
        try
        {
            switch (LangExport.export(directory, code))
            {
                case OVERLAY -> sender.sendMessage(ChatText.info(Messages.get(sender, "lang.written-overlay",
                    ChatText.name(file), ChatText.command(RELOAD))));
                case TRANSLATION -> sender.sendMessage(ChatText.info(Messages.get(sender, "lang.written-translation",
                    ChatText.name(file), ChatText.command(RELOAD))));
                case EXISTS -> sender.sendMessage(ChatText.error(Messages.get(sender, "lang.exists", ChatText.name(file))));
            }
        }
        catch (final IOException e)
        {
            sender.sendMessage(ChatText.error(Messages.get(sender, "lang.failed", ChatText.name(file),
                String.valueOf(e.getMessage()))));
        }
        return true;
    }
}
