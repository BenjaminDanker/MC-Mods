package com.silver.openpacpruner;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Stream;

/** Offline only: OpenPAC NBT -> claimed region coordinates -> interactive file deletion. */
public final class Main {
    private static final String CLAIMS = "data/openpartiesandclaims/player-claims";
    private static final Set<String> KINDS = Set.of("region", "entities", "poi");
    private Main() {}

    public static void main(String[] args) {
        try { run(Options.parse(args)); }
        catch (Usage e) { System.err.println("Error: " + e.getMessage()); Options.usage(); System.exit(2); }
        catch (Safety e) { System.err.println("Safety stop: " + e.getMessage()); System.exit(3); }
        catch (Exception e) { System.err.println("Aborted: " + e.getMessage()); e.printStackTrace(); System.exit(1); }
    }

    private static void run(Options options) throws IOException {
        Path requestedWorld = options.world.toAbsolutePath().normalize();
        if (!Files.isDirectory(requestedWorld)) throw new Usage("World directory does not exist: " + requestedWorld);
        Path world = requestedWorld.toRealPath();
        Path lockPath = world.resolve("session.lock");
        try (OptionalLock ignored = OptionalLock.acquire(lockPath)) {
            Plan plan = plan(world, options);
            plan.print(options.dryRun);
            options.checks.forEach(check -> plan.printCheck(check));
            if (options.dryRun) { System.out.println("Dry run complete. No files were modified."); return; }
            if (plan.candidates.isEmpty()) { System.out.println("Nothing is eligible for deletion."); return; }
            System.out.print("Delete these " + plan.candidates.size() + " file(s)? [yes/no]: ");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.hasNextLine() ? scanner.nextLine().trim().toLowerCase(Locale.ROOT) : "";
            if (!answer.equals("yes")) { System.out.println("Cancelled. No files were changed."); return; }
            plan.assertUnchanged();
            long bytes = 0;
            for (Candidate c : plan.candidates) { ensureContained(world, c.path); Files.delete(c.path); bytes += c.snapshot.size; }
            System.out.printf(Locale.ROOT, "Deleted %,d file(s), freeing %.2f GiB.%n", plan.candidates.size(), bytes / 1073741824.0);
        }
    }

