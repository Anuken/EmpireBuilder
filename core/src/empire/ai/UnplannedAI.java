package empire.ai;

import empire.game.Actions.LoadCargo;
import empire.game.Actions.Move;
import empire.game.Actions.PlaceTrack;
import empire.game.Actions.SellCargo;
import empire.game.DemandCard.Demand;
import empire.game.Player;
import empire.game.State;
import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
import io.anuke.arc.util.Log;

public class UnplannedAI extends AI{

    public UnplannedAI(Player player, State state){
        super(player, state);
    }

    @Override
    public void act(){
        if(!player.chosenLocation){
            selectLocation();
        }

        moveWithoutPlan();

        end();
    }

    void moveWithoutPlan(){
        Array<Tile> finalPath = new Array<>();
        boolean shouldMove = !state.isPreMovement();

        boolean moved = true;

        //keep attempting to move unless nothing happened
        while(moved){
            moved = false;

            //player already has some cargo to unload; find a city to unload to
            if(!player.cargo.isEmpty()){
                String good = player.cargo.peek();
                float minBuyCost = Float.MAX_VALUE;

                //find best city to sell good to
                for(City city : Array.with(state.world.cities()).select(s -> player.canDeliverGood(s, good))){
                    //get a* cost to this city
                    float dst = astar(player.position, state.world.tile(city.x, city.y));

                    //if this source city is better, update things
                    if(dst < minBuyCost){
                        minBuyCost = dst;
                        finalPath.set(astarTiles);
                    }
                }

                //check if player is at a city that they can deliver to right now
                City atCity = state.world.getCity(player.position);
                if(player.canDeliverGood(atCity, good)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position)){
                        SellCargo sell = new SellCargo();
                        sell.cargo = good;
                        sell.act();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't sell {0} at {1}, waiting.", good, atCity.name);
                        shouldMove = false;
                    }
                }
            }else{

                City bestLoadCity = null;
                String bestGood = null;
                float bestCost = Float.MAX_VALUE;

                //find best demand
                for(Demand demand : player.allDemands()){
                    float minBuyCost = Float.MAX_VALUE;
                    City minBuyCity = null;

                    //find best city to get good from for this demand
                    for(City city : Array.with(state.world.cities()).select(s -> s.goods.contains(demand.good))){
                        //get a* cost: from the player to the city, then from the city to the final destination
                        float dst = astar(player.position, state.world.tile(city.x, city.y)) +
                                astar(state.world.tile(city.x, city.y),
                                        state.world.tile(demand.city.x, demand.city.y));

                        //if this source city is better, update things
                        if(dst < minBuyCost){
                            minBuyCost = dst;
                            minBuyCity = city;
                        }
                    }

                    //update cost to reflect the base good cost
                    minBuyCost -= demand.cost * 250f;

                    if(minBuyCost < bestCost){
                        bestCost = minBuyCost;
                        bestGood = demand.good;
                        bestLoadCity = minBuyCity;
                    }
                }

                if(bestLoadCity == null) throw new IllegalArgumentException("No city to load from found!");

                //now the best source and destination has been found.
                finalPath.clear();
                //move from position to the city
                astar(player.position, state.world.tile(bestLoadCity.x, bestLoadCity.y));
                finalPath.set(astarTiles);

                //check if player is at a city that they can get cargo from right now
                City atCity = state.world.getCity(player.position);
                if(atCity == bestLoadCity && atCity.goods.contains(bestGood)){
                    //attempt to deliver if possible
                    if(state.canLoadUnload(player, player.position)){
                        LoadCargo load = new LoadCargo();
                        load.cargo = bestGood;
                        load.act();
                        moved = true;
                    }else{
                        //if it's not possible, something's up with events, don't move
                        Log.info("Can't load {0} at {1}, waiting.", bestGood, atCity.name);
                        shouldMove = false;
                    }
                }
            }

            Tile last = player.position;
            //now place all track if it can
            for(Tile tile : finalPath){
                if(!player.hasTrack(last, tile)){
                    if(state.canPlaceTrack(player, last, tile)){
                        PlaceTrack place = new PlaceTrack();
                        place.from = last;
                        place.to = tile;
                        place.act();
                        moved = true;
                    }else{
                        //can't move or place track, maybe due to an event
                        Log.info("{0}: Can't place track {1} -> {2}", player.name, last.str(), tile.str());
                        break;
                    }
                }
                last = tile;
            }

            //if the AI should move, try to do so
            if(shouldMove){
                for(Tile tile : finalPath){
                    if(state.canMove(player, tile)){
                        Move move = new Move();
                        move.to = tile;
                        move.act();
                        moved = true;

                        //moves may skip turns; if that happens, break out of the whole thing
                        if(state.player() != player){
                            return;
                        }
                    }else{
                        //can't move due to an event or something
                        Log.info("{0}: Can't move {1} -> {2}", player.name, player.position.str(), tile.str());
                        break;
                    }
                }
            }
        }
    }
}
