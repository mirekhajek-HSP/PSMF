# Schema, versions and migrations

## The three kinds of file

| | Describes |
|---|---|
| `**/*.sq` | the schema **as it is now**, plus the queries |
| `**/*.sqm` | how to get an older database *to* now |
| `databases/<n>.db` | the recorded bytes of version *n* — what proves the other two agree |

**The version number is not written down anywhere.** SQLDelight derives it
as *highest `.sqm` number + 1*, so `1.sqm` existing is what makes the
current schema version 2. `PsmfDatabase.Schema.version` reports it, and the
platform drivers stamp it into SQLite's `user_version` on the device.

Version 1 is the schema the demo shipped with, up to and including commit
`eb772a4`. Everything before that was developed under a standing
"clean install" instruction and has no version — there is no path from it
and there does not need to be one.

## Adding a schema change

The order matters and only the first step is easy to get wrong:

1. **Record the current version first, before touching a `.sq` file:**

   ```
   ./gradlew :shared:generateCommonMainPsmfDatabaseSchema
   ```

   This writes `databases/<current>.db` from the `.sq` files as they stand.
   Run it afterwards and it records the *new* schema under the *old*
   number, and the recorded baseline is then a description of a database
   that never existed.

2. Edit the `.sq` files to the new shape.
3. Write `<current>.sqm` — the DDL that takes a version-`<current>`
   database to the new one.
4. Run the task again. It now writes `databases/<current+1>.db`. Commit
   both `.db` files and the `.sqm`.
5. `./gradlew :shared:check` runs `verifyCommonMainPsmfDatabaseMigration`,
   which applies the migrations to each recorded `.db` and compares the
   result against the `.sq` files. A mismatch fails the build. It is wired
   into `check` by `verifyMigrations = true` in `shared/build.gradle.kts`,
   so it is not something anyone has to remember to run.
6. Extend `SchemaMigrationTest`. **Verification compares shapes, not
   contents.** Only a test that writes a match into an old database and
   reads it out of a migrated one proves the data survived, and that is the
   part that ends a pilot when it is wrong.

## Two things that surprise

**The generated `.db` is not stamped with its own version.**
`generateCommonMainPsmfDatabaseSchema` leaves `user_version` at 0, whereas
every database created on a device is stamped by the driver that created
it. A test that opens a recorded schema file therefore has to set
`user_version` itself, or the driver reads 0, concludes the database is
empty, and tries to create tables that are already there.
`SchemaMigrationTest` does this explicitly.

**`.sqm` files are not package-scoped.** They live beside the `.sq` files
here because they describe the same schema, but the directory carries no
meaning for them the way `cz/hspinovace/psmf/db/` does for a `.sq` file.

## Why the `.db` files are committed

They are the only record of what was actually installed. A migration can
be verified against the schema in the repository at any time; it can only
be verified against version 1 if version 1 still exists somewhere. They are
90 KB of empty tables and they are the reason an update does not have to be
an uninstall.
