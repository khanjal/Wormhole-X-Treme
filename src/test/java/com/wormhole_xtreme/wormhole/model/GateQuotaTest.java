package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * How many gates one player may own, {@code max-gates-per-player} and {@code wormhole.gates.max.<n>} (#633).
 */
class GateQuotaTest
{
    private final Set<PermissionAttachmentInfo> held = new HashSet<>();
    private boolean unlimited;

    @BeforeEach
    void setUp() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.install(mock(WormholeXTreme.class));
        held.clear();
        unlimited = false;
    }

    @AfterEach
    void tearDown() throws Exception
    {
        ConfigTestSupport.clear();
        PluginTestSupport.forgetAllGates();
        PluginTestSupport.remove();
    }

    private Player player(final UUID id)
    {
        final Player p = mock(Player.class);
        when(p.getName()).thenReturn("builder");
        when(p.getUniqueId()).thenReturn(id);
        when(p.hasPermission(GateQuota.UNLIMITED)).thenAnswer(call -> unlimited);
        when(p.getEffectivePermissions()).thenAnswer(call -> held);
        return p;
    }

    private void grant(final String node, final boolean value)
    {
        final PermissionAttachmentInfo info = mock(PermissionAttachmentInfo.class);
        when(info.getPermission()).thenReturn(node);
        when(info.getValue()).thenReturn(value);
        held.add(info);
    }

    /** Registers a gate owned by this id. */
    private static void own(final UUID id, final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateOwner(id.toString());
        StargateManager.registerStargate(gate);
    }

    /** With nothing set, nobody is limited: what an upgrading server gets. */
    @Test
    void thereIsNoLimitUnlessOneIsSet()
    {
        final UUID id = UUID.randomUUID();
        own(id, "a");
        own(id, "b");

        assertEquals(0, GateQuota.limitFor(player(id)));
        assertNull(GateQuota.refusal(player(id)));
        assertNull(GateQuota.standing(player(id)), "nothing to say of a limit there is not");
    }

    /** The setting is the limit for everyone, and a player is refused once they own that many. */
    @Test
    void theSettingLimitsEveryone()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 2);
        final UUID id = UUID.randomUUID();
        own(id, "a");

        assertNull(GateQuota.refusal(player(id)), "one of two");
        own(id, "b");
        assertNotNull(GateQuota.refusal(player(id)), "two of two");
    }

    /** Only the player's own gates count, and the refusal says the limit. */
    @Test
    void otherPeoplesGatesDoNotCount()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 2);
        final UUID id = UUID.randomUUID();
        own(UUID.randomUUID(), "theirs1");
        own(UUID.randomUUID(), "theirs2");
        own(UUID.randomUUID(), "theirs3");
        own(id, "mine");

        assertEquals(1, GateQuota.ownedBy(player(id)));
        assertNull(GateQuota.refusal(player(id)));

        own(id, "mine2");
        assertTrue(ChatText.plain(GateQuota.refusal(player(id))).contains("limit"), GateQuota.refusal(player(id)));
        assertTrue(ChatText.plain(GateQuota.refusal(player(id))).contains("2 gates"), GateQuota.refusal(player(id)));
    }

    /** One gate reads as one, more as gates. */
    @Test
    void theRefusalSaysGateOrGates()
    {
        final UUID id = UUID.randomUUID();
        own(id, "a");
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 1);
        assertTrue(ChatText.plain(GateQuota.refusal(player(id))).contains("1 gate,"), GateQuota.refusal(player(id)));

        own(id, "b");
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 2);
        assertTrue(ChatText.plain(GateQuota.refusal(player(id))).contains("2 gates,"), GateQuota.refusal(player(id)));
    }

    /** A group's node replaces the setting, up or down, and the highest one wins. */
    @Test
    void aGroupNodeReplacesTheSettingAndTheHighestWins()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 3);
        final Player p = player(UUID.randomUUID());

        grant("wormhole.gates.max.5", true);
        assertEquals(5, GateQuota.limitFor(p), "more than the setting");
        grant("wormhole.gates.max.12", true);
        grant("wormhole.gates.max.8", true);
        assertEquals(12, GateQuota.limitFor(p), "the highest of three");

        held.clear();
        grant("wormhole.gates.max.1", true);
        assertEquals(1, GateQuota.limitFor(p), "fewer than the setting");
    }

    /** A node that is denied, is not a number, or names no gates is not a limit. */
    @Test
    void nodesThatAreNotLimitsAreIgnored()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 3);
        final Player p = player(UUID.randomUUID());

        grant("wormhole.gates.max.9", false);
        grant("wormhole.gates.max.lots", true);
        grant("wormhole.gates.max.0", true);
        grant("wormhole.gates.max.-4", true);
        grant("wormhole.gates.max.", true);
        grant("wormhole.build", true);

        assertEquals(3, GateQuota.limitFor(p), "the setting stands");
    }

    /** The unlimited node lifts every limit, the setting's and a group's. */
    @Test
    void theUnlimitedNodeLiftsTheLimit()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 1);
        final UUID id = UUID.randomUUID();
        own(id, "a");
        own(id, "b");
        grant("wormhole.gates.max.1", true);
        final Player p = player(id);

        assertNotNull(GateQuota.refusal(p));
        unlimited = true;

        assertEquals(0, GateQuota.limitFor(p));
        assertNull(GateQuota.refusal(p));
    }

    /** The gate list's line says how many of how many. */
    @Test
    void theStandingSaysHowManyOfHowMany()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 4);
        final UUID id = UUID.randomUUID();
        own(id, "a");
        own(id, "b");

        assertEquals("Gates owned: 2. Your limit: 4.", ChatText.plain(GateQuota.standing(player(id))));
    }

    /** A gate taken down stops counting. */
    @Test
    void aRemovedGateIsNotOwned()
    {
        ConfigTestSupport.set(ConfigKeys.MAX_GATES_PER_PLAYER, 1);
        final UUID id = UUID.randomUUID();
        own(id, "a");
        assertNotNull(GateQuota.refusal(player(id)));

        StargateManager.removeStargate(StargateManager.getStargate("a"));

        assertNull(GateQuota.refusal(player(id)));
    }

    /** A permissions plugin's capitals do not hide a limit. */
    @Test
    void theNodeIsRecognisedInAnyCase()
    {
        final Player p = player(UUID.randomUUID());
        grant("Wormhole.Gates.MAX.6", true);

        assertEquals(6, GateQuota.limitFor(p));
    }
}
