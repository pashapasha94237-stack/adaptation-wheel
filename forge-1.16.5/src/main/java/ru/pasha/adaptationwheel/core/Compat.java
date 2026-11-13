package ru.pasha.adaptationwheel.core;
import java.util.*;
public final class Compat {
 private Compat(){}
 @SafeVarargs public static <T> List<T> list(T...items){return Collections.unmodifiableList(Arrays.asList(items.clone()));}
 @SafeVarargs public static <T> Set<T> set(T...items){return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(items)));}
 public static <T> List<T> copyList(Collection<? extends T> items){return Collections.unmodifiableList(new ArrayList<T>(items));}
 @SuppressWarnings("unchecked") public static <K,V> Map<K,V> map(Object...pairs){Map<K,V> result=new LinkedHashMap<K,V>();for(int i=0;i<pairs.length;i+=2)result.put((K)pairs[i],(V)pairs[i+1]);return Collections.unmodifiableMap(result);}
}
