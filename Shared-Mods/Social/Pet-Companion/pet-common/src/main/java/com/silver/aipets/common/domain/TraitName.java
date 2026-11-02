package com.silver.aipets.common.domain;

public enum TraitName {
    CURIOSITY(TraitCategory.TEMPERAMENT),
    BOLDNESS(TraitCategory.TEMPERAMENT),
    PLAYFULNESS(TraitCategory.TEMPERAMENT),
    EXPRESSIVENESS(TraitCategory.TEMPERAMENT),
    INDEPENDENCE(TraitCategory.TEMPERAMENT),
    ATTACHMENT(TraitCategory.RELATIONSHIP),
    TRUST(TraitCategory.RELATIONSHIP),
    SECURITY(TraitCategory.RELATIONSHIP);

    private final TraitCategory category;

    TraitName(TraitCategory category) {
        this.category = category;
    }

    public TraitCategory category() {
        return category;
    }
}
