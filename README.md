# FormCraft PLM Customs

Client-specific customizations for [FormCraft PLM](../Poc_Migration/formcraft-plm/formcraft-plm),
kept in their own repository so they can be versioned, reviewed and deployed without
ever touching (or requiring a rebuild of) the core application.

Each top-level folder here is one independently buildable customization module:

```
Customs/
└── nordic-snacks/                  ← Gate 1 workflow rule for Nordic Snacks Co.
    ├── pom.xml
    └── src/main/java/fr/formcraft/extensions/changerequest/
        └── RequireCommentOnRejectionHandler.java
```

## How it plugs in

A customization implements one of core's SDK extension points
(`fr.formcraft.sdk.*`, e.g. `ChangeRequestTransitionHandler`) as a Spring
`@Component`, and compiles against core with a `provided`-scope dependency —
so the built jar contains only that customization's own classes, never a copy
of core.

At runtime, the customization's jar sits alongside core's classes on the same
JVM classpath. There's no registry to wire up: Spring's component scan (rooted
at `fr.formcraft`, core's base package) finds any `@Component` wherever it is
on the classpath. The FormCraft PLM core Docker image starts with
`/app/customs` on its classpath for exactly this reason — drop a jar there (or
bind-mount a folder over it) and it's picked up with no core changes or rebuild.

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
