package ru.pasha.adaptationwheel.net;

import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.StageDurations;
import ru.pasha.adaptationwheel.core.AdaptationTimings;

/** Registry-derived menu data. Contains no client or server Minecraft classes. */
public record CatalogueEntry(String key, String translationKey, String fallbackName, AdaptationProfile profile,StageDurations timings) {
    public CatalogueEntry(String key,String translationKey,String fallbackName,AdaptationProfile profile) {
        this(key,translationKey,fallbackName,profile,AdaptationTimings.forSource(key,profile));
    }
}
