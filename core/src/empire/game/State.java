package empire.game;

import empire.game.World.CitySize;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.util.Structs;

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
    /** The demand cards of this state.*/
    public Array<DemandCard> demandCards;

    /** Grabs 3 demand cards from the top of the deck and returns them.*/
    public DemandCard[] grabCards(){
        DemandCard[] out = new DemandCard[3];
        for(int i = 0; i < 3; i++){
            out[i] = demandCards.pop();
        }
        return out;
    }

    /** Switches turns to the next player.
     * Increments total turn if needed.*/
    public void nextPlayer(){
        currentPlayer().moneySpent = 0;
        currentPlayer().moved = 0;

        currentPlayer ++;
        if(currentPlayer >= players.size){
            currentPlayer = 0;
            turn ++;
        }
    }

    public void placeTrack(Player player, Tile from, Tile to){
        player.tracks.getOr(from, Array::new).add(to);
        player.tracks.getOr(to, Array::new).add(from);

        int cost = getTrackCost(from, to);
        player.money -= cost;
        player.moneySpent += cost;
    }

    public Player currentPlayer(){
        return players.get(currentPlayer);
    }

    public boolean canPlaceTrack(Player player, Tile from, Tile to){
        //can't place track into itself
        if(from == to) return false;

        //player needs to be there to place tracks there
        if(player.position != from && !player.tracks.containsKey(from) && !player.tracks.containsKey(to)){
            return false;
        }

        //this basically looks through all adjacent points; if from is not adjacent to 'to', return false
        if(!Structs.contains(from.getAdjacent(), p -> world.tileOpt(from.x + p.x, from.y + p.y) == to)){
            return false;
        }
        //make sure these tiles are passable
        if(!isPassable(player, from) || !isPassable(player, to)){
            return false;
        }

        //make sure that this track is not used by a player
        for(Player other : players){
            if((other.tracks.containsKey(from) && other.tracks.get(from).contains(to))
                || (other.tracks.containsKey(to) && other.tracks.get(to).contains(from))){
                return false;
            }
        }
        return true;
    }

    public int getTrackCost(Tile from, Tile to){
        //calculate base cost with (8!) nested ternary statements
        int baseCost =
            to.city != null ?
                    (
                    to.city.size == CitySize.small ? 3 :
                    to.city.size == CitySize.medium ? 3 : 0
                    ) :
            Structs.contains(to.getAdjacent(), p -> {
                Tile other = world.tile(to.x + p.x, to.y + p.y);
                return other != null && other.city != null && other.city.size == CitySize.major;
            }) ? 5 :
            to.port != null ? to.port.cost :
            to.type == Terrain.plain ? 1 :
            to.type == Terrain.mountain ? 2 :
            to.type == Terrain.alpine ? 5 : 0;

        //TODO factor in costs for inlets, lakes and rivers
        return baseCost;
    }

    /** Returns whether a tile is passable, e.g. whether a rail can go through it.*/
    public boolean isPassable(Player player, Tile tile){
        if(!canPlaceOn(tile.type)){
            return false;
        }
        //large cities cannot be tracked into, but they can be tracked out of, if the player starts there
        if(player.position != tile && tile.city != null && tile.city.size == CitySize.major){
            return false;
        }
        //TODO add limits for other cities:
        //- some cities only have 2 or 3 max connections
        //- can't block players from accessing major cities, there's a limit
        return true;
    }

    /** Returns whether a rail can be placed on this terrain.*/
    public boolean canPlaceOn(Terrain terrain){
        return terrain == Terrain.alpine || terrain == Terrain.mountain || terrain == Terrain.plain;
    }

    /** A reason for preventing the player from placing a track at a location.
     * TODO implement*/
    public enum BlockedReason{
        badTerrain, overlapsTrack, notAdjacent, inMajorCity, cityLimit
    }
}
