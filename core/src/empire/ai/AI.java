package empire.ai;

import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.Player;
import empire.game.State;
import empire.game.World;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.GridBits;
import io.anuke.arc.collection.IntFloatMap;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.Tmp;

import java.util.PriorityQueue;

/** Handles the AI for a specific player.*/
public class AI{
    /** The player this AI controls.*/
    public final Player player;
    /** The game state.*/
    public final State state;

    /** list of planned actions */
    //private Array<Action> plan = new Array<>();

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
        //if(plan.isEmpty()){
        //    updatePlan();
        //}

        move();

        end();
    }

    /** Acts on the AI's plan.
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
    }*/

    void move(){
        Array<Tile> finalPath = new Array<>();
        boolean shouldMove = !state.isPreMovement();

        boolean moved = true;

        //keep attempting to move unless nothing happened
        while(moved){
            moved = false;

            //player already has some cargo to unload; find a city to unload to
            if(!player.cargo.isEmpty()){
                String good = player.cargo.peek();
                Array<Tile> outArray = new Array<>();
                float minBuyCost = Float.MAX_VALUE;
                City minBuyCity = null;

                //find best city to sell good to
                for(City city : Array.with(state.world.cities()).select(s -> player.canDeliverGood(s, good))){
                    //get a* cost to this city
                    float dst = astar(player.position, state.world.tile(city.x, city.y), outArray);

                    //if this source city is better, update things
                    if(dst < minBuyCost){
                        minBuyCost = dst;
                        minBuyCity = city;
                        finalPath.clear();
                        finalPath.addAll(outArray);
                    }
                }

                //check if player is at a city that they can deliver to right now
                City atCity = state.world.getCity(player.position);
                if(player.canDeliverGood(atCity, good)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position)){
                        SellCargo sell = new SellCargo();
                        sell.cargo = good;
                        sell.act();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't sell {0} at {1}, waiting.", good, atCity.name);
                        shouldMove = false;
                    }
                }
            }else{

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

                        //if this source city is better, update things
                        if(dst < minBuyCost){
                            minBuyCost = dst;
                            minBuyCity = city;
                        }
                    }

                    //update cost to reflect the base good cost
                    minBuyCost -= demand.cost * 2f;

                    if(minBuyCost < bestCost){
                        bestCost = minBuyCost;
                        bestGood = demand.good;
                        bestSellCity = demand.city;
                        bestLoadCity = minBuyCity;
                    }
                }

                if(bestLoadCity == null) throw new IllegalArgumentException("No city to load from found!");

                //now the best source and destination has been found.
                finalPath.clear();
                //move from position to the city
                astar(player.position, state.world.tile(bestLoadCity.x, bestLoadCity.y), finalPath);

                //check if player is at a city that they can get cargo from right now
                City atCity = state.world.getCity(player.position);
                if(atCity == bestLoadCity && atCity.goods.contains(bestGood)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position)){
                        LoadCargo load = new LoadCargo();
                        load.cargo = bestGood;
                        load.act();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't load {0} at {1}, waiting.", bestGood, atCity.name);
                        shouldMove = false;
                    }
                }
            }

            Tile last = player.position;
            //now place all track if it can
            for(Tile tile : finalPath){
                if(!player.hasTrack(last, tile)){
                    if(state.canPlaceTrack(player, last, tile)){
                        PlaceTrack place = new PlaceTrack();
                        place.from = last;
                        place.to = tile;
                        place.act();
                        moved = true;
                    }else{
                        //can't move or place track, maybe due to an event
                        Log.info("{0}: Can't place track {1} -> {2}", player.name, last.str(), tile.str());
                        break;
                    }
                }
                last = tile;
            }

            //if the AI should move, try to do so
            if(shouldMove){
                for(Tile tile : finalPath){
                    if(state.canMove(player, tile)){
                        Move move = new Move();
                        move.to = tile;
                        move.act();
                        moved = true;

                        //moves may skip turns; if that happens, break out of the whole thing
                        if(state.player() != player){
                            return;
                        }
                    }else{
                        //can't move due to an event or something
                        Log.info("{0}: Can't move {1} -> {2}", player.name, player.position.str(), tile.str());
                        break;
                    }
                }
            }
        }
    }

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
