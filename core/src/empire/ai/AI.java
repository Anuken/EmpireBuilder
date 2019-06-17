package empire.ai;

import empire.game.Actions.EndTurn;
import empire.game.*;
import io.anuke.arc.Core;
import io.anuke.arc.util.async.*;

/** Handles the AI for a specific player.*/
public abstract class AI{
    protected static final AsyncExecutor executor = new AsyncExecutor(4);
    protected static final boolean checkHistory = true;

    /** The player this AI controls.*/
    public final Player player;
    /** The game state.*/
    public final State state;

    private AsyncResult<Void> waiting;

    public AI(Player player, State state){
        this.player = player;
        this.state = state;
    }

    /** Performs actions on this AI's turn.*/
    public abstract void act();

    public boolean waitAsync(){
        return waiting == null || waiting.isDone();
    }

    public void async(Runnable runnable){
        if(!waitAsync()){
            throw new IllegalArgumentException("Wait for the task to be done until trying again.");
        }

        waiting = executor.submit(() -> {
            try{
                runnable.run();
            }catch(Throwable t){
                Core.app.post(() -> {throw new RuntimeException(t);});
            }
        });
    }


    /** End the turn if necessary.*/
    void end(){
        if(state.player() == player){
            new EndTurn().act();
        }
    }
}
