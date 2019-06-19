package empire.ai;

import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.*;
import empire.game.World.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.util.*;

/** Next iteration of this AI.*/
public class NextAI extends AI{
    private static final String defaultStartingCity = "ruhr";
    /** Whether to choose a location.*/
    private static final boolean chooseLocation = false;
    /** Money after which the AI will consider upgrading their loco.*/
    private static final int upgradeAfterMoney = 60;
    /** Demand cost scale: how many units to reduce a score by, per ECU.*/
    private static final float demandCostScale = 20f;

    /** List of planned actions.*/
    private Plan plan = new Plan(new Array<>());
    /** Object for handling pathfinding.*/
    private Astar astar;
    /** Plans skipped due to branch and bound.*/
    private int skipped = 0;

    public NextAI(Player player, State state){
        super(player, state);
        astar = new Astar(player);
    }

    @Override
    public void act(){
        //select a random start location if not chosen already
        if(!player.chosenLocation && waitAsync()){
            selectLocation();
        }

        //update the plan if it's empty
        if(plan.actions.isEmpty() && waitAsync()){
            async(this::updatePlan);
        }

        //wait until plan is ready to be executed; if it is, execute it; then end the turn if it's over
        if(waitAsync()){
            if(executePlan()){
                end();
            }
        }

        state.checkIfWon(player);
    }

    /** Attempts to execute the plan.
     * @return whether or not this turn should end. */
    boolean executePlan(){
        //path that this AI will attempt to travel or build to
        Array<Tile> finalPath = new Array<>();
        //whether this AI should move this turn
        boolean shouldMove = !state.isPreMovement();
        //whether something happened last iterations
        boolean moved = true;
        //where the player should start building from
        Tile startTile = player.position;

        Log.info("MOVING. Turn {0}, {1} ECU.", state.turn, player.money);

        while(moved && !plan.actions.isEmpty()){
            moved = false;

            //get current action that should be executed
            NextAction action = plan.actions.peek();

            Log.info("| Executing action {0}.", action.getClass().getSimpleName() + action.toString());

            //player already has some cargo to unload; find a city to unload to
            if(action instanceof UnloadAction){
                UnloadAction u = (UnloadAction) action;
                String good = u.good;

                astar.astar(startTile, state.world.tile(u.city));
                finalPath.set(astar.tiles);

                //check if player can deliver this good right now
                City atCity = state.world.getCity(player.position);
                if(atCity == u.city && player.canDeliverGood(atCity, good)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position) && player.cargo.contains(good)){
                        SellCargo sell = new SellCargo();
                        sell.cargo = good;
                        sell.act();
                        //it is done, pop it out
                        plan.actions.pop();

                        //wait to update the plan but don't end the turn
                        async(this::updatePlan);
                        return false;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("| | Can't sell {0} at {1}, waiting.", good, atCity.name);
                        shouldMove = false;
                    }
                }
            }else if(action instanceof LoadAction){
                //load up a good from a specific city
                LoadAction l = (LoadAction) action;
                String good = l.good;

                //queue a move to this location
                astar.astar(player.position, state.world.tile(l.city));
                finalPath.set(astar.tiles);

                //check if player can deliver this good
                City atCity = state.world.getCity(player.position);
                if(atCity == l.city && atCity.goods.contains(good)){
                    //attempt to load up cargo if possible
                    if(state.canLoadUnload(player, player.position)){
                        LoadCargo load = new LoadCargo();
                        load.cargo = good;
                        load.act();
                        plan.actions.pop();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info(" | | Can't load {0} at {1}, waiting.", good, atCity.name);
                        shouldMove = false;
                    }
                }
            }else if(action instanceof LinkCitiesAction){
                LinkCitiesAction l = (LinkCitiesAction) action;

                //a-star from the start to the end, add all the tiles
                ObjectSet<Tile> connected = state.connectedTiles(player, state.world.tile(l.to));
                //finish plan when the city gets connected
                if(connected.contains(state.world.tile(l.from))){
                    plan.actions.pop();
                    moved = true;
                }else{
                    astar.astar(state.world.tile(l.from), state.world.tile(l.to), connected::contains);
                    finalPath.set(astar.tiles);
                    shouldMove = false;
                    startTile = state.world.tile(l.from);
                }
            }

