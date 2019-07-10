package empire.ai;

import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.*;
import empire.game.World.*;
import io.anuke.arc.Core;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.util.*;

/** Next iteration of this AI.*/
public class NextAI extends AI{
    private static final String defaultStartingCity = "ruhr";
    /** Whether to choose a location.*/
    private static final boolean chooseLocation = true;
    /** Money after which the AI will consider upgrading their loco.*/
    private static final int upgradeAfterMoney = 60;
    /** Demand cost scale: how many units to reduce a score by, per ECU.
     * If this value is, for example, 10, this AI will move 10 extra spaces to gain 1 ECU. */
    private static final float demandCostScale = 6;

    /** Listener to visualizer events.*/
    private AIListener listener = new AIListener(){};
    /** List of planned actions.*/
    private Plan plan = new Plan(new Array<>());
    /** Object for handling pathfinding.*/
    private Astar astar;
    /** Plans skipped due to branch and bound.*/
    private int skipped = 0;
    /** Worst plan accepted.*/
    private float worstPlan;

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
        if(plan.actions.isEmpty() && !state.hasWinner && waitAsync()){
            Log.info("No plan, updating...");
            async(this::updatePlan);
        }

        //wait until plan is ready to be executed; if it is, execute it; then end the turn if it's over
        if(waitAsync()){
            if(plan.bad){
                //discard cards and end turn
                new DiscardCards().act();
                //also clear actions to cause a recalculation next turn
                plan.actions.clear();
            }else if(executePlan()){
                end();
            }
        }

