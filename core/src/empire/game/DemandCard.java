package empire.game;

import empire.game.World.City;

import java.util.Arrays;

/** Represents a demand card in the game.*/
public class DemandCard implements Card{
    /** The 3 demands of this card.*/
    public final Demand[] demands;

    public DemandCard(Demand[] demands){
        this.demands = demands;
    }

    @Override
    public String toString(){
        return Arrays.toString(demands);
    }

    public static class Demand{
        public final String good;
        public final City city;
        public final int cost;

        public Demand(String good, City city, int cost){
            this.city = city;
            this.cost = cost;
            this.good = good;
        }

        @Override
        public String toString(){
            return good + " to " + city.name + " at " + cost + "e";
        }
    }
}
