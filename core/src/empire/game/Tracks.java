package empire.game;

import io.anuke.arc.collection.*;
import io.anuke.arc.collection.LongMap.Keys;
import io.anuke.arc.function.IntSegmentConsumer;
import io.anuke.arc.util.Pack;

/** Data structure for storing and querying placed track.*/
public class Tracks{
    private LongMap<Void> map = new LongMap<>();
    private IntIntMap used = new IntIntMap();

    public void each(IntSegmentConsumer cons){
        Keys keys = map.keys();
        while(keys.hasNext){
            long key = keys.next();
            int start = Pack.leftInt(key), end = Pack.rightInt(key);
            short x1 = Pack.leftShort(start), y1 = Pack.rightShort(start);
            short x2 = Pack.leftShort(end), y2 = Pack.rightShort(end);
            cons.accept(x1, y1, x2, y2);
        }
    }

    public void clear(){
        map.clear();
        used.clear();
    }

    public void add(int x, int y, int x2, int y2){
        addDirectional(x, y, x2, y2);
        addDirectional(x2, y2, x, y);
    }

    public void add(Tracks tracks){
        tracks.each(this::add);
    }

    public void set(Tracks other){
        clear();
        map.putAll(other.map);
        used.putAll(other.used);
    }

    private void addDirectional(int x, int y, int x2, int y2){
        long link = hash(x, y, x2, y2);
        if(!map.containsKey(link)){
            map.put(link, null);
            //increment links
            used.getAndIncrement(hash(x, y), 0, 1);
        }
    }

    public void remove(int x, int y, int x2, int y2){
        removeDirectional(x, y, x2, y2);
        removeDirectional(x2, y2, x, y);
    }

    private void removeDirectional(int x, int y, int x2, int y2){
        long link = hash(x, y, x2, y2);
        if(map.containsKey(link)){
            map.remove(link);
            used.getAndIncrement(hash(x, y), 0, -1);
        }
    }

    public boolean has(int x, int y){
        return connections(x, y) > 0;
    }

    public int connections(int x, int y){
        return used.get(hash(x, y), 0);
    }

    public boolean has(int x, int y, int x2, int y2){
        return map.containsKey(hash(x, y, x2, y2));
    }

    private int hash(int x, int y){
        return Pack.shortInt((short)x, (short)y);
    }

    private long hash(int x, int y, int x2, int y2){
        return Pack.longInt(Pack.shortInt((short)x, (short)y), Pack.shortInt((short)x2, (short)y2));
    }
}
