package ru.pasha.adaptationwheel.core;

import java.util.*;

/** Local draft: browsing and cancelling never change server settings or lose unavailable mod IDs. */
public final class MobSelectionModel {
    public record Entry(String id,String name,boolean available) {}
    private final List<Entry> entries;
    private final Set<String> known;
    private final LinkedHashSet<String> selected;
    private final Map<String,String> multiplierInputs=new LinkedHashMap<>();
    public MobSelectionModel(Collection<Entry> entries,Collection<String> initial) {
        this(entries,initial,Map.of());
    }
    public MobSelectionModel(Collection<Entry> entries,Collection<String> initial,Map<String,Double> multipliers) {
        Map<String,Entry> unique=new LinkedHashMap<>();
        for(Entry entry:entries)unique.putIfAbsent(entry.id(),entry);
        selected=new LinkedHashSet<>(initial);
        for(String id:initial)unique.putIfAbsent(id,new Entry(id,id,false));
        this.entries=unique.values().stream().sorted(Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
        known=Set.copyOf(unique.keySet());
        multipliers.forEach((id,value)->multiplierInputs.put(id,Double.toString(value)));
    }
    public List<Entry> filtered(String query,boolean selectedOnly) {
        String match=query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(e->!selectedOnly || selected.contains(e.id()))
            .filter(e->match.isEmpty() || e.name().toLowerCase(Locale.ROOT).contains(match) || e.id().toLowerCase(Locale.ROOT).contains(match)).toList();
    }
    public boolean selected(String id){return selected.contains(id);}
    public void toggle(String id){if(!known.contains(id))return;if(!selected.remove(id))selected.add(id);}
    public void clear(){selected.clear();}
    public List<String> selection(){return List.copyOf(selected);}
    public String multiplierInput(String id){return multiplierInputs.getOrDefault(id,"");}
    public void setMultiplierInput(String id,String text){if(known.contains(id))multiplierInputs.put(id,text);}
    public boolean validMultiplierInput(String id) {
        String text=multiplierInput(id).strip();if(text.isEmpty())return true;
        try{return SwordSettings.validMultiplier(Double.parseDouble(text.replace(',','.')));}catch(NumberFormatException invalid){return false;}
    }
    public boolean valid(){return selected.stream().allMatch(this::validMultiplierInput);}
    public Map<String,Double> multipliers() {
        if(!valid())throw new IllegalStateException("Invalid selected mob multiplier");
        Map<String,Double> result=new LinkedHashMap<>();
        multiplierInputs.forEach((id,text)->{if(!text.isBlank() && validMultiplierInput(id))result.put(id,Double.parseDouble(text.strip().replace(',','.')));});
        return Map.copyOf(result);
    }
}
