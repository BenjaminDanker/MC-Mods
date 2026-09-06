package com.silver.aipets.service.dialogue;

/** Exact provider structured-output schema; parsing independently revalidates the same shape. */
public final class DialogueStructuredSchema {
    public static final String JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["reply","importance","memory_candidate","trait_deltas","mood_deltas"],
              "properties":{
                "reply":{"type":"string","minLength":1,"maxLength":2000},
                "importance":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                "memory_candidate":{"type":["string","null"],"maxLength":500},
                "trait_deltas":{
                  "type":"object","additionalProperties":false,
                  "required":["curiosity","boldness","playfulness","expressiveness","independence","attachment","trust","security"],
                  "properties":{
                    "curiosity":{"type":"integer","minimum":-100,"maximum":100},
                    "boldness":{"type":"integer","minimum":-100,"maximum":100},
                    "playfulness":{"type":"integer","minimum":-100,"maximum":100},
                    "expressiveness":{"type":"integer","minimum":-100,"maximum":100},
                    "independence":{"type":"integer","minimum":-100,"maximum":100},
                    "attachment":{"type":"integer","minimum":-100,"maximum":100},
                    "trust":{"type":"integer","minimum":-100,"maximum":100},
                    "security":{"type":"integer","minimum":-100,"maximum":100}
                  }
                },
                "mood_deltas":{
                  "type":"object","additionalProperties":false,
                  "required":["content","excited","anxious","tired"],
                  "properties":{
                    "content":{"type":"integer","minimum":-100,"maximum":100},
                    "excited":{"type":"integer","minimum":-100,"maximum":100},
                    "anxious":{"type":"integer","minimum":-100,"maximum":100},
                    "tired":{"type":"integer","minimum":-100,"maximum":100}
                  }
                }
              }
            }
            """;

    private DialogueStructuredSchema() {
    }
}
