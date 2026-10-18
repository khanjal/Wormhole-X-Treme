package com.wormhole_xtreme.wormhole.utils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Reads a mock player's action bar from whichever one this API build sends to.
 *
 * <p>On Paper's API a Player is an Adventure audience, so {@link ActionBar} never reaches
 * {@code player.spigot()} there. A test reading only Spigot's bar would see silence on the Paper
 * legs and call it silence from the code.
 */
public final class ActionBarLines
{
    /** True on Paper's API, where a Player is an Audience and the bar goes as Adventure's. */
    public static final boolean PAPER = Audience.class.isAssignableFrom(Player.class);

    /** Static use only. */
    private ActionBarLines()
    {
    }

    /**
     * @param player
     *            the mock told
     * @param hotbar
     *            what its {@code spigot()} answers, read on Spigot's API only
     * @return every line sent above its hotbar, in order, colour codes included
     */
    public static List<String> shown(final Player player, final Player.Spigot hotbar)
    {
        final List<String> lines = new ArrayList<>();
        if (PAPER)
        {
            final ArgumentCaptor<Component> said = ArgumentCaptor.forClass(Component.class);
            verify((Audience) player, atLeast(0)).sendActionBar(said.capture());
            said.getAllValues().forEach(line -> lines.add(LegacyComponentSerializer.legacySection().serialize(line)));
        }
        else
        {
            final ArgumentCaptor<BaseComponent> said = ArgumentCaptor.forClass(BaseComponent.class);
            verify(hotbar, atLeast(0)).sendMessage(eq(ChatMessageType.ACTION_BAR), said.capture());
            said.getAllValues().forEach(line -> lines.add(line.toPlainText()));
        }
        return lines;
    }

    /**
     * For a player whose {@code spigot()} is not stubbed, where a Spigot line goes nowhere.
     *
     * @param player
     *            the mock
     * @return how many times it was sent an Adventure action bar or asked for {@code spigot()}
     */
    public static long attempts(final Player player)
    {
        return mockingDetails(player).getInvocations().stream()
            .map(call -> call.getMethod().getName())
            .filter(name -> "spigot".equals(name) || "sendActionBar".equals(name))
            .count();
    }
}
