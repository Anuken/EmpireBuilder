package empire.game;

import empire.ai.Astar;
import empire.game.World.City;
import io.anuke.arc.collection.Array;

public class PathCache{
    private final float[][] cityCosts;
    private final State state;
    private final Astar astar;

    public PathCache(State state, Player player){
        int length = Array.with(state.world.cities()).size;

        this.state = state;
        this.cityCosts = new float[length][length];
        this.astar = new Astar(player);
    }

    /** Calculates distances between all cities.*/
    public void calculate(State state){
        for(City city : Array.with(state.world.cities())){
            for(City other : state.world.cities()){
                cityCosts[city.id][other.id] = shortestPath(city, other);
            }
        }
    }

    public void recalculate(City city){
        for(City other : state.world.cities()){
            float dst = shortestPath(city, other);

        }
    }

    private float shortestPath(City from, City to){
        return astar.astar(state.world.tile(from), state.world.tile(to));
    }
}
