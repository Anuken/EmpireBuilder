package empire.game;

import empire.game.World.City;
import empire.game.World.River;
import empire.game.World.Sea;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.collection.Array;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.util.Strings;

import java.util.Scanner;

import static empire.gfx.EmpireCore.ui;

/** Defines some type of event card, which may prevent the player from doing an action
 * or simply do something to the player when it is first drawn. */
public abstract class EventCard extends Card{

    /** Loads an event card from a text file.*/
    public abstract void load(Scanner scan, World world);

    public abstract String name();

    public abstract String description(Player player);

    /** Applies the card's effects. Optional.
     * @return true if this card was discarded. */
    public boolean apply(State state, Player player){
        return false;
    }

    /** @return whether the player can move with this card drawn. */
    public boolean canMove(Player player, Tile from, Tile to){
        return true;
    }

    /** @return whether the player can place track with this card drawn. */
    public boolean canPlaceTrack(Player player, Tile from, Tile to){
        return true;
    }

    /** @return whether the player can load cargo with this card drawn. */
    public boolean canLoadOrUnload(Player player, Tile tile){
        return true;
    }

    /** @return whether this position is in an area with half rate. */
    public boolean isHalfRate(Player player, Tile tile){
        return false;
    }

    public static class InlandStrikeEvent extends EventCard{

        @Override
        public void load(Scanner scan, World world){
            scan.nextLine();
        }

        @Override
        public String name(){
            return "Inland Strike";
        }

        @Override
        public String description(Player player){
            return "No loading or unloading >3 mileposts from shore.";
        }

        @Override
        public boolean canLoadOrUnload(Player player, Tile tile){
            return !tile.inland;
        }
    }

    public static class CoastalStrikeEvent extends EventCard{

        @Override
        public void load(Scanner scan, World world){
            scan.nextLine();
        }

        @Override
        public String name(){
            return "Coastal Strike";
        }

        @Override
        public String description(Player player){
            return "No loading or unloading <=3 mileposts from shore.";
        }

        @Override
        public boolean canLoadOrUnload(Player player, Tile tile){
            return tile.inland;
        }
    }

    public static class GeneralRailStrikeEvent extends EventCard{

        @Override
        public void load(Scanner scan, World world){
            scan.nextLine();
        }

        @Override
        public String name(){
            return "General Rail Strike";
        }

        @Override
        public String description(Player player){
            return "No building or movement.";
        }

        @Override
        public boolean canPlaceTrack(Player player, Tile from, Tile to){
            return player.eventCards.contains(c -> c == this);
        }

        @Override
        public boolean canMove(Player player, Tile from, Tile to){
            return !EmpireCore.state.players.contains(p -> p.eventCards.contains(c -> c == this));
        }
    }

    public static class ExcessProfitsTaxEvent extends EventCard{

        @Override
        public void load(Scanner scan, World world){
            //that's it
        }

        @Override
        public String name(){
            return "Profits Tax";
        }

        @Override
        public boolean apply(State state, Player player){
            player.money -= getTax(player.money);
            return true;
        }

        @Override
        public String description(Player player){
            return "Pay[coral] " + getTax(player.money) + "[] ECU in taxes.";
        }

        /** Returns tax amount based on player's money.*/
        public int getTax(int money){
            if(money < 50){
                return 0;
            }else if(money < 100){
                return 10;
            }else if(money < 150){
                return 15;
            }else if(money < 200){
                return 20;
            }else{
                return 25;
            }
        }
    }

    public static class DerailmentEvent extends EventCard{
        public Array<City> cities;
        public final int dst = 3;

        @Override
        public void load(Scanner scan, World world){
            cities = Array.with(scan.nextLine().substring(1).split(" ")).map(name -> world.getCity(name.toLowerCase()));
        }

        @Override
        public String name(){
            return "Derailment";
        }

        @Override
        public String description(Player player){
            return "All trains within " + dst +
                    " mileposts of these cities lose a load and a turn: \n[lime]"
                    + cities.toString(", ", City::formalName);
        }

        @Override
        public boolean apply(State state, Player player){
            //remove cargo and lose turns for players near these cities
            for(Player p : state.players){
                for(City city : cities){
                    if(p.position.distanceTo(city.x, city.y) <= dst){
                        p.lostTurns ++;
                        //TODO, currently the last cargo is popped off instead of a choice
                        if(p.cargo.size > 0){
                            String cargo = p.cargo.pop();
                            if(p.local){
                                ui.showDialog("You've been derailed!", d -> {
                                    d.cont.add("You were near [lime]" + city.formalName() + "[].\nCargo lost: [yellow]" + Strings.capitalize(cargo));
                                });
                            }
                        }else{
                            if(p.local){
                                ui.showDialog("You've been derailed!", d -> {
                                    d.cont.add("You were near [lime]" + city.formalName() + "[].\nNo cargo in train, so none lost.");
                                });
                            }
                        }
                        break;
                    }
                }
            }

            //check for lost turns to skip forward if needed
            state.checkLostTurns();
            return true;
        }
    }

