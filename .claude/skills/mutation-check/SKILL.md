---
name: mutation-check
description: Prove a test in this repository (khanjal/Wormhole-X-Treme) actually guards the code it claims to, by breaking that code on purpose and watching the test fail — with the bundled harness that refuses a dirty baseline, confirms each mutation applied, and restores the file byte for byte. Use this before writing any sentence (commit message, CHANGELOG, PR body, doc comment) that says a test "guards", "catches" or "pins" something; after writing a characterisation test ahead of a refactor; when a coverage or Sonar sweep adds tests; and whenever a test looks like it might pass whether or not the code is right.
---

# Proving a test can fail

A test that cannot fail looks exactly like cover and stops anyone writing the real one. In
this project, reading the test has never been enough to spot one. Mutating the code has. One
coverage session produced four tests that passed against broken code. None was caught by
review, and every one was caught by a mutation.

**Run the mutation before claiming the test guards anything.** Twice the claim was written
first and turned out to be false.

## The shapes that pass vacuously

Check a new test for these before running anything:

- **The fixture value equals the field's default.** Asserting `PRIVATE` on a `RingPair` that
  is `PRIVATE` until something sets it. Asserting `GLOWSTONE` on a ring the fixture built with
  `GLOWSTONE`. Asserting pitch `0` on a `Location`, where pitch is already `0`. Before
  asserting a value, ask what the field holds when nobody sets it, and pick a fixture value
  that differs. Put the contrast in the message: `"not the 33 the record stored"`.
- **Asserting absence rather than presence.** "The iris was not toggled" is also true when
  nothing happened at all. Assert the thing that should have happened.
- **A match too loose to discriminate.** `contains("O:AVeryLongOwn")` holds for any truncation
  limit of 12 or more. Assert the exact boundary.
- **A guarded comparison that never runs.** "Only if both are non-null", when one of them
  always is.
- **A rule with two readings, and the test passes under both.** A quota of 1 refused whether
  it counted the giver or the recipient, because the giver was also at the limit. Build the
  fixture so only one reading passes.
- **Source-scanning guard tests** (walking `src/main/java` for a banned shape). Count the files
  opened and assert a floor. `Files.exists(src/main/java)` also holds for an empty directory
  under the wrong working directory. Then reintroduce the banned shape in its most awkward
  form (nested, wrapped, with a ternary) and confirm the test names the file and line.

## Running a battery

The harness is `.claude/skills/mutation-check/mutate.py`. Write the battery as JSON in the
scratchpad. Use the Write tool, not a heredoc, because heredocs mangle the `\n` in multi-line
finds.

```json
{
  "file": "src/main/java/com/wormhole_xtreme/wormhole/config/ConfigurationYAML.java",
  "tests": "Config*Test",
  "mutations": [
    {"name": "never rename a key", "find": "&& RENAMED.containsKey(key))", "replace": "&& false)"},
    {"name": "rename indented lines too", "find": "(colon > 0) && !Character.isWhitespace(line.charAt(0)) && ", "replace": "(colon > 0) && "}
  ]
}
```

For the tests in `src/mockbukkit/`, add their profile and run the harness under JDK 21:

```json
  "mvn_args": ["-Pmodern-api,mockbukkit", "-Dpaper.api.version=1.21.11-R0.1-SNAPSHOT"],
```

or under JDK 25 for MockBukkit's 26.2 line. Run `mvn clean` first when switching between the two.
Otherwise the baseline is refused as not green, and the cause is the old classes, not the tests:

```json
  "mvn_args": ["-Pmodern-api,mockbukkit", "-Dpaper.api.version=26.2.build.124-stable",
               "-Dmockbukkit.artifact=mockbukkit-v26.2", "-Dmockbukkit.version=4.116.1", "-Dmockbukkit.release=25"],
```

Then run it:

```bash
set -o pipefail; python -u .claude/skills/mutation-check/mutate.py <scratchpad>/battery.json | tee <scratchpad>/battery.log
```

Run anything longer than a couple of mutations with `run_in_background: true`. A foreground
timeout kills the process, and a killed process never reaches its `finally`. The `-u` and
`tee` matter too: stopping a background task throws away output that was only buffered.
Keep the `pipefail`. Without it, Bash reports `tee`'s exit status, which is 0 whatever the
harness returned.

What the harness does for you:

| Guard | Why it exists |
|---|---|
| Refuses (exit 3) unless the target matches `git HEAD` in content, with CRLF normalised | A killed battery once left `BeamCommand.java` mutated. The next battery measured eleven mutations against the corrupted file and restored it *to the corruption*. |
| Reports a find that matches zero or several times as `UNAPPLIED` and skips it | This tree is CRLF. A multi-line regex with a bare `\n` matched nothing, the suite stayed green, and two good tests were nearly called vacuous. |
| Restores the original bytes after every mutation and checks them | `git checkout --` as a revert once threw away 110 lines of uncommitted work along with the mutation. |
| Separates `NO-COMPILE` from `KILLED` | A mutation that does not compile says nothing about the tests. |
| Refuses (exit 4) if the unmutated baseline is not green | Otherwise every mutation reads as killed. |
| Exits non-zero on any refusal or malformed battery (2-4), a restore failure (5), any survivor (1), and any mutation not measured (6) | A refusal that exits 0 looks exactly like a clean battery. So did two batteries whose finds all went `UNAPPLIED`: "0 killed, 0 survived, 2 not measured", exit 0. |

Because of the HEAD guard, **commit before you mutate.** A WIP commit on the feature branch is
fine. Don't edit the target while a battery runs, either. The harness writes its startup copy
back over the file when it finishes, so your edit would be lost.

## Choosing mutations

Mutate the specific behaviour the test's doc comment claims to guard. Random operators are no
use here. Good mutations for this codebase:

- flip or drop the one condition the test is about (`>=` to `>`, `&&` to `||`, `== null` to `!= null`)
- delete the setter or the call whose effect the test asserts (`setAccess`, `setPitch`)
- swap the enum constant or config key for its neighbour (`PermissionType.DAMAGE` to `BUILD`)
- return early from the method under test
- for cross-version code, remove the fallback branch (see the `cross-version-compat` skill)

One test with several mutations beats several tests with one mutation each. The question is
whether *this* test can tell right code from wrong code.

## Reading the result

- **KILLED**: the test guards that behaviour. You may now say so.
- **SURVIVED**: either the test is vacuous for that behaviour, or the mutation is equivalent
  (it changes the text but not the behaviour). Decide which before doing anything. If the test
  is vacuous, fix it using the shapes above and run the battery again until the mutation is
  killed. If the mutation is equivalent, drop it and say so. Don't count it either way.
- **UNAPPLIED / NO-COMPILE / NO-TESTS / BUILD-ERROR**: nothing was measured, and the harness
  exits 6, as it does for a battery with no mutations. Fix the find text, or read the log tail
  printed for a build error, and rerun. Never read one of these as a result.

Report every number: killed, survived, and not measured. A battery where half the mutations
never applied is not "all killed".

## Mutating by hand

For a single quick check without the harness:

1. `cp` the file to the scratchpad first.
2. Apply the mutation, then run `git diff --numstat <file>`. If it prints nothing, the mutation
   did not land, and the suite will tell you nothing.
3. Run the tests: `mvn -o test -Dtest=<Class>`.
4. Revert by copying the backup back. Don't use `git checkout --` or an inverse `sed`.
   `sed -i` in git-bash also rewrites CRLF files as LF.
5. Check the file is back: `cmp <backup> <file>`.
