package com.silver.aipets.service.dialogue;

import java.util.Locale;

/** Luna's reply intent. Code does not reclassify or repair its language. */
public enum DialogueConversationMode {
    ANSWER, ASK, CLARIFY, BRIEF, DECLINE, OBSERVE;

    public static DialogueConversationMode validated(String value) {
        try { return valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException invalid) { return ANSWER; }
    }
}
