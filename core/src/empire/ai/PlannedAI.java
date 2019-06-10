package empire.ai;

import empire.game.*;
import empire.game.Actions.*;
import empire.game.DemandCard.Demand;
import empire.game.GameEvents.EventEvent;
import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.Structs;

public class PlannedAI extends AI{
    /** Money after which the AI will consider upgrading their loco.*/
    private static final int upgradeAfterMoney = 60;
    /** List of planned actions.*/
    private Array<PlanAction> plan = new Array<>();
    /** Tmp return tile.*/
    private Tile returnTile;

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
        Array<Tile> finalPath = new Array<>();
        boolean shouldMove = !state.isPreMovement();

        boolean moved = true;

        //keep attempting to move unless nothing happened
        while(moved){
            moved = false;

            PlanAction action = plan.peek();

            Log.info("Execute action {0}", action.getClass().getSimpleName());

            //player already has some cargo to unload; find a city to unload to
            if(action instanceof UnloadPlan){
                UnloadPlan u = (UnloadPlan) action;
                String good = u.good;

                //queue a move to this location
                astar(player.position, state.world.tile(u.city), finalPath);

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
                astar(player.position, state.world.tile(l.city), finalPath);

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
                player.money > upgradeAfterMoney && player.loco != Loco.superFreight){
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
        plan.clear(); //clear old data, it's not useful anymore

        //player can win if they place track and connect cities
        if(player.money > State.winMoneyAmount){
            //TODO attempt to connect track
        }

        Demand[] bestCombination = new Demand[3];
        float bestCost = Float.POSITIVE_INFINITY;

        //find the best sequence of demands possible
        for(Demand first : allDemands()){
            for(Demand second : allDemands(first)){
                for(Demand third : allDemands(first, second)){
                    float cost = evaluateDemands(first, second, third);
                    if(cost < bestCost){
                        bestCost = cost;
                        bestCombination[0] = first;
                        bestCombination[1] = second;
                        bestCombination[2] = third;
                    }
                }
            }
        }

        makePlan(bestCombination);

        Log.info(Array.with(bestCombination).toString(", ", d -> d.good + " to " + d.city.name));
    }

    void makePlan(Demand[] demands){
        City load1 = getBestCity(player.position, demands[0]);
        City load2 = getBestCity(state.world.tile(demands[0].city), demands[1]);
        City load3 = getBestCity(state.world.tile(demands[1].city), demands[2]);

        for(int i = 0; i < 3; i++){
            if(!player.cargo.contains(demands[i].good)) plan.add(new LoadPlan(
                    i == 0 ? load1 : i == 1 ? load2 : load3, demands[i].good));
            plan.add(new UnloadPlan(demands[i].city, demands[i].good));
        }
        plan.reverse();
    }

    /** Evaluates the relative cost of doing all of these demands in sequence.
     * Currently does not chain together loading at all.*/
    float evaluateDemands(Demand first, Demand second, Demand third){
        //possibilities:

        //l1 l2 l3 u1 u2 u3 (cargo size 3 only)
        //l1 u1 l2 l3 u2 u3
        //l1 l2 u1 u2 l3 u3

        return getDemandCost(player.position, first) +
                getDemandCost(state.world.tile(first.city), second) +
                getDemandCost(state.world.tile(second.city), third);
    }

    /** Returns the cost to satisfy this demand, given the specified starting position.
     * Finds the best city to get the good from and calculates the cost of the
     * path to that city, then adds the cost of the path to the demand's city.*/
    float getDemandCost(Tile position, Demand demand){
        float minBuyCost = Float.MAX_VALUE;

        //find best city to get good from for this demand
        for(City city : Array.with(state.world.cities()).select(s -> s.goods.contains(demand.good))){
            //get a* cost: from the player to the city, then from the city to the final destination
            float dst = (player.cargo.contains(demand.good) ? 0f : //movement to the city is free if
                    astar(position, state.world.tile(city.x, city.y), tmpArray)) +
                    astar(state.world.tile(city.x, city.y),
                            state.world.tile(demand.city.x, demand.city.y), tmpArray);

            //if this source city is better, update things
            if(dst < minBuyCost){
                minBuyCost = dst;
                //update return tile to reflect the best end point, which is usually just the dest city
                returnTile = tmpArray.peek();
            }
        }

        //update cost to reflect the base good cost
        minBuyCost -= demand.cost * 250f;

        return minBuyCost;
    }

    City getBestCity(Tile position, Demand demand){
        float minBuyCost = Float.MAX_VALUE;
        City bestCity = null;

        //find best city to get good from for this demand
        for(City city : Array.with(state.world.cities()).select(s -> s.goods.contains(demand.good))){
            //get a* cost: from the player to the city, then from the city to the final destination
            float dst = (player.cargo.contains(demand.good) ? 0f :
                    astar(position, state.world.tile(city.x, city.y), tmpArray)) +
                    astar(state.world.tile(city.x, city.y),
                            state.world.tile(demand.city.x, demand.city.y), tmpArray);

            //if this source city is better, update things
            if(dst < minBuyCost){
                minBuyCost = dst;
                bestCity = city;
                //update return tile to reflect the best end point, which is usually just the dest city
                returnTile = tmpArray.peek();
            }
        }

        return bestCity;
    }

    /** Returns an array of valid demands, given that the passed demands have already been used.*/
    Array<Demand> allDemands(Demand... alreadyUsed){
        Array<Demand> out = new Array<>();
        Array.with(player.demandCards).select(card -> !Structs.contains(card.demands,
                d -> Structs.contains(alreadyUsed, d)))
                .each(c -> out.addAll(c.demands));
        return out;
    }

    abstract class PlanAction{

    }

    class LoadPlan extends PlanAction{
        final City city;
        final String good;

        public LoadPlan(City city, String good){
            this.good = good;
            this.city = city;
        }
    }

    class UnloadPlan extends PlanAction{
        final City city;
        final String good;


        public UnloadPlan(City city, String good){
            this.good = good;
            this.city = city;
        }
    }
}
