package com.silver.aipets.service.consolidation;

public final class ConsolidationStructuredSchema {
    public static final String JSON_SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["memory_cards","relationship_summary","trait_deltas"],
             "properties":{
              "memory_cards":{"type":"array","maxItems":5,"items":{
               "type":"object","additionalProperties":false,
               "required":["text","importance","source_event_ids","emotion_tags","entity_tags","location_tags"],
               "properties":{"text":{"type":"string","minLength":1,"maxLength":2000},
                "importance":{"type":"string","enum":["MEDIUM","HIGH"]},
                "source_event_ids":{"type":"array","minItems":1,"maxItems":30,"items":{"type":"string","format":"uuid"}},
                "emotion_tags":{"type":"array","maxItems":16,"items":{"type":"string","maxLength":128}},
                "entity_tags":{"type":"array","maxItems":16,"items":{"type":"string","maxLength":128}},
                "location_tags":{"type":"array","maxItems":16,"items":{"type":"string","maxLength":128}}}}},
              "relationship_summary":{"type":"string","maxLength":2000},
              "trait_deltas":{"type":"object","additionalProperties":false,
               "required":["curiosity","boldness","playfulness","expressiveness","independence","attachment","trust","security"],
               "properties":{"curiosity":{"type":"integer","minimum":-100,"maximum":100},
                "boldness":{"type":"integer","minimum":-100,"maximum":100},
                "playfulness":{"type":"integer","minimum":-100,"maximum":100},
                "expressiveness":{"type":"integer","minimum":-100,"maximum":100},
                "independence":{"type":"integer","minimum":-100,"maximum":100},
                "attachment":{"type":"integer","minimum":-100,"maximum":100},
                "trust":{"type":"integer","minimum":-100,"maximum":100},
                "security":{"type":"integer","minimum":-100,"maximum":100}}}}}
            """;

    private ConsolidationStructuredSchema() {
    }
}
