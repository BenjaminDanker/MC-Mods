package com.silver.aipets.service.memory;

import com.silver.aipets.common.transport.SpatialLocation;
import com.silver.aipets.common.transport.SpatialLocationCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Versioned structured locations inside the existing SQL location_tags array; never model tags. */
public final class WitnessedLocations {
    public static final String PREFIX = "witnessed-location-v1:";
    public static final double MAXIMUM_BOOST = .05;
    public static final double RADIUS_BLOCKS = 128;
    private WitnessedLocations() { }

    public static String tag(SpatialLocation location) {
        return PREFIX + SpatialLocationCodec.encode(location);
    }

    public static List<SpatialLocation> from(Set<String> tags) {
        return tags.stream().filter(tag -> tag.startsWith(PREFIX))
                .map(tag -> SpatialLocationCodec.readStored(tag.substring(PREFIX.length())))
                .flatMap(Optional::stream).toList();
    }

    /** Additive tie breaker after relevance admission. Never a distance filter or semantic input. */
    public static double boost(Optional<SpatialLocation> current, List<SpatialLocation> witnessed) {
        if (current.isEmpty()) return 0;
        SpatialLocation here = current.orElseThrow();
        return witnessed.stream().filter(here::sameSpace)
                .mapToDouble(place -> MAXIMUM_BOOST * Math.max(0, 1 - here.distanceTo(place) / RADIUS_BLOCKS))
                .max().orElse(0);
    }

    public static Set<String> ordinaryTags(Set<String> tags) {
        return tags.stream().filter(tag -> !tag.startsWith(PREFIX))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
