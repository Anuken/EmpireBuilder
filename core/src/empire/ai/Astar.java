package empire.ai;

import empire.game.*;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.Predicate;
import io.anuke.arc.math.geom.Vector2;

import java.util.PriorityQueue;

import static empire.gfx.EmpireCore.*;

public class Astar{
    private Vector2 vec = new Vector2();

    protected Array<Tile> tiles = new Array<>();
    protected int newTrackCost = 0;
    protected Tracks outputTracks = new Tracks(), inputTracks = new Tracks();
    protected Player player;
    protected IntSet usedOtherTrack = new IntSet();

    public Astar(Player player){
        this.player = player;
    }

    public void setPlayer(Player player){
        this.player = player;
    }

    public void begin(){
        inputTracks.clear();
    }

    public void end(){
        inputTracks.clear();
    }

    /** Copies the output, placed tracks into the input buffer.
     * This essentialy makes the AI consider the newly placed tracks in the next calculations. */
    public void placeTracks(){
        inputTracks.add(outputTracks);
    }

    public float astar(Tile from, Tile to, Array<Tile> out){
        float cost = astar(from, to);
        out.set(tiles);
        return cost;
    }

    public float astar(Tile from, Tile to){
        return astar(from, to, test -> test == to || state.world.getMajorCity(test) == to.city && to.city != null);
    }

    public float astar(Tile from, Tile to, Predicate<Tile> endTest){
        DistanceHeuristic dh = this::cost;
        World world = state.world;

        outputTracks.clear();
        usedOtherTrack.clear();

        IntFloatMap costs = new IntFloatMap();
        ObjectMap<Tile, Tile> searchParent = new ObjectMap<>();
        PriorityQueue<Tile> queue = new PriorityQueue<>(100,
                (a, b) -> Float.compare(
                        costs.get(world.index(a), 0f) + dh.cost(a, to),
                        costs.get(world.index(b), 0f) + dh.cost(b, to)));

        queue.add(from);
        Tile end = null;
        boolean found = false;
        while(!queue.isEmpty()){
            Tile parent = queue.poll();
            float baseCost = costs.get(world.index(parent), 0f);
            if(endTest.test(parent)){
                found = true;
                end = parent;
                break;
            }
            world.adjacentsOf(parent, child -> {
                if(state.isPassable(player, child) &&
                        !(world.getCity(player.position) == null &&
                                player.position == parent && player.position.directionTo(child) != null &&
                                player.position.directionTo(child).opposite(player.direction))){

                    float newCost = dh.cost(parent, child) + baseCost;

                    if(!costs.containsKey(world.index(child)) || newCost < costs.get(world.index(child), Float.POSITIVE_INFINITY)){
                        searchParent.put(child, parent);
                        costs.put(world.index(child), newCost);
                        queue.add(child);

                        //update chain of "used other's track" flags
                        if(usedOtherTrack.contains(parent.index())){
                            usedOtherTrack.add(child.index());
                        }else{
                            usedOtherTrack.remove(child.index());
                        }

                        //check if another player has this track, and if that is the case, mark this tile as
                        //using someone else's track
                        if(state.players.contains(p -> p != player && p.hasTrack(from, to))){
                            usedOtherTrack.add(child.index());
                        }
                    }
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
            totalCost += cost(searchParent.get(current), current);

            //add up direct track costs
            Tile cfrom = searchParent.get(current), cto = current;
            if(!hasTrack(cfrom, cto)){
                newTrackCost += state.getTrackCost(cfrom, cto);
                outputTracks.add(cfrom.x, cfrom.y, cto.x, cto.y);
            }else if(!movedOnOtherTrack && state.players.contains(p -> p.hasTrack(cfrom, cto))){
                newTrackCost += State.otherMoveTrackCost;
                movedOnOtherTrack = true;
            }

            current = searchParent.get(current);
        }

        tiles.reverse();

        return totalCost;
    }

    /** Cost heuristic for two tiles.*/
    float cost(Tile from, Tile to){
        //note that the board is a non-euclidean space, since ports exist!
        //this may not be an overestimate in some cases
        float dst = tileDst(from, to);

        //not adjacent, no idea how much they cost, just return an underestimate
        if(!state.world.isAdjacent(from, to)){
            return dst;
        }

        //cost of two tiles with track across them is just the amount of moves (1)
        if(hasTrack(from, to)){
            return 1f;
        }

        //when moving across ports, the cost is equal to the movement speed since
        //the player loses all their movement points that turn, but no money
        //this takes an average amount of turns lost of 1/2
        if(state.world.samePort(from, to)){
            return player.loco.speed/2;
        }

        //other players may have track here, which takes 4 ECU per turn
        if(state.players.contains(p -> p.hasTrack(from, to))){
            if(!usedOtherTrack.contains(state.world.index(from))){
                //no track between these, so it costs ECU
                return 1f + costScale(State.otherMoveTrackCost);
            }else{
                //if the track has already been used, the cost is just the moves (1)
                return 1f;
            }
        }

        //no links whatsoever, return base track cost to build here
        return 1f + costScale(state.getTrackCost(from, to));
    }

    float costScale(int base){
        return base * 6f;
    }

    boolean hasTrack(Tile from, Tile to){
        return player.hasTrack(from, to) || (state.world.sameCity(from, to) && state.world.isAdjacent(from, to))
                || inputTracks.has(from.x, from.y, to.x, to.y);
    }

    public float tileDst(Tile from, Tile to){
        vec.set(EmpireCore.control.toWorld(from.x, from.y));
        return Math.round(vec.dst(EmpireCore.control.toWorld(to.x, to.y)) / tilesize);
    }

    //interfaces

    interface DistanceHeuristic{
        float cost(Tile from, Tile to);
    }
}
