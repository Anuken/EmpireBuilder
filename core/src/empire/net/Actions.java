package empire.net;

import empire.game.*;
import empire.game.World.Tile;
import io.anuke.arc.util.serialization.Json;
import io.anuke.arc.util.serialization.Json.Serializer;
import io.anuke.arc.util.serialization.JsonValue;

import static empire.gfx.EmpireCore.state;

public class Actions{
    private static Json json = new Json();

    static{
        json.setSerializer(Tile.class, new Serializer<Tile>(){
            @Override
            public void write(Json json, Tile object, Class knownType){
                json.writeValue(state.world.index(object));
            }

            @Override
            public Tile read(Json json, JsonValue jsonData, Class type){
                return state.world.tile(jsonData.asInt());
            }
        });
    }

    public interface Action{
        void apply(State state);
    }

    public abstract static class PlayerAction implements Action{
        public Player player;
    }

    public static class PlaceTrack extends PlayerAction{
        public Tile from, to;

        public void apply(State state){
            state.placeTrack(player, from, to);
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
