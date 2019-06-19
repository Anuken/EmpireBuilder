package empire.gfx;

import empire.ai.AI;
import io.anuke.arc.*;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.util.*;
import io.anuke.arc.util.Timer.Task;

import static empire.gfx.EmpireCore.*;

/** Handles scheduling of AI actions, including pausing and resuming.*/
public class AIScheduler implements ApplicationListener{
    private static final float interval = 0.25f;

    private Task task;
    private AI ai;

    public AIScheduler(){
        //nothing to update
    }

    public AIScheduler(AI ai){
        this.task = Timer.schedule(() -> {
            if(state.player().ai == ai){
                ai.act();
            }

            if(state.hasWinner){
                renderer.takeWorldScreenshot();
                Log.info("Won in {0} turns.", state.turn);
                this.task.cancel();
            }
        }, interval, interval);

        this.ai = ai;
    }

    @Override
    public void update(){
        //don't update when not active
        if(ai == null) return;

        if(Core.input.keyTap(KeyCode.SPACE) && task.isScheduled()){
            task.cancel();
        }else if(Core.input.keyTap(KeyCode.SPACE) && !task.isScheduled()){
            Timer.schedule(task, interval, interval);
        }
    }

    public boolean isThinking(){
        return ai != null && !ai.waitAsync();
    }

    public boolean isPaused(){
        return task != null && !task.isScheduled();
    }
}
