package empire.game;

import io.anuke.arc.collection.Array;

/** Holds the state of the entire game. */
public class State{
    /** All player states by index.*/
    public final Array<Player> players = new Array<>();
    /** The current world state.*/
    public World world;
    /** The global turn, in terms of completed whole turns.*/
    public int turn;
    /** The index of the player whose turn it is right now.*/
    public int currentPlayer;
}