    public static class HeavySnowEvent extends EventCard{
        public int dst;
        public City city;

        @Override
        public void load(Scanner scan, World world){
            dst = scan.nextInt();
            city = world.getCity(scan.next().toLowerCase());
        }

        @Override
        public String name(){
            return "Heavy Snow";
        }

        @Override
        public String description(Player player){
            return "All trains within " + dst + " mileposts of[lime] " + city.formalName()
                    +"[] move at half rate.\nTrains in mountain here are prevented from placing track or moving.";
        }

        @Override
        public boolean canPlaceTrack(Player player, Tile from, Tile to){
            //prevent mountain placing
            return !(to.isMountainous() && to.distanceTo(city.x, city.y) <= dst);
        }

        @Override
        public boolean canMove(Player player, Tile from, Tile to){
            //prevent mountain moving
            return !(to.isMountainous() && to.distanceTo(city.x, city.y) <= dst);
        }

        @Override
        public boolean isHalfRate(Player player, Tile tile){
            return tile.distanceTo(city.x, city.y) <= dst;
        }
    }

    //TODO add no build zone near seas!
    public static class GaleEvent extends EventCard{
        public int dst;
        public Array<Sea> seas;

        private boolean out = false;

        @Override
        public void load(Scanner scan, World world){
            dst = scan.nextInt();
            seas = Array.with(scan.nextLine().substring(1).split(" ")).map(world::getSea);
        }

        @Override
        public String name(){
            return "Gale";
        }

        @Override
        public String description(Player player){
            return "All trains within " + dst + " mileposts of the following seas move at half rate:\n[lime]"
                    + seas.toString(", ", Sea::formalName);
        }

        @Override
        public boolean isHalfRate(Player player, Tile tile){
            State state = EmpireCore.state;
            out = false;

            Geometry.circle(player.position.x, player.position.y,
                    state.world.width, state.world.height, dst, (x, y) -> {
                if(seas.contains(state.world.tile(x, y).sea)){
                    out = true;
                }
            });

            return out;
        }

        @Override
        public boolean canPlaceTrack(Player player, Tile from, Tile to){
            return !isHalfRate(player, to);
        }
    }

    public static class FogEvent extends EventCard{
        public int dst;
        public City city;

        @Override
        public void load(Scanner scan, World world){
            dst = scan.nextInt();
            city = world.getCity(scan.next().toLowerCase());
        }

        @Override
        public String name(){
            return "Fog";
        }

        @Override
        public String description(Player player){
            return "Can't place track within " + dst + " mileposts of " + city.formalName() + ".\nPlayer moves at half rate here.";
        }

        @Override
        public boolean canPlaceTrack(Player player, Tile from, Tile to){
            return !isHalfRate(player, to);
        }

        @Override
        public boolean isHalfRate(Player player, Tile tile){
            return tile.distanceTo(city.x, city.y) > dst;
        }
    }

    public static class FloodEvent extends EventCard{
        public River river;

        @Override
        public void load(Scanner scan, World world){
            river = world.getRiver(scan.next().toLowerCase());
        }

        @Override
        public String name(){
            return "Flood";
        }

        @Override
        public String description(Player player){
            return "All tracks crossing the[lime] " + Strings.capitalize(river.name) + "[] have been destroyed.\nNo tracks can be placed across this river.";
        }

        @Override
        public boolean apply(State state, Player player){
            //remove all track that crosses this river
            Array<Tile[]> removals = new Array<>();

            player.eachTrack((from, to) -> {
                if(from.crossings != null && from.crossings.contains(p -> p.to == to && p.river == river)){
                    removals.add(new Tile[]{from, to});
                }
            });

            for(Tile[] pair : removals){
                player.removeTrack(pair[0], pair[1]);
            }
            return false;
        }

        @Override
        public boolean canPlaceTrack(Player player, Tile from, Tile to){
            //prevent placing across rivers here
            if(from.crossings != null && from.crossings.contains(p -> p.to == to && p.river == river)){
                return false;
            }
            return true;
        }
    }
}
