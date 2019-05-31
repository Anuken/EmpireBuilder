package empire.net;

import io.anuke.arc.Core;
import io.anuke.arc.collection.IntMap;
import io.anuke.arc.collection.ObjectIntMap;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.Strings;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class WebsocketNet extends Net{
    InternalServer server;
    InternalClient client;

    int lastClientID;
    boolean connecting = false;
    IntMap<WebSocket> clients = new IntMap<>();
    ObjectIntMap<WebSocket> clientsIds = new ObjectIntMap<>();

    Runnable success;
    Consumer<Throwable> error;

    @Override
    public boolean connecting(){
        return connecting;
    }

    @Override
    public boolean server(){
        return server != null;
    }

    @Override
    public boolean client(){
        return client != null && client.isOpen();
    }

    @Override
    public void connect(String host, Runnable success, Consumer<Throwable> error){
        close(); //close any current connections

        this.success = success;
        this.error = error;

        try{
            connecting = true;
            client = new InternalClient(new URI("ws://" + host + ":" + port));
            async(() -> {
                try{
                    client.connectBlocking(1000, TimeUnit.MILLISECONDS);
                    connecting = false;
                }catch(Exception e){
                    connecting = false;
                    Core.app.post(() -> error.accept(e));
                }
            });
        }catch(Exception e){
            connecting = false;
            Core.app.post(() -> error.accept(e));
        }
    }

    @Override
    public void send(String text){
        if(!active()) throw new IllegalArgumentException("Net isn't ready yet!");

        Log.info("SEND " + text);

        if(client()){
            client.send(text);
        }else{
            for(WebSocket socket : server.getConnections()){
                socket.send(text);
            }
        }
    }

    @Override
    public void close(){
        try{
            if(server != null){
                server.stop(1);
            }

            if(client != null){
                client.close();
            }
        }catch(Exception e){
            Log.info("Error closing server:\n{0}", Strings.parseException(e, true));
        }

        client = null;
        server = null;
    }

    @Override
    public void host(){
        close();

        server = new InternalServer(new InetSocketAddress(port));
        server.start();
    }

    @Override
    public void send(int connection, String text){
        if(server == null) throw new IllegalArgumentException("Server isn't ready yet!");

        clients.get(connection).send(text);
    }

    private void async(Runnable run){
        new Thread(run){{
            setDaemon(true);
        }}.start();
    }

    public class InternalServer extends WebSocketServer{

        public InternalServer(InetSocketAddress address){
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake){

        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote){
            Core.app.post(() -> {
                if(clientsIds.containsKey(conn)){
                    int id = clientsIds.get(conn, 0);
                    clientsIds.remove(conn, 0);
                    clients.remove(id);
                    listener.disconnected(id);
                }
            });
        }

        @Override
        public void onMessage(WebSocket conn, String message){
            Core.app.post(() -> {
                if(!clientsIds.containsKey(conn)){
                    int id = lastClientID ++;
                    clients.put(id, conn);
                    clientsIds.put(conn, id);
                }

                int id = clientsIds.get(conn, 0);
                listener.messsage(id, message);
            });
        }

        @Override
        public void onError(WebSocket conn, Exception ex){
            handleError(ex);
        }

        @Override
        public void onStart(){

        }
    }

    public class InternalClient extends WebSocketClient{

        public InternalClient(URI serverUri){
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata){
            Core.app.post(success);
        }

        @Override
        public void onMessage(String message){
            Core.app.post(() -> listener.message(message));
        }

        @Override
        public void onClose(int code, String reason, boolean remote){
            Core.app.post(() -> error.accept(new IOException((reason == null || reason.isEmpty()) ? "Code " + code : reason)));
            Core.app.post(() -> listener.disconnected(new IOException(reason)));
            close();
        }

        @Override
        public void onError(Exception ex){
            handleError(ex);
        }
    }
}
