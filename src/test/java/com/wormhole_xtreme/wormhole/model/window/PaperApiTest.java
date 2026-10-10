package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorPackets;

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
        ViewFog.sendDistanceWith(null);
    }

    @Test
    void theFogFindsPapersSendViewDistance() throws NoSuchMethodException
    {
        ViewFog.sendDistanceWith(null);

        assertTrue(ViewFog.available(), "mirror-fog-at-depth would do nothing on Paper");
        assertEquals(int.class, Player.class.getMethod("getSendViewDistance").getReturnType());
    }

    /** Paper loads the chunks held for a view's creatures off the main thread, asking for each held chunk not to be generated. */
    @Test
    void theChunkHoldsFindPapersAsyncChunkLoad() throws NoSuchMethodException
    {
        assertTrue(FarChunkHolds.loadsAsync(), "held chunks would load on the main thread on Paper");
        assertEquals(CompletableFuture.class,
            World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class).getReturnType());
    }

    /** Where Paper has a Mannequin (1.21.9 on), a far player's stand-in finds Paper's own names for its skin, label and pose. */
    @Test
    void aPlayersMannequinFindsPapersNames()
    {
        PlayerFigures.mannequinFromServer();
        final PlayerFigures.Mannequins found = PlayerFigures.mannequins();
        if (found != null)
        {
            assertNotNull(found.profile, "setProfile(ResolvableProfile)");
            assertNotNull(found.resolve, "ResolvableProfile.resolvableProfile(PlayerProfile)");
            assertNotNull(found.description, "setDescription(Component)");
            assertNotNull(found.fixedPose, "Entity.setPose(Pose, boolean)");
        }
    }

    /** Not on 1.20, which has no such method and where a window rightly goes without. */
    @Test
    @DisabledIfSystemProperty(named = "paper.api.version", matches = "1\\.20-.*")
    void aWindowFindsSendBlockUpdate()
    {
        assertTrue(MirrorPackets.available(), "a window's banner would stay in front of its view on Paper");
    }
}
