package empire.game;

import empire.game.World.City;

/** Represents a demand card in the game.*/
public class DemandCard{
    /** The 3 demands of this card.*/
    public final Demand[] demands;

    public DemandCard(Demand[] demands){
        this.demands = demands;
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
    }
}
