#!/usr/bin/env python3
"""Mutation battery for Wormhole X-Treme: break production code on purpose, run the tests
that claim to guard it, and report which mutations the tests noticed.

    python -u .claude/skills/mutation-check/mutate.py BATTERY.json | tee <log>

BATTERY.json:

    {
      "file": "src/main/java/com/wormhole_xtreme/wormhole/model/Foo.java",
      "tests": "FooTest,BarTest",
      "mvn_args": ["-Pmodern-api,mockbukkit", "-Dpaper.api.version=1.21.11-R0.1-SNAPSHOT"],
      "mutations": [
        {"name": "drop the null check", "find": "if (gate == null)\\n        {\\n            return;\\n        }\\n", "replace": ""},
        {"name": "off by one", "find": "count >= limit", "replace": "count > limit"}
      ]
    }

"mvn_args" is optional: extra Maven arguments, for tests that only run under a profile.

"find" is matched against the file with CRLF normalised to LF, so write multi-line finds
with plain \\n. Each must match exactly once, or the mutation is reported as UNAPPLIED and
nothing runs for it.

Guarantees, each learned the hard way:
  - refuses to start unless the target's content matches git HEAD, so a battery killed
    mid-run cannot leave a mutated file that the next battery measures as its baseline;
  - checks each mutation actually changed the file before running anything;
  - restores the original bytes after every mutation and proves they match;
  - separates "tests failed" (KILLED) from "did not compile" (NO-COMPILE), which is not a kill;
  - exits non-zero unless every mutation was measured and killed, so neither a refusal nor
    a battery that measured nothing can be mistaken for a clean one.

Exit codes: 0 all killed; 1 a mutation survived; 2 usage, or no mvn; 3 target differs from HEAD;
4 baseline not green; 5 restore failed; 6 nothing survived, but a mutation was not measured
(UNAPPLIED, NO-COMPILE, NO-TESTS, BUILD-ERROR) or the battery had no mutations. A restore
failure (5) outranks the rest, and a survivor (1) outranks 6.
"""

import json
import re
import shutil
import subprocess
import sys
from pathlib import Path


def repo_root() -> Path:
    out = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True)
    return Path(out.stdout.strip())


def head_text(root: Path, rel: str) -> str:
    out = subprocess.run(["git", "show", f"HEAD:{rel}"], cwd=root, capture_output=True, check=True)
    return out.stdout.decode("utf-8").replace("\r\n", "\n")


def mvn() -> str:
    exe = shutil.which("mvn") or shutil.which("mvn.cmd")
    if not exe:
        print("REFUSING: mvn is not on PATH.")
        sys.exit(2)
    return exe


SUMMARY = re.compile(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$", re.M)


def run_tests(root: Path, tests: str, extra: list):
    cmd = [mvn(), "-o", "-B", "test", f"-Dtest={tests}", "-Dsurefire.failIfNoSpecifiedTests=false", *extra]
    proc = subprocess.run(cmd, cwd=root, capture_output=True, text=True, encoding="utf-8", errors="replace")
    log = proc.stdout + proc.stderr
    # A profile can add a second surefire execution, and whichever one ran no tests prints an
    # empty summary; read the last one that ran something.
    totals = [t for t in SUMMARY.findall(log) if int(t[0]) > 0]
    if "COMPILATION ERROR" in log:
        return "NO-COMPILE", log
    if not totals:
        return "NO-TESTS", log
    run, fail, err, _ = (int(x) for x in totals[-1])
    if proc.returncode != 0 and (fail or err):
        return "KILLED", log
    if proc.returncode == 0:
        return "SURVIVED", log
    return "BUILD-ERROR", log


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    battery = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    root = repo_root()
    rel = battery["file"].replace("\\", "/")
    target = root / rel
    original = target.read_bytes()
    text = original.decode("utf-8")
    crlf = "\r\n" in text
    lf_text = text.replace("\r\n", "\n")

    if lf_text != head_text(root, rel):
        print(f"REFUSING: {rel} differs in content from git HEAD. Commit your work first "
              f"(a WIP commit is fine), or restore the file if a previous battery was killed.")
        return 3

    extra = battery.get("mvn_args", [])
    baseline, log = run_tests(root, battery["tests"], extra)
    if baseline != "SURVIVED":
        print(f"REFUSING: the unmutated baseline is {baseline}, not green. Fix that first.")
        print(log[-3000:])
        return 4
    ran = [t for t in SUMMARY.findall(log) if int(t[0]) > 0][-1][0]
    print(f"baseline green: {ran} tests in {battery['tests']}", flush=True)

    results = []
    try:
        for m in battery.get("mutations", []):
            name = m["name"]
            count = lf_text.count(m["find"])
            if count != 1:
                print(f"UNAPPLIED  {name}: find matched {count} times, expected 1", flush=True)
                results.append((name, "UNAPPLIED"))
                continue
            mutated = lf_text.replace(m["find"], m["replace"])
            if mutated == lf_text:
                print(f"UNAPPLIED  {name}: replacement left the file unchanged", flush=True)
                results.append((name, "UNAPPLIED"))
                continue
            target.write_bytes((mutated.replace("\n", "\r\n") if crlf else mutated).encode("utf-8"))
            verdict, log = run_tests(root, battery["tests"], extra)
            target.write_bytes(original)
            print(f"{verdict:<10} {name}", flush=True)
            if verdict in ("NO-TESTS", "BUILD-ERROR"):
                print(log[-2000:], flush=True)
            results.append((name, verdict))
    finally:
        target.write_bytes(original)
        if target.read_bytes() != original:
            print(f"RESTORE FAILED: {rel} does not match its original bytes. Check it by hand.")
            return 5
        print(f"restored {rel}", flush=True)

    survivors = [n for n, v in results if v == "SURVIVED"]
    unmeasured = [n for n, v in results if v not in ("KILLED", "SURVIVED")]
    print(f"\n{sum(v == 'KILLED' for _, v in results)} killed, {len(survivors)} survived, "
          f"{len(unmeasured)} not measured")
    for n in survivors:
        print(f"  survived: {n}")
    for n in unmeasured:
        print(f"  not measured: {n}")
    if survivors:
        return 1
    return 6 if unmeasured or not results else 0


if __name__ == "__main__":
    sys.exit(main())
