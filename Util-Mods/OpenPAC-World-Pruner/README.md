# OpenPAC World Pruner

An offline Java 25 utility, not a Fabric/server mod. It reads OpenPAC claim NBT,
keeps every 32x32-chunk MCA region containing at least one claim, and can delete
unclaimed region files only after an interactive confirmation.

## Safety

- If a `session.lock` is present and lockable, the tool checks that it is not held.
  A stale lock (or no lock) in an offline copied world does not block the tool.
- It retains/deletes full region files only, including matching `region`, `entities`,
  and `poi` files. It never rewrites MCA files.
- Every invocation plans, displays totals, then accepts only `yes` or `no`.
- Candidates and OpenPAC data are revalidated immediately before deletion.
- Zero parsed claims is rejected unless `--allow-empty-claims` is explicit.
- It refuses symlinks and verifies every scanned/deleted path resolves beneath the
  supplied world directory.

OpenPAC stores `positions.x` and `positions.z` as signed **chunk** coordinates
(`TAG_Int`). MCA region coordinates are therefore `floorDiv(chunk, 32)`, including
for negatives: chunk `-1` and `-32` are both in region `-1`; chunk `-33` is in `-2`.

Use an offline local copy first. A lock cannot prove that a remote server is using a
different copy via a network mount.

## Run

```powershell
gradle run --args='--world C:\Users\bsd20\world --ignore-end --ignore-nether'
```

Or, use this project's wrapper:

```powershell
.\gradlew.bat run --args='--world C:\Users\bsd20\world'
```

Options: `--world <path>`, `--ignore-overworld`, `--ignore-nether`, `--ignore-end`,
repeatable `--ignore-dimension <namespace:path>`, `--allow-empty-claims`, and
`--dry-run`. A dry run never prompts or changes files, and prints totals plus small
samples of retained and deletion-candidate regions.

For a direct manual spot check, add repeatable
`--check-chunk <namespace:path> <chunk-x> <chunk-z>`. For example:

```powershell
.\gradlew.bat run --args='--world C:\copied\vanilla1 --dry-run --check-chunk minecraft:overworld -33 -33'
```