    private static FileLock lock(FileChannel channel, Path path) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new Safety("Minecraft or another process holds " + path + ". Stop the server first.");
            return lock;
        } catch (OverlappingFileLockException e) { throw new Safety("This process already holds " + path + "."); }
    }

    /** A copied offline world may legitimately retain session.lock; only an actively held lock stops us. */
    private static final class OptionalLock implements AutoCloseable {
        final FileChannel channel; final FileLock lock;
        OptionalLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        static OptionalLock acquire(Path path) throws IOException {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return new OptionalLock(null, null);
            if (Files.isSymbolicLink(path)) throw new Safety("session.lock must not be a symlink: " + path);
            FileChannel channel;
            try { channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE); }
            catch (IOException e) { System.err.println("Warning: cannot inspect session.lock; continuing because this is an offline-copy tool: " + e.getMessage()); return new OptionalLock(null, null); }
            try { return new OptionalLock(channel, lock(channel, path)); }
            catch (IOException | RuntimeException e) { try { channel.close(); } catch (IOException ignored) {} throw e; }
        }
        public void close() throws IOException { if (lock != null) lock.release(); if (channel != null) channel.close(); }
    }

    static Plan plan(Path world, Options options) throws IOException {
        Path claimsDir = world.resolve(CLAIMS);
        ensureContained(world, claimsDir);
        if (!Files.isDirectory(claimsDir, LinkOption.NOFOLLOW_LINKS)) throw new Safety("No OpenPAC claim directory: " + claimsDir);
        Map<String, Set<Region>> claims = readClaims(claimsDir);
        long claimed = claims.values().stream().mapToLong(Set::size).sum();
        if (claimed == 0 && !options.allowEmpty) throw new Safety("Parsed zero OpenPAC claims. Use --allow-empty-claims only if that is intentional.");
        Map<String, List<Path>> dimensions = dimensions(world);
        Map<String, Summary> summaries = new TreeMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (var entry : dimensions.entrySet()) {
            String id = entry.getKey(); boolean ignored = options.ignored.contains(id);
            Summary summary = new Summary(id, ignored, claims.getOrDefault(id, Set.of()).size()); summaries.put(id, summary);
            for (Path dir : entry.getValue()) { ensureContained(world, dir); try (Stream<Path> stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    ensureContained(world, file);
                    Region region = Region.parse(file.getFileName().toString()); if (region == null) continue;
                    Snapshot snapshot = Snapshot.of(file); summary.files++; summary.bytes += snapshot.size;
                    if (!ignored && !claims.getOrDefault(id, Set.of()).contains(region)) {
                        candidates.add(new Candidate(file, snapshot)); summary.candidates++; summary.candidateBytes += snapshot.size;
                    }
                }
            }}
        }
        candidates.sort(Comparator.comparing(c -> c.path.toString()));
        return new Plan(world, claims, summaries, candidates, Snapshot.tree(claimsDir));
    }

    private static Map<String, Set<Region>> readClaims(Path claimsDir) throws IOException {
        Map<String, Set<Region>> out = new HashMap<>();
        try (Stream<Path> stream = Files.list(claimsDir)) {
            for (Path file : stream.toList()) {
                if (!file.getFileName().toString().endsWith(".nbt")) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new Safety("OpenPAC claim entry is not a regular file: " + file);
                Map<String, Object> root = Nbt.compound(file); Map<String, Object> dimensions = map(root.get("dimensions")); if (dimensions == null) throw new Safety("Missing dimensions compound: " + file);
                for (var dimension : dimensions.entrySet()) {
                    Map<String, Object> data = map(dimension.getValue()); List<Object> entries = data == null ? null : list(data.get("claims")); if (entries == null) throw new Safety("Malformed claims list in " + file + " for " + dimension.getKey());
                    Set<Region> regions = out.computeIfAbsent(id(dimension.getKey()), unused -> new HashSet<>());
                    for (Object entry : entries) { Map<String, Object> claim = map(entry); List<Object> positions = claim == null ? null : list(claim.get("positions")); if (positions == null) throw new Safety("Malformed positions list in " + file);
                        for (Object raw : positions) { Map<String, Object> pos = map(raw); Integer x = pos == null ? null : integer(pos.get("x")); Integer z = pos == null ? null : integer(pos.get("z"));
                            if (x == null || z == null) throw new Safety("Expected TAG_Int x/z chunk coordinates in " + file); regions.add(regionForChunk(x, z)); }
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, List<Path>> dimensions(Path world) throws IOException {
        Map<String, List<Path>> out = new TreeMap<>(); Path root = world.resolve("dimensions");
        if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path dir : paths.filter(Files::isDirectory).toList()) {
                    if (!KINDS.contains(dir.getFileName().toString())) continue; Path rel = root.relativize(dir.getParent()); if (rel.getNameCount() < 2) continue;
                    StringBuilder path = new StringBuilder(); for (int i = 1; i < rel.getNameCount(); i++) { if (i > 1) path.append('/'); path.append(rel.getName(i)); }
                    String dimensionId = id(rel.getName(0) + ":" + path);
                    out.computeIfAbsent(dimensionId, unused -> new ArrayList<>()).add(dir);
                }
            }
        }
        legacy(out, "minecraft:overworld", world); legacy(out, "minecraft:the_nether", world.resolve("DIM-1")); legacy(out, "minecraft:the_end", world.resolve("DIM1"));
        return out;
    }
    private static void legacy(Map<String, List<Path>> out, String id, Path root) { for (String kind : KINDS) { Path dir = root.resolve(kind); if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) out.computeIfAbsent(id, unused -> new ArrayList<>()).add(dir); } }

    record Check(String dimension, int x, int z) {}
    record Options(Path world, Set<String> ignored, boolean allowEmpty, boolean dryRun, List<Check> checks) {
        static Options parse(String[] args) {
            Path world = null; Set<String> ignored = new HashSet<>(); boolean allowEmpty = false, dryRun = false; List<Check> checks = new ArrayList<>();
            for (int i = 0; i < args.length; i++) switch (args[i]) {
                case "--world" -> { if (++i == args.length) throw new Usage("--world needs a path."); world = Path.of(args[i]); }
                case "--ignore-overworld" -> ignored.add("minecraft:overworld"); case "--ignore-nether" -> ignored.add("minecraft:the_nether"); case "--ignore-end" -> ignored.add("minecraft:the_end");
                case "--ignore-dimension" -> { if (++i == args.length) throw new Usage("--ignore-dimension needs an id."); ignored.add(id(args[i])); }
                case "--allow-empty-claims" -> allowEmpty = true; case "--dry-run" -> dryRun = true;
                case "--check-chunk" -> { if (i + 3 >= args.length) throw new Usage("--check-chunk needs <dimension> <x> <z>."); try { checks.add(new Check(id(args[++i]), Integer.parseInt(args[++i]), Integer.parseInt(args[++i]))); } catch (NumberFormatException e) { throw new Usage("--check-chunk x and z must be integers."); } }
                case "--help", "-h" -> { usage(); System.exit(0); }
                default -> { if (!args[i].startsWith("--") && world == null) world = Path.of(args[i]); else throw new Usage("Unknown argument: " + args[i]); }
            };
            if (world == null) throw new Usage("A world path is required."); return new Options(world, Set.copyOf(ignored), allowEmpty, dryRun, List.copyOf(checks));
        }
        static void usage() { System.out.println("Usage: openpac-world-pruner --world <path> [--dry-run] [--check-chunk <dimension> <x> <z>] [--ignore-overworld] [--ignore-nether] [--ignore-end] [--ignore-dimension <id>] [--allow-empty-claims]"); }
    }

    static final class Plan {
        final Path world; final Map<String, Set<Region>> claims; final Map<String, Summary> summaries; final List<Candidate> candidates; final Map<Path, Snapshot> claimSnapshot;
        Plan(Path world, Map<String, Set<Region>> claims, Map<String, Summary> summaries, List<Candidate> candidates, Map<Path, Snapshot> claimSnapshot) { this.world=world; this.claims=claims; this.summaries=summaries; this.candidates=candidates; this.claimSnapshot=claimSnapshot; }
        void print(boolean dryRun) { long bytes = candidates.stream().mapToLong(c -> c.snapshot.size).sum(); System.out.println("\nOpenPAC world-pruner " + (dryRun ? "dry-run " : "") + "plan: " + world); for (Summary s : summaries.values()) System.out.printf(Locale.ROOT, "  %s%s - %,d retained claimed region(s), %,d MCA file(s), %,d candidate files, %.2f GiB%n", s.id, s.ignored ? " [IGNORED]" : "", s.claimed, s.files, s.candidates, s.candidateBytes / 1073741824.0); System.out.printf(Locale.ROOT, "Total candidate deletion: %,d file(s), %.2f GiB%n", candidates.size(), bytes / 1073741824.0); sample("Retained claim regions", claims.entrySet().stream().flatMap(e -> e.getValue().stream().map(r -> e.getKey()+" r."+r.x+"."+r.z+".mca")).sorted().toList()); sample("Deletion candidates", candidates.stream().map(c -> world.relativize(c.path).toString()).toList()); System.out.println(); }
        void printCheck(Check check) { Region region = regionForChunk(check.x, check.z); boolean retained = claims.getOrDefault(check.dimension, Set.of()).contains(region); System.out.printf("chunk %d,%d -> region %d,%d -> r.%d.%d.mca in %s: %s%n", check.x, check.z, region.x, region.z, region.x, region.z, check.dimension, retained ? "RETAINED (claim exists)" : "not claimed"); }
        private static void sample(String label, List<String> values) { System.out.println(label + " (up to 8):"); values.stream().limit(8).forEach(v -> System.out.println("    " + v)); if (values.isEmpty()) System.out.println("    (none)"); }
        void assertUnchanged() throws IOException { if (!claimSnapshot.equals(Snapshot.tree(world.resolve(CLAIMS)))) throw new Safety("OpenPAC claim data changed during confirmation."); for (Candidate c : candidates) if (!c.snapshot.equals(Snapshot.of(c.path))) throw new Safety("World file changed during confirmation: " + c.path); }
    }
    private static final class Summary { final String id; final boolean ignored; final long claimed; long files, bytes, candidates, candidateBytes; Summary(String id, boolean ignored, long claimed) { this.id=id; this.ignored=ignored; this.claimed=claimed; } }
    record Candidate(Path path, Snapshot snapshot) {}
    record Region(int x, int z) { static Region parse(String n) { if (!n.startsWith("r.") || !n.endsWith(".mca")) return null; String[] p=n.substring(2,n.length()-4).split("\\."); if (p.length != 2) return null; try { return new Region(Integer.parseInt(p[0]),Integer.parseInt(p[1])); } catch (NumberFormatException e) { return null; } } }
    private record Snapshot(long size, FileTime modified, Object fileKey) { static Snapshot of(Path p) throws IOException { BasicFileAttributes a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS); if(!a.isRegularFile()) throw new Safety("Not a regular file: "+p); return new Snapshot(a.size(),a.lastModifiedTime(),a.fileKey()); } static Map<Path, Snapshot> tree(Path dir) throws IOException { Map<Path,Snapshot> out=new HashMap<>(); try(Stream<Path> s=Files.walk(dir)){ for(Path p:s.toList()) if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) out.put(p,of(p)); } return out; } }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object v) { return v instanceof Map<?,?> m ? (Map<String,Object>)m : null; }
    @SuppressWarnings("unchecked") private static List<Object> list(Object v) { return v instanceof List<?> l ? (List<Object>)l : null; }
    private static Integer integer(Object v) { return v instanceof Integer n ? n : null; }
    static Region regionForChunk(int x, int z) { return new Region(Math.floorDiv(x, 32), Math.floorDiv(z, 32)); }
    private static String id(String value) { if (value == null || value.isBlank()) throw new Usage("Blank dimension id."); String result = value.contains(":") ? value : "minecraft:" + value; if (!result.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new Usage("Invalid dimension id: " + value); return result; }
    private static void ensureContained(Path world, Path path) throws IOException { if (Files.isSymbolicLink(path)) throw new Safety("Refusing symbolic link: " + path); Path real = path.toRealPath(); if (!real.startsWith(world)) throw new Safety("Path escapes supplied world directory: " + path); }
    private static final class Usage extends RuntimeException { Usage(String m){super(m);} } private static final class Safety extends RuntimeException { Safety(String m){super(m);} }

    /** Minimal big-endian raw-NBT reader; OpenPAC player-claim files are uncompressed NBT. */
    private static final class Nbt {
        static Map<String,Object> compound(Path file) throws IOException { try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) { int type=in.readUnsignedByte(); if(type!=10) throw new Safety("Expected compound NBT: "+file); string(in); return compound(in); } catch(EOFException e){ throw new Safety("Truncated NBT: "+file); } }
        static Object payload(DataInputStream in,int t)throws IOException{return switch(t){case 1->in.readByte();case 2->in.readShort();case 3->in.readInt();case 4->in.readLong();case 5->in.readFloat();case 6->in.readDouble();case 7->bytes(in);case 8->string(in);case 9->list(in);case 10->compound(in);case 11->ints(in);case 12->longs(in);default->throw new Safety("Unsupported NBT tag "+t);};}
        static Map<String,Object> compound(DataInputStream in)throws IOException{Map<String,Object> out=new LinkedHashMap<>();while(true){int t=in.readUnsignedByte();if(t==0)return out;out.put(string(in),payload(in,t));}}
        static List<Object> list(DataInputStream in)throws IOException{int t=in.readUnsignedByte(),n=len(in);List<Object> out=new ArrayList<>(n);for(int i=0;i<n;i++)out.add(payload(in,t));return out;}
        static byte[] bytes(DataInputStream in)throws IOException{byte[] a=new byte[len(in)];in.readFully(a);return a;} static int[] ints(DataInputStream in)throws IOException{int[]a=new int[len(in)];for(int i=0;i<a.length;i++)a[i]=in.readInt();return a;} static long[] longs(DataInputStream in)throws IOException{long[]a=new long[len(in)];for(int i=0;i<a.length;i++)a[i]=in.readLong();return a;}
        static int len(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>16_777_216)throw new Safety("Invalid NBT length: "+n);return n;} static String string(DataInputStream in)throws IOException{return in.readUTF();}
    }
}
