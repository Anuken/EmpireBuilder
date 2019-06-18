package empire.ai;

import empire.game.*;
import empire.game.World.*;
import io.anuke.arc.collection.Array;

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

    void updatePlan(){

    }

    boolean executePlan(){
        return true;
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

            astar.begin();

            for(NextAction action : actions){
                if(action instanceof LoadAction){
                    LoadAction l = (LoadAction)action;

                    total += astar.astar(position, state.world.tile(l.city));
                    money -= astar.newTrackCost;
                    position = state.world.tile(l.city);

                    astar.placeTracks();

                    //when the player runs out of money, bail out, this plan isn't possible
                    if(money < 0) return Float.POSITIVE_INFINITY;
                }else if(action instanceof UnloadAction){
                    UnloadAction u = (UnloadAction)action;

                    total += astar.astar(position, state.world.tile(u.city));
                    money -= astar.newTrackCost;

                    astar.placeTracks();

                    //when the player runs out of money, bail out, this plan isn't possible
                    if(money < 0) return Float.POSITIVE_INFINITY;

                    //after checking money, add unloaded cost by finding the correct demand
                    money += player.allDemands().find(d -> d.good.equals(u.good) && d.city == u.city).cost;
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
