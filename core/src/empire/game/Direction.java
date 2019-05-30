package empire.game;

import io.anuke.arc.math.Mathf;

/** Defines a direction a player could face.*/
public enum Direction{
    right, upRight, upLeft, left, downLeft, downRight;

    public static final Direction[] all = values();

    public float angle(){
        return ordinal() * 60;
    }

    public int diff(Direction other){
        return Math.abs(other.ordinal() - ordinal());
    }

    public boolean opposite(Direction other){
        return diff(other) == 3;
    }

    public Direction next(){
        return all[(ordinal() + 1) % all.length];
    }

    public Direction prev(){
        return all[Mathf.mod(ordinal() - 1, all.length)];
    }
}
