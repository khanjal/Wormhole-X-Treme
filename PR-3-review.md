PR #3 Review: Upgrade/spigot-1.20.4

Overview
- Branch: upgrade/spigot-1.20.4
- Summary: Large compatibility and modernization PR. Key goals were switching compile target to Spigot, updating CI, removing Paper-specific APIs, and resolving tests. This review focuses on runtime/memory efficiency, correctness, build hygiene, and maintainability.

High-priority issues (actionable)
- Replace global entity scan in `WormholeXTreme.java` (runTaskTimer) that iterates `world.getEntities()` every 5 ticks. Recommendation: query per-active-gate nearby entities or use event-driven detection; reduce frequency to configurable interval (e.g., 20 ticks) during interim.
- Reduce scheduling/allocation churn for mount reattach retries in `WormholeXTremePlayerListener` and `WormholeXTremeVehicleListener`. Consider a small bounded retry queue + single worker or reuse Runnables.
- Replace `Collections.synchronizedSet(new HashSet<>())` with `ConcurrentHashMap.newKeySet()` for `recentlyTeleported` to improve concurrent performance and clarity.
- Remove accidental/backup files from PR: `.git_stash_StargateHelper_backup.java`, `.vscode/settings.json`, duplicate README.txt, and any editor temp files.
- Convert DB resource handling to try-with-resources in `HsqldbStorage` and `SqliteStorage` and ensure proper connection lifecycle; avoid systemPath dependency for `hsqldb.jar` in `pom.xml`.

Medium-priority issues
- Use bounded caches or eviction for maps that can grow unbounded (permissions maps, gate caches). Consider Guava Cache for time-based expiry where appropriate.
- Replace `Collections.synchronizedSet` usage elsewhere with concurrent collections if heavy contention expected.
- Address maven-shade overlapping resource warnings: exclude or properly merge `META-INF` resources.
- Prefer using proper external dependencies (avoid vendoring `Permissions` classes) unless license or version reasons force it.

Low-priority / polish
- Add Checkstyle / SpotBugs to CI to catch concurrency and style issues.
- Use pre-sized collections when sizes are known to reduce resizing overhead.
- Improve logging guards to avoid expensive string concat at high frequency logs.

Suggested immediate changes (small PRs)
1. Remove backup and editor files from branch (tiny, non-functional cleanup).
2. Replace `recentlyTeleported` with a `ConcurrentHashMap`-backed keySet.
3. Convert DB code to try-with-resources in `HsqldbStorage` and `SqliteStorage` (small, safe PR).
4. Reduce or refactor the periodic full-entity scan: implement conservative per-gate nearby-entities query or reduce interval.
5. Replace repeated anonymous scheduled Runnables by a small request queue + single repeating worker or reuse tasks for retries.
6. Fix `pom.xml` systemPath for HSQLDB (remove systemPath or publish artifact to repo).

Checklist (track progress in repo + locally)
- [ ] Remove accidental files from PR
- [ ] Replace `recentlyTeleported` with `ConcurrentHashMap.newKeySet()`
- [ ] Convert DB access to try-with-resources
- [ ] Replace global entity scan with spatial queries OR reduce frequency
- [ ] Refactor scheduler retry allocations (reuse or queue)
- [ ] Fix `pom.xml` (hsqldb systemPath)
- [ ] Add SpotBugs/Checkstyle to CI
- [ ] Address maven-shade resource overlaps
- [ ] Evaluate vendored permissions sources (remove or clearly document licensing)

Notes & rationale
- The global entity scan and scheduling churn are the highest risk for large servers (CPU & GC). Fixing these reduces per-tick work and allocations.
- Concurrency improvements (concurrent collections, bounded caches) prevent contention and memory growth over long server uptimes.

Next steps
- Decide if you want me to implement the small safety fixes now (concurrent set, DB try-with-resources, remove backup files) and run tests locally. I can prepare separate commits/PRs for each small change.

Reference: PR #3 files inspected; local builds/tests were run during review.
