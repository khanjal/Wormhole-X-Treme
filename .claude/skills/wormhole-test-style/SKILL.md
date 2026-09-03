---
name: wormhole-test-style
description: How to write a test that fits this repository's (khanjal/Wormhole-X-Treme) established JUnit 5 + Mockito conventions — testable seams instead of a live server, sentence-length test names, doc comments that explain what real bug the test guards against, and the Mockito gotchas this project has already been bitten by. Use this whenever writing a new test file, adding a test method to an existing one, or deciding how to make a piece of logic testable in this codebase.
---

# Writing a test in Wormhole X-Treme's style

This project's test suite (450+ tests) runs with no live Minecraft server, entirely through
`mvn test`. That is only possible because the code is deliberately architected for it, and a
new test should extend that architecture rather than work around it.

## Test decision logic as pure functions, not through the whole system

When a fix needs verifying and the natural place to check it is buried inside a method that
needs a live `World`, a real player connection, or a running game loop, look first for whether
the actual **decision** can be pulled out as its own small `static` method that takes plain
values (ints, a `Location`, a `Material`) and returns a plain value. This project has done that
repeatedly on purpose:

- `RegenerateCommand.exitMoved(Location before, Location after)` — a comparison, pulled out of
  a method that otherwise needs a live `StargateManager` full of gates, so the comparison
  itself could be pinned with six fast, server-free tests.
- Every ring animation frame calculation lives in `RingAnimator` as static methods over plain
  ints, entirely separate from `RingTransit`, which is the (deliberately thin, deliberately
  undertested) part that actually touches a live world.

If a fix is hard to test, that is often a sign the logic and the I/O are tangled together, and
extracting the decision is usually less work than building a fake environment to test it in
place.

## When a mock is genuinely needed

`org.bukkit.World`, `Player`, and `Block` are interfaces, so Mockito can stand in for them.
This project's convention: stub **only what the code under test actually calls**, not a
speculative full setup — and when in doubt, stub more rather than less, because of a real
incident:

**A bare `mock(Player.class)` returns `null` for any method you didn't stub — including
`getUniqueId()`.** That was invisible for a long time because nothing needed it, until a fix
started keying a map on the player's UUID and every test using that mock threw
`NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null`. A real Bukkit
`Player` always has a UUID; the mock simply hadn't been told to have one. When a test's mock
starts throwing an NPE from inside plugin code after an unrelated change, check whether the
mock is missing a stub for something the new code path calls, rather than assuming the
production code is wrong — a mock that returns `null` for everything unstubbed is a much more
likely explanation than a live server suddenly handing back a null UUID.

**`Location` can be constructed with a `null` `World`** when a test only cares about
coordinates, not the world itself — see `RegenerateCommandTest`'s `at(world, x, y, z)` helper,
or `RingSounds.centre()`'s own use of a `null`-world `Location` in production code for the same
reason. Don't reach for a full `World` mock if the logic under test never calls anything on it.

## Name the test as a sentence describing the guarantee, not the method under test

Not `testRefusal1` or `exitMovedReturnsTrue`. This project's actual test names read as
complete claims:

- `theSamePositionInTheSameWorldIsNotAMove`
- `holdingForwardAgainstTheExitIsRefusedOnceNotEveryTick`
- `aDeeperCeilingRingStillLightsTowardsItsPad`

A reader scanning a failure list should understand what broke from the name alone, before
reading the body.

## Explain what the test is actually guarding against

Every test class in this project carries a class-level doc comment, and often individual test
methods do too, explaining *why the test exists* — frequently naming the actual historical bug
or the actual failure mode, not just describing the code being tested:

```java
/**
 * Holding forward against a locked gate no longer spams chat.
 *
 * <p>Cancelling a move event returns the player to event.getFrom() -- the exact spot they
 * tried to leave -- so someone holding a movement key generates a fresh event every tick
 * with an identical from/to pair. Before this was fixed, every one of those sent its own
 * chat line.
 */
```

This is not decoration. A future change that breaks this test should be understood by its
author as "you just reintroduced the chat-spam bug," not as "some assertion failed for reasons
unclear." Write the comment as if explaining to someone who will only ever see the test fail,
never this conversation.

## Put the reasoning in the assertion message, not just the assertion

`assertTrue(pitch > previous, "ring " + index + " should be higher than the last")` tells you
what was expected. This project usually goes further and explains *why that matters* right in
the failure message when it isn't obvious from the test name alone — e.g. a comment or message
noting that a wrong ordering here would mean "the animation loses the note it starts on."
Someone reading a red test in five years should not need to open this conversation, or even the
git blame, to understand what a failure means.

## Add the test as part of the fix, not after

If a fix is worth making, it is worth a test that would have failed before the fix and passes
after — write it that way round when practical (confirm it actually fails against the old code
first), rather than writing a test that only exercises the happy path the fix already produces.
Several real, additional bugs in this project's history were only caught because writing the
test surfaced a second issue the fix alone hadn't addressed — the test is a check on the fix,
not a formality performed after the fix is already trusted.
