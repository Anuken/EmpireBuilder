package empire.gfx;

import empire.game.Actions.*;
import empire.game.*;
import empire.game.World.Tile;
import empire.io.CardIO;
import empire.net.Net.NetListener;
import io.anuke.arc.collection.IntMap;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.reflect.ClassReflection;
import io.anuke.arc.util.serialization.Json;
import io.anuke.arc.util.serialization.Json.Serializer;
import io.anuke.arc.util.serialization.JsonValue;

import static empire.gfx.EmpireCore.net;
import static empire.gfx.EmpireCore.state;

/** Relays and handles actions.*/
public class ActionRelay implements NetListener{
    private Json json = new Json();
    private IntMap<Player> players = new IntMap<>();
    private ObjectMap<String, Class<?>> classMap = new ObjectMap<>();

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

        json.setSerializer(Direction.class, new Serializer<Direction>(){
            @Override
            public void write(Json json, Direction object, Class knownType){
                json.writeValue(object.ordinal());
            }

            @Override
            public Direction read(Json json, JsonValue jsonData, Class type){
                return Direction.all[jsonData.asInt()];
            }
        });

        json.setSerializer(Loco.class, new Serializer<Loco>(){
            @Override
            public void write(Json json, Loco object, Class knownType){
                json.writeValue(object.ordinal());
            }

            @Override
            public Loco read(Json json, JsonValue jsonData, Class type){
                return Loco.values()[jsonData.asInt()];
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

        if(action instanceof AnyPlayerAction){
            ((AnyPlayerAction) action).player = state.player();
        }

        if(net.active()){
            //apply action locally; happens for server by default, but also for special local actions
            if(net.server() || action instanceof LocalAction){
                action.apply(state);
            }
            //apply effect and send
            net.send(write(action));
        }else{
            //no net? just apply it
            action.apply(state);
        }
    }

    private String write(Action action){
        Class<?> type = action.getClass();
        if(action.getClass().isAnonymousClass()){
            type = action.getClass().getSuperclass();
        }
        return ClassReflection.getSimpleName(type) + json.toJson(action);
    }

    private Action read(String str){
        int idx = str.indexOf('{');
        String name = str.substring(0, idx);
        String data = str.substring(idx);
        Class<?> type = classMap.getOr(name, () -> find("empire.game.Actions$" + name));
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
        Log.info("Client: received {0}", txt);
        Action action = read(txt);

        //assign player to action
        if(action instanceof PlayerAction){
            ((PlayerAction) action).player = state.player();
        }

        //local actions have already been applied clientside, ignored htem
        if(state.player().local && action instanceof LocalAction){
            return;
        }

        //apply it
        action.apply(state);
    }

    @Override
    public void disconnected(Throwable reason){

    }

    //server

    @Override
    public void disconnected(int connection){
        if(!players.containsKey(connection)){
            return; //nobody cares
        }
        Player p = players.get(connection);

        state.reclaimCards(p);
        //send it out to everyone else first
        net.send(write(new Disconnect(){{
            player = state.players.indexOf(p);
        }}));

        //only apply after it has been sent.
        state.players.remove(p);
        players.remove(connection);

        //prevent index out of bounds errors
        state.currentPlayer %= state.players.size;
    }

    @Override
    public void messsage(int connection, String text){
        Action action = read(text);
        if(!players.containsKey(connection)){
            if(!(action instanceof Connect)){
                return; //first message MUST be a connect
            }

            Connect connect = (Connect)action;

            //write world state
            net.send(connection, write(new WorldSend(){{
                cards = state.cards.mapInt(c -> c.id);
                players = state.players.toArray(Player.class);
                currentPlayer = state.currentPlayer;
                turn = state.turn;
            }}));

            //send forward message to everyone
            handle(new ConnectForward(){{
                name = connect.name;
                start = connect.start;
                color = connect.color;
            }});

            //last player must always be the one that was just added
            state.players.peek().local = false;
            players.put(connection, state.players.peek());
            Log.info("Connection success: {0}//'{1}'", connection, connect.name);
        }else{
            Player player = players.get(connection);
            if(action instanceof PlayerAction){
                ((PlayerAction) action).player = player;
            }

            if(action instanceof AnyPlayerAction){
                ((AnyPlayerAction) action).player = player;
            }

            if(state.player() != player && !(action instanceof AnyPlayerAction)){
                Log.err("Player '{0}' just attempted to do an action not in their turn!", player.name);
                return;
            }

            //apply action and send it out
            action.apply(state);
            net.send(write(action));
            Log.info("Received packet from {0}: \n{1}", connection, json.prettyPrint(action));
        }
    }
}
