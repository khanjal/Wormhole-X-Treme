package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * What a player is told once their gate design checks out: say what to type next, in the
 * shared palette, and carry the sign's own name into the command when the sign has one.
 */
class ValidDesignMessageTest
{
    private static List<String> announced(final Stargate gate)
    {
        final Player player = mock(Player.class);
        GateInteractionHandler.announceValidDesign(player, gate);
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(player, times(2)).sendMessage(said.capture());
        return said.getAllValues();
    }

    @Test
    void aButtonGateIsToldToCompleteItWithAName()
    {
        final List<String> lines = announced(new Stargate());

        assertEquals(":: Valid stargate design.", ChatText.plain(lines.get(0)));
        assertEquals(":: Type /wormhole complete <name> [idc=IDC] [net=NET] to finish it.",
            ChatText.plain(lines.get(1)));
        assertTrue(lines.get(1).contains(ChatText.command("/wormhole complete")), "the command is white");
        assertTrue(lines.get(1).contains(ChatText.name("<name>")), "the name to choose is aqua");
    }

    @Test
    void aSignGateWithNoNameYetIsToldTheSameWay()
    {
        final Stargate gate = new Stargate();
        gate.setGateSignPowered(true);
        gate.setGateName("");

        final List<String> lines = announced(gate);

        assertEquals(":: Valid sign nav stargate design.", ChatText.plain(lines.get(0)));
        assertTrue(ChatText.plain(lines.get(1)).contains("/wormhole complete <name> [idc=IDC]"), lines.get(1));
    }

    @Test
    void aSignGateWithANameHasItFilledIntoTheCommand()
    {
        final Stargate gate = new Stargate();
        gate.setGateSignPowered(true);
        gate.setGateName("Abydos");

        final List<String> lines = announced(gate);

        assertEquals(":: Type /wormhole complete Abydos [idc=IDC] [net=NET] to finish it.",
            ChatText.plain(lines.get(1)));
        assertTrue(lines.get(1).contains(ChatText.name("Abydos")), "the sign's name is aqua like any gate name");
    }
}
