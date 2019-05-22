package empire.game;

import io.anuke.arc.collection.Array;

/** Holds the state of the entire game. */
public class State{
    public final Array<Player> players = new Array<>();
    public World world;
}
