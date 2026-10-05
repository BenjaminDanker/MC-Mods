package com.silver.aipets.service.devplayer;

import com.silver.aipets.common.domain.*;
import com.silver.aipets.common.transport.PetSceneSnapshot;
import com.silver.aipets.service.consolidation.*;
import com.silver.aipets.service.dialogue.*;
import com.silver.aipets.service.experience.*;
import com.silver.aipets.service.http.JdbcPetSleepStateReader;
import com.silver.aipets.service.memory.*;
import com.silver.aipets.service.persistence.JdbcPetRepository;
import com.silver.aipets.service.sleep.*;
import com.silver.aipets.service.vector.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Operator-only CLI. This class is never installed in the normal service entry point. */
public final class DevPlayerCli implements AutoCloseable {
    private static final DevPlayerIdentity IDENTITY = DevPlayerIdentity.forSession(
            System.getenv().getOrDefault("DEV_PLAYER_SESSION", "default"));
    public static final UUID OWNER = IDENTITY.owner();
    public static final UUID PET = IDENTITY.pet();
    public static final UUID ENTITY = IDENTITY.entity();
    private static final String BACKEND = "dev_player_simulated";
    private static final String DIMENSION = "minecraft:overworld";
    private final DevPlayerEnvironment env;
    private final HikariDataSource db;
    private final DevPlayerClock clock;
    private final Path report;
    private final JdbcPetRepository pets;
    private final JdbcLongTermMemoryStore cards;
    private final MemoryEmbeddingWorker embeddings;
    private final PetDecisionPlanner planner;
    private final PetSleepService sleep;
    private final ConsolidationWorker consolidation;
    private final JdbcDialogueContextLoader contexts;
    private final DialogueService dialogue;
    private Trace trace;
    private boolean vectorOffline;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); return; }
        DevPlayerEnvironment env = DevPlayerEnvironment.from(System.getenv());
        Path state = Path.of(System.getenv().getOrDefault("DEV_PLAYER_STATE_DIR",
                System.getProperty("user.home") + "/.local/state/pet-dev-player"));
        if (!IDENTITY.session().equals("default")) state = state.resolve(IDENTITY.session());
        try (DevPlayerCli cli = new DevPlayerCli(env, state)) { cli.run(args); }
    }

    private DevPlayerCli(DevPlayerEnvironment env, Path state) throws Exception {
        this.env = env;
        Files.createDirectories(state);
        this.clock = new DevPlayerClock(state.resolve("clock.txt"));
        this.report = state.resolve("session.md");
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(env.jdbcUrl()); pool.setUsername(env.dbUser());
        pool.setPassword(env.dbPassword()); pool.setMaximumPoolSize(4);
        pool.setMinimumIdle(1); pool.setPoolName("dev-player-isolated");
        db = new HikariDataSource(pool);
        try (Connection c = db.getConnection()) {
            if (!DevPlayerEnvironment.DATABASE.equals(c.getCatalog()))
                throw new IllegalStateException("Connected to unexpected schema");
        }
        pets = new JdbcPetRepository(db, AppearanceRules.defaults());
        cards = new JdbcLongTermMemoryStore(db);
        Duration timeout = Duration.ofSeconds(20);
        EmbeddingModelClient embeddingModel = new OpenAiEmbeddingModelClient(env.openAiUri(),
                env.embeddingModel(), env.embeddingDimension(), env.openAiKey(), timeout);
        VectorMemoryRepository vector = new QdrantVectorMemoryRepository(env.qdrantUri(),
                env.qdrantCollection(), env.embeddingDimension(), Duration.ofSeconds(5),
                Optional.ofNullable(env.qdrantKey()).filter(s -> !s.isBlank()));
        EmbeddingModelClient tracedEmbedding = new EmbeddingModelClient() {
            public String model() { return embeddingModel.model(); }
            public EmbeddingVector embed(String text) { return embedWithUsage(text).embedding(); }
            public EmbeddingModelResponse embedWithUsage(String text) {
                long start = System.nanoTime();
                EmbeddingModelResponse result = embeddingModel.embedWithUsage(text);
                if (trace != null) trace.line("Embedding model: `" + model() + "`, input `" + text
                        + "`, tokens " + result.inputTokens() + ", " + ms(start) + " ms.");
                return result;
            }
        };
        VectorMemoryRepository tracedVector = new VectorMemoryRepository() {
            public String upsert(VectorMemoryDocument document) {
                long start = System.nanoTime(); String point = vector.upsert(document);
                if (trace != null) trace.line("Qdrant upsert: collection `" + env.qdrantCollection()
                        + "`, point `" + point + "`, " + ms(start) + " ms.");
                return point;
            }
            public void delete(UUID pet, UUID memory) { vector.delete(pet, memory); }
            public List<VectorMemoryMatch> search(MemorySearchQuery query, EmbeddingVector embedding) {
                if (vectorOffline) {
                    if (trace != null) trace.line("Qdrant forced unavailable by this Dev Player call; relational fallback expected.");
                    throw new IllegalStateException("Synthetic Qdrant outage");
                }
                long start = System.nanoTime(); List<VectorMemoryMatch> hits = vector.search(query, embedding);
                if (trace != null) trace.line("Qdrant search: collection `" + env.qdrantCollection()
                        + "`, query `" + query.deterministicSearchText() + "`, " + ms(start)
                        + " ms; candidates " + hits);
                return hits;
            }
        };
        embeddings = new MemoryEmbeddingWorker(cards, new JdbcEmbeddingJobStore(db),
                tracedEmbedding, tracedVector, clock, UUID::randomUUID, 5,
                Duration.ofMinutes(2), Duration.ofSeconds(30));
        DialogueMemoryRetriever fallback = new RelationalDialogueMemoryRetriever(cards);
        DialogueMemoryRetriever memory = new VectorDialogueMemoryRetriever(
                new MemoryRetrievalService(cards, tracedVector, tracedEmbedding,
                        Runnable::run, clock), fallback);
        contexts = new JdbcDialogueContextLoader(pets, new JdbcPetSleepStateReader(db),
                memory, new JdbcDialogueHistoryReader(db), owner -> OWNER.equals(owner),
                new com.silver.aipets.service.dialogue.JdbcOrganicPetContextReader(db));
        DialogueLimits limits = DialogueLimits.defaultsWithoutReplyCap();
        OpenAiDialogueModelClient provider = new OpenAiDialogueModelClient(env.openAiUri(),
                env.dialogueModel(), env.openAiKey(), timeout);
        DialogueModelClient tracedModel = new DialogueModelClient() {
            public String model() { return provider.model(); }
            public DialogueModelResponse complete(UUID id, DialoguePrompt prompt,
                    String schema, Duration deadline) {
                if (trace != null) {
                    trace.line("Model: `" + model() + "`; Chat Completions, `reasoning_effort=none`, "
                            + "`max_completion_tokens=" + OpenAiDialogueModelClient.MAX_COMPLETION_TOKENS
                            + "`, strict JSON schema. Nonstreaming; TTFT unavailable.");
                    trace.line("Prompt section tokens (local o200k estimate): `"
                            + new Gson().toJson(prompt.contextUsage()) + "`");
                    trace.block("Exact assembled model message (system role)", prompt.text());
                    prompt.currentOwnerMessage().ifPresent(text ->
                            trace.block("Exact assembled model message (user role)", text));
                    trace.block("Exact structured-output schema", schema);
                }
                long start = System.nanoTime();
                DialogueModelResponse response = provider.complete(id, prompt, schema, deadline);
                if (trace != null) {
                    trace.stage("provider_complete", ms(start));
                    trace.block("Raw model content", response.structuredJson());
                    trace.line("Provider completion metadata: request_id="
                            + response.providerRequestId().orElse("<absent>")
                            + ", finish_reason=" + response.finishReason()
                            + ", configured_completion_limit=" + response.configuredCompletionLimit()
                            + ", token_limit_hit=" + response.finishReason().equals("length")
                            + ", HTTP elapsed=" + response.latencyMillis() + " ms.");
                    trace.line("Provider usage: input=" + response.inputTokens() + ", cached="
                            + response.cachedInputTokens() + ", output=" + response.outputTokens());
                }
                return response;
            }
        };
        DialogueSafety moderation = new ModeratedDialogueSafety(new OpenAiModerationClient(
                env.openAiUri(), env.moderationModel(), env.openAiKey(), timeout));
        DialogueSafety timedSafety = new DialogueSafety() {
            @Override public SafetyResult preprocess(String input, int max) {
                long start = System.nanoTime();
                SafetyResult result = moderation.preprocess(input, max);
                if (trace != null) trace.stage("moderation_and_local_safety_legacy_serial", ms(start));
                return result;
            }
            @Override public SafetyResult preprocessLocal(String input, int max) {
                long start = System.nanoTime();
                SafetyResult result = moderation.preprocessLocal(input, max);
                if (trace != null) trace.stage("local_safety", ms(start));
                return result;
            }
            @Override public CompletableFuture<SafetyResult> moderateAsync(
                    String normalizedInput, java.util.concurrent.Executor executor) {
                long start = System.nanoTime();
                return moderation.moderateAsync(normalizedInput, executor).toCompletableFuture()
                        .whenComplete((result, failure) -> {
                            if (trace != null) trace.stage("remote_moderation_async", ms(start));
                        });
            }
        };
        DialogueStateStore jdbcState = new JdbcDialogueStateStore(db, UUID::randomUUID);
        DialogueStateStore timedState = new DialogueStateStore() {
            public DialoguePersistResult commit(DialoguePersistRequest request) {
                long start = System.nanoTime(); DialoguePersistResult result = jdbcState.commit(request);
                if (trace != null) trace.stage("sql_commit", ms(start));
                return result;
            }
            public void recordUsage(DialogueUsage usage) { jdbcState.recordUsage(usage); }
        };
        dialogue = new DialogueService(limits, timedSafety,
                new DialoguePromptBuilder(limits, new JTokkitDialogueTokenCounter()),
                tracedModel, new DialogueOutputCodec(), new DialogueResponseMapper(limits),
                new DialogueAdmissionController(limits), timedState,
                clock, UUID::randomUUID, UUID::randomUUID);
        ConsolidationModelClient consolidationProvider = new OpenAiConsolidationModelClient(
                env.openAiUri(), env.consolidationModel(), env.openAiKey(), timeout);
        ConsolidationModelClient tracedConsolidation = new ConsolidationModelClient() {
            public String model() { return consolidationProvider.model(); }
            public DialogueModelResponse complete(UUID id, DialoguePrompt prompt,
                    String schema, Duration deadline) {
                if (trace != null) {
                    trace.line("Consolidation model: `" + model() + "`; nonstreaming strict JSON schema.");
                    trace.block("Exact consolidation model message", prompt.text());
                    trace.block("Exact consolidation schema", schema);
                }
                long start=System.nanoTime();
                DialogueModelResponse response=consolidationProvider.complete(id,prompt,schema,deadline);
                if(trace!=null) {
                    trace.stage("consolidation_provider_complete",ms(start));
                    trace.block("Raw consolidation model content",response.structuredJson());
                }
                return response;
            }
        };
        consolidation = new ConsolidationWorker(ConsolidationConfig.defaults(),
                new JdbcConsolidationRepository(db, pets, UUID::randomUUID, UUID::randomUUID),
                owner -> OWNER.equals(owner),
                new ConsolidationCandidateSelector(ConsolidationConfig.defaults(),
                        new JTokkitDialogueTokenCounter()),
                new ConsolidationPromptBuilder(ConsolidationConfig.defaults(),
                        new JTokkitDialogueTokenCounter()),
                tracedConsolidation, new ConsolidationOutputCodec(),
                new ConsolidationOutputValidator(ConsolidationConfig.defaults(),
                        new JTokkitDialogueTokenCounter()),
                card -> embeddings.enqueue(card), clock, UUID::randomUUID, UUID::randomUUID);
        sleep = new PetSleepService(new JdbcPetSleepStateStore(db), PetSleepPolicy.defaults(),
                clock, new SleepConsolidationTrigger(consolidation));
        planner = new PetDecisionPlanner(new JdbcDecisionStore(db),
                com.silver.aipets.service.billing.AiBudgetService.UNLIMITED,
                com.silver.aipets.service.billing.AiPricing.defaults(), tracedModel, true,
                new JdbcOpenThreadRecall(db), new JdbcDecisionMemoryRecall(db));
    }

    private void run(String[] args) throws Exception {
        switch (args[0]) {
            case "create" -> create();
            case "reset" -> reset();
            case "cleanup" -> cleanup();
            case "say" -> {
                int sceneAt = Arrays.asList(args).indexOf("--scene-json");
                int offlineAt = Arrays.asList(args).indexOf("--vector-offline");
                int end = args.length;
                if(sceneAt>=0) end=Math.min(end,sceneAt);
                if(offlineAt>=0) end=Math.min(end,offlineAt);
                String message = String.join(" ", Arrays.copyOfRange(args, 1,
                        end));
                if (message.isBlank()) throw new IllegalArgumentException("Missing message");
                PetSceneSnapshot scene = sceneAt < 0 ? PetSceneSnapshot.unknown()
                        : scene(JsonParser.parseString(args[sceneAt + 1]).getAsJsonObject());
                vectorOffline=offlineAt>=0;
                say(message, scene);
            }
            case "observe" -> {
                if (args.length < 3) throw new IllegalArgumentException("observe TYPE SUMMARY");
                int placeAt = Arrays.asList(args).indexOf("--location-json");
                int quietAt = Arrays.asList(args).indexOf("--no-decision");
                int end = args.length;
                if (placeAt >= 0) end = Math.min(end, placeAt);
                if (quietAt >= 0) end = Math.min(end, quietAt);
                var location = placeAt < 0 ? java.util.Optional.<com.silver.aipets.common.transport.SpatialLocation>empty()
                        : java.util.Optional.of(com.silver.aipets.common.transport.SpatialLocationCodec.decode(
                                JsonParser.parseString(args[placeAt + 1]).getAsJsonObject()));
                observe(args[1], String.join(" ", Arrays.copyOfRange(args, 2, end)), location, quietAt < 0);
            }
            case "sleep" -> sleep();
            case "wake" -> wake();
            case "consolidate" -> consolidate();
            case "advance" -> { clock.advance(Duration.parse(args[1])); System.out.println(clock.instant()); }
            case "inspect" -> inspect();
            case "traits" -> traits(args);
            case "seed-owner-interpretation" -> seedOwnerInterpretation(args);
            case "run-scenario" -> scenario();
            case "bench" -> bench(args.length > 1 ? Integer.parseInt(args[1]) : 16);
            case "diagnose-provider" -> diagnoseProvider();
            case "diagnose-relationship" -> diagnoseRelationshipSchema();
            case "compare-conversation" -> compareConversation(join(args,1));
            default -> usage();
        }
    }

    private void create() {
        if (pets.findById(PET).isPresent()) { System.out.println("Existing Dev Player pet " + PET); return; }
        Instant at = clock.instant();
        PetAppearance appearance = PetAppearance.create(PetSpecies.CAT, "minecraft:tabby",
                0.7, null, AppearanceRules.defaults());
        Pet adopted = new Pet(PET, OWNER, "Mica", appearance,
                PetTraits.initial(72, 38, 33, 42, 76, at),
                PetMood.initial(52, 12, 12, 22, at),
                PlacedPlacement.materialized(new BackendId(BACKEND), DimensionId.parse(DIMENSION),
                        new WorldPosition(0, 64, 0), ENTITY), 0, at, at);
        pets.createIfOwnerAbsent(adopted);
        System.out.println("Created simulated Dev Player " + OWNER + " and pet " + PET);
    }

    private void reset() throws Exception {
        cleanupRows(); clock.reset(); Files.deleteIfExists(report); create();
    }

    private void cleanup() throws Exception {
        if (IDENTITY.session().equals("default"))
            throw new IllegalArgumentException("cleanup requires a named DEV_PLAYER_SESSION; use explicit reset for default");
        cleanupRows();
    }

    private void cleanupRows() throws Exception {
        JsonObject body = new JsonObject(), filter = new JsonObject(), clause = new JsonObject(), match = new JsonObject();
        match.addProperty("value", PET.toString()); clause.addProperty("key", "pet_id"); clause.add("match", match);
        JsonArray must = new JsonArray(); must.add(clause); filter.add("must", must); body.add("filter", filter);
        HttpRequest.Builder request = HttpRequest.newBuilder(env.qdrantUri().resolve(
                "/collections/" + env.qdrantCollection() + "/points/delete?wait=true"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!env.qdrantKey().isBlank()) request.header("api-key",env.qdrantKey());
        var response=HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.discarding());
        if (response.statusCode()!=404 && response.statusCode()/100!=2) throw new IllegalStateException("Test vector cleanup failed");
        try (Connection c=db.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Corrections have a self-referencing FK. This explicit test-pet deletion
                // can detach its own links; foreign keys and other pets remain untouched.
                try (PreparedStatement q=c.prepareStatement(
                        "UPDATE long_term_memories SET supersedes_memory_id=NULL WHERE pet_id=?")) {
                    q.setString(1,PET.toString()); q.executeUpdate();
                }
                List<String> pending=new ArrayList<>();
                try (ResultSet columns=c.getMetaData().getColumns(DevPlayerEnvironment.DATABASE,null,"%","pet_id")) {
                    while(columns.next()) {
                        String table=columns.getString("TABLE_NAME");
                        if(!table.matches("[a-z_]+")) throw new IllegalStateException("Unexpected table name");
                        if(!pending.contains(table)) pending.add(table);
                    }
                }
                // Foreign keys stay enabled. Leaf tables delete first; retry parents after their children.
                while(!pending.isEmpty()) {
                    int removed=0;
                    for(var iterator=pending.iterator();iterator.hasNext();) {
                        String table=iterator.next();
                        try(PreparedStatement q=c.prepareStatement("DELETE FROM `"+table+"` WHERE pet_id=?")) {
                            q.setString(1,PET.toString()); q.executeUpdate(); iterator.remove(); removed++;
                        } catch(SQLException blocked) {
                            if(blocked.getErrorCode()!=1451) throw blocked;
                        }
                    }
                    if(removed==0) throw new IllegalStateException("Test cleanup has unresolved foreign keys");
                }
                c.commit();
            } catch(Exception failure) { c.rollback(); throw failure; }
        }
        System.out.println("Removed only named session "+IDENTITY.session()+" SQL/vector rows for pet "+PET+"; report retained.");
    }

    private void say(String message, PetSceneSnapshot scene) throws Exception {
        create(); trace = new Trace("say", clock.instant(), report);
        long total = System.nanoTime();
        try (AutoCloseable diagnostics=DialogueDiagnostics.attach(trace::stage);
             AutoCloseable providerDiagnostics=DialogueProviderDiagnostics.attach(trace::line)) {
        trace.line("Input: `" + message + "`; scene: `" + scene + "` (simulated). Owner `" + OWNER
                + "`; pet `" + PET + "`.");
        snapshot("before");
        long contextStart = System.nanoTime();
        DialogueSafety.SafetyResult local = dialogue.preprocessLocally(message);
        if (!local.allowed()) throw new IllegalArgumentException("Dev Player message failed local safety");
        CompletableFuture<DialogueSafety.SafetyResult> moderation = dialogue.moderateAsync(
                local, java.util.concurrent.ForkJoinPool.commonPool());
        DialogueContext context = contexts.loadSpeculatively(OWNER, PET, BACKEND, DIMENSION,
                clock.instant(), local.normalizedText(), scene).orElseThrow();
        trace.stage("context_sql_embedding_vector", ms(contextStart));
        trace.line("Selected long-term cards: " + context.longTermMemories().stream()
                .map(m -> m.card().memoryId() + " source=" + m.source() + " score="
                        + m.similarity() + " rank=" + m.rankingScore() + " witnessed_places="
                        + com.silver.aipets.service.memory.WitnessedLocations.from(m.card().locationTags()) + " text=" + m.card().text()).toList());
        long responseStart = System.nanoTime();
        DialogueResult result = dialogue.respondPrepared(
                UUID.randomUUID(), OWNER, context, local, moderation);
        trace.stage("dialogue_after_context", ms(responseStart));
        trace.stage("service_pipeline_to_validated_reply",ms(contextStart));
        trace.line("Validated/displayed reply: `" + result.playerMessage() + "`; status `"
                + result.status() + "`; schema_validation="
                + (result.output().isPresent() ? "passed" : result.status() == DialogueResultStatus.INVALID_MODEL_OUTPUT
                        ? "failed" : "not_applicable")
                + ". No Minecraft display is measured by this CLI.");
        result.output().ifPresent(output -> trace.line("Validated immediate state: action=" + output.action()
                + ", attention=" + output.attention() + ", affect=" + output.affectDelta()
                + "×" + output.affectStrength() + ", relationship=" + output.relationshipSignal().type()
                + "/" + output.relationshipSignal().strength() + ", conversation_mode=" + output.conversationMode()
                + ", self_model_candidate=" + output.selfModelCandidate().kind()
                + ", supporting_event_ids=" + output.selfModelCandidate().supportingEventIds()
                + ", owner_interpretation=" + output.ownerInterpretation().intent().orElse("none") + "/"
                + output.ownerInterpretation().emotion().orElse("none") + " confidence="
                + output.ownerInterpretation().confidence()));
        Set<UUID> pending = new HashSet<>();
        try(Connection c=db.getConnection(); PreparedStatement q=c.prepareStatement("""
                SELECT memory_id FROM long_term_memories WHERE pet_id=? AND active=TRUE
                  AND embedding_status='PENDING' LIMIT 100
                """)) {
            q.setString(1,PET.toString());
            try(ResultSet r=q.executeQuery()) { while(r.next()) pending.add(UUID.fromString(r.getString(1))); }
        }
        if (result.status() == DialogueResultStatus.SUCCEEDED) {
            for (LongTermMemoryCard card : cards.listActiveForPet(PET, 100).cards()) {
                if(pending.contains(card.memoryId())) embeddings.enqueue(card);
            }
            embeddings.processDueForPet(PET, "dev-player", 20);
        }
        snapshot("after");
        trace.stage("cli_total_including_embedding_job", ms(total));
        trace.finish();
        System.out.println("Mica: " + result.playerMessage());
        System.out.println("Trace: " + report);
        trace = null;
        clock.advance(Duration.ofSeconds(6));
        }
    }

    private void observe(String type, String summary) throws Exception {
        observe(type, summary, java.util.Optional.empty(), true);
    }

    private void observe(String type, String summary,
            java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> location,
            boolean plan) throws Exception {
        if (location.isPresent() && (!location.get().server().equals(BACKEND)
                || !location.get().dimension().equals(DIMENSION)))
            throw new IllegalArgumentException("Synthetic location must match this test pet's placement");
        create(); trace = new Trace("observe", clock.instant(), report);
        UUID event = UUID.randomUUID(); Instant now = clock.instant();
        trace.line("Simulated witnessed event `" + event + "` type `" + type + "`: " + summary);
        snapshot("before");
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                INSERT INTO pet_events(event_id,pet_id,owner_uuid,event_type,occurred_at,
                source_server,source_dimension,importance,summary,short_term_expires_at,
                prompt_eligible,consolidation_status,created_at,location_json)
                VALUES(?,?,?,?,?,?,?,?,?,?,TRUE,'PENDING',?,?)
                """)) {
            q.setString(1,event.toString()); q.setString(2,PET.toString());
            q.setString(3,OWNER.toString()); q.setString(4,type);
            q.setObject(5,utc(now)); q.setString(6,BACKEND); q.setString(7,DIMENSION);
            q.setString(8, Set.of("SURVIVED_DANGER","ARRIVED_TOGETHER").contains(type)
                    ? "HIGH" : "MEDIUM");
            q.setString(9,summary); q.setObject(10,utc(now.plus(Duration.ofHours(2))));
            q.setObject(11,utc(now));
            q.setString(12, location.map(value -> com.silver.aipets.common.transport.SpatialLocationCodec.encode(value).toString()).orElse(null));
            q.executeUpdate();
        }
        var decision = plan ? planner.consider(pets.findById(PET).orElseThrow(), event,
                type, summary, now, true) : java.util.Optional.empty();
        trace.line("Intention: " + decision);
        snapshot("after"); trace.finish(); trace = null;
        System.out.println("Simulated observation " + event + ": " + decision);
        clock.advance(Duration.ofSeconds(6));
    }

    private void sleep() throws Exception {
        create(); trace = new Trace("sleep", clock.instant(), report);
        try(AutoCloseable diagnostics=ConsolidationDiagnostics.attach(trace::line)) {
        sleep.ownerOffline(OWNER, UUID.randomUUID(), clock.instant());
        clock.advance(Duration.ofMinutes(31));
        var transition = sleep.evaluate(PET);
        var transitions = transition.changed() ? List.of(transition) : List.of();
        trace.line("Sleep transitions: " + transitions);
        int jobs = consolidation.processDueForPet(PET, "dev-player", 10);
        trace.line("Consolidation jobs processed: " + jobs);
        int indexed = embeddings.processDueForPet(PET, "dev-player", 20);
        trace.line("Embedding jobs processed: " + indexed);
        snapshot("after"); trace.finish(); trace = null;
        System.out.println("Sleep transitions=" + transitions.size() + ", consolidation=" + jobs
                + ", embedding=" + indexed);
        }
    }

    private void wake() throws Exception {
        create(); trace = new Trace("wake", clock.instant(), report);
        clock.advance(Duration.ofHours(1));
        trace.line("Wake transition: " + sleep.evaluate(PET));
        sleep.ownerOnline(OWNER, clock.instant());
        snapshot("after"); trace.finish(); trace = null;
        System.out.println("Woke at " + clock.instant());
    }

    private void consolidate() throws Exception {
        create(); trace = new Trace("consolidate",clock.instant(),report);
        try(AutoCloseable diagnostics=ConsolidationDiagnostics.attach(trace::line)) {
            int jobs=consolidation.processDueForPet(PET,"dev-player",10);
            int indexed=embeddings.processDueForPet(PET,"dev-player",20);
            trace.line("Consolidation jobs processed: "+jobs+"; embedding jobs: "+indexed);
            snapshot("after"); trace.finish(); trace=null;
            System.out.println("Consolidation="+jobs+", embedding="+indexed);
        }
    }

    private void inspect() throws Exception {
        create(); trace = new Trace("inspect", clock.instant(), report);
        snapshot("current"); trace.finish(); trace = null;
        System.out.println("Report: " + report);
    }

    private void traits(String[] args) throws Exception {
        if (args.length != 9) throw new IllegalArgumentException("traits needs 8 values (0..100)");
        String[] fields={"curiosity","boldness","playfulness","expressiveness",
                "independence","attachment","trust","security"};
        StringBuilder sql=new StringBuilder("UPDATE pet_traits SET ");
        for(int i=0;i<8;i++) sql.append(fields[i]).append("=?").append(i==7?" WHERE pet_id=?":",");
        try(Connection c=db.getConnection(); PreparedStatement q=c.prepareStatement(sql.toString())) {
            for(int i=0;i<8;i++) { int value=Integer.parseInt(args[i+1]);
                if(value<0||value>100) throw new IllegalArgumentException("trait outside 0..100");
                q.setInt(i+1,value); }
            q.setString(9,PET.toString()); q.executeUpdate();
        }
        System.out.println("Updated isolated pet traits");
    }

    private void seedOwnerInterpretation(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException(
                "seed-owner-interpretation INTENT EMOTION CONFIDENCE (Dev Player fixtures only)");
        create();
        String intent = args[1].equalsIgnoreCase("NONE") ? null : args[1];
        String emotion = args[2].equalsIgnoreCase("NONE") ? null : args[2];
        double confidence = Double.parseDouble(args[3]);
        if ((intent == null && emotion == null) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("Provide an interpretation with confidence in [0,1]");
        trace = new Trace("seed-owner-interpretation (synthetic fixture)", clock.instant(), report);
        trace.line("Synthetic uncertain test state only. No owner fact, memory candidate, relationship event, embedding, or vector point is created.");
        snapshot("before");
        UUID sourceEvent;
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT event_id FROM pet_events WHERE pet_id=? AND event_type='DIALOGUE' ORDER BY created_at DESC LIMIT 1")) {
            q.setString(1, PET.toString());
            try (ResultSet rows = q.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Run one Dev Player say first so the temporary guess has an interaction version");
                sourceEvent = UUID.fromString(rows.getString(1));
            }
        }
        try (Connection c = db.getConnection(); PreparedStatement q = c.prepareStatement("""
                UPDATE pet_mood SET owner_intent_guess=?,owner_emotion_guess=?,
                    owner_interpretation_confidence=?,owner_interpretation_event_id=?,
                    owner_interpretation_updated_at=? WHERE pet_id=?
                """)) {
            q.setString(1, intent); q.setString(2, emotion);
            q.setBigDecimal(3, java.math.BigDecimal.valueOf(confidence));
            q.setString(4, sourceEvent.toString()); q.setObject(5, utc(clock.instant()));
            q.setString(6, PET.toString());
            if (q.executeUpdate() != 1) throw new IllegalStateException("Dev Player mood row is missing");
        }
        trace.line("Seeded owner interpretation: intent=" + Objects.toString(intent, "none")
                + ", emotion=" + Objects.toString(emotion, "none") + ", confidence=" + confidence
                + ", source_interaction_event_id=" + sourceEvent + ".");
        snapshot("after"); trace.finish(); trace = null;
        System.out.println("Seeded temporary Dev Player interpretation at confidence " + confidence);
    }

    private void scenario() throws Exception {
        create();
        say("What are you watching?", PetSceneSnapshot.unknown());
        observe("ARRIVED_TOGETHER", "We crossed a narrow spruce bridge together in the rain.");
        say("Do you remember the spruce bridge?", PetSceneSnapshot.unknown());
        say("I prefer quiet walks near the river.", PetSceneSnapshot.unknown());
        say("Actually, I prefer the hill path, not the river.", PetSceneSnapshot.unknown());
        say("Would the hill path be better for our walk today?", PetSceneSnapshot.unknown());
        vectorOffline=true;
        say("Would the hill path be better for our walk today?", PetSceneSnapshot.unknown());
        vectorOffline=false;
        say("I am building a bridge here and will finish it tomorrow.", PetSceneSnapshot.unknown());
        say("Stop following me so closely. I need some room.", PetSceneSnapshot.unknown());
        say("Sorry I snapped earlier; I just needed a little room.", PetSceneSnapshot.unknown());
        say("How many stars are in the sky?", PetSceneSnapshot.unknown());
        sleep();
        for (int retry = 0; retry < 3 && !sleepConsolidationSucceeded(); retry++) {
            clock.advance(Duration.ofMinutes(3));
            consolidate();
        }
        if (!sleepConsolidationSucceeded()) {
            throw new IllegalStateException("Sleep consolidation did not succeed after bounded retries; inspect the report");
        }
        wake();
        observe("OWNER_ACTION", "Owner checked the spruce bridge near the pet.");
        clock.advance(Duration.ofSeconds(46));
        observe("OWNER_RETURNED_AFTER_ABSENCE", "Owner returned near the unfinished bridge; biome=minecraft:plains");
        say("What did we do near the bridge?", PetSceneSnapshot.unknown());
        say("What color is my shield?", PetSceneSnapshot.unknown());
    }

    private boolean sleepConsolidationSucceeded() throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement q = c.prepareStatement(
                     "SELECT status FROM jobs WHERE pet_id=? AND job_type='SLEEP_CONSOLIDATION' "
                             + "ORDER BY created_at DESC LIMIT 1")) {
            q.setString(1, PET.toString());
            try (ResultSet rows = q.executeQuery()) {
                return rows.next() && "SUCCEEDED".equals(rows.getString(1));
            }
        }
    }

    private void bench(int count) throws Exception {
        if(count<8||count>40) throw new IllegalArgumentException("bench count must be 8..40");
        String[] questions={
                "Would the hill path be better for our walk today?",
                "What color is my shield?",
                "What happened on the spruce bridge?",
                "Is anything near us worth watching?"};
        for(int i=0;i<count;i++) {
            System.out.println("BENCH_CALL="+(i+1)+"/"+count);
            say(questions[i%questions.length],PetSceneSnapshot.unknown());
        }
    }

    private void diagnoseProvider() throws Exception {
        create();
        trace = new Trace("diagnose-provider", clock.instant(), report);
        String message = "What happened on the spruce bridge?";
        String affectMessage = "I'm nervous about going into this cave.";
        trace.line("Controlled isolated provider comparison; no dialogue state is written by these calls.");
        trace.line("Same model and owner question for all variants. Reply length is observed, not forced.");
        DialogueContext fullContext = contexts.loadSpeculatively(OWNER, PET, BACKEND, DIMENSION,
                clock.instant(), message, PetSceneSnapshot.unknown()).orElseThrow();
        DialogueLimits limits = DialogueLimits.defaultsWithoutReplyCap();
        JTokkitDialogueTokenCounter counter = new JTokkitDialogueTokenCounter();
        DialoguePromptBuilder builder = new DialoguePromptBuilder(limits, counter);
        DialoguePrompt full = builder.build(fullContext, message);
        DialogueContext affectContext = contexts.loadSpeculatively(OWNER, PET, BACKEND, DIMENSION,
                clock.instant(), affectMessage, PetSceneSnapshot.unknown()).orElseThrow();
        DialoguePrompt affectPrompt = builder.build(affectContext, affectMessage);
        DialogueContext noMemoryContext = new DialogueContext(fullContext.pet(),
                fullContext.sleeping(), fullContext.aiAccessEnabled(), List.of(), List.of(),
                fullContext.recentTurns(), fullContext.gameContext(), fullContext.now());
        DialoguePrompt noMemory = builder.build(noMemoryContext, message);
        String shortText = "You are Mica, a cautious, independent cat speaking briefly to your owner. "
                + "Witnessed: We crossed a narrow spruce bridge together in the rain. "
                + "Answer the owner's question without inventing details. "
                + "Return one structured response matching the supplied schema.";
        DialoguePrompt shortPrompt = new DialoguePrompt(shortText, counter.count(shortText)+counter.count(message),
                com.silver.aipets.common.transport.DialogueContextUsageWire.empty(), Optional.of(message));
        DialoguePrompt reduced = full;
        String reducedSchema = """
                {"type":"object","additionalProperties":false,"required":["reply"],
                 "properties":{"reply":{"type":"string","minLength":1,"maxLength":2000}}}
                """;
        OpenAiDialogueModelClient normalClient = new OpenAiDialogueModelClient(env.openAiUri(),
                env.dialogueModel(), env.openAiKey(), Duration.ofSeconds(20),
                "pet_dialogue_diagnostic", com.silver.aipets.service.billing.AiPricing.defaults(), "none");
        OpenAiDialogueModelClient lowClient = new OpenAiDialogueModelClient(env.openAiUri(),
                env.dialogueModel(), env.openAiKey(), Duration.ofSeconds(20),
                "pet_dialogue_diagnostic", com.silver.aipets.service.billing.AiPricing.defaults(), "low");
        try (AutoCloseable providerDiagnostics=DialogueProviderDiagnostics.attach(trace::line)) {
            for (int repetition=1; repetition<=4; repetition++) {
                diagnosticCall("full_schema", repetition, full, DialogueStructuredSchema.FULL_JSON_SCHEMA,
                        "none", counter, normalClient);
                diagnosticCall("compact_without_affect", repetition, full,
                        DialogueStructuredSchema.COMPACT_JSON_SCHEMA, "none", counter, normalClient);
                diagnosticCall("compact_with_affect", repetition, full,
                        DialogueStructuredSchema.JSON_SCHEMA, "none", counter, normalClient);
                diagnosticCall("compact_without_affect_owner_anxious", repetition, affectPrompt,
                        DialogueStructuredSchema.COMPACT_JSON_SCHEMA, "none", counter, normalClient);
                diagnosticCall("compact_with_affect_owner_anxious", repetition, affectPrompt,
                        DialogueStructuredSchema.JSON_SCHEMA, "none", counter, normalClient);
                diagnosticCall("reply_only_schema", repetition, reduced,
                        reducedSchema, "none", counter, normalClient);
                diagnosticCall("short_prompt_same_full_schema", repetition, shortPrompt,
                        DialogueStructuredSchema.FULL_JSON_SCHEMA, "none", counter, normalClient);
                diagnosticCall("full_without_retrieved_memories", repetition, noMemory,
                        DialogueStructuredSchema.FULL_JSON_SCHEMA, "none", counter, normalClient);
            }
            for (int repetition=1; repetition<=4; repetition++) {
                diagnosticCall("full_low_reasoning", repetition, full,
                        DialogueStructuredSchema.JSON_SCHEMA, "low", counter, lowClient);
            }
        }
        trace.finish(); trace = null;
        System.out.println("Provider comparison trace: " + report);
    }

    private void diagnoseRelationshipSchema() throws Exception {
        create();
        trace = new Trace("diagnose-relationship", clock.instant(), report);
        String[] messages = {
                "What happened on the spruce bridge?",
                "Thanks for waiting for me while I finished the bridge.",
                "Stop following me so closely while I build; I need some space.",
                "I'm sorry I pushed you away earlier. I should have given you space."
        };
        trace.line("Isolated, non-persistent schema comparison. The same prompt and owner input are sent "
                + "to the current compact schema and the compact schema with relationship_event. "
                + "No production player or vector data is read or written.");
        DialogueLimits limits = DialogueLimits.defaultsWithoutReplyCap();
        JTokkitDialogueTokenCounter counter = new JTokkitDialogueTokenCounter();
        DialoguePromptBuilder builder = new DialoguePromptBuilder(limits, counter);
        OpenAiDialogueModelClient client = new OpenAiDialogueModelClient(env.openAiUri(),
                env.dialogueModel(), env.openAiKey(), Duration.ofSeconds(20),
                "pet_relationship_diagnostic", com.silver.aipets.service.billing.AiPricing.defaults(), "none");
        try (AutoCloseable providerDiagnostics = DialogueProviderDiagnostics.attach(trace::line)) {
            for (int scenario = 0; scenario < messages.length; scenario++) {
                String message = messages[scenario];
                DialogueContext context = contexts.loadSpeculatively(OWNER, PET, BACKEND, DIMENSION,
                        clock.instant(), message, PetSceneSnapshot.unknown()).orElseThrow();
                DialoguePrompt prompt = builder.build(context, message);
                for (int repetition = 1; repetition <= 3; repetition++) {
                    trace.line("## Scenario " + (scenario + 1) + " repetition=" + repetition
                            + " input=" + message);
                    boolean relationshipFirst = (scenario + repetition) % 2 == 0;
                    if (relationshipFirst) {
                        diagnosticCall("relationship_compact", repetition, prompt,
                                DialogueStructuredSchema.JSON_SCHEMA, "none", counter, client);
                        diagnosticCall("current_compact", repetition, prompt,
                                DialogueStructuredSchema.COMPACT_AFFECT_JSON_SCHEMA, "none", counter, client);
                    } else {
                        diagnosticCall("current_compact", repetition, prompt,
                                DialogueStructuredSchema.COMPACT_AFFECT_JSON_SCHEMA, "none", counter, client);
                        diagnosticCall("relationship_compact", repetition, prompt,
                                DialogueStructuredSchema.JSON_SCHEMA, "none", counter, client);
                    }
                }
            }
        }
        trace.finish(); trace = null;
        System.out.println("Relationship schema comparison trace: " + report);
    }

    /** Paired, non-persisting prompt-policy calls; reset the isolated pet before each repetition. */
    private void compareConversation(String caseName) throws Exception {
        String message=switch(caseName) {
            case "bridge_records" -> "What did we do near the bridge?";
            case "why_sitting" -> "Why are you sitting here?";
            case "stars" -> "How many stars are in the sky?";
            case "going_back" -> "I've been thinking about going back to that place.";
            case "river_no_preference", "river_after_exploration_interest" ->
                    "Do you like walking by the river?";
            case "ambiguous_bridges" -> "What happened at the bridge?";
            default -> throw new IllegalArgumentException("Unknown policy case: " + caseName);
        };
        Path comparisonDir=report.getParent().resolve("conversation-policy-"+clock.instant().atZone(ZoneOffset.UTC).toLocalDate());
        Files.createDirectories(comparisonDir);
        System.out.println("Policy comparison: "+caseName+"; reports at "+comparisonDir);
        for(int repetition=1;repetition<=3;repetition++) {
            reset();
            seedPolicyCase(caseName);
            Path caseReport=comparisonDir.resolve(caseName+"-r"+repetition+".md");
            trace=new Trace("same-schema conversation policy comparison "+caseName+" repetition="+repetition,
                    clock.instant(),caseReport);
            try {
                trace.line("Owner input: `"+message+"`; synthetic scene `"+PetSceneSnapshot.unknown()+"`. "
                        +"Dev Player identity and pet were reset before this repetition.");
                trace.line("Two diagnostic provider calls share one retrieved DialogueContext, model ID, base route, "
                        +"reasoning_effort=none, non-streaming transport, max_completion_tokens=2000, timeout, "
                        +"and strict current schema. Only the deployed answer-first policy text differs from the current "
                        +"conversation-aware policy. These are non-persisting comparison calls, not accepted interactions.");
                snapshot("seeded_before");
                long contextStart=System.nanoTime();
                DialogueContext context=contexts.loadSpeculatively(OWNER,PET,BACKEND,DIMENSION,
                        clock.instant(),message,PetSceneSnapshot.unknown()).orElseThrow();
                trace.stage("context_sql_embedding_qdrant",ms(contextStart));
                trace.line("Selected long-term context: "+context.longTermMemories().stream()
                        .map(item->item.card().memoryId()+"/"+item.source()+"/"+item.similarity()+"/"+item.card().text()).toList());
                DialogueLimits diagnosticLimits=DialogueLimits.defaultsWithoutReplyCap();
                JTokkitDialogueTokenCounter counter=new JTokkitDialogueTokenCounter();
                DialoguePromptBuilder builder=new DialoguePromptBuilder(diagnosticLimits,counter);
                DialoguePrompt current=builder.build(context,message);
                DialoguePrompt baseline=builder.buildAnswerFirstComparison(context,message);
                OpenAiDialogueModelClient client=new OpenAiDialogueModelClient(env.openAiUri(),env.dialogueModel(),
                        env.openAiKey(),Duration.ofSeconds(20),"pet_conversation_policy_diagnostic",
                        com.silver.aipets.service.billing.AiPricing.defaults(),"none");
                DialogueOutputCodec codec=new DialogueOutputCodec();
                DialogueResponseMapper responseMapper=new DialogueResponseMapper(diagnosticLimits);
                try(AutoCloseable diagnostics=DialogueProviderDiagnostics.attach(trace::line)) {
                    if(repetition%2==1) {
                        comparePolicyCall("deployed_answer_first",baseline,message,context,client,codec,responseMapper);
                        comparePolicyCall("conversation_aware",current,message,context,client,codec,responseMapper);
                    } else {
                        comparePolicyCall("conversation_aware",current,message,context,client,codec,responseMapper);
                        comparePolicyCall("deployed_answer_first",baseline,message,context,client,codec,responseMapper);
                    }
                }
                snapshot("after_nonpersisting_model_calls");
                trace.line("The diagnostic calls do not run moderation, interaction persistence, Qdrant writes, or Fabric delivery. "
                        +"Context retrieval may record its normal isolated recall telemetry.");
                trace.finish();
            } finally { trace=null; }
        }
        System.out.println("Completed three reset, paired model repetitions for "+caseName);
    }

    private void comparePolicyCall(String policy,DialoguePrompt prompt,String message,DialogueContext context,
            OpenAiDialogueModelClient client,DialogueOutputCodec codec,DialogueResponseMapper responseMapper) {
        trace.line("### Policy `"+policy+"`; model `"+client.model()+"`; prompt tokens="+prompt.inputTokens()+
                "; current response schema; nonstreaming; reasoning_effort=none; max_completion_tokens=2000.");
        trace.block("Exact rendered prompt",prompt.text());
        prompt.currentOwnerMessage().ifPresent(text -> trace.block("Exact current owner user message",text));
        trace.block("Exact structured-output schema",DialogueStructuredSchema.JSON_SCHEMA);
        long start=System.nanoTime();
        try {
            DialogueModelResponse response=client.complete(UUID.randomUUID(),prompt,
                    DialogueStructuredSchema.JSON_SCHEMA,Duration.ofSeconds(20));
            trace.stage("http_complete_response_body_"+policy,ms(start));
            trace.block("Raw structured response",response.structuredJson());
            trace.line("Provider metadata: request_id="+response.providerRequestId().orElse("<absent>")
                    +", finish_reason="+response.finishReason()+", configured_completion_limit="
                    +response.configuredCompletionLimit()+", token_limit_hit="+response.finishReason().equals("length")
                    +", input_tokens="+response.inputTokens()+", cached_tokens="+response.cachedInputTokens()
                    +", completion_tokens="+response.outputTokens()+", HTTP elapsed="+response.latencyMillis()+" ms.");
            var decoded=codec.decode(response.structuredJson());
            var validated=responseMapper.map(decoded,message,context);
            trace.line("Validated visible reply: `"+validated.reply()+"`; conversation_mode="
                    +validated.conversationMode()+"; validator_fallback="
                    +!validated.reply().equals(decoded.reply())+"; action="+validated.action()
                    +"; affect="+validated.affectDelta()+"; interpretation="
                    +validated.ownerInterpretation().intent().orElse("none")+"/"
                    +validated.ownerInterpretation().emotion().orElse("none")+"/"
                    +validated.ownerInterpretation().confidence()+". No interaction state was committed.");
        } catch(RuntimeException failure) {
            trace.stage("http_or_validation_failure_"+policy,ms(start));
            trace.line("Call rejected: "+failure.getClass().getSimpleName()+" (no state commit).");
        }
    }

    private void seedPolicyCase(String caseName) throws SQLException {
        Instant now=clock.instant();
        switch(caseName) {
            case "bridge_records" -> {
                insertPolicyEvent("ARRIVED_TOGETHER","We crossed a narrow spruce bridge together in the rain.",
                        "VERIFIED_OBSERVATION","EPISODE",now,null);
                UUID ownerSource=insertPolicyEvent("DIALOGUE",
                        "Owner is building a bridge and plans to finish it tomorrow.",
                        "OWNER_STATEMENT","OPEN_THREAD",now,"Owner is building a bridge and plans to finish it tomorrow.");
                try(Connection c=db.getConnection();PreparedStatement q=c.prepareStatement("""
                        INSERT INTO pet_open_threads(thread_id,pet_id,summary,source_kind,source_event_id,thread_key,
                            location_server,location_dimension,location_cue,state,created_at,last_referenced_at,
                            next_cue,expires_at,version,updated_at)
                        VALUES(?,?,?,'OWNER_REPORTED',?,'bridge',?,?,'unknown','OPEN',?,?,?, ?,1,?)
                        """)) {
                    q.setString(1,UUID.randomUUID().toString());q.setString(2,PET.toString());
                    q.setString(3,"Owner is building a bridge and plans to finish it tomorrow.");
                    q.setString(4,ownerSource.toString());q.setString(5,BACKEND);q.setString(6,DIMENSION);
                    q.setObject(7,utc(now));q.setObject(8,utc(now));q.setString(9,"owner returns to this activity");
                    q.setObject(10,utc(now.plus(Duration.ofDays(30))));q.setObject(11,utc(now));q.executeUpdate();
                }
            }
            case "ambiguous_bridges" -> {
                insertPolicyEvent("ARRIVED_TOGETHER","We crossed the spruce bridge in the rain.",
                        "VERIFIED_OBSERVATION","EPISODE",now.minusSeconds(60),null);
                insertPolicyEvent("OWNER_ACTION","We repaired the stone bridge near the mill.",
                        "VERIFIED_OBSERVATION","EPISODE",now,null);
            }
            case "river_after_exploration_interest" -> seedPromotedExplorationInterest(now);
            default -> { }
        }
    }

    private UUID insertPolicyEvent(String type,String summary,String provenance,String kind,Instant at,String rawOwner)
            throws SQLException {
        UUID event=UUID.randomUUID();
        try(Connection c=db.getConnection();PreparedStatement q=c.prepareStatement("""
                INSERT INTO pet_events(event_id,pet_id,owner_uuid,event_type,occurred_at,source_server,
                    source_dimension,importance,summary,raw_player_text,short_term_expires_at,prompt_eligible,
                    consolidation_status,created_at,memory_kind,source_provenance,confidence,salience,emotional_significance)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING',?,?,?,?,?,0)
                """)) {
            q.setString(1,event.toString());q.setString(2,PET.toString());q.setString(3,OWNER.toString());
            q.setString(4,type);q.setObject(5,utc(at));q.setString(6,BACKEND);q.setString(7,DIMENSION);
            q.setString(8,type.equals("ARRIVED_TOGETHER")?"HIGH":"MEDIUM");q.setString(9,summary);
            q.setString(10,rawOwner);q.setObject(11,utc(at.plus(Duration.ofDays(30))));q.setBoolean(12,true);
            q.setObject(13,utc(at));q.setString(14,kind);q.setString(15,provenance);
            q.setBigDecimal(16,provenance.equals("OWNER_STATEMENT")?new java.math.BigDecimal("1.000"):
                    new java.math.BigDecimal("0.900"));q.setInt(17,provenance.equals("OWNER_STATEMENT")?2:3);
            q.executeUpdate();
        }
        return event;
    }

    private void seedPromotedExplorationInterest(Instant now) throws SQLException {
        String selfModel=null;
        for(int index=0;index<3;index++) {
            Instant actionAt=now.minus(Duration.ofDays(index==2?1:2)).plusSeconds(index*60L);
            UUID action=insertPolicyEvent("ACTION_COMPLETED","Pet completed EXPLORE_NEARBY reason=CURIOSITY",
                    "VERIFIED_OBSERVATION","EPISODE",actionAt,null);
            UUID interaction=insertPolicyEvent("DIALOGUE","Do you like exploring nearby?",
                    "OWNER_STATEMENT","EPISODE",actionAt.plusSeconds(1),"Do you like exploring nearby?");
            var candidate=new DialogueSelfModelCandidate(DialogueSelfModelCandidate.Kind.INTEREST,
                    "nearby exploration",.72,Set.of(action));
            selfModel=PetSelfModel.recordCandidate(selfModel,candidate,interaction,actionAt,false);
        }
        selfModel=PetSelfModel.promoteEligible(selfModel);
        try(Connection c=db.getConnection();PreparedStatement q=c.prepareStatement("""
                UPDATE pet_traits SET self_model_json=?,self_model_version=1,self_model_updated_at=?,updated_at=?
                WHERE pet_id=?
                """)) {
            q.setString(1,selfModel);q.setObject(2,utc(now));q.setObject(3,utc(now));q.setString(4,PET.toString());
            q.executeUpdate();
        }
    }

    private void diagnosticCall(String variant, int repetition, DialoguePrompt prompt,
            String schema, String effort, JTokkitDialogueTokenCounter counter,
            OpenAiDialogueModelClient client) {
        trace.line("### Variant " + variant + " repetition=" + repetition
                + " reasoning_effort=" + effort + " prompt_tokens_local=" + prompt.inputTokens());
        trace.line("Prompt section tokens (local o200k estimate): `"
                + new Gson().toJson(prompt.contextUsage()) + "`");
        trace.block("Exact diagnostic prompt", prompt.text());
        prompt.currentOwnerMessage().ifPresent(text -> trace.block("Exact diagnostic user message",text));
        trace.block("Exact diagnostic schema", schema);
        long began = System.nanoTime();
        try {
            DialogueModelResponse response = client.complete(UUID.randomUUID(), prompt,
                    schema, Duration.ofSeconds(20));
            trace.stage("diagnostic_call_" + variant, ms(began));
            trace.block("Complete structured response", response.structuredJson());
            JsonObject parsed = JsonParser.parseString(response.structuredJson()).getAsJsonObject();
            String reply = parsed.has("reply") ? parsed.get("reply").getAsString() : "<missing>";
            int visibleTokens = counter.count(reply);
            trace.line("VISIBLE REPLY: `" + reply + "`; local reply tokens=" + visibleTokens
                    + "; provider completion tokens=" + response.outputTokens()
                    + "; remaining completion tokens (JSON fields/framing and any reasoning)="
                    + Math.max(0, response.outputTokens() - visibleTokens));
        } catch (RuntimeException failure) {
            trace.stage("diagnostic_call_" + variant, ms(began));
            trace.line("Diagnostic call failed: " + failure.getClass().getSimpleName()
                    + " (provider status, if received, appears above)");
        }
    }

    private void snapshot(String label) throws SQLException {
        trace.line("SQL " + label + " (isolated schema):");
        rows("pet_traits", "SELECT curiosity,boldness,playfulness,expressiveness,independence,attachment,trust,security,relationship_summary,summary_version,self_model_json,self_model_version FROM pet_traits WHERE pet_id=?");
        rows("pet_mood", "SELECT content,excited,anxious,tired,last_decay_at,affect_valence,affect_valence_intensity,affect_alertness,affect_alertness_intensity,affect_social_stance,affect_social_intensity,affect_trigger,affect_target_uuid,affect_event_id,affect_updated_at,owner_intent_guess,owner_emotion_guess,owner_interpretation_confidence,owner_interpretation_event_id,owner_interpretation_updated_at FROM pet_mood WHERE pet_id=?");
        rows("pet_sleep_state", "SELECT sleeping,sleep_started_at,sleep_ends_at,fatigue,owner_network_online FROM pet_sleep_state WHERE pet_id=?");
        rows("pet_decisions", "SELECT source_event_id,intent,utterance,motivation_memory_id,decision_version,expires_at FROM pet_decisions WHERE pet_id=?");
        rows("pet_events", "SELECT event_id,event_type,importance,summary,memory_kind,source_provenance,confidence,salience,emotional_significance,participants_json,location_json,metadata_json,memory_candidate_text,memory_candidate_confidence,consolidation_status FROM pet_events WHERE pet_id=? ORDER BY occurred_at DESC LIMIT 12");
        rows("pet_relationship_evidence", "SELECT interaction_id,event_id,relationship_type,strength,occurred_at FROM pet_relationship_evidence WHERE pet_id=? ORDER BY occurred_at DESC LIMIT 12");
        rows("long_term_memories", "SELECT memory_id,memory_text,memory_kind,provenance,confidence,salience,emotional_significance,fact_key,supersedes_memory_id,embedding_status,embedding_reference,active FROM long_term_memories WHERE pet_id=? ORDER BY created_at DESC LIMIT 12");
        rows("pet_open_threads", "SELECT thread_id,thread_key,summary,source_kind,source_event_id,state,location_server,location_dimension,location_cue,created_at,updated_at,last_referenced_at,next_cue,expires_at,version FROM pet_open_threads WHERE pet_id=? ORDER BY created_at DESC LIMIT 12");
        rows("jobs", "SELECT job_id,job_type,status,attempt_count FROM jobs WHERE pet_id=? ORDER BY created_at DESC LIMIT 12");
        rows("ai_usage", "SELECT operation,model,input_tokens,cached_input_tokens,output_tokens,latency_ms,status FROM ai_usage WHERE pet_id=? ORDER BY created_at DESC LIMIT 12");
    }

    private void rows(String table, String sql) throws SQLException {
        try(Connection c=db.getConnection(); PreparedStatement q=c.prepareStatement(sql)) {
            q.setString(1,PET.toString());
            try(ResultSet r=q.executeQuery()) {
                JsonArray array=new JsonArray(); ResultSetMetaData m=r.getMetaData();
                while(r.next()) { JsonObject row=new JsonObject();
                    for(int i=1;i<=m.getColumnCount();i++) {
                        Object value=r.getObject(i); row.addProperty(m.getColumnLabel(i),
                                value==null?null:value.toString()); }
                    array.add(row); }
                trace.line("- `"+table+"` SELECT pet_id="+PET+": `"+array+"`");
            }
        }
    }

    private static String join(String[] args,int from) {
        if(args.length<=from) throw new IllegalArgumentException("Missing text");
        return String.join(" ",Arrays.copyOfRange(args,from,args.length));
    }
    private static PetSceneSnapshot scene(JsonObject json) {
        return new PetSceneSnapshot(
                get(json,"petBiome","unknown"), get(json,"ownerBiome","unknown"),
                get(json,"proximity","UNKNOWN"), bool(json,"ownerVisible",false),
                get(json,"ownerActivity","UNKNOWN"), get(json,"ownerHealth","UNKNOWN"),
                get(json,"petActivity","UNKNOWN"), get(json,"weather","UNKNOWN"),
                bool(json,"nearbyThreat",false), json.has("location") ? java.util.Optional.of(
                        com.silver.aipets.common.transport.SpatialLocationCodec.decode(json.getAsJsonObject("location")))
                        : java.util.Optional.empty());
    }
    private static String get(JsonObject json,String key,String fallback) {
        return json.has(key)?json.get(key).getAsString():fallback;
    }
    private static boolean bool(JsonObject json,String key,boolean fallback) {
        return json.has(key)?json.get(key).getAsBoolean():fallback;
    }
    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at,ZoneOffset.UTC); }
    private static long ms(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static void usage() {
        System.out.println("dev-player create|reset|say TEXT [--scene-json JSON] [--vector-offline]|observe TYPE SUMMARY [--location-json JSON] [--no-decision]|sleep|wake|consolidate|advance PT...|inspect|traits 8_VALUES|seed-owner-interpretation INTENT EMOTION CONFIDENCE|run-scenario|bench [8..40]|diagnose-provider|compare-conversation CASE");
    }
    @Override public void close() { db.close(); }

    private static final class Trace {
        private final Path report;
        private final StringBuilder content=new StringBuilder();
        private final long started=System.nanoTime();
        Trace(String kind,Instant at,Path report) throws IOException {
            this.report=report;
            if(!Files.exists(report)) Files.writeString(report,
                    "# Dev Player diagnostic session\n\nSynthetic identity and scene; no Minecraft client.\n\n");
            content.append("## ").append(kind).append(" at ").append(at).append("\n\n");
        }
        void line(String value) { content.append(DevPlayerRedactor.redact(value)).append("\n\n"); }
        void stage(String name,long millis) { line("Timing `"+name+"`: "+millis+" ms (monotonic)."); }
        void block(String heading,String value) {
            content.append("### ").append(heading).append("\n\n```text\n")
                    .append(DevPlayerRedactor.redact(value).replace("```","'''"))
                    .append("\n```\n\n");
        }
        void finish() throws IOException {
            stage("trace_total",ms(started));
            Files.writeString(report,content.toString()+"\n---\n\n",StandardOpenOption.APPEND);
        }
    }
}
