package com.silver.aipets.service.dialogue;

/** Exact provider structured-output schemas; parsing independently revalidates the same shape. */
public final class DialogueStructuredSchema {
    /** The pet's voice: what it says, does, and feels. No record-keeping fields. */
    public static final String VOICE_JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["reply","action","attention","affect_delta","affect_strength","relationship_event"],
              "properties":{
                "reply":{"type":"string","minLength":1,"maxLength":2000},
                "action":{"type":"string","enum":["NONE","APPROACH_OWNER","LOOK_AT_OWNER","LOOK_AT_INTEREST","SIT","REST","EXPLORE_NEARBY","VOCALIZE"]},
                "attention":{"type":"string","enum":["QUIET","CURIOUS","CALM","CAUTIOUS","CONCERNED","PLAYFUL"]},
                "affect_delta":{"type":"string","enum":["NONE","CALMER","BRIGHTER","WARY","TIRED"]},
                "affect_strength":{"type":"integer","minimum":0,"maximum":2},
                "relationship_event":{
                  "type":"object","additionalProperties":false,
                  "required":["type","strength"],
                  "properties":{
                    "type":{"type":"string","enum":["NONE","WARMTH","TRUST","BOUNDARY","REPAIR","IRRITATION","FEAR","DISTANCE","CURIOSITY"]},
                    "strength":{"type":"integer","minimum":0,"maximum":2}
                  }
                }
              }
            }
            """;

    /** Parallel record-keeping call: owner-report memory, self-model evidence, owner reading. */
    public static final String MEMORY_JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["memory_candidate","self_model_candidate","owner_interpretation"],
              "properties":{
                "memory_candidate":{"anyOf":[{"type":"null"},{
                  "type":"object","additionalProperties":false,
                  "required":["kind","summary","owner_quote","replaces_memory_id","thread_id","thread_operation"],
                  "properties":{
                    "kind":{"type":"string","enum":["FACT","PREFERENCE","OPEN_THREAD","CORRECTION"]},
                    "summary":{"type":"string","minLength":1,"maxLength":500},
                    "owner_quote":{"type":"string","minLength":1,"maxLength":500},
                    "replaces_memory_id":{"type":["string","null"]},
                    "thread_id":{"type":["string","null"]},
                    "thread_operation":{"type":"string","enum":["NONE","CREATE","CONTINUE","PAUSE","COMPLETE","FORGET"]}
                  }
                }]},
                "self_model_candidate":{
                  "type":"object","additionalProperties":false,
                  "required":["kind","value","confidence"],
                  "properties":{
                    "kind":{"type":"string","enum":["NONE","PREFERENCE","INTEREST","AVOIDANCE","GOAL"]},
                    "value":{"type":["string","null"],"maxLength":100},
                    "confidence":{"type":"number","minimum":0,"maximum":1}
                  }
                },
                "owner_interpretation":{
                  "type":"object","additionalProperties":false,
                  "required":["intent","emotion","confidence"],
                  "properties":{
                    "intent":{"type":["string","null"],"maxLength":80},
                    "emotion":{"type":["string","null"],"maxLength":40},
                    "confidence":{"type":"number","minimum":0,"maximum":1}
                  }
                }
              }
            }
            """;

    private DialogueStructuredSchema() {
    }
}
