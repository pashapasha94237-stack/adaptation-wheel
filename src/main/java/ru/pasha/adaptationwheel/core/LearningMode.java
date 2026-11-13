package ru.pasha.adaptationwheel.core;

/** Per-player policy for acquiring new analyzer examples; earned protection is independent. */
public enum LearningMode {
    FROZEN, ADAPTIVE, RESEARCH;

    public LearningMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String translationKey() { return "learning.adaptationwheel.mode." + name().toLowerCase(java.util.Locale.ROOT); }
    public String descriptionKey() { return translationKey() + ".description"; }
    public boolean learns() { return this != FROZEN; }
    public boolean researches() { return this == RESEARCH; }
}
