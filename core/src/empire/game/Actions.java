package empire.game;

import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import io.anuke.arc.graphics.Color;

/** Just a class for storing a bunch of action classes.*/
public class Actions{

    public interface Action{
        void apply(State state);

        default void act(){
            EmpireCore.actions.handle(this);
        }
    }

    public abstract static class PlayerAction implements Action{
        public Player player;
    }

    public static class Connect implements Action{
        public String name;
        public Color color;
        public Tile start;

        @Override
        public void apply(State state){
            //nothing happens as this is a special case handled by the server
        }
    }

    public static class ConnectForward implements Action{
        public String name;
        public Color color;
        public Tile start;

        @Override
        public void apply(State state){
            state.players.add(new Player(name, start, color, state.grabCards()));
        }
    }

    public static class Disconnect implements Action{
        public Player player;

        @Override
        public void apply(State state){
            state.players.remove(player);
        }
    }

    public static class Chat extends PlayerAction{

        @Override
        public void apply(State state){

        }
    }

    public static class PlaceTrack extends PlayerAction{
        public Tile from, to;

        @Override
        public void apply(State state){
            state.placeTrack(player, from, to);
        }
    }

    public static class LoadCargo extends PlayerAction{
        public String cargo;

        @Override
        public void apply(State state){

        }
    }

    public static class SellCargo extends PlayerAction{
        public String cargo;

        @Override
        public void apply(State state){

        }
    }

    public static class DiscardCards extends PlayerAction{

        @Override
        public void apply(State state){

        }
    }

    public static class UpgradeLoco extends PlayerAction{
        public int type; //0 = fast, 1 = heavy

        public void apply(State state){
            state.purchaseLoco(player, player.loco == Loco.freight ? type == 0 ? Loco.fastFreight : Loco.heavyFreight : Loco.superFreight);
        }
    }
}
