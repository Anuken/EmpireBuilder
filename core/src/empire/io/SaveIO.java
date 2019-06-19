package empire.io;

import empire.game.Actions.WorldSend;
import empire.game.*;
import empire.gfx.EmpireCore;
import io.anuke.arc.files.FileHandle;

public class SaveIO{

    public static void save(State state, FileHandle file){
        file.writeString(EmpireCore.actions.writeString(new WorldSend(){{
            cards = state.cards.mapInt(c -> c.id);
            players = state.players.toArray(Player.class);
            currentPlayer = state.currentPlayer;
            turn = state.turn;
        }}));
    }

    public static void load(State state, FileHandle file){
        WorldSend send = EmpireCore.actions.readString(WorldSend.class, file.readString());
        send.apply(state);
        state.players.first().local = true;

        EmpireCore.actions.handleStateLoad(send);
    }
}
