package ru.pasha.adaptationwheel.net;import ru.pasha.adaptationwheel.core.*;public final class CatalogueEntry {private final String key;
private final String translationKey;
private final String fallbackName;
private final AdaptationProfile profile;
public CatalogueEntry(String key,String translationKey,String fallbackName,AdaptationProfile profile){this.key=key;this.translationKey=translationKey;this.fallbackName=fallbackName;this.profile=profile;}public String key(){return key;}
public String translationKey(){return translationKey;}
public String fallbackName(){return fallbackName;}
public AdaptationProfile profile(){return profile;}
@Override public boolean equals(Object value){if(!(value instanceof CatalogueEntry))return false;CatalogueEntry other=(CatalogueEntry)value;return java.util.Objects.equals(key,other.key) && java.util.Objects.equals(translationKey,other.translationKey) && java.util.Objects.equals(fallbackName,other.fallbackName) && java.util.Objects.equals(profile,other.profile);} @Override public int hashCode(){return java.util.Objects.hash(key,translationKey,fallbackName,profile);}}