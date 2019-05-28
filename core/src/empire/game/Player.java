package empire.game;

import empire.game.DemandCard.Demand;
import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.function.BiConsumer;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.util.Structs;

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

    /** Creates a player at a position.*/
    public Player(Tile position, Color color, DemandCard[] cards){
        this.position = position;
        this.color = color;
        this.demandCards = cards;
    }

    /** Iterates through each good demanded in a city.*/
    public void eachGoodByCity(City city, Consumer<Demand> cons){
        for(DemandCard card : demandCards){
            for(Demand d : card.demands){
                if(d.city == city){
                    cons.accept(d);
                }
            }
        }
    }

    public boolean hasGoodDelivery(City city){
        return Structs.contains(demandCards, card -> Structs.contains(card.demands, d -> d.city == city ));
    }

    /** Returns whether this player has a card that matches this city and good.*/
    public boolean canDeliverGood(City city, String good){
        return Structs.contains(demandCards, card -> Structs.contains(card.demands, d -> d.city == city && d.good.equals(good)));
    }

    /** Returns whether this player can hold more cargo.*/
    public boolean hasCargoSpace(){
        return cargo.size < loco.loads;
    }

    public void addCargo(String good){
        cargo.add(good);
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
