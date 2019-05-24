package empire.game;

import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.collection.Queue;
import io.anuke.arc.function.BiConsumer;
import io.anuke.arc.graphics.Color;

/** The state of a single player in the game. */
public class Player{
    /** Total money in millions of ECU.*/
    public int money = 50;
    /** Money spent this turn (on rails)*/
    public int moneySpent;
    /** How much the player has moved this turn.*/
    public int moved;
    /** The current locomotive type.*/
    public Loco loco = Loco.freight;
    /** The position on the board of this player.*/
    public Tile position;
    /** Tracks that this player has placed down.*/
    public final ObjectMap<Tile, Array<Tile>> tracks = new ObjectMap<>();
    /** Player color, used for display purposes.*/
    public final Color color;
    /** Current cargo held.*/
    public final Array<String> cargo = new Array<>();
    /** This player's demand cards. Always of length 3, never with null elements.*/
    public final DemandCard[] demandCards;

    /** A set of closed tiles. Temp usage only.*/
    private static final ObjectSet<Tile> closedSet = new ObjectSet<>();
    private static final Queue<Tile> queue = new Queue<>();

    /** Creates a player at a position.*/
    public Player(Tile position, Color color, DemandCard[] cards){
        this.position = position;
        this.color = color;
        this.demandCards = cards;
    }

    /** Returns whether this player can hold more cargo.*/
    public boolean hasCargoSpace(){
        return cargo.size < loco.loads;
    }

    public void addCargo(String good){
        cargo.add(good);
    }

    /** Returns how long it would take this player to move to this tile.
     * Returns -1 if impossible.*/
    public int distanceTo(Tile other){
        //TODO distance when in a major city?

        if(!hasTrack(other)){
            return -1;
        }

        //already there
        if(other == position){
            return 0;
        }

        closedSet.clear();
        queue.clear();

        other.searchDst = 0;

        //perform BFS
        queue.addFirst(other);
        closedSet.add(other);
        while(!queue.isEmpty()){
            Tile tile = queue.removeLast();
            for(Tile child : tracks.get(tile)){
                //if found, return its search distance
                if(child == position){
                    return tile.searchDst + 1;
                }

                if(!closedSet.contains(child)){
                    child.searchDst = tile.searchDst + 1;
                    queue.addFirst(child);
                    closedSet.add(child);
                }
            }
        }

        closedSet.clear();
        return -1;
    }

    /** Whether this player's track goes through this tile.*/
    public boolean hasTrack(Tile tile){
        return tracks.containsKey(tile);
    }

    /** Iterates through each unique track that this player has.
     * Pairs are only iterated once, so make sure to check both ends.*/
    public void eachTrack(BiConsumer<Tile, Tile> cons){
        tracks.each((tile, tiles) -> tiles.each(other -> cons.accept(tile, other)));
    }
}
