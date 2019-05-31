package empire.game;

import empire.game.DemandCard.Demand;
import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.function.BiConsumer;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.function.Predicate;
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
    /** How many turns this player has lost due to event cards.*/
    public int lostTurns;
    /** The current locomotive type.*/
    public Loco loco = Loco.freight;
    /** The position on the board of this player.*/
    public Tile position;
    /** Direction this player is facing.*/
    public Direction direction = Direction.right;
    /** Whether this player is local and controllable.*/
    public boolean local;

    /** Other players' tracks this player has moved on.*/
    public final ObjectSet<Player> movedPlayers = new ObjectSet<>();
    /** All the event cards this player drew this turn.*/
    public final Array<EventCard> eventCards = new Array<>();
    /** Tracks that this player has placed down.*/
    public final ObjectMap<Tile, Array<Tile>> tracks = new ObjectMap<>();
    /** Player color, used for display purposes.*/
    public Color color;
    /** Current cargo held.*/
    public Array<String> cargo = new Array<>();
    /** This player's demand cards. Always of length 3, never with null elements.*/
    public DemandCard[] demandCards;
    /** Name for this player, displayed on the board.*/
    public String name;

    /** Creates a player at a position.*/
    public Player(String name, Tile position, Color color, DemandCard[] cards){
        this.name = name;
        this.position = position;
        this.color = color;
        this.demandCards = cards;
    }

    /** Serialization use only. */
    protected Player(){}

    /** @return whether this player is within distance of this tile.*/
    public boolean within(int x, int y, int dst){
        return position.distanceTo(x, y) <= dst;
    }

    /** Removes one of this player's tracks.*/
    public void removeTrack(Tile from, Tile to){
        if(tracks.containsKey(from)){
            tracks.get(from).remove(to);
            if(tracks.get(from).isEmpty()){
                tracks.remove(from);
            }
        }

        if(tracks.containsKey(to)){
            tracks.get(to).remove(from);
            if(tracks.get(to).isEmpty()){
                tracks.remove(to);
            }
        }
    }

    /** Returns whether a certain action is allowed, according to the event cards.
     * If any one of them returns false, false is returned.*/
    public boolean isAllowed(Predicate<EventCard> pred){
        for(EventCard card : eventCards){
            if(!pred.test(card)){
                return false;
            }
        }
        return true;
    }

    /** Returns whether this player has this specific track.*/
    public boolean hasTrack(Tile from, Tile to){
        return tracks.get(from) != null && tracks.get(from).contains(to);
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

    /** Whether this city has a good that can be sold there.*/
    public boolean hasGoodDelivery(City city){
        return Structs.contains(demandCards, card -> Structs.contains(card.demands, d -> d.city == city ));
    }

    /** Whether this city has a good that this player can sell somewhere else.*/
    public boolean hasGoodDemand(City city){
        return city.goods.contains(good -> Structs.contains(demandCards, card -> Structs.contains(card.demands, d -> d.good.equals(good))));
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
