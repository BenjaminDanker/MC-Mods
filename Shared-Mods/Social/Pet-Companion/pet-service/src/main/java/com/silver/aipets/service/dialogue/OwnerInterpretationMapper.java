package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.Optional;

/** Conservative lexical cues; ambiguous emotion and intent remain absent. */
public final class OwnerInterpretationMapper {
    private OwnerInterpretationMapper() { }
    public static OwnerInterpretation infer(String message, java.time.Instant at) {
        String text=message.toLowerCase(Locale.ROOT);
        Optional<String> emotion=Optional.empty(), intent=Optional.empty();
        if(text.matches("(?s).*(\\b(worried|concerned|scared|afraid)\\b).*")) emotion=Optional.of("CONCERNED");
        else if(text.matches("(?s).*(\\b(frustrated|annoyed|upset)\\b).*")) emotion=Optional.of("FRUSTRATED");
        if(text.matches("(?s).*(\\b(please|could you|can you|would you)\\b).*")) intent=Optional.of("REQUEST");
        else if(text.matches("(?s).*(\\b(i want|i need|i plan|i'm going to|we should)\\b).*")) intent=Optional.of("SHARE_GOAL");
        if(text.matches("(?s).*(\\b(no,? i meant|that's not what i meant|i didn't mean)\\b).*")) {
            emotion=Optional.empty(); intent=Optional.empty();
        }
        double confidence=emotion.isPresent()&&intent.isPresent()?.62:emotion.isPresent()||intent.isPresent()?.55:0;
        return new OwnerInterpretation(emotion.isPresent()||intent.isPresent()?intent:Optional.empty(),emotion,confidence,at);
    }
    public static boolean isOwnerCorrection(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        return text.matches("(?s).*(\\b(no,? i meant|i meant|that is not what i meant|that's not what i meant|i didn't mean|i did not mean|actually,? i)\\b).*");
    }
}
