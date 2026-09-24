package com.wormhole_xtreme.wormhole;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * Fails a test that reaches a method MockBukkit has not implemented, which it would otherwise
 * report as skipped: a journey cut short that way shows green in CI.
 */
final class FailOnUnimplemented implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler
{
    @Override
    public void handleTestExecutionException(final ExtensionContext context, final Throwable thrown) throws Throwable
    {
        throw failure(thrown);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(final ExtensionContext context, final Throwable thrown)
        throws Throwable
    {
        throw failure(thrown);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(final ExtensionContext context, final Throwable thrown)
        throws Throwable
    {
        throw failure(thrown);
    }

    private static Throwable failure(final Throwable thrown)
    {
        if (thrown instanceof UnimplementedOperationException)
        {
            return new AssertionError("MockBukkit has not implemented a method this reached; stand one in"
                + " MockServerSupport", thrown);
        }
        return thrown;
    }
}
