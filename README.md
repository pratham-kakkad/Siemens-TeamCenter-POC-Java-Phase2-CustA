# FormCraft PLM Customs

Client-specific customizations for [FormCraft PLM](../Poc_Migration/formcraft-plm/formcraft-plm),
kept in their own repository so they can be versioned, reviewed and deployed without
ever touching (or requiring a rebuild of) the core application.

Each top-level folder here is one independently buildable customization module:

```
Customs/
└── nordic-snacks/                          ← Nordic Snacks Co.'s customizations
    ├── pom.xml
    └── src/main/java/fr/formcraft/
        ├── extensions/changerequest/        ← Gate 1: Java workflow rule handlers
        │   ├── RequireCommentOnRejectionHandler.java
        │   ├── EnforceAllergenSignOffBeforeApprovalHandler.java
        │   ├── EnforceMinimumReviewWindowHandler.java
        │   ├── RequireDetailedJustificationForHighImpactSubmissionHandler.java
        │   ├── RequireImpactNotesBeforeSubmissionHandler.java
        │   └── ChangeRequestAuditTrailHandler.java
        └── extensions/customization/        ← Gate 2: config seeded on startup
            └── NordicSnacksCustomizationSeeder.java
```

## Two kinds of customization

Core exposes five "Customization Gates" in total, but this module deliberately
sticks to just the first two. Only **Gate 1** has a Java interface to implement
(`fr.formcraft.sdk.workflow.ChangeRequestTransitionHandler`) — **Gate 2** is
config-driven: a "customization" there is rows in core's own table, normally
created by calling core's REST API by hand. (Gates 3-5 exist in core but aren't
used by this module.)

**Gate 1 (Java code)** — a `@Component` implementing an SDK interface, compiled
against core with a `provided`-scope dependency, so the built jar contains only
that customization's own classes, never a copy of core. At runtime, the jar sits
alongside core's classes on the same JVM classpath — there's no registry to wire
up, Spring's component scan (rooted at `fr.formcraft`, core's base package) finds
any `@Component` wherever it is on the classpath. Nordic Snacks Co.'s six
handlers, all vetoing or observing `ChangeRequest` transitions:

| Handler | Vetoes | Rule |
|---|---|---|
| `RequireCommentOnRejectionHandler` | → REJECTED | Rejection requires a decision comment |
| `EnforceAllergenSignOffBeforeApprovalHandler` | → APPROVED | Allergen-relevant changes need `"ALLERGEN-REVIEWED"` in the decision comment |
| `EnforceMinimumReviewWindowHandler` | → APPROVED | At least 4 hours must pass between request and approval |
| `RequireDetailedJustificationForHighImpactSubmissionHandler` | → SUBMITTED | High-impact requests need a reason ≥ 40 characters |
| `RequireImpactNotesBeforeSubmissionHandler` | → SUBMITTED | Submission requires a non-blank impact field |
| `ChangeRequestAuditTrailHandler` | *(non-vetoing)* | Logs every transition via core's `AuditService`, including ones core itself doesn't log |

That last one demonstrates that a Gate 1 handler isn't limited to guarding a
transition — since it runs in core's own Spring context, it can inject and call
*any* core service (not just SDK interfaces) to add side effects.

**Gate 2 (config, not code)** — `NordicSnacksCustomizationSeeder` is an
`ApplicationRunner` `@Component` that, on startup, injects core's
`CustomAttributeService` bean (the same one core's own REST controller calls)
and registers three custom attributes through it — idempotently, so re-running
it on every restart is safe: `nordic_organic_certified` (RAW_MATERIAL),
`recyclable_packaging_pct` (PACKAGING), `eu_novel_food_status`
(FINISHED_PRODUCT, regex-validated).

This is the same "no core code change" idea as Gate 1, just expressed as data
instead of a class: nothing here is possible without core's SDK service already
being a public Spring bean, but nothing here touches a core file either.

## Building a customization

```bash
# 1. Make formcraft-plm-core resolvable (once per machine, or after a core change):
#    from the core repo, run:
mvn install

# 2. Build the customization jar:
cd nordic-snacks
mvn clean package
# → target/formcraft-plm-customs-nordic-snacks-1.0.0.jar
```

## Deploying it against core

Point core's `/app/customs` mount at the folder holding this jar — for example,
in core's `docker-compose.yml`:

```yaml
services:
  formcraft-app:
    volumes:
      - /path/to/Customs/nordic-snacks/target:/app/customs
```

Restart the core container and the customization is live — no core image rebuild.

## Adding a new customization

1. Copy `nordic-snacks/` to a new folder (e.g. `acme-co/`) and update the
   `pom.xml`'s `artifactId`, `name` and description.
2. Implement the SDK interface(s) you need as a `@Component`.
3. `mvn clean package` builds it as its own jar, independent of every other
   customization here and of core.
