package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;

/**
 * {@code loadShapes()}'s own first-load rule -- an existing entry under the same name is kept,
 * not replaced -- is exactly wrong for reloading a shape someone is actively editing: it would
 * mean every reload after the first silently does nothing. {@link StargateShapeRegistry#replaceIfValid}
 * is the decision a reload actually needs, tested here against plain lines rather than a real
 * file on disk, the same split {@link ShapeFileValidator} itself is built around.
 */
class StargateShapeRegistryReloadTest
{
    private static final String[] VALID_SHAPE = {
        "Name=ReloadableTest",
        "Version=2",
        "GateShape=",
        "",
        "Layer#1=",
        "[S][S][S]",
        "[S:A][P][S]",
        "[S][S:EP][S]",
        "",
        "REDSTONE_ACTIVATED=FALSE",
    };

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
        StargateShapeRegistry.getStargateShapes().remove("ReloadableTest");
    }

    @AfterEach
    void tearDown()
    {
        StargateShapeRegistry.getStargateShapes().remove("ReloadableTest");
    }

    @Test
    void aValidShapeIsAddedToTheRegistry()
    {
        final ShapeFileValidator.Result result = StargateShapeRegistry.replaceIfValid(VALID_SHAPE);

        assertTrue(result.isValid());
        assertNotNull(StargateShapeRegistry.getStargateShape("ReloadableTest"));
    }

    @Test
    void reloadingAgainReplacesTheEarlierInstanceRatherThanKeepingIt()
    {
        // The behaviour a reload command exists for: unlike loadShapes()'s first-load rule,
        // an existing entry under the same name must not survive a second, different reload.
        StargateShapeRegistry.replaceIfValid(VALID_SHAPE);
        final StargateShape first = StargateShapeRegistry.getStargateShape("ReloadableTest");

        final String[] changed = VALID_SHAPE.clone();
        changed[6] = "[S:A][P][S:L#1]"; // still valid, but a visibly different shape
        StargateShapeRegistry.replaceIfValid(changed);
        final StargateShape second = StargateShapeRegistry.getStargateShape("ReloadableTest");

        assertNotSame(first, second, "reload must replace the old instance, not keep serving it");
    }

    @Test
    void anInvalidReloadLeavesTheExistingEntryAloneRatherThanRemovingIt()
    {
        StargateShapeRegistry.replaceIfValid(VALID_SHAPE);
        final StargateShape before = StargateShapeRegistry.getStargateShape("ReloadableTest");

        final String[] broken = VALID_SHAPE.clone();
        broken[7] = "[S][S:EP]"; // one cell short
        final ShapeFileValidator.Result result = StargateShapeRegistry.replaceIfValid(broken);

        assertFalse(result.isValid());
        assertSame(before, StargateShapeRegistry.getStargateShape("ReloadableTest"),
            "a broken edit must not tear down the last good version while it's being fixed");
    }

    @Test
    void aFileThatDoesNotExistReportsAProblemInsteadOfThrowing()
    {
        final ShapeFileValidator.Result result = StargateShapeRegistry.reloadShapeFile("ThisShapeDoesNotExist.shape");

        assertFalse(result.isValid());
        assertFalse(result.getProblems().isEmpty());
    }
}
