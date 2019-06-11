package empire.ai;

import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.GameEvents.EventEvent;
import empire.game.*;
import empire.game.World.*;
import io.anuke.arc.Events;
import io.anuke.arc.collection.*;
import io.anuke.arc.util.*;

import java.util.Arrays;

public class PlannedAI extends AI{
    /** Money after which the AI will consider upgrading their loco.*/
    private static final int upgradeAfterMoney = 40;
    /** Demand cost scale: how many units to reduce a score by, per ECU.*/
    private static final float demandCostScale = 250f;
    /** All the action combinations of 3 ordered demand cards with cargo space 2.*/
    private static final int[][] combinations = {
            //{1, -1, 2, 3, -2, -3},
            //{1, -1, 2, 3, -3, -2},
            //{1, 2, -1, -2, 3, -3},
            //{1, 2, -2, -1, 3, -3},
            {1, -1, 2, -2, 3, -3}
    };
    /** All the action combinations of 3 ordered demand cards with cargo space 3.*/
    private static final int[][] cargo3Combinations = {
            /*{1, 2, 3, -1, -2, -3},
            {1, 2, 3, -1, -3, -2},
            {1, 2, 3, -2, -1, -3},
            {1, 2, 3, -2, -3, -1},
            {1, 2, 3, -3, -1, -2},
            {1, 2, 3, -3, -2, -1},*/
    };
    /** List of planned actions.*/
    private Array<PlanAction> plan = new Array<>();

    public PlannedAI(Player player, State state){
        super(player, state);

        Events.on(EventEvent.class, e -> {
            updatePlan();
        });
    }

    @Override
    public void act(){
        //select a random start location if not chosen already
        if(!player.chosenLocation){
            selectLocation();
        }

        //update the plan if it's empty
        if(plan.isEmpty()){
            updatePlan();
        }

        executePlan();

        end();
    }

