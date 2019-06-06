package empire.ai;

import empire.game.Actions.EndTurn;
import empire.game.Player;
import empire.game.State;
import empire.game.World;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.GridBits;
import io.anuke.arc.collection.IntFloatMap;
import io.anuke.arc.util.Tmp;

import java.util.PriorityQueue;

/** Handles the AI for a specific player.*/
public abstract class AI{
    /** The player this AI controls.*/
    public final Player player;
    /** The game state.*/
    public final State state;

    public AI(Player player, State state){
        this.player = player;
        this.state = state;
    }

    /** Performs actions on this AI's turn.*/
    public abstract void act();

    public float astar(Tile from, Tile to, Array<Tile> out){
        DistanceHeuristic dh = this::tileDst;
        TileHeuristic th = this::cost;
        World world = state.world;

        GridBits closed = new GridBits(world.width, world.height);
        GridBits open = new GridBits(world.width, world.height);
        IntFloatMap costs = new IntFloatMap();
        PriorityQueue<Tile> queue = new PriorityQueue<>(100,
                (a, b) -> Float.compare(
                        costs.get(world.index(a), 0f) + dh.cost(a.x, a.y, to.x, to.y),
                        costs.get(world.index(b), 0f) + dh.cost(b.x, b.y, to.x, to.y)));

        queue.add(from);
        Tile end = null;
        boolean found = false;
        while(!queue.isEmpty()){
            Tile next = queue.poll();
            float baseCost = costs.get(world.index(next), 0f);
            if(next == to || world.getMajorCity(next) == to.city && to.city != null){
                found = true;
                end = next;
                break;
            }
            closed.set(next.x, next.y);
            open.set(next.x, next.y, false);
            world.adjacentsOf(next, child -> {
                if(!closed.get(child.x, child.y) && state.isPassable(player, child) &&
                    !(world.getCity(player.position) == null &&
                            player.position == next && player.position.directionTo(child) != null &&
                            player.position.directionTo(child).opposite(player.direction))
                ){
                    float newCost = th.cost(next, child) + baseCost;
                    if(costs.get(world.index(child), Float.POSITIVE_INFINITY) > newCost){
                        child.searchParent = next;
                        costs.put(world.index(child), newCost);
                    }
                    if(!open.get(child.x, child.y)){
                        queue.add(child);
                    }
                    open.set(child.x, child.y);
                    //closed.set(child.x, child.y);
                }
            });
        }

        out.clear();

        if(!found) return Float.MAX_VALUE;
        float totalCost = 0;
        Tile current = end;
        while(current != from){
            out.add(current);
            totalCost += cost(current.searchParent, current);
            current = current.searchParent;
        }

        out.reverse();

        return totalCost;
    }

    float cost(Tile from, Tile to){
        if(player.hasTrack(from, to)){
            return 0.5f;
        }
        if(state.players.contains(p -> p.hasTrack(from, to))){
            return State.otherMoveTrackCost * 500;
        }
        return state.getTrackCost(from, to) * 500;
    }

    float tileDst(int x, int y, int x2, int y2){
        Tmp.v2.set(EmpireCore.control.toWorld(x, y));
        return Tmp.v2.dst(EmpireCore.control.toWorld(x2, y2));
    }

    /** End the turn if necessary.*/
    void end(){
        if(state.player() == player){
            new EndTurn().act();
        }
    }

    /** Select a good starting location based on cards.
     * TODO implement this properly*/
    void selectLocation(){
        City city = Array.with(state.world.cities()).random();

        player.chosenLocation = true;
        player.position = state.world.tile(city.x, city.y);
    }

    //interfaces

    interface DistanceHeuristic{
        float cost(int x1, int y1, int x2, int y2);
    }

    interface TileHeuristic{
        float cost(Tile from, Tile to);
    }
}
