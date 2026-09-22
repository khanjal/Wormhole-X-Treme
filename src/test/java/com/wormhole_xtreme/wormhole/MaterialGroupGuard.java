package com.wormhole_xtreme.wormhole;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;

/**
 * Puts {@link MaterialGroupRegistry} back the way each test class found it.
 *
 * <p>The registry is one static, and loading a palette is often the whole point of a test, so
 * a class that loads one and walks away hands it to whichever class runs next. Surefire's
 * class order follows the file system and changes from one CI runner to the next: {@code
 * UnlitChevronTest} left the shipped lamp-chevron palette behind, and {@code GateDetectionTest}
 * failed only on the runs where it happened to come straight after.
 *
 * <p>Unlike {@link GateStateGuard} this restores without failing, because a class holding a
 * palette at the end is not a mistake; only passing it on is.
 */
public final class MaterialGroupGuard implements BeforeAllCallback, AfterAllCallback
{
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(MaterialGroupGuard.class);

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception
    {
        context.getStore(NAMESPACE).put("state", state().get());
    }

    @Override
    public void afterAll(final ExtensionContext context) throws Exception
    {
        state().set(context.getStore(NAMESPACE).get("state"));
    }

    private static AtomicReference<Object> state() throws ReflectiveOperationException
    {
        return PrivateStatics.of(MaterialGroupRegistry.class, "STATE");
    }
}