    void executePlan(){
        Log.info("| Turn {0}", state.turn);
        Array<Tile> finalPath = new Array<>();
        boolean shouldMove = !state.isPreMovement();

        boolean moved = true;

        //keep attempting to move unless nothing happened
        while(moved){
            moved = false;

            if(plan.size == 0){
                Log.info("No plan.");
                return;
            }

            PlanAction action = plan.peek();

            Log.info("Execute action {0}", action.getClass().getSimpleName() + action.toString());

            //player already has some cargo to unload; find a city to unload to
            if(action instanceof UnloadPlan){
                UnloadPlan u = (UnloadPlan) action;
                String good = u.good;

                //queue a move to this location
                astar(player.position, state.world.tile(u.city));
                finalPath.clearAdd(astarTiles);

                //check if player can deliver this good
                City atCity = state.world.getCity(player.position);
                if(atCity == u.city && player.canDeliverGood(atCity, good)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position) && player.cargo.contains(good)){
                        SellCargo sell = new SellCargo();
                        sell.cargo = good;
                        sell.act();
                        moved = true;
                        plan.pop();
                        updatePlan();
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't sell {0} at {1}, waiting.", good, atCity.name);
                        shouldMove = false;
                    }
                }
            }else if(action instanceof LoadPlan){
                LoadPlan l = (LoadPlan) action;
                String good = l.good;

                //queue a move to this location
                astar(player.position, state.world.tile(l.city));
                finalPath.clearAdd(astarTiles);

                //check if player can deliver this good
                City atCity = state.world.getCity(player.position);
                if(atCity == l.city && atCity.goods.contains(good)){
                    //attempt to load up cargo if possible
                    if(state.canLoadUnload(player, player.position)){
                        LoadCargo load = new LoadCargo();
                        load.cargo = good;
                        load.act();
                        plan.pop();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't load {0} at {1}, waiting.", good, atCity.name);
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
                            plan.pop();
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

        //upgrade if the player can do it now; only happens after a money threshold
        if(state.player() == player &&
                player.money > upgradeAfterMoney && player.loco != Loco.fastFreight){
            new UpgradeLoco(){{
                type = 0;
            }}.act();
        }
    }

    /**
     * The general idea behind planning:
     * Figure out which actions would result in the best profit.
     * Each card has 3 demands.
     *   Each demand card has two actions associated with it: loading and unloading.
     *   These actions do not necessarily need to be done immediately with one after another;
     *   it may be the case that loading up two things, then unloading two things is a better idea.
     *   Thus, this AI must scan through every valid combinations of loads and unloads.
     * There are 3 * 3 * 3 different combinations of demand cards that this AI can do.
     *
     * Go through every combination of possible planned demands, find the best one. (27)
     * Then, act on it by pathfinding and doing each one every frame.
     *
     */
    void updatePlan(){
        Log.info("Updating plan. Money: {0}", player.money);
        plan.clear(); //clear old data, it's not useful anymore

        //player can win if they place track and connect cities
        if(player.money > State.winMoneyAmount){
            //TODO attempt to connect track
        }

        Demand[] bestCombination = new Demand[3];
        Demand[] inDemands = new Demand[3];
        int[] bestPlan = null;
        float bestCost = Float.POSITIVE_INFINITY;

        //find the best sequence of demands possible
        for(Demand first : allDemands()){
            for(Demand second : allDemands(first)){
                for(Demand third : allDemands(first, second)){
                    inDemands[0] = first;
                    inDemands[1] = second;
                    inDemands[2] = third;

                    //check all plan combinations
                    for(int[] plan : combinations){
                        float cost = evaluatePlan(inDemands, plan);
                        if(cost < bestCost){
                            Log.info("Plan '{0}' is better than plan '{1}'" +
                                    " with scores {2} > {3}.", Arrays.toString(plan), Arrays.toString(bestPlan), bestCost, cost);
                            bestCost = cost;
                            bestPlan = plan;
                            bestCombination[0] = first;
                            bestCombination[1] = second;
                            bestCombination[2] = third;
                        }
                    }

                    //check 3-cargo plans if applicable
                    if(player.loco.loads - player.cargo.size == 3){
                        for(int[] plan : cargo3Combinations){
                            float cost = evaluatePlan(inDemands, plan);
                            if(cost < bestCost){
                                bestCost = cost;
                                bestPlan = plan;
                                bestCombination[0] = first;
                                bestCombination[1] = second;
                                bestCombination[2] = third;
                            }
                        }
                    }
                }
            }
        }

        if(bestPlan == null){
            Log.info("All plans are invalid, you're screwed.");
            return;
        }

        makePlan(bestCombination, bestPlan);

        Log.info(Array.with(bestCombination).toString(", ", d -> d.good + " to " + d.city.name));
    }

    void makePlan(Demand[] demands, int[] inPlan){
        Tile currentTile = player.position;

        for(int value : inPlan){
            boolean unload = value < 0;
            //demand that is being evaluated
            Demand demand = demands[Math.abs(value) - 1];

            if(unload){
                plan.add(new UnloadPlan(demand.city, demand.good));
                //update new position
                currentTile = state.world.tile(demand.city);
            }else if(!player.cargo.contains(demand.good)){ //only plan to load if you don't have this good
                Tile position = currentTile;
                //find best city to get load from
                City loadFrom = Array.with(state.world.cities())
                        .select(s -> s.goods.contains(demand.good))
                        .min(city -> astar(position, state.world.tile(city)));
                plan.add(new LoadPlan(loadFrom, demand.good));
                //update new position since you have to go to this city to load from it
                currentTile = state.world.tile(loadFrom);
            }
        }

        Log.info("Created plan:\n| | {0}", plan.toString("\n| | ",
                s -> s.getClass().getSimpleName() + s.toString()));

        plan.reverse();
    }

    /** Evaluates a plan of action for 3 demands.
     * The action sequence's format is [demand index + 1] * (unload ? -1 : 1).
     */
    float evaluatePlan(Demand[] demands, int[] sequence){
        Tile currentTile = player.position;
        float finalCost = 0f;
        //keep track of money to prevent illegal dead-end moves
        int currentMoney = player.money;

        for(int value : sequence){
            //whether this is a load or unload action
            boolean unload = value < 0;
            //demand that is being evaluated
            Demand demand = demands[Math.abs(value) - 1];

            if(unload){
                //unload: just pathfind to the sell city, add cost
                finalCost += astar(currentTile, state.world.tile(demand.city));
                currentTile = state.world.tile(demand.city);

                //make sure you can actually get to there to unload it!
                currentMoney -= astarNewTrackCost;
                //TODO implement
                //if(currentMoney < 0) return Float.POSITIVE_INFINITY;

                //assume you sold it, update money
                currentMoney += demand.cost;

                //factor in demand cost
                finalCost -= demand.cost * demandCostScale;
            }else{
                Tile position = currentTile;
                //load: find the closest city to load up this good (to the current tile)
                City min = Array.with(state.world.cities())
                        .select(s -> s.goods.contains(demand.good))
                        .min(city -> astar(position, state.world.tile(city)));

                //update the final cost and position based on this
                finalCost += astar(position, state.world.tile(min));
                currentMoney -= astarNewTrackCost;
                currentTile = state.world.tile(min);
            }

            //bail out if at any point this AI runs out of money
            //this doesn't work currently!
            //TODO implement
            if(currentMoney < 0){
            //    return Float.POSITIVE_INFINITY;
            }
        }
        return finalCost;
    }

    /** Returns an array of valid demands, given that the passed demands have already been used.*/
    Array<Demand> allDemands(Demand... alreadyUsed){
        Array<Demand> out = new Array<>();
        Array.with(player.demandCards).select(card -> !Structs.contains(card.demands,
                d -> Structs.contains(alreadyUsed, d)))
                .each(c -> out.addAll(c.demands));
        return out;
    }

    /** Updates the plan to link cities. Clears all old plans.*/
    void linkCities(){
        Array<City> majors = Array.with(state.world.cities()).select(c -> c.size == CitySize.major);
        //found city with maximum number of connections.
        City maxConnected = majors.max(city -> state.countConnectedCities(player, state.world.tile(city)));
        ObjectSet<Tile> connected = state.connectedTiles(player, state.world.tile(maxConnected));

        //find connected and unconnected cities
        Array<City> connectedCities = majors.select(c -> connected.contains(state.world.tile(c)));
        Array<City> unconnectedCities = majors.select(c -> !connected.contains(state.world.tile(c)));

        //now, find the best cities that are unconnected to connect to the ones that are not
        //this is done by computing a 'connection cost' of a city to a group of tiles, then ordering cities by that cost
        ObjectFloatMap<City> costs = new ObjectFloatMap<>();
        ObjectMap<City, City> linkages = new ObjectMap<>();
        unconnectedCities.each(city -> {
            float minCost = Float.POSITIVE_INFINITY;
            City minCity = null;
            for(City other : unconnectedCities){
               float dst = astar(state.world.tile(city), state.world.tile(other), connected::contains);
               if(dst < minCost){
                   minCity = other;
                   minCost = dst;
               }
            }

            costs.put(city, minCost);
            linkages.put(city, minCity);
        });

        //sort by min cost
        unconnectedCities.sort(Structs.comparingFloat(c -> costs.get(c, 0f)));

        //clear plan and add link plans
        plan.clear();

        for(int i = 0; i < State.winCityAmount - connectedCities.size; i ++){
            City city = unconnectedCities.get(i);
            plan.add(new LinkCities(city, linkages.get(city)));
        }

        plan.reverse();
    }

    abstract class PlanAction{

    }

    class LinkCities extends PlanAction{
        final City from, to;

        public LinkCities(City from, City to){
            this.from = from;
            this.to = to;
        }

        public String toString(){
            return "{"+from.name + " to " + to.name+"}";
        }
    }

    class LoadPlan extends PlanAction{
        final City city;
        final String good;

        public LoadPlan(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " from " + city.name+"}";
        }
    }

    class UnloadPlan extends PlanAction{
        final City city;
        final String good;


        public UnloadPlan(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " to " + city.name+"}";
        }
    }
}
