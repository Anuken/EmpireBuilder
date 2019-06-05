package empire.ai;

import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.Player;
import empire.game.State;
import empire.game.World;
import empire.game.World.City;
import empire.game.World.CitySize;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.GridBits;
import io.anuke.arc.collection.IntFloatMap;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.serialization.Json;
import io.anuke.arc.util.serialization.JsonValue;

import java.util.PriorityQueue;

/** Handles the AI for a specific player.*/
public class AI{
    /** The player this AI controls.*/
    public final Player player;
    /** The game state.*/
    public final State state;

    /** list of planned actions */
    private Array<Action> plan = new Array<>();

    public AI(Player player, State state){
        this.player = player;
        this.state = state;
    }

    /** Performs actions on this AI's turn.*/
    public void act(){
        if(!player.chosenLocation){
            selectLocation();
        }

        //update the plan if it's empty
        if(plan.isEmpty()){
            updatePlan();
        }

        actPlan();

        end();
    }

    /** Acts on the AI's plan.*/
    void actPlan(){
        if(false)
        Log.info(new Json(){{
            setSerializer(Tile.class, new Serializer<Tile>(){
                @Override
                public void write(Json json, Tile object, Class knownType){
                    json.writeValue("Tile[" + object.x + "," + object.y + "]");
                }

                @Override
                public Tile read(Json json, JsonValue jsonData, Class type){
                    return null;
                }
            });
        }}.prettyPrint(plan));

        while(!plan.isEmpty()){
            Action action = plan.peek();
            //moves may require rail placement
            if(action instanceof Move){
                Move move = (Move)action;

                //useless move
                if(player.position == move.to){
                    plan.pop();
                    continue;
                }

                if(!player.hasTrack(player.position, move.to) &&
                        //don't place track between ports, it's pointless
                        state.world.isAdjacent(player.position, move.to) &&
                        //don't overwrite other's tracks
                        !state.players.contains(p -> p.hasTrack(player.position, move.to))){
                    int cost = state.getTrackCost(player.position, move.to);
                    if(state.canSpendRail(player, cost)){
                        //place a track if applicable
                        PlaceTrack place = new PlaceTrack();
                        place.from = player.position;
                        place.to = move.to;
                        place.act();
                    }else{
                        //end the turn, out of money for this turn
                        break;
                    }
                }

                //can't move, stuck.
                if(!state.canMove(player, move.to)){
                    Log.info("{0}: can't move to {1}", player.name, move.to.str());
                    break;
                }

                //can't move if no moves left.
                if(player.moved + 1 > player.loco.speed){
                    break;
                }
            }

            plan.pop();
            action.act();
        }
    }

    void updatePlan(){
        City bestSellCity = null, bestLoadCity = null;
        String bestGood = null;
        float bestCost = Float.MAX_VALUE;

        Array<Tile> outArray = new Array<>();

        //find best demand
        for(Demand demand : player.allDemands()){
            float minBuyCost = Float.MAX_VALUE;
            City minBuyCity = null;

            //find best city to get good from for this demand
            for(City city : Array.with(state.world.cities()).select(s -> s.goods.contains(demand.good))){
                //get a* cost: from the player to the city, then from the city to the final destination
                float dst = astar(player.position, state.world.tile(city.x, city.y), outArray) +
                            astar(state.world.tile(city.x, city.y),
                                    state.world.tile(demand.city.x, demand.city.y), outArray);

                if(city.size == CitySize.major){
                //    dst -= 10f;
                }

                //if this source city is better, update things
                if(dst < minBuyCost){
                    minBuyCost = dst;
                    minBuyCity = city;
                }
            }

            //update cost to reflect the base good cost
            minBuyCost -= demand.cost*2f;

            if(minBuyCost < bestCost){
                bestCost = minBuyCost;
                bestGood = demand.good;
                bestSellCity = demand.city;
                bestLoadCity = minBuyCity;
            }
        }

        if(bestLoadCity == null) throw new IllegalArgumentException("No city to load from found!");

        //now the best source and destination has been found.
        outArray.clear();
        //move from position to the city
        astar(player.position, state.world.tile(bestLoadCity.x, bestLoadCity.y), outArray);
        for(Tile tile : outArray){
            Move move = new Move();
            move.to = tile;
            plan.add(move);
        }

        //load the cargo
        LoadCargo load = new LoadCargo();
        load.cargo = bestGood;
        plan.add(load);

        //move to the city to sell to
        astar(state.world.tile(bestLoadCity.x, bestLoadCity.y),
                state.world.tile(bestSellCity.x, bestSellCity.y), outArray);
        for(Tile tile : outArray){
            Move move = new Move();
            move.to = tile;
            plan.add(move);
        }

        //sell the cargo
        SellCargo sell = new SellCargo();
        sell.cargo = bestGood;
        plan.add(sell);

        //reverse it for easy access (pop)
        plan.reverse();
    }

    float astar(Tile from, Tile to, Array<Tile> out){
        DistanceHeuristic dh = this::tileDst;
        TileHeuristic th = this::cost;
        World world = state.world;

        GridBits closed = new GridBits(world.width, world.height);
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
            if(next == to || world.getMajorCity(next) == to.city){
                found = true;
                end = next;
                break;
            }
            closed.set(next.x, next.y);
            world.adjacentsOf(next, child -> {
                if(state.isPassable(player, child) && !closed.get(child.x, child.y)){
                    closed.set(child.x, child.y);
                    child.searchParent = next;
                    costs.put(world.index(child), th.cost(next, child) + baseCost);
                    queue.add(child);
                }
            });
        }

        out.clear();

        if(!found) return Float.MAX_VALUE;

        Tile current = end;
        while(current != from){
            out.add(current);
            current = current.searchParent;
        }

        out.reverse();

        return costs.get(world.index(to), 0f);
    }

    float cost(Tile from, Tile to){
        if(player.hasTrack(from, to)){
            return 0.5f;
        }
        if(state.players.contains(p -> p.hasTrack(from, to))){
            return State.otherMoveTrackCost * 4;
        }
        return state.getTrackCost(from, to) * 4;
    }

    float tileDst(int x, int y, int x2, int y2){
        return Mathf.dst(x, y, x2, y2);
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
