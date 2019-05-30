package empire.game;

import empire.game.World.City;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;

import java.util.Scanner;

/** Defines some type of event card, which may prevent the player from doing an action
 * or simply do something to the player when it is first drawn. */
public abstract class EventCard implements Card{

    /** Loads an event card from a text file.*/
    public abstract void load(Scanner scan);

    public abstract String name();

    public abstract String description(Player player);

    /** Applies the card's effects. Optional.
     * @return true if this card was discarded.*/
    public boolean apply(Player player){
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
    public boolean canLoad(Player player, City city){
        return true;
    }

    /** @return whether the player can unload cargo with this card drawn. */
    public boolean canUnload(Player player, City city){
        return true;
    }

    /** Inland strike: >3 mileposts from water, no pickup or delivery. */
    public static class InlandStrikeEvent extends EventCard{
        public final int dst = 3;

        @Override
        public void load(Scanner scan){
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
    }

    /** Coastal strike: <3 mileposts from water, no pickup or delivery. */
    public static class CoastalStrikeEvent extends EventCard{
        public final int dst = 3;

        @Override
        public void load(Scanner scan){
            scan.nextLine();
        }

        @Override
        public String name(){
            return "Coastal Strike";
        }

        @Override
        public String description(Player player){
            return "No loading or unloading <3 mileposts from shore.";
        }
    }

    /** General strike: no movement, building, pickup or delivery. basically skip a turn. */
    public static class GeneralRailStrikeEvent extends EventCard{

        @Override
        public void load(Scanner scan){
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
    }

    /** Excess profits tax: Player pays tax depending on their current money.*/
    public static class ExcessProfitsTaxEvent extends EventCard{

        @Override
        public void load(Scanner scan){
            //that's it
        }

        @Override
        public String name(){
            return "Profits Tax";
        }

        @Override
        public boolean apply(Player player){
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
        public Array<String> cities;

        @Override
        public void load(Scanner scan){
            cities = Array.with(scan.nextLine().split(" "));
        }

        @Override
        public String name(){
            return "Derailment";
        }

        @Override
        public String description(Player player){
            return "No building or movement.";
        }
    }

    public static class HeavySnowEvent extends EventCard{
        public int dst;
        public String city;

        @Override
        public void load(Scanner scan){
            dst = scan.nextInt();
            city = scan.next();
        }
    }

    public static class HeavyRainsEvent extends EventCard{
        public int dst;
        public Array<String> waters;

        @Override
        public void load(Scanner scan){
            dst = scan.nextInt();
            waters = Array.with(scan.nextLine().split(" "));
        }
    }

    public static class FogEvent extends EventCard{
        public int dst;
        public String city;

        @Override
        public void load(Scanner scan){
            dst = scan.nextInt();
            city = scan.next();
        }
    }

    public static class FloodEvent extends EventCard{
        public String river;

        @Override
        public void load(Scanner scan){
            river = scan.next();
        }
    }
}
