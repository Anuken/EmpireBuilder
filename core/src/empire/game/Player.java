package empire.game;

import empire.game.World.Tile;
import empire.game.World.Track;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;

/** The state of a single player in the game. */
public class Player{
    /** Total money in millions of ECU.*/
    public int money = 50;
    /** The current locomotive type.*/
    public Loco loco = Loco.freight;
    /** The position on the board of this player.*/
    public Tile position;
    /** Tracks that this player has placed down.*/
    public Array<Track> tracks = new Array<>();
    /** Player color, used for display purposes.*/
    public Color color;

    /** Creates a player at a position.*/
    public Player(Tile position, Color color){
        this.position = position;
        this.color = color;
    }
}
