package ru.pasha.adaptationwheel.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Water placed in an ultra-warm dimension at stages I-III that is still waiting to be taken away. It lives in the
 * overworld's data storage so that a restart or a crash does not turn a timed source into a permanent one.
 * {@code due} is the overworld game time, which (unlike the server tick count) survives restarts.
 */
public final class TimedWater extends SavedData {
    public static final String NAME="adaptationwheel_nether_water";
    public record Timed(ResourceKey<Level> dim,BlockPos pos,long due,UUID owner){}
    /** Insertion order: the first entry is the oldest. Changed only through {@link MovementAdaptations}. */
    public final List<Timed> list=new ArrayList<>();
    public TimedWater(){}

    public static TimedWater load(CompoundTag tag){
        var data=new TimedWater();
        for(var raw:tag.getList("water",Tag.TAG_COMPOUND)){
            var c=(CompoundTag)raw;var dim=ResourceLocation.tryParse(c.getString("dim"));
            if(dim==null)continue;
            data.list.add(new Timed(ResourceKey.create(Registries.DIMENSION,dim),BlockPos.of(c.getLong("pos")),c.getLong("due"),c.hasUUID("owner")?c.getUUID("owner"):null));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var out=new ListTag();
        for(var t:list){
            var c=new CompoundTag();
            c.putString("dim",t.dim().location().toString());c.putLong("pos",t.pos().asLong());c.putLong("due",t.due());
            if(t.owner()!=null)c.putUUID("owner",t.owner());
            out.add(c);
        }
        tag.put("water",out);return tag;
    }
}
