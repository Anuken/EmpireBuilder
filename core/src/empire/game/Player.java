package empire.game;

import empire.ai.AI;
import empire.game.DemandCard.Demand;
import empire.game.World.*;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.*;
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
    /** The AI that controls this player. May be null. */
    public transient AI ai;

    /** Other players' tracks this player has moved on.*/
    public final transient ObjectSet<Player> movedPlayers = new ObjectSet<>();
    /** All the event cards this player drew this turn.*/
    public final transient Array<EventCard> drawEventCards = new Array<>();
    /** All the event cards this player has active.*/
    public final transient Array<EventCard> eventCards = new Array<>();
    /** Tracks that this player has placed down.*/
    public Tracks tracks = new Tracks();
    /** Player color, used for display purposes.*/
    public Color color;
    /** Current cargo held.*/
    public Array<String> cargo = new Array<>();
    /** This player's demand cards. Always of length 3, never with null elements.*/
    public DemandCard[] demandCards;
    /** Name for this player, displayed on the board.*/
    public String name;
    /** Whether this player has chosen a starting location.*/
    public boolean chosenLocation = false;

    /** Creates a player at a position.*/
    public Player(String name, Tile position, Color color, DemandCard[] cards){
        this.name = name;
        this.position = position;
        this.color = color;
        this.demandCards = cards;
    }

    /** Serialization use only. */
    protected Player(){}

    /** Returns an array of all the demands.*/
    public Array<Demand> allDemands(){
        Array<Demand> out = new Array<>();
        for(DemandCard card : demandCards){
            out.addAll(card.demands);
        }
        return out;
    }

    /** @return whether this player is within distance of this tile.*/
    public boolean within(int x, int y, int dst){
        return position.distanceTo(x, y) <= dst;
    }

    /** Removes one of this player's tracks.*/
    public void removeTrack(Tile from, Tile to){
        tracks.remove(from.x, from.y, to.x, to.y);
    }

    /** Returns whether a certain action is allowed, according to the event cards.
     * If any one of them returns false, false is returned.*/
    public boolean isAllowed(Predicate<EventCard> pred){
        for(Player player : EmpireCore.state.players){
            for(EventCard card : eventCards){
                if(!pred.test(card)){
                    return false;
                }
            }
        }
        return true;
    }

    /** Returns whether this player has this specific track.*/
    public boolean hasTrack(Tile from, Tile to){
        return tracks.has(from.x, from.y, to.x, to.y);
    }

    public void addTrack(Tile from, Tile to){
        tracks.add(from.x, from.y, to.x, to.y);
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
        return Structs.contains(demandCards, card -> Structs.contains(card.demands, d -> d.city == city));
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

    public boolean hasTrack(Tile tile){
        return tracks.has(tile.x, tile.y);
    }

    public int getTrackConnections(Tile tile){
        return tracks.connections(tile.x, tile.y);
    }

    /** Iterates through each unique track that this player has.
     * Pairs are only iterated once, so make sure to check both ends.*/
    public void eachTrack(BiConsumer<Tile, Tile> cons){
        tracks.each((x, y, x2, y2) -> cons.accept(EmpireCore.state.world.tile(x, y), EmpireCore.state.world.tile(x2, y2)));
    }
}
