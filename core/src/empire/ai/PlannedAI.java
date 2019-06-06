package empire.ai;

import empire.game.Player;
import empire.game.State;
import io.anuke.arc.collection.Array;

public class PlannedAI extends AI{
    /** List of planned actions.*/
    private Array<PlanAction> plan = new Array<>();

    public PlannedAI(Player player, State state){
        super(player, state);
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

        end();
    }

    /** The general idea behind plan:
     * Figure out which actions would result in the best profit.
     * There are 3 * 3 * 3 different combinations of demand cards that this AI can do.
     *
     * Go through every combination of possible planned demands. (27)
     *
     */
    void updatePlan(){
        plan.clear(); //clear old data, it's not useful anymore


    }

    abstract class PlanAction{

    }
}
