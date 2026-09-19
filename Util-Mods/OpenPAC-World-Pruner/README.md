# OpenPAC World Pruner

An offline Java 25 utility, not a Fabric/server mod. It reads OpenPAC claim NBT,
keeps every 32x32-chunk MCA region containing at least one claim, and can delete
unclaimed region files only after an interactive confirmation.

## Safety

- The tool obtains the world's `session.lock` before it plans. If Minecraft has the
  world open, it aborts without changing anything.
- It retains/deletes full region files only, including matching `region`, `entities`,
  and `poi` files. It never rewrites MCA files.
- Every invocation plans, displays totals, then accepts only `yes` or `no`.
- Candidates and OpenPAC data are revalidated immediately before deletion.
- Zero parsed claims is rejected unless `--allow-empty-claims` is explicit.

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
repeatable `--ignore-dimension <namespace:path>`, and `--allow-empty-claims`.
