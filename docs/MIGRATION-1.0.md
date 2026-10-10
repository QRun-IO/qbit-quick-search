# Migrating from 0.x to 1.0

1.0 is a trust and platform release. Hosts that used the 0.2 fluent API keep compiling; the steps below are what changes at runtime.

## Prerequisites

- QQQ 4.1 (the QBit uses record-change listeners, after-commit callbacks and runtime services, none of which exist in 4.0). Until `qbit-build-parent` 2.1.0 is released, the QBit builds against 4.1.0-RC.1.
- OpenSearch 2.19.x or 3.x, or an Amazon OpenSearch Service managed domain. Serverless is not supported.

## Required changes

1. **Remove any manual `QBitMetaData` registration.** The producer now registers `com.kingsrook.qbits:quick-search` itself; a second `addQBit` with that name throws at startup. (qqq-all's `DemoQBits` did this in 0.x.)
2. **Decide the startup mode.** `startupMode` defaults to `FAIL_FAST`: `produce()` fails when OpenSearch is unreachable. Demo profiles that may run without a cluster should set `withStartupMode(QuickSearchStartupMode.DEGRADED)`.
3. **Run a full reindex once after upgrading.** The index mapping changed (version 2: search analyzer, text-typed `fieldValues`). The QBit logs a warning when it finds an index with the old mapping; "Quick Search Full Reindex" with no table name builds a new physical index and swaps the configured name to an alias. Search keeps working during the rebuild. The identity the QBit uses needs index create, delete and alias privileges for this (see the README role).
4. **Set `schedulerName` and start the host through `QApplicationLauncher`** (and optionally `basepullRepeatSeconds`, or `reconcileCronExpression` with `reconcileCronTimeZoneId` on a Quartz scheduler) so basepull and reconcile actually run. In 0.x they were never scheduled. See [Scheduling](#scheduling).
5. **Credentials over HTTP are refused.** Use `opensearchUrl` with `https://`, or set `allowPlaintextCredentials(true)` for local development. Prefer `${env.OPENSEARCH_PASSWORD}` references over literal secrets; never name an OpenSearch secret `QQQ_ENV_*` (those are sent to the frontend).
6. **Permissions.** Processes and the admin app now require `quickSearchAdmin.hasAccess`; the operational tables require read and write permissions. Grant them to operators, or override `adminPermissionRules` and `tablePermissionRules`. Search now honours table read permissions and record security locks, so users see fewer results than in 0.x if they lacked access.

## Behaviour changes to be aware of

- Real-time indexing happens after commit. Code that searched for a record inside the same transaction that inserted it will not find it until commit.
- Real-time failures no longer vanish: they land in `quickSearchFailedEvent` and are replayed by the next basepull. Review that table after an OpenSearch outage.
- A table without the change-detection field (`modifyDate` by default) is indexed on the first basepull and thereafter only by reconcile. The QBit logs a warning for each such table at startup, for annotated and config-driven tables alike; a config-driven table with `basepullTimestampField` set to null has opted out and gets no warning.
- `limit` is capped (default 100), `offset + limit` at 10,000, and search terms must be 2 to 100 characters.
- Query semantics changed from OR over n-grams to AND over whole terms with prefix matching: "blue widget" now requires both words.
- `enableScheduledProcesses` still works and sets both `enableBasepullProcess` and `enableMaintenanceProcesses`.
- `QuickSearchQBitContext` is deprecated and removed in 1.1; use `QuickSearchRuntime.get()`.

## Scheduling

Setting `schedulerName` puts schedules on the basepull and reconcile processes; QQQ runs them only once something starts its `QScheduleManager`.

- **Start through `QApplicationLauncher`.** In QQQ 4.1 that is `com.kingsrook.qqq.middleware.javalin.QApplicationLauncher` (`qqq-middleware-javalin`). `QApplicationLauncher.run(new MyApplication(), new QApplicationLauncherConfig())` starts the Javalin server, then the `QScheduleManager` when any process is scheduled, then the instance's runtime services, including this QBit's. A host that starts only a `QApplicationJavalinServer`, or builds its `QInstance` without the launcher, gets the processes but no scheduled runs, unless it calls `QScheduleManager.initInstance(qInstance, systemSessionSupplier).start()` itself. `qqq.scheduleManager.enabled=false` (or `QQQ_SCHEDULE_MANAGER_ENABLED=false`) turns schedules off on a node.
- **A reconcile cron needs a Quartz scheduler.** Basepull's fixed interval runs on any QQQ scheduler, but only `QuartzSchedulerMetaData` supports cron; QQQ's `SimpleSchedulerMetaData` runs fixed intervals only, and a cron schedule on it fails when the schedule manager starts.
- **A reconcile cron needs a time zone.** Set `reconcileCronTimeZoneId` to a Java time zone ID (`UTC`, `America/Chicago`). QQQ has no default zone: its instance validation rejects a cron schedule without one at startup. `produce()` now catches this first, along with an invalid Quartz expression (seconds first, for example `0 0 3 * * ?`), an unknown zone ID, and a cron on a scheduler in the instance that does not support cron.

## Removed

- `QuickSearchPostInsertCustomizer`, `QuickSearchPostUpdateCustomizer`, `QuickSearchPostDeleteCustomizer`. Hosts that referenced them (for example to compose with their own customizers) should remove those references; the record-change listener coexists with host customizers.
- `QuickSearchOpenSearchClient.search(String term, String tableName, ...)`; use the collection overload.
