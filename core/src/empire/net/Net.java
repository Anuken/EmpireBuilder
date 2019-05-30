package empire.net;

import io.anuke.arc.function.Consumer;

public abstract class Net{
    protected Consumer<Throwable> errorHandler = Throwable::printStackTrace;

    public abstract void connect(String host);

    protected void handleError(Throwable t){
        errorHandler.accept(t);
    }

    public void setErrorHandler(Consumer<Throwable> handler){
        this.errorHandler = handler;
    }

    //packet types

    static class Chat{

    }

    static class RailPlace{

    }

    static class LocoUpgrade{

    }

    static class Move{

    }
}
