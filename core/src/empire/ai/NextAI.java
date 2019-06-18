package empire.ai;

import empire.game.DemandCard.Demand;
import empire.game.*;
import empire.game.World.*;
import io.anuke.arc.collection.Array;
import io.anuke.arc.util.*;

/** Next iteration of this AI.*/
public class NextAI extends AI{
    private static final String defaultStartingCity = "ruhr";
    /** Whether to choose a location.*/
    private static final boolean chooseLocation = false;
    /** Money after which the AI will consider upgrading their loco.*/
    private static final int upgradeAfterMoney = 60;
    /** Demand cost scale: how many units to reduce a score by, per ECU.*/
    private static final float demandCostScale = 300f;

    /** List of planned actions.*/
    private Plan plan = new Plan(new Array<>());
    /** Object for handling pathfinding.*/
    private Astar astar;

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

    /** Attempts to execute this pla.
     * @return whether or not this turn should end. */
    boolean executePlan(){
        return true;
    }

    /** Update the plan, find the best one. */
    void updatePlan(){
        Plan bestPlan = null;
        float bestCost = Float.POSITIVE_INFINITY; //min cost

        //find the cheapest plan
        for(Demand first : allDemands()){
            for(Demand second : allDemands(first)){
                for(Demand third : allDemands(first, second)){
                    for(int[] combination : PlanCombinations.all){
                        Plan plan = makePlan(new Demand[]{first, second, third}, combination);
                        float cost = plan.cost();
                        if(cost < bestCost){
                            bestPlan = plan;
                            bestCost = cost;
                        }
                    }
                }
            }
        }

        if(bestPlan != null){
            plan = bestPlan;
            //reverse to act on it layer
            plan.actions.reverse();
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

        for(int value : combination){
            boolean unload = value < 0;
            Demand demand = demands[Math.abs(value) - 1];
            Tile currentTile = player.position;

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
        float cost(){
            Tile position = player.position;
            float total = 0f;
            int money = player.money;
            int cargoUsed = player.cargo.size;

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
                    //remove earned ECU cost to make this cheaper
                    total -= earned * Astar.ecuCostScale;
                }
            }

            astar.end();

            return total;
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
