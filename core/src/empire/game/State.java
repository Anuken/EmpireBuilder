package empire.game;

import empire.game.DemandCard.Demand;
import empire.game.World.City;
import empire.game.World.CitySize;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.collection.Queue;
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
    /** Cost to upgrade a loco.*/
    public final int locoCost = 20;
    /** Max amount of money to spend on rails per turn.*/
    public final int maxRailSpend = 20;

    /** A set of closed tiles. Temp usage only.*/
    private static final ObjectSet<Tile> closedSet = new ObjectSet<>();
    private static final Queue<Tile> queue = new Queue<>();

    /** Grabs 3 demand cards from the top of the deck and returns them.*/
    public DemandCard[] grabCards(){
        DemandCard[] out = new DemandCard[3];
        for(int i = 0; i < 3; i++){
            out[i] = demandCards.pop();
        }
        return out;
    }

    public void sellGood(Player player, City city, String good){
        DemandCard card = Structs.find(player.demandCards, f -> Structs.contains(f.demands, res -> res.city == city && res.good.equals(good)));
        if(card != null){
            Demand demand = Structs.find(card.demands, res -> res.city == city && res.good.equals(good));
            player.money += demand.cost;
            player.cargo.remove(good);
            int idx = Structs.indexOf(player.demandCards, card);
            player.demandCards[idx] = demandCards.pop();
            demandCards.insert(0, card);
        }else{
            throw new IllegalArgumentException("Incorrect usage. No matching city/good combination found.");
        }
    }

    /** Discards this player's demand cards and replaces them with new ones.*/
    public void discardCards(Player player){
        for(int i = 2; i >= 0; i--){
            demandCards.insert(0, player.demandCards[i]);
        }
        for(int i = 0; i < 3; i++){
            player.demandCards[i] = demandCards.pop();
        }
    }

    /** @return whether this player can place this rail at the specified price.*/
    public boolean canSpendRail(Player player, int amount){
        return player.moneySpent + amount <= maxRailSpend && player.money - amount >= 0;
    }

    /** Simulates a player purchasing a loco.*/
    public void purchaseLoco(Player player, Loco loco){
        player.loco = loco;
        player.money -= locoCost;
    }

    /** Switches turns to the next player.
     * Increments total turn if needed.*/
    public void nextPlayer(){
        player().moneySpent = 0;
        player().moved = 0;

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

    public Player player(){
        return players.get(currentPlayer);
    }

    public boolean canPlaceTrack(Player player, Tile from, Tile to){
        //can't place track into itself
        if(from == to) return false;

        //player needs to be there to place tracks there
        if(player.position != from && !player.tracks.containsKey(from) && !player.tracks.containsKey(to)
          && world.getMajorCity(from) == null){ //make sure to check for major cities too; track can be placed from any major city
            return false;
        }

        //this basically looks through all adjacent points; if from is not adjacent to 'to', return false
        if(!world.isAdjacent(from, to)){
            return false;
        }

        //make sure these tiles are passable
        if(!isPassable(player, from) || !isPassable(player, to)){
            return false;
        }

        //make sure they're not both in major cities, that's illegal
        if(world.getMajorCity(from) == world.getMajorCity(to) && world.getMajorCity(from) != null){
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
                Tile other = world.tileOpt(to.x + p.x, to.y + p.y);
                return other != null && other.city != null && other.city.size == CitySize.major;
            }) ? 5 :
            to.port != null ? to.port.cost :
            to.type == Terrain.plain ? 1 :
            to.type == Terrain.mountain ? 2 :
            to.type == Terrain.alpine ? 5 : 0;

        if(from.riverTiles != null && from.riverTiles.contains(to)){
            baseCost += 2;
        }

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

    /** Returns how long it would take this player to move to this tile.
     * Returns -1 if impossible.*/
    public int distanceTo(Player player, Tile other){

        if(world.getMajorCity(player.position) == world.getMajorCity(other) && world.getMajorCity(other) != null){
            if(world.isAdjacent(player.position, other)){ //adjacent means dist = 1
                return 1;
            }else{
                return 2; //if not adjacent, the only alternative is 2
            }
        }

        if(!player.hasTrack(other)){
            return -1;
        }

        //already there
        if(other == player.position){
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
            for(Tile child : player.tracks.get(tile)){
                //if found, return its search distance
                if(child == player.position){
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

    /** A reason for preventing the player from placing a track at a location.
     * TODO implement*/
    public enum BlockedReason{
        badTerrain, overlapsTrack, notAdjacent, inMajorCity, cityLimit
    }
}
