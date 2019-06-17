package empire.ai;

import empire.game.*;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.Predicate;
import io.anuke.arc.util.Tmp;
import static empire.gfx.EmpireCore.*;

import java.util.PriorityQueue;

public class Astar{
    protected Array<Tile> tiles = new Array<>();
    protected int newTrackCost = 0;
    protected Tracks outputTracks = new Tracks(), inputTracks = new Tracks();
    protected boolean usedOtherTrack = false;

    public float astar(Tile from, Tile to, Array<Tile> out){
        float cost = astar(from, to);
        out.set(tiles);
        return cost;
    }

    public float astar(Tile from, Tile to){
        return astar(from, to, test -> test == to || state.world.getMajorCity(test) == to.city && to.city != null);
    }

    public float astar(Tile from, Tile to, Predicate<Tile> endTest){
        BaseAI.DistanceHeuristic dh = this::tileDst;
        BaseAI.TileHeuristic th = this::cost;
        World world = state.world;

        outputTracks.clear();
        usedOtherTrack = false;

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

        tiles.clear();
        newTrackCost = 0;

        if(!found) return Float.MAX_VALUE;
        float totalCost = 0;
        Tile current = end;
        boolean movedOnOtherTrack = false;
        while(current != from){
            tiles.add(current);
            totalCost += cost(current.searchParent, current);

            //add up direct track costs
            Tile cfrom = current.searchParent, cto = current;
            if(!player.hasTrack(cfrom, cto) && !state.world.sameCity(cfrom, cto)
                    && !(checkHistory && inputTracks.has(cfrom.x, cfrom.y, cto.x, cto.y))){
                newTrackCost += state.getTrackCost(cfrom, cto);
                outputTracks.add(cfrom.x, cfrom.y, cto.x, cto.y);
            }else if(!movedOnOtherTrack && state.players.contains(p -> p.hasTrack(cfrom, cto))){
                newTrackCost += State.otherMoveTrackCost;
                movedOnOtherTrack = true;
            }

            current = current.searchParent;
        }

        tiles.reverse();

        return totalCost;
    }

    float cost(Tile from, Tile to){
        if(player.hasTrack(from, to) || state.world.sameCity(from, to) || state.world.samePort(from, to)
                || (checkHistory && inputTracks.has(from.x, from.y, to.x, to.y))){
            return 0.5f;
        }
        if(state.players.contains(p -> p.hasTrack(from, to))){
            if(!usedOtherTrack){
                usedOtherTrack = true;
                return State.otherMoveTrackCost * 500;
            }else{
                return 0.5f;
            }
        }
        return state.getTrackCost(from, to) * 500;
    }

    float tileDst(int x, int y, int x2, int y2){
        Tmp.v2.set(EmpireCore.control.toWorld(x, y));
        return Tmp.v2.dst(EmpireCore.control.toWorld(x2, y2));
    }

    //interfaces

    interface DistanceHeuristic{
        float cost(int x1, int y1, int x2, int y2);
    }

    interface TileHeuristic{
        float cost(Tile from, Tile to);
    }
}
