package empire.ai;

import empire.game.*;
import empire.game.Actions.EndTurn;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.GridBits;
import io.anuke.arc.collection.IntFloatMap;
import io.anuke.arc.function.Predicate;
import io.anuke.arc.util.Tmp;
import io.anuke.arc.util.async.*;

import java.util.PriorityQueue;

/** Handles the AI for a specific player.*/
public abstract class AI{
    protected static final AsyncExecutor executor = new AsyncExecutor(4);
    protected static final boolean checkHistory = false;

    /** The player this AI controls.*/
    public final Player player;
    /** The game state.*/
    public final State state;

    private AsyncResult<Void> waiting;

    protected Array<Tile> astarTiles = new Array<>();
    protected int astarNewTrackCost = 0;
    protected Tracks astarOutputTracks = new Tracks(),
                        astarInputTracks = new Tracks();

    public AI(Player player, State state){
        this.player = player;
        this.state = state;
    }

    /** Performs actions on this AI's turn.*/
    public abstract void act();

    public boolean waitAsync(){
        return waiting == null || waiting.isDone();
    }

    public void async(Runnable runnable){
        if(!waitAsync()){
            throw new IllegalArgumentException("Wait for the task to be done until trying again.");
        }

        waiting = executor.submit(runnable);
    }

    public float astar(Tile from, Tile to, Array<Tile> out){
        float cost = astar(from, to);
        out.set(astarTiles);
        return cost;
    }

    public float astar(Tile from, Tile to){
        return astar(from, to, test -> test == to || state.world.getMajorCity(test) == to.city && to.city != null);
    }

    public float astar(Tile from, Tile to, Predicate<Tile> endTest){
        DistanceHeuristic dh = this::tileDst;
        TileHeuristic th = this::cost;
        World world = state.world;

        astarOutputTracks.clear();

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
            if(endTest.test(next)){
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
                }
            });
        }

        astarTiles.clear();
        astarNewTrackCost = 0;

        if(!found) return Float.MAX_VALUE;
        float totalCost = 0;
        Tile current = end;
        boolean movedOnOtherTrack = false;
        while(current != from){
            astarTiles.add(current);
            totalCost += cost(current.searchParent, current);

            //add up direct track costs
            Tile cfrom = current.searchParent, cto = current;
            if(!player.hasTrack(cfrom, cto) && !state.world.sameCity(cfrom, cto)
                     && !(checkHistory && astarInputTracks.has(cfrom.x, cfrom.y, cto.x, cto.y))){
                astarNewTrackCost += state.getTrackCost(cfrom, cto);
                astarOutputTracks.add(cfrom.x, cfrom.y, cto.x, cto.y);
            }else if(!movedOnOtherTrack && state.players.contains(p -> p.hasTrack(cfrom, cto))){
                astarNewTrackCost += State.otherMoveTrackCost;
                movedOnOtherTrack = true;
            }

            current = current.searchParent;
        }

        astarTiles.reverse();

        return totalCost;
    }

    float cost(Tile from, Tile to){
        if(player.hasTrack(from, to) || (checkHistory && astarInputTracks.has(from.x, from.y, to.x, to.y))){
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
