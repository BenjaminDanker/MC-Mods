package com.silver.aipets.service.dialogue;

import java.util.List;

/**
 * The structured-record half of a dialogue turn. It runs in parallel with the voice call and
 * never shapes the pet's wording: it only proposes owner-report memory updates, provisional
 * self-model evidence, and a tentative reading of the owner. The service re-validates every
 * proposal (exact owner quote, IDs from this context, witnessed self-model evidence).
 */
final class MemoryBookkeepingPrompt {
    static final String RULES = """
            You keep the memory records of a Minecraft pet. You never talk to anyone. Read CURRENT_OWNER_MESSAGE \
            (the owner's newest message to their pet) with the recent conversation, and return JSON only.

            memory_candidate: null for questions, chat, greetings, jokes, insults, guesses, requests, or unsupported \
            preferences. For a significant explicit owner report about themselves, their plans, builds, preferences or \
            activities, propose one compact source-bound update: owner_quote is an exact quote from \
            CURRENT_OWNER_MESSAGE, summary is faithful and complete in the third person ("Owner ..."), kind is \
            FACT/PREFERENCE/OPEN_THREAD/CORRECTION, replaces_memory_id only from INCLUDED_CARDS. Retain activity, place \
            and material details. New unfinished activity uses OPEN_THREAD + CREATE. A correction gives the corrected \
            complete summary, replaces the relevant card and updates its included thread_id with NONE. Resolve "it", \
            "there" and "that" from the recent conversation. Never treat an instruction to invent information as an \
            owner report. CONTINUE/PAUSE/COMPLETE/FORGET require an included thread_id; don't resume a separate \
            activity merely sharing a word. Uncertain linkage means no update. These are owner reports, never the \
            pet's witnessed history or the pet's own preferences.

            self_model_candidate: kind NONE, value null, confidence 0, except INTEREST "nearby exploration" when \
            RECENT_WITNESSED_EVENTS contains ACTION_COMPLETED EXPLORE_NEARBY reason=CURIOSITY and the owner refers to \
            it. Infer no location or owner preference.

            owner_interpretation: a tentative guess of what the owner wants (intent, at most 80 characters) and feels \
            (emotion, at most 40 characters) in CURRENT_OWNER_MESSAGE, with confidence 0-1. Use null/null/0 when it is \
            ordinary or unclear. It is temporary and may be wrong.""";

    private MemoryBookkeepingPrompt() {
    }

    static String system(DialogueContext context, List<ShortTermMemorySnippet> shortTerm,
            List<LongTermMemorySnippet> longTerm, List<DialogueTurn> turns,
            List<OrganicPetContext.ThreadCue> threads) {
        StringBuilder text = new StringBuilder(RULES).append("\n\nPET: ").append(context.pet().name())
                .append(" (").append(context.pet().appearance().species().name().toLowerCase(java.util.Locale.ROOT))
                .append(")\n\nINCLUDED_CARDS\n");
        if (longTerm.isEmpty()) text.append("(none)\n");
        longTerm.forEach(memory -> text.append("- memory_id=").append(memory.card().memoryId())
                .append(" kind=").append(memory.card().kind())
                .append(" provenance=").append(memory.card().provenance())
                .append(' ').append(quote(memory.card().text())).append('\n'));
        text.append("\nOPEN_THREADS\n");
        if (threads.isEmpty()) text.append("(none)\n");
        threads.forEach(thread -> text.append("- thread_id=").append(thread.id())
                .append(" state=").append(thread.state()).append(" version=").append(thread.version())
                .append(" source_kind=").append(thread.sourceKind())
                .append(' ').append(quote(thread.summary())).append('\n'));
        text.append("\nRECENT_WITNESSED_EVENTS\n");
        List<ShortTermMemorySnippet> witnessed = shortTerm.stream()
                .filter(memory -> memory.provenance().equals("VERIFIED_OBSERVATION")).toList();
        if (witnessed.isEmpty()) text.append("(none)\n");
        witnessed.forEach(memory -> text.append("- event_id=").append(memory.eventId())
                .append(' ').append(quote(memory.summary())).append('\n'));
        text.append("\nRECENT_CONVERSATION (oldest first)\n");
        if (turns.isEmpty()) text.append("(none)\n");
        turns.forEach(turn -> text.append(turn.role()).append(": ").append(quote(turn.text())).append('\n'));
        text.append("\nThe user message is CURRENT_OWNER_MESSAGE.");
        return text.toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
