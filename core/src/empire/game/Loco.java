package empire.game;

/** Defines a loco card.*/
public enum Loco{
    freight("Freight", 2, 9),
    fastFreight("Fast Freight", 2, 12),
    heavyFreight("Heavy Freight", 3, 9),
    superFreight("Super Freight", 3, 12);

    /** Max number of goods carried and maximum number of mileposts moved per turn, respectively.*/
    public final int loads, speed;
    public final String name;

    Loco(String name, int loads, int speed){
        this.loads = loads;
        this.speed = speed;
        this.name = name;
    }

    @Override
    public String toString(){
        return name;
    }
}
