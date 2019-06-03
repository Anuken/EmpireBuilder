package empire.game;

import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.EmpireCore;
import empire.io.CardIO;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.IntArray;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.util.Strings;

import static empire.gfx.EmpireCore.renderer;
import static empire.gfx.EmpireCore.ui;

/** Just a class for storing a bunch of action classes.*/
public class Actions{

    public interface Action{
        void apply(State state);

        default void act(){
            EmpireCore.actions.handle(this);
        }
    }

    public interface LocalAction{

    }

    public abstract static class PlayerAction implements Action{
        public transient Player player;
    }

    public abstract static class AnyPlayerAction implements Action{
        public int playerID;
    }

    public static class WorldSend implements Action{
        public IntArray cards;
        public Player[] players;
        public int currentPlayer;
        public int turn;

        @Override
        public void apply(State state){
            state.players.clear();
            state.players.addAll(players);
            //what this does is clear local players, since nothing sent here can be local
            state.players.each(player -> player.local = false);
            state.turn = turn;
            state.currentPlayer = currentPlayer;
            state.cards.clear();

            for(int i = 0; i < cards.size; i++){
                state.cards.add(CardIO.cardsByID[cards.get(i)]);
            }
        }
    }

    public static class Connect implements Action{
        public String name;
        public Color color;

        @Override
        public void apply(State state){
            //nothing happens as this is a special case handled by the server
        }
    }

    public static class ConnectForward implements Action{
        public String name;
        public Color color;

        @Override
        public void apply(State state){
            //doesn't matter what's selected here, as the city will be selected later anyway
            City randomCity = state.world.cities().iterator().next();

            state.players.add(new Player(name, state.world.tile(randomCity.x, randomCity.y), color, state.grabCards()));

            //must be local player if it's just sent and there's nothing local yet.
            if(!state.players.contains(p -> p.local)){
                state.players.peek().local = true;
            }
        }
    }

    public static class Disconnect implements Action{
        public int player;

        @Override
        public void apply(State state){
            Player p = state.players.get(player);
            state.reclaimCards(p);
            state.players.remove(player);
        }
    }

    public static class Chat extends AnyPlayerAction{
        public String message;

        @Override
        public void apply(State state){
            Player player = state.players.get(playerID);
            ui.chat.addMessage(message, "[#" + player.color + "]" + player.name);
        }
    }

    public static class EndTurn extends PlayerAction{

        @Override
        public void apply(State state){
            state.nextPlayer();
        }
    }

    public static class ChooseStart extends PlayerAction{
        public Tile location;

        @Override
        public void apply(State state){
            player.position = location;
            player.chosenLocation = true;
            renderer.doLerp = true;
        }
    }

    public static class Move extends PlayerAction{
        public Tile to;

        @Override
        public void apply(State state){
            //TODO animation
            Array<Tile> path = state.movePlayer(player, to);
        }
    }

    public static class PlaceTrack extends PlayerAction implements LocalAction{
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
            player.addCargo(cargo);
            if(player.local){
                ui.showFade(Strings.capitalize(cargo) + " obtained.");
            }
        }
    }

    public static class SellCargo extends PlayerAction{
        public String cargo;

        @Override
        public void apply(State state){
            state.sellGood(state.player(), player.position.city, cargo, ui.events::show);
            ui.hud.refresh();
        }
    }

    public static class DiscardCards extends PlayerAction{

        @Override
        public void apply(State state){
            state.discardCards(player, ui.events::show);
            state.nextPlayer();
        }
    }

    public static class UpgradeLoco extends PlayerAction{
        public int type; //0 = fast, 1 = heavy

        public void apply(State state){
            if(player.local){
                ui.showFade("Upgrade Purchased!");
            }
            state.purchaseLoco(player, player.loco == Loco.freight ? type == 0 ? Loco.fastFreight : Loco.heavyFreight : Loco.superFreight);
        }
    }
}
