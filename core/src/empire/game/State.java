package empire.game;

import empire.game.DemandCard.Demand;
import empire.game.World.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.util.Structs;

/** Holds the state of the entire game. */
public class State{
    /** Cost to upgrade a loco.*/
    public final static int locoCost = 20;
    /** Max amount of money to spend on rails per turn.*/
    public final int maxRailSpend = 20;
    /** Amount of money needed to win.*/
    public final static int winMoneyAmount = 250;
    /** Number of turns with no movement at the start.*/
    public final static int preMovementTurns = 2;
    /** Number of cities connected needed to win.*/
    public final static int winCityAmount = 7;

    /** The current world state.*/
    public World world;
    /** All player states by index.*/
    public final Array<Player> players = new Array<>();
    /** The global turn, in terms of completed whole turns.*/
    public int turn = 1;
    /** The index of the player whose turn it is right now.*/
    public int currentPlayer;
    /** All the cards of this state. May be event or demand cards*/
    public Array<Card> cards;
    /** Event listener for a player winning.*/
    public Consumer<Player> onWin = p -> {};

    /** Tile collections for temporary usage.*/
    private static final ObjectSet<Tile> closedSet = new ObjectSet<>();
    private static final Queue<Tile> queue = new Queue<>();
    private static final Array<Tile> moveArray = new Array<>();
    private static final ObjectSet<City> citySet = new ObjectSet<>();

    /** Grabs 3 demand cards from the top of the deck and returns them.
     * Event cards are discarded.*/
    public DemandCard[] grabCards(){
        DemandCard[] out = new DemandCard[3];
        for(int i = 0; i < 3; i++){
            //draw a demand card, any event cards found will be inserted in index 0 (shuffled to back)
            out[i] = drawDemandCard(e -> cards.insert(0, e));
        }
        return out;
    }

    /** Reclaims cards for a player. This is only called after net disconnects.*/
    public void reclaimCards(Player player){
        for(DemandCard card : player.demandCards){
            cards.insert(0, card);
        }
    }

    /** Attempts to draw a demand card; passes on all event cards drawn.*/
    public DemandCard drawDemandCard(Consumer<EventCard> eventHandler){
        Card nextDemand;
        while(!((nextDemand = cards.pop()) instanceof DemandCard)){
            eventHandler.accept((EventCard)nextDemand);
        }
        return (DemandCard)nextDemand;
    }

    /** Returns whether players are currently in the pre-movement phase.*/
    public boolean isPreMovement(){
        return turn <= preMovementTurns;
    }

    /** Checks if a player has won a game, and runs the onWin callback if that is the case.*/
    public void checkIfWon(Player player){
        if(player.money >= winMoneyAmount){
            int checked = 0;

            for(City city : world.cities()){
                //if 2 cities don't have the right connections, none of them can, since there are 8 cities and 7 needed to win.
                if(checked >= 2){
                    return;
                }

                if(city.size == CitySize.major){
                    if(countConnectedCities(player, world.tile(city.x, city.y)) >= winCityAmount){
                        onWin.accept(player);
                        return;
                    }
                }

                checked ++;
            }
        }
    }

    public boolean canLoadUnload(Player player, Tile tile){
        return player.isAllowed(e -> e.canLoadOrUnload(player, tile));
    }

    /** Simulates a 'sell good' event.*/
    public void sellGood(Player player, City city, String good, Consumer<EventCard> eventHandler){
        DemandCard card = Structs.find(player.demandCards, f -> Structs.contains(f.demands, res -> res.city == city && res.good.equals(good)));
        if(card != null){
            Demand demand = Structs.find(card.demands, res -> res.city == city && res.good.equals(good));
            player.money += demand.cost;
            player.cargo.remove(good);
            int idx = Structs.indexOf(player.demandCards, card);
            player.demandCards[idx] = drawDemandCard(event -> handleEvent(event, player, eventHandler));
            this.cards.insert(0, card);
        }else{
            throw new IllegalArgumentException("Incorrect usage. No matching city/good combination found.");
        }
    }