        state.checkIfWon(player);
    }

    /** Sets a listener to handle planning events.*/
    public void setListener(AIListener listener){
        this.listener = listener;
    }

    /** Test-only function. Used for whatever is necessary at the time.*/
    public void test(){
        float c1 = new Plan(Array.with(
            new LoadAction(state.world.getCity("beograd"), "oil"),
            new UnloadAction(state.world.getCity("zurich"), "oil"),
            new LoadAction(state.world.getCity("beograd"), "oil"),
            new LoadAction(state.world.getCity("ruhr"), "steel"),
            new UnloadAction(state.world.getCity("valencia"), "steel"),
            new UnloadAction(state.world.getCity("madrid"), "oil")
        )).cost(0);

        Log.info("Awful plan cost: " + c1);

        float c2 = new Plan(Array.with(
            new LoadAction(state.world.getCity("beograd"), "oil"),
            new LoadAction(state.world.getCity("beograd"), "oil"),
            new UnloadAction(state.world.getCity("zurich"), "oil"),
            new LoadAction(state.world.getCity("ruhr"), "steel"),
            new UnloadAction(state.world.getCity("valencia"), "steel"),
            new UnloadAction(state.world.getCity("madrid"), "oil")
        )).cost(0);

        Log.info( "Cool and reasonable plan cost: " + c2);
    }

    /** Asynchronously calculates a plan and returns a preview of the result. Debugging only.*/
    public void previewPlan(Consumer<String> result){
        executor.submit(() -> {
            updatePlan();

            Core.app.post(() -> {
                plan.actions.reverse();
                result.accept(Strings.format("---\n{0}\n---",
                        plan.actions.toString("\n", n -> n.getClass().getSimpleName() + n.toString())));
            });
        });
    }

    /** @return whether the current cards are terrible and should be discarded. */
    boolean cardsAreTerrible(Plan plan){
        return false;//state.turn > 10 && plan.cost(0f) > -90;
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
                    if(!player.cargo.contains(good)){
                        Log.err("No cargo to deliver, what?");
                        async(this::updatePlan);
                        return false;
                    }

                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position)){
                        SellCargo sell = new SellCargo();
                        sell.cargo = good;
                        sell.act();
                        //it is done, pop it out
                        plan.actions.pop();

                        Log.info("Sold {0} to {1}, updating plan.", sell.cargo, atCity.name);

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

                //the player can already have this cargo; if the city doesn't have this good, just ignore this load request
                //ALSO ignore the path!
                if(player.cargo.contains(good) && (atCity == null || !atCity.goods.contains(good))){
                    plan.actions.pop();
                    return false;
                }else if(atCity == l.city && atCity.goods.contains(good)){
                    //attempt to load up cargo if possible
                    if(state.canLoadUnload(player, player.position)){
                        LoadCargo load = new LoadCargo();
                        load.cargo = good;

                        if(player.cargo.size >= player.loco.loads){
                            String dump = player.cargo.find(p -> !player.allDemands().contains(d -> d.good.equals(p)));
                            if(dump == null){
                                dump = player.cargo.min(p -> player.allDemands().find(d -> d.good.equals(p)).cost);
                            }

                            String fdump = dump;

                            new DumpCargo(){{
                                cargo = fdump;
                            }}.act();

                            load.act();

                            if(player.allDemands().contains(d -> d.good.equals(good)) && !good.equals(load.cargo)){
                                Log.info("Dumped {0}, updating plan.", fdump);

                                //dumped some cargo, what now?
                                async(this::updatePlan);
                            }
                            plan.actions.pop();
                            return false;
                        }

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


            if(state.world.getCity(player.position) != null && state.canLoadUnload(player, player.position) &&
                    player.cargo.size < player.loco.loads){
                City city = state.world.getCity(player.position);
                if(!city.goods.isEmpty()){
                    LoadCargo load = new LoadCargo();
                    load.cargo = city.goods.max(good -> {
                        Demand best = player.allDemands().find(d -> d.good.equals(good));
                        if(best != null){
                            return best.cost;
                        }else{
                            //if the player already has this cargo, return a lower value since that's worse
                            return player.cargo.contains(good) ? -1 : 0;
                        }
                    });
                    Log.info("Goods: {0} Result: {1}", city.goods, load.cargo);
                    load.act();
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
        if(state.player() == player && player.money > upgradeAfterMoney && player.loco != Loco.superFreight){
            new UpgradeLoco(){{
                type = 0;
            }}.act();
        }

        return true;
    }

    /** Update the plan, find the best one. */
    void updatePlan(){
        Core.app.post(listener::planningBegin);

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
                        Core.app.post(() -> listener.planConsidered(plan.copy(), cost));
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
            Log.info("---\nFinal plan: \n{0}\n---",
                    bestPlan.actions.toString("\n", n -> n.getClass().getSimpleName() + n.toString()));

            plan = bestPlan;
            //reverse to act on it layer
            plan.actions.reverse();

            worstPlan = Math.max(worstPlan, bestCost);
            Log.info("Current worst plan: {0}", worstPlan);
            Log.info("Plan cost: {0}", bestCost);

            //mark the plan as bad if it's terrible; cards will be discarded
            if(cardsAreTerrible(plan)){
                Log.info("| | Terrible plan, discarding cards!");
                plan.bad = true;
            }

            //player can win if they place track and connect cities, try doing that
            if(player.money > State.winMoneyAmount/2){
                plan.actions.addAll(planLinkCities());
            }

            Core.app.post(() -> listener.planChosen(plan.copy()));
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
            }else{
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

        //now, find the best cities that are connected to connect to the ones that are not
        //this is done by computing a 'connection cost' of a city to a group of tiles, then ordering cities by that cost
        ObjectFloatMap<City> costs = new ObjectFloatMap<>();
        ObjectMap<City, City> linkages = new ObjectMap<>();
        unconnectedCities.each(city -> {
            float minCost = Float.POSITIVE_INFINITY;
            City minCity = null;
            for(City other : connectedCities){
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
                int i = 0;
                City best = null;
                float bestCost = Float.POSITIVE_INFINITY;
                for(City city : Array.with(state.world.cities()).select(c -> c.size == CitySize.major)){
                    player.position = state.world.tile(city);
                    Log.info("Check city {0}/8", i++);
                    updatePlan();

                    if(plan.lastCost < bestCost){
                        best = city;
                        bestCost = plan.lastCost;
                    }
                }
                Log.info("Chose city {0}", best.name);
                player.position = state.world.tile(best);
            }
        });
    }

    public class Plan{
        public Array<NextAction> actions;
        public float lastCost;
        boolean bad;

        Plan(Array<NextAction> actions){
            this.actions = actions;
        }

        Plan copy(){
            return new Plan(new Array<>(actions));
        }

        /** Calculates a cost for this plan of actions. Disregards city linking actions.
         * Returns plan cost in moves.*/
        float cost(float bestSoFar){
            Tile position = player.position;
            float total = 0f;
            int money = player.money;
            int neededCargoUsed = 0;
            boolean linked = state.hasConnectedAllCities(player);

            float totalProfit = actions.sum(a -> a instanceof UnloadAction ?
                    player.allDemands().find(d -> d.good.equals(((UnloadAction) a).good)
                            && d.city == ((UnloadAction) a).city).cost : 0f) * demandCostScale;

            astar.begin();

            for(NextAction action : actions){
                if(action instanceof LoadAction){
                    LoadAction l = (LoadAction)action;

                    neededCargoUsed ++;

                    //player may already have this good, in which case loading is free
                    if(!player.cargo.contains(l.good)){
                        float added = astar.astar(position, state.world.tile(l.city));
                        total += added;
                        money -= astar.newTrackCost;
                        position = state.world.tile(l.city);

                        astar.placeTracks();
                    }

                    //when the player runs out of money, bail out, this plan isn't possible
                    //also bail out if player has no cargo space to hold this new load
                    if(money < 0 || neededCargoUsed > player.loco.loads){
                        return Float.POSITIVE_INFINITY;
                    }
                }else if(action instanceof UnloadAction){
                    UnloadAction u = (UnloadAction)action;

                    float added = astar.astar(position, state.world.tile(u.city));
                    total += added;
                    money -= astar.newTrackCost;
                    neededCargoUsed --;

                    astar.placeTracks();

                    //when the player runs out of money, bail out, this plan isn't possible
                    if(money < 0){
                        return Float.POSITIVE_INFINITY;
                    }

                    //get money earned
                    int earned = player.allDemands().find(d -> d.good.equals(u.good) && d.city == u.city).cost;
                    position = state.world.tile(u.city);

                    //after checking money, add unloaded cost by finding the correct demand
                    money += earned;
                }

                //branch and bound step: total is only going to get higher from here, if it's already over the best
                //drop out
                //if(total - totalProfit > bestSoFar){
                    //skipped ++;
                    //return Float.POSITIVE_INFINITY;
                //}
            }

            astar.end();

            //note that when the player is about to win, only total amount of moves win matters
            lastCost = linked && money >= State.winMoneyAmount ? total - 10000f : total - totalProfit;

            return lastCost;
        }
    }

    //action classes; should be self explanatory on what they represent

    public class LinkCitiesAction extends NextAction{
        public final City from, to;

        public LinkCitiesAction(City from, City to){
            this.from = from;
            this.to = to;
        }

        public String toString(){
            return "{"+from.name + " to " + to.name+"}";
        }
    }

    public class LoadAction extends NextAction{
        public final City city;
        public final String good;

        public LoadAction(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " from " + city.name+"}";
        }
    }

    public class UnloadAction extends NextAction{
        public final City city;
        public final String good;

        public UnloadAction(City city, String good){
            this.good = good;
            this.city = city;
        }

        public String toString(){
            return "{"+good + " to " + city.name+"}";
        }
    }

    public interface AIListener{
        default void planningBegin(){}
        default void planConsidered(Plan plan, float cost){}
        default void planChosen(Plan plan){}
    }

    public abstract class NextAction{

    }
}
