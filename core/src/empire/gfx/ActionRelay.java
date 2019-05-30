package empire.gfx;

import empire.game.Actions.*;
import empire.game.DemandCard;
import empire.game.EventCard;
import empire.game.Player;
import empire.game.World.Tile;
import empire.io.CardIO;
import empire.net.Net.NetListener;
import io.anuke.arc.collection.IntMap;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.reflect.ClassReflection;
import io.anuke.arc.util.serialization.Json;
import io.anuke.arc.util.serialization.Json.Serializer;
import io.anuke.arc.util.serialization.JsonValue;

import static empire.gfx.EmpireCore.*;

/** Relays and handles actions.*/
public class ActionRelay implements NetListener{
    private Json json = new Json();
    private IntMap<Player> players = new IntMap<>();
    private ObjectMap<String, Class<?>> classMap = new ObjectMap<>();
    private Connect connectInfo;

    public ActionRelay(){
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

        json.setSerializer(Player.class, new Serializer<Player>(){
            @Override
            public void write(Json json, Player object, Class knownType){
                json.writeValue(state.players.indexOf(object));
            }

            @Override
            public Player read(Json json, JsonValue jsonData, Class type){
                return state.players.get(jsonData.asInt());
            }
        });

        json.setSerializer(Color.class, new Serializer<Color>(){
            @Override
            public void write(Json json, Color object, Class knownType){
                json.writeValue(object.toString());
            }

            @Override
            public Color read(Json json, JsonValue jsonData, Class type){
                return Color.valueOf(jsonData.asString());
            }
        });

        json.setSerializer(EventCard.class, new Serializer<EventCard>(){
            @Override
            public void write(Json json, EventCard object, Class knownType){
                json.writeValue(object.id);
            }

            @Override
            public EventCard read(Json json, JsonValue jsonData, Class type){
                return (EventCard) CardIO.cardsByID[jsonData.asInt()];
            }
        });

        json.setSerializer(DemandCard.class, new Serializer<DemandCard>(){
            @Override
            public void write(Json json, DemandCard object, Class knownType){
                json.writeValue(object.id);
            }

            @Override
            public DemandCard read(Json json, JsonValue jsonData, Class type){
                return (DemandCard) CardIO.cardsByID[jsonData.asInt()];
            }
        });
    }

    public void beginConnect(Connect connect, String host, Runnable connected, Consumer<Throwable> error){
        net.connect(host, () -> {
            net.send(write(connect));
            connected.run();
        }, error);
    }

    /** Handles an action locally. This only comes from the local player.*/
    public void handle(Action action){

        //assign player to action
        if(action instanceof PlayerAction){
            ((PlayerAction) action).player = state.player();
        }

        if(net.active()){
            //apply effect and send
            net.send(write(action));
        }else{
            //no net? just apply it
            action.apply(state);
        }
    }

    private String write(Action action){
        String name = ClassReflection.getSimpleName(action.getClass());
        return name + "|" + json.toJson(action);
    }

    private Action read(String str){
        int idx = str.indexOf('|');
        String name = str.substring(0, idx - 1);
        String data = str.substring(idx + 1);
        Class<?> type = classMap.getOr(name, () -> find("empire.game.Actions." + name));
        return (Action) json.fromJson(type, data);
    }

    private Class<?> find(String name){
        try{
            return ClassReflection.forName(name);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    //client

    @Override
    public void message(String txt){
        handle(read(txt));
    }

    @Override
    public void disconnected(Throwable reason){
        reason.printStackTrace();
        ui.showDialog("Disconnected.", d -> d.cont.add(Strings.parseException(reason, false)).width(400f));
    }

    //server

    @Override
    public void disconnected(int connection){
        if(!players.containsKey(connection)){
            return; //nobody cares
        }
        Player p = players.get(connection);

        //send it out to everyone else first
        net.send(write(new Disconnect(){{
            player = p;
        }}));

        //only apply after it has been sent.
        state.players.remove(p);
        players.remove(connection);
    }

    @Override
    public void messsage(int connection, String text){
        Action action = read(text);
        if(!players.containsKey(connection)){
            if(!(action instanceof Connect)){
                return; //first message MUST be a connect
            }

            Connect connect = (Connect)action;

            //send forward message to everyone
            handle(new ConnectForward(){{
                name = connect.name;
                start = connect.start;
                color = connect.color;
            }});

            //last player must always be the one that was just added
            players.put(connection, state.players.peek());
            Log.info("Connection success: {0}//'{1}'", connection, connect.name);
        }else{
            Player player = players.get(connection);
            if(action instanceof PlayerAction){
                ((PlayerAction) action).player = player;
            }
            //apply action and send it out
            action.apply(state);
            net.send(write(action));
            Log.info("Received packet from {0}: \n{1}", connection, json.prettyPrint(action));
        }
    }
}
