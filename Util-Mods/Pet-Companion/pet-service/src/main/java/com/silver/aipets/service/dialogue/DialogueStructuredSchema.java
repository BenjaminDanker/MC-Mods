package com.silver.aipets.service.dialogue;

/** Exact provider structured-output schema; parsing independently revalidates the same shape. */
public final class DialogueStructuredSchema {
    public static final String FULL_JSON_SCHEMA = """
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

    /** Compact baseline for isolated comparisons: immediate state without persistent affect. */
    public static final String COMPACT_JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["reply","action","attention","memory_candidate"],
              "properties":{
                "reply":{"type":"string","minLength":1,"maxLength":2000},
                "action":{"type":"string","enum":["NONE","APPROACH_OWNER","LOOK_AT_OWNER","LOOK_AT_INTEREST","SIT","REST","EXPLORE_NEARBY","VOCALIZE"]},
                "attention":{"type":"string","enum":["QUIET","CURIOUS","CALM","CAUTIOUS","CONCERNED","PLAYFUL"]},
                "memory_candidate":{"type":["string","null"],"maxLength":500}
              }
            }
            """;

    /** Current compact schema before relationship evidence, retained for A/B benchmarks. */
    public static final String COMPACT_AFFECT_JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["reply","action","attention","memory_candidate","affect_delta"],
              "properties":{
                "reply":{"type":"string","minLength":1,"maxLength":2000},
                "action":{"type":"string","enum":["NONE","APPROACH_OWNER","LOOK_AT_OWNER","LOOK_AT_INTEREST","SIT","REST","EXPLORE_NEARBY","VOCALIZE"]},
                "attention":{"type":"string","enum":["QUIET","CURIOUS","CALM","CAUTIOUS","CONCERNED","PLAYFUL"]},
                "memory_candidate":{"type":["string","null"],"maxLength":500},
                "affect_delta":{"type":"string","enum":["NONE","CALMER","BRIGHTER","WARY","TIRED"]}
              }
            }
            """;

    /** Production response adds one immutable interaction-level evidence signal. */
    public static final String JSON_SCHEMA = """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["reply","action","attention","memory_candidate","affect_delta","affect_strength","relationship_event","conversation_mode","self_model_candidate","owner_interpretation"],
              "properties":{
                "reply":{"type":"string","minLength":1,"maxLength":2000},
                "action":{"type":"string","enum":["NONE","APPROACH_OWNER","LOOK_AT_OWNER","LOOK_AT_INTEREST","SIT","REST","EXPLORE_NEARBY","VOCALIZE"]},
                "attention":{"type":"string","enum":["QUIET","CURIOUS","CALM","CAUTIOUS","CONCERNED","PLAYFUL"]},
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
                "affect_delta":{"type":"string","enum":["NONE","CALMER","BRIGHTER","WARY","TIRED"]},
                "affect_strength":{"type":"integer","minimum":0,"maximum":2},
                "relationship_event":{
                  "type":"object","additionalProperties":false,
                  "required":["type","strength"],
                  "properties":{
                    "type":{"type":"string","enum":["NONE","WARMTH","TRUST","BOUNDARY","REPAIR","IRRITATION","FEAR","DISTANCE","CURIOSITY"]},
                    "strength":{"type":"integer","minimum":0,"maximum":2}
                  }
                },
              "conversation_mode":{"type":"string","enum":["ANSWER","ASK","CLARIFY","BRIEF","DECLINE","OBSERVE"]},
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
