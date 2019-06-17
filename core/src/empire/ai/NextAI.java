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

    public NextAI(Player player, State state){
        super(player, state);
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

        /** Calculates a cost for this plan of actions. Disregards city linking actions*/
        float cost(){
            Tile position = player.position;

            for(NextAction action : actions){
                if(action instanceof LoadAction){
                    LoadAction l = (LoadAction)action;



                }else if(action instanceof UnloadAction){
                    UnloadAction u = (UnloadAction)action;
                }
            }

            return 0f;
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
