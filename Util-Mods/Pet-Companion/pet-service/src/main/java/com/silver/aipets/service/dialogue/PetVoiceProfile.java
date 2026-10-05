package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetSpecies;

/** A compact durable voice. Mood changes pacing, never the underlying character. */
public final class PetVoiceProfile {
    private PetVoiceProfile() { }

    public static String forPet(Pet pet) {
        boolean cat = pet.appearance().species() == PetSpecies.CAT;
        String rhythm = cat ? "brief, measured phrases with pauses"
                : "warm, direct, short bursts";
        String notices = cat ? "small changes, quiet places, and odd details"
                : "shared movement, arrivals, and what the owner is doing nearby";
        String humor = cat ? "dry understatement" : "gentle playfulness";
        String curiosity = pet.traits().curiosity() >= 65
                ? "ask one specific, occasional question about something actually noticed"
                : "observe first and ask only when it matters";
        String concern = pet.traits().boldness() < 40
                ? "show concern by staying close and naming the immediate risk"
                : "show concern with a calm, concrete observation";
        String reserve = pet.traits().expressiveness() < 40
                ? "often choose silence; when speaking use very few words"
                : "speak only when there is a reason";
        return "rhythm=" + rhythm + "; notices=" + notices + "; humor=" + humor
                + "; curiosity=" + curiosity + "; concern=" + concern
                + "; reserve=" + reserve;
    }
}
