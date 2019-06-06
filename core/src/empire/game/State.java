package empire.game;

import empire.game.DemandCard.Demand;
import empire.game.GameEvents.EndTurnEvent;
import empire.game.GameEvents.EventEvent;
import empire.game.GameEvents.WinEvent;
import empire.game.World.*;
import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.collection.Queue;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.Structs;

/** Holds the state of the entire game. */
public class State{
    /** Cost to upgrade a loco.*/
    public final static int locoCost = 20;
    /** Max amount of money to spend on rails per turn.*/
    public final static int maxRailSpend = 20;
    /** Amount of money needed to win.*/
    public final static int winMoneyAmount = 250;
    /** Number of turns with no movement at the start.*/
    public final static int preMovementTurns = 2;
    /** Number of cities connected needed to win.*/
    public final static int winCityAmount = 7;
    /** How much it costs to move on someone else's track per turn.*/
    public final static int otherMoveTrackCost = 4;

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
                        Events.fire(new WinEvent(player));
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
    public void discardCards(Player player){
        for(int i = 2; i >= 0; i--){
            cards.insert(0, player.demandCards[i]);
        }
        for(int i = 0; i < 3; i++){
            player.demandCards[i] = drawDemandCard(event ->
                    handleEvent(event, player, card -> Events.fire(new EventEvent(card))));
        }
    }

    /** Simulates a player purchasing a loco.*/
    public void purchaseLoco(Player player, Loco loco){
        player.loco = loco;
        player.money -= locoCost;
    }

    /** Switches turns to the next player.
     * Increments total turn if needed.*/
    public void nextPlayer(){
        Player last = player();
        //reset turn-specific data
        last.moneySpent = 0;
        last.moved = 0;
        last.movedPlayers.clear();
        last.eventCards.clear();
        last.eventCards.addAll(last.drawEventCards);
        last.drawEventCards.clear();
        if(last.lostTurns > 0){
            last.lostTurns --;
        }
        checkIfWon(last);

        //begin next player's turn
        currentPlayer ++;
        if(currentPlayer >= players.size){
            currentPlayer = 0;
            turn ++;
        }

        Events.fire(new EndTurnEvent(last, player()));

        //recursively advance the next player until there are no lost turns left.
        if(player().lostTurns > 0){
            nextPlayer();
        }
    }

    /** Checks for lost turns. If the current player has a lost turn, skips to the next player. */
    public void checkLostTurns(){
        if(player().lostTurns > 0){
            nextPlayer();
        }
    }

    /** Places a single track for a player and updates money for the player. */
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

    public Player localPlayer(){
        Player found = players.find(p -> p.local);
        return player().local ? player() : (found == null ? player() : found);
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
            if(tile.city.size != CitySize.major && player.tracks.containsKey(tile) && player.tracks.get(tile).size >= 3){
                return true;
            }

            //max 3 players can build into medium, max 2 into small
            if(tile.city.size == CitySize.medium && players.count(p -> p.tracks.containsKey(tile)) >= 3){
                return false;
            }else if(tile.city.size == CitySize.small && players.count(p -> p.tracks.containsKey(tile)) >= 2){
                return false;
            }
        }

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

        //make sure that this track is not used by a different player
        for(Player other : players){
            if((other.tracks.containsKey(from) && other.tracks.get(from).contains(to))
                || (other.tracks.containsKey(to) && other.tracks.get(to).contains(from))){
                return false;
            }
        }

        //make sure the player can spend the money
        if(!canSpendTrack(player, getTrackCost(from, to))){
            return false;
        }

        return true;
    }

    /** @return whether this player can place this rail at the specified price.*/
    public boolean canSpendTrack(Player player, int amount){
        return player.moneySpent + amount <= maxRailSpend && player.money - amount >= 0;
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
        //large cities cannot be placed in
        return tile.city == null || tile.city.size != CitySize.major;
    }

    /** Returns whether a rail can be placed on this terrain.*/
    public boolean canPlaceOn(Terrain terrain){
        return terrain != Terrain.water;
    }

    /** Returns whether or not this player can move to this adjacent tile.*/
    public boolean canMove(Player player, Tile to){
        //can't move nowhere, that would be pointless
        if(to == player.position){
            return false;
        }

        //out of moves
        if(player.moved + moveCost(player, to) > player.loco.speed){
            return false;
        }

        //moving backwards
        if(player.position.directionTo(to) != null &&
                player.position.directionTo(to).opposite(player.direction) &&
                world.getCity(player.position) == null){
            return false;
        }

        //player has a track here, so they can definitely move
        if(player.hasTrack(player.position, to)){
            return true;
        }

        //port connection
        if(player.position.port == to.port && player.position.port != null){
            return true;
        }

        //can move around major cities for free
        if(world.getMajorCity(player.position) == world.getMajorCity(to) && world.getMajorCity(to) != null
                && world.isAdjacent(player.position, to)){
            return true;
        }

        //check if there's another player with a track here; if there is, and the player hasn't yet
        //moved on that track, check the amount of money the player has to make sure they can move here
        Player otherTrack = players.find(p -> p.hasTrack(player.position, to));
        if(otherTrack != null){
            return player.money >= (player.movedPlayers.contains(otherTrack) ? 0 : otherMoveTrackCost);
        }

        return false;
    }

    /** Returns move cost of getting to this tile. */
    public int moveCost(Player player, Tile to){
        for(EventCard card : player.eventCards){
            if(card.isHalfRate(player, to)){
                return 2;
            }
        }
        return 1;
    }

    /** Moves a player by a single tile.
     * Adds movement cost as needed.*/
    public void move(Player player, Tile to){
        if(!canMove(player, to)){
            throw new IllegalArgumentException(Strings.format("Illegal player movement of {0}: {1} to {2}",
                        player.name, player.position.str(), to.str()));
        }

        //change player direction
        if(player.position.directionTo(to) != null){
            player.direction = player.position.directionTo(to);
        }

        //moving ports.
        boolean endTurn = to.port != null && player.position.port != null;

        //pay up for moving on other's tracks
        Player otherTrack = players.find(p -> p.hasTrack(player.position, to));
        if(otherTrack != player && otherTrack != null){
            if(!player.movedPlayers.contains(otherTrack)){
                player.money -= otherMoveTrackCost;
                player.movedPlayers.add(otherTrack);
            }
        }

        player.moved += moveCost(player, to);
        player.position = to;

        //end turn on port movement
        if(endTurn){
            nextPlayer();
        }
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
            world.trackConnectionsOf(this, player, tile, false, child -> {
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
     * to a destination. Returns an empty array if impossible.
     * This should be used to move from tile to tile and check each one for validity.*/
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
            world.trackConnectionsOf(this, player, tile, true, child -> {
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
            player.drawEventCards.add(card);
        }
        handler.accept(card);
    }

    /** A reason for preventing the player from placing a track at a location.
     * TODO implement*/
    public enum BlockedReason{
        badTerrain, overlapsTrack, notAdjacent, inMajorCity, cityLimit
    }
}
