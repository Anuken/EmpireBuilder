package empire.net;

import io.anuke.arc.function.Consumer;

/** An abstract class for handling netcode. */
public abstract class Net{
    /** The port to be used. */
    protected static final int port = 3257;

    protected Consumer<Throwable> errorHandler = Throwable::printStackTrace;
    protected NetListener listener;

    public void setListener(NetListener listener){
        this.listener = listener;
    }

    public void setErrorHandler(Consumer<Throwable> handler){
        this.errorHandler = handler;
    }

    /** Whether the net is currently in server mode, e.g. hosting. */
    public abstract boolean server();

    /** Whether the net is currently in client mode, e.g. connected to a server. */
    public abstract boolean client();

    /** Whether any sort of connection is active right now, e.g. server OR client.*/
    public boolean active(){
        return server() || client();
    }

    /** Client: Connects to an IP asynchronously. Calls either the success or error callbacks. */
    public abstract void connect(String host, Runnable success, Consumer<Throwable> error);

    /** Server: Starts hosting. */
    public abstract void host();

    /** Server: sends a packet to a specific connection ID. */
    public abstract void send(int connection, String text);

    /** If client, sends the specified text to the server.
     * If server, sends the specified text to all connected clients.*/
    public abstract void send(String text);

    /** Server: stops hosting.
     * Client: disconnects.*/
    public abstract void close();

    protected void handleError(Throwable t){
        errorHandler.accept(t);
    }

    public interface NetListener{
        /** Server: Called when a message is sent from a client ID.*/
        void messsage(int connection, String text);
        /** Server: Called when a client disconnects from this server.*/
        void disconnected(int connection);

        /** Client: called when a message is recieved.*/
        void message(String txt);
        /** Client: called on server disconnect.*/
        void disconnected(Throwable reason);
    }
}