    /** Discards this player's demand cards and replaces them with new ones.*/
    public void discardCards(Player player, Consumer<EventCard> eventHandler){
        for(int i = 2; i >= 0; i--){
            cards.insert(0, player.demandCards[i]);
        }
        for(int i = 0; i < 3; i++){
            player.demandCards[i] = drawDemandCard(event -> handleEvent(event, player, eventHandler));
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
        //reset turn-specific data
        player().moneySpent = 0;
        player().moved = 0;
        player().movedPlayers.clear();
        player().eventCards.clear();
        if(player().lostTurns > 0){
            player().lostTurns --;
        }
        checkIfWon(player());

        //begin next player's turn
        currentPlayer ++;
        if(currentPlayer >= players.size){
            currentPlayer = 0;
            turn ++;
        }

        //recursively advance the next player until there are no lost turns left.
        if(player().lostTurns > 0){
            nextPlayer();
        }
    }

    public void checkLostTurns(){
        if(player().lostTurns > 0){
            nextPlayer();
        }
    }

    /** Places a single track for a player and updates money for the player.*/
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

    public boolean canBeginTrack(Player player, Tile tile){
        return world.getMajorCity(tile) != null ||
                player.position == tile ||
                player.tracks.containsKey(tile) ||
                (tile.type == Terrain.port &&
                        (player.tracks.containsKey(tile.port.from) ||
                        player.tracks.containsKey(tile.port.to)));
    }

    public boolean checkTrackLimits(Player player, Tile tile){
        //only 2 players can build to or from a port
        if(tile.port != null){
            if(players.count(p -> p.tracks.containsKey(tile)) >= 2){
                return false;
            }
        }

        if(tile.city != null){
            //can't build 3 tracks into a city
            if(tile.city.size != CitySize.major && player.tracks.get(tile).size >= 3){
                return true;
            }

            //max 3 players can build into medium, max 2 into small
            if(tile.city.size == CitySize.medium && players.count(p -> p.tracks.containsKey(tile)) >= 3){
                return false;
            }else if(tile.city.size == CitySize.small && players.count(p -> p.tracks.containsKey(tile)) >= 2){
                return false;
            }
        }

        //TODO obstruction limits; right of building into a city

        return true;
    }

    public boolean canPlaceTrack(Player player, Tile from, Tile to){
        //can't place track into itself
        if(from == to) return false;

        //player needs to be there to place tracks there
        if(player.position != from
            && !player.tracks.containsKey(from) //check existing track connections
            && !player.tracks.containsKey(to)
            && !(from.port != null && //check port
                (from.port.from == from || from.port.to == from))
            && world.getMajorCity(from) == null){ //make sure to check for major cities too; track can be placed from any major city
            return false;
        }

        //check max track limits now
        if(!checkTrackLimits(player, from) || !checkTrackLimits(player, to)){
            return false;
        }

        //sometimes events don't allow placing tack
        if(!player.isAllowed(event -> event.canPlaceTrack(player, from, to))){
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

        //TODO custom cost for inlets and lakes
        if(from.crossings != null){
            WaterCrossing cross = from.crossings.find(c -> c.to == to);
            if(cross != null){
                baseCost += cross.cost;
            }
        }

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
        return terrain != Terrain.water;
    }

    public Array<Tile> movePlayer(Player player, Tile other){
        Array<Tile> moves = calculateMovement(player, other);
        if(moves.isEmpty()){
            return null;
        }

        //can't move backwards
        if(player.position.directionTo(moves.get(1)).opposite(player.direction) &&
                world.getCity(player.position) == null){
            return null;
        }

        //find a port in the path if one exists and stop if that is the case
        int used = moves.size;
        boolean endTurn = false;
        for(int i = 1; i < moves.size; i++){
            //find an instance of a port move
            if(moves.get(i).port == moves.get(i - 1).port && moves.get(i).port != null){
                used = i + 1;
                endTurn = true;
                break;
            }
        }

        //find set of other tracks this player moved on
        ObjectSet<Player> otherMoves = new ObjectSet<>();
        for(int i = 0; i < moves.size - 1; i++){
            Tile from = moves.get(i);
            Tile to = moves.get(i + 1);
            if(!player.hasTrack(from, to)){
                for(Player op : players){
                    if(op.hasTrack(from, to)){
                        otherMoves.add(op);
                        break;
                    }
                }
            }
        }

        //remove already moved players so as not to offset calculations
        for(Player already : player.movedPlayers){
            otherMoves.remove(already);
        }

        //truncate moves
        if(endTurn){
            moves.truncate(used);
        }

        //check costs
        if(moves.size - 1 + player.moved <= player.loco.speed &&
            player.money - otherMoves.size*4 >= 0){
            player.position = moves.peek();
            player.moved += (moves.size - 1);
            player.money -= otherMoves.size*4;
            player.movedPlayers.addAll(otherMoves);
            player.direction = moves.get(moves.size - 2).directionTo(moves.get(moves.size - 1));
            if(endTurn){
                //go to the next player if there's a port in the way
                nextPlayer();
            }
            return moves;
        }
        return null;
    }

    /** Counts cities connected to a tile using only this player's track.*/
    public int countConnectedCities(Player player, Tile other){
        moveArray.clear();
        closedSet.clear();
        queue.clear();
        citySet.clear();

        queue.addFirst(other);
        closedSet.add(other);

        while(!queue.isEmpty()){
            Tile tile = queue.removeLast();

            //add encountered city to set
            if(tile.city != null && tile.city.size == CitySize.major){
                citySet.add(tile.city);
            }

            //iterate through /connections/ of each tile
            world.connectionsOf(this, player, tile, false, child -> {
                if(!closedSet.contains(child)){
                    child.searchParent = tile;
                    queue.addFirst(child);
                    closedSet.add(child);
                }
            });
        }

        return citySet.size;
    }

    /** Attempts to calculate in-between movement tiles for a player from a start point
     * to a destination. Returns an empty array if impossible. */
    public Array<Tile> calculateMovement(Player player, Tile other){
        moveArray.clear();

        //already there.
        if(other == player.position){
            return moveArray;
        }

        closedSet.clear();
        queue.clear();

        other.searchParent = null;

        Tile result = null;
        //perform BFS
        queue.addFirst(other);
        closedSet.add(other);
        while(!queue.isEmpty()){
            Tile tile = queue.removeLast();
            if(tile == player.position){
                result = tile;
                break;
            }

            //iterate through /connections/ of each tile
            world.connectionsOf(this, player, tile, true, child -> {
                if(!closedSet.contains(child)
                        //make sure player isn't blocked by event cards!
                        && player.isAllowed(e -> e.canMove(player, tile, child))){
                    child.searchParent = tile;
                    queue.addFirst(child);
                    closedSet.add(child);
                }
            });
        }

        closedSet.clear();
        if(result != null){

            while(result != null){
                moveArray.add(result);
                result = result.searchParent;
            }
        }

        return moveArray;
    }

    /** Utility method for handling events that put the event card to the back,
     * gives it to the player and activates the handler.*/
    private void handleEvent(EventCard card, Player player, Consumer<EventCard> handler){
        cards.insert(0, card);
        if(!card.apply(this, player)){
            player.eventCards.add(card);
        }
        handler.accept(card);
    }

    /** A reason for preventing the player from placing a track at a location.
     * TODO implement*/
    public enum BlockedReason{
        badTerrain, overlapsTrack, notAdjacent, inMajorCity, cityLimit
    }
}