            Tile last = startTile;
            //now place all track if it can
            for(Tile tile : finalPath){
                if(!player.hasTrack(last, tile) && last != tile && !state.world.sameCity(last, tile)
                        && !state.world.samePort(last, tile)){
                    if(state.canPlaceTrack(player, last, tile)){
                        PlaceTrack place = new PlaceTrack();
                        place.from = last;
                        place.to = tile;
                        place.act();
                        moved = true;
                    }else{
                        //can't move or place track, maybe due to an event or maybe because it's out of money
                        Log.info("| | {0}: Can't place track {1} -> {2}", player.name, last.str(), tile.str());
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

                        //moves may skip turns due to ports; if that happens, break out of the whole thing
                        if(state.player() != player){
                            plan.actions.pop();
                            return true;
                        }
                    }else{
                        //can't move due to an event or something
                        Log.info("| | {0}: Can't move {1} -> {2}", player.name, player.position.str(), tile.str());
                        break;
                    }
                }
            }
        }

        //upgrade if the player can do it now; only happens after a money threshold
        if(state.player() == player && player.money > upgradeAfterMoney && player.loco != Loco.fastFreight){
            new UpgradeLoco(){{
                type = 0;
            }}.act();
        }

        return true;
    }

    /** Update the plan, find the best one. */
    void updatePlan(){
        Plan bestPlan = null;
        float bestCost = Float.POSITIVE_INFINITY; //min cost
        int considered = 0;

        Log.info("Updating plan...");
        skipped = 0;

        //find the cheapest plan
        for(Demand first : allDemands()){
            for(Demand second : allDemands(first)){
                for(Demand third : allDemands(first, second)){
                    for(int[] combination : PlanCombinations.all){
                        Plan plan = makePlan(new Demand[]{first, second, third}, combination);
                        float cost = plan.cost(bestCost);
                        considered ++;
                        if(cost < bestCost){
                            bestPlan = plan;
                            bestCost = cost;
                        }
                    }
                }
            }
        }

        Log.info("Considered {0} plans, skipped {1}.", considered, skipped);

        if(bestPlan != null){
            plan = bestPlan;
            //reverse to act on it layer
            plan.actions.reverse();

            //player can win if they place track and connect cities, try doing that
            if(player.money > State.winMoneyAmount/2){
                plan.actions.addAll(planLinkCities());
            }
        }else{
            Log.err("No good plan found.");
        }
    }

    /** Returns an array of valid demands, given that the passed demands have already been used.*/
    Array<Demand> allDemands(Demand... alreadyUsed){
        Array<Demand> out = new Array<>();
        Array.with(player.demandCards).select(card -> !Structs.contains(card.demands,
                d -> Structs.contains(alreadyUsed, d)))
                .each(c -> out.addAll(c.demands));
        return out;
    }

    Plan makePlan(Demand[] demands, int[] combination){
        Array<NextAction> actions = new Array<>();

        astar.begin();
        Tile currentTile = player.position;

        for(int value : combination){
            boolean unload = value < 0;
            Demand demand = demands[Math.abs(value) - 1];

            if(unload){
                astar.astar(currentTile, state.world.tile(demand.city));
                astar.placeTracks();

                actions.add(new UnloadAction(demand.city, demand.good));
                //update new position
                currentTile = state.world.tile(demand.city);
            }else if(!player.cargo.contains(demand.good)){
                //only plan to load if you don't have this good
                Tile position = currentTile;
                //find best city to get load from
                City loadFrom = Array.with(state.world.cities())
                        .select(s -> s.goods.contains(demand.good))
                        .min(city -> astar.astar(position, state.world.tile(city)));

                //add the placed tracks now
                astar.astar(position, state.world.tile(loadFrom));
                astar.placeTracks();

                actions.add(new LoadAction(loadFrom, demand.good));
                //update new position since you have to go to this city to load from it
                currentTile = state.world.tile(loadFrom);
            }
        }

        astar.end();

        return new Plan(actions);
    }

    /** Updates the plan to link cities. Clears all old plans.*/
    Array<NextAction> planLinkCities(){
        Array<City> majors = Array.with(state.world.cities()).select(c -> c.size == CitySize.major);
        //found city with maximum number of connections.
        City maxConnected = majors.max(city -> state.countConnectedCities(player, state.world.tile(city)));
        ObjectSet<Tile> connected = state.connectedTiles(player, state.world.tile(maxConnected));

        //find connected and unconnected cities
        Array<City> connectedCities = majors.select(c -> connected.contains(state.world.tile(c)));
        Array<City> unconnectedCities = majors.select(c -> !connected.contains(state.world.tile(c)));

        //everything's already connected
        if(connectedCities.size >= State.winCityAmount){
            return new Array<>();
        }

        //now, find the best cities that are unconnected to connect to the ones that are not
        //this is done by computing a 'connection cost' of a city to a group of tiles, then ordering cities by that cost
        ObjectFloatMap<City> costs = new ObjectFloatMap<>();
        ObjectMap<City, City> linkages = new ObjectMap<>();
        unconnectedCities.each(city -> {
            float minCost = Float.POSITIVE_INFINITY;
            City minCity = null;
            for(City other : unconnectedCities){
                float dst = astar.astar(state.world.tile(city), state.world.tile(other), connected::contains);
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
        Array<NextAction> actions = new Array<>();

        for(int i = 0; i < State.winCityAmount - connectedCities.size; i ++){
            City city = unconnectedCities.get(i);
            actions.add(new LinkCitiesAction(city, linkages.get(city)));
        }

        actions.reverse();

        return actions;
    }

    void selectLocation(){
        async(() -> {
            if(!chooseLocation){
                player.position = state.world.tile(state.world.getCity(defaultStartingCity));
                player.chosenLocation = true;
            }else{
                throw new IllegalArgumentException("NYI");
            }
        });
    }

    class Plan{
        Array<NextAction> actions;

        Plan(Array<NextAction> actions){
            this.actions = actions;
        }

        /** Calculates a cost for this plan of actions. Disregards city linking actions.
         * Returns plan cost in moves.*/
        float cost(float bestSoFar){
            Tile position = player.position;
            float total = 0f;
            int money = player.money;
            int cargoUsed = player.cargo.size;

            float totalProfit = actions.sum(a -> a instanceof UnloadAction ?
                    player.allDemands().find(d -> d.good.equals(((UnloadAction) a).good)
                            && d.city == ((UnloadAction) a).city).cost : 0f) * demandCostScale;

            astar.begin();

            for(NextAction action : actions){
                if(action instanceof LoadAction){
                    LoadAction l = (LoadAction)action;

                    total += astar.astar(position, state.world.tile(l.city));
                    money -= astar.newTrackCost;
                    position = state.world.tile(l.city);
                    cargoUsed ++;

                    astar.placeTracks();

                    //when the player runs out of money, bail out, this plan isn't possible
                    //also bail out if player has no cargo space to hold this new load
                    if(money < 0 || cargoUsed > player.loco.loads) return Float.POSITIVE_INFINITY;
                }else if(action instanceof UnloadAction){
                    UnloadAction u = (UnloadAction)action;

                    total += astar.astar(position, state.world.tile(u.city));
                    money -= astar.newTrackCost;
                    cargoUsed --;

                    astar.placeTracks();

                    //when the player runs out of money, bail out, this plan isn't possible
                    if(money < 0) return Float.POSITIVE_INFINITY;

                    //get money earned
                    int earned = player.allDemands().find(d -> d.good.equals(u.good) && d.city == u.city).cost;

                    //after checking money, add unloaded cost by finding the correct demand
                    money += earned;
                }

                //branch and bound step: total is only going to get higher from here, if it's already over the best
                //drop out
                if(total - totalProfit > bestSoFar){
                    skipped ++;
                    //return Float.POSITIVE_INFINITY;
                }
            }

            astar.end();

            return total - totalProfit;
        }
    }

    //action classes

    class LinkCitiesAction extends NextAction{
        final City from, to;

        public LinkCitiesAction(City from, City to){
            this.from = from;
            this.to = to;
        }

        public String toString(){
            return "{"+from.name + " to " + to.name+"}";
        }
    }

    class LoadAction extends NextAction{
        final City city;
        final String good;

        public LoadAction(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " from " + city.name+"}";
        }
    }

    class UnloadAction extends NextAction{
        final City city;
        final String good;


        public UnloadAction(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " to " + city.name+"}";
        }
    }


    abstract class NextAction{

    }
}
