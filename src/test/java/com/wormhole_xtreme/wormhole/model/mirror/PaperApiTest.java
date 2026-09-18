package com.wormhole_xtreme.wormhole.model.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The Paper methods mirrors reach reflectively are really there, under the names and types looked up.
 *
 * <p>Only runs in the CI job built against paper-api. A misspelt lookup fails quietly on a real
 * server, so nothing else would notice.
 */
@EnabledIfSystemProperty(named = "server.api", matches = "paper")
class PaperApiTest
{
    @AfterEach
    void tearDown()
    {
        MirrorFog.sendDistanceWith(null);
    }

    @Test
    void theFogFindsPapersSendViewDistance() throws NoSuchMethodException
    {
        MirrorFog.sendDistanceWith(null);

        assertTrue(MirrorFog.available(), "mirror-fog-at-depth would do nothing on Paper");
        assertEquals(int.class, Player.class.getMethod("getSendViewDistance").getReturnType());
    }

    /** Not on 1.20, which has no such method and where a window rightly goes without. */
    @Test
    @DisabledIfSystemProperty(named = "paper.api.version", matches = "1\\.20-.*")
    void aWindowFindsSendBlockUpdate()
    {
        assertTrue(MirrorPackets.available(), "a window's banner would stay in front of its view on Paper");
    }
}
