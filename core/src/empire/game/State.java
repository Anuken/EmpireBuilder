package empire.game;

import io.anuke.arc.collection.Array;

/** Holds the state of the entire game. */
public class State{
    /** All player states by index.*/
    public final Array<Player> players = new Array<>();
    /** The current world state.*/
    public World world;
    /** The global turn.*/
    public int turn;
}
