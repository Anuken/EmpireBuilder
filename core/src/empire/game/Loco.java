package empire.game;

/** Defines a loco card.*/
public enum Loco{
    freight(2, 9),
    fastFreight(2, 12),
    heavyFreight(3, 9),
    superFreight(3, 12);

    /** Max number of goods carried and maximum number of mileposts moved per turn, respectively.*/
    public final int loads, speed;

    Loco(int loads, int speed){
        this.loads = loads;
        this.speed = speed;
    }
}
