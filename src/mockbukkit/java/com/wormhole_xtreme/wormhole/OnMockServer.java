package com.wormhole_xtreme.wormhole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A test class that runs the plugin on MockBukkit: tagged so the profile runs it in a JVM of its
 * own, and failed rather than skipped when it reaches a method MockBukkit has not implemented.
 *
 * <p>A tag rather than a name pattern because -Dtest overrides surefire's includes and excludes
 * but not its groups.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("mockbukkit")
@ExtendWith(FailOnUnimplemented.class)
public @interface OnMockServer
{
}
