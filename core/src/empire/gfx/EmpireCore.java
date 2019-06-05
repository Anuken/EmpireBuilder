package empire.gfx;

import empire.ai.AI;
import empire.game.Player;
import empire.game.State;
import empire.game.World.City;
import empire.io.CardIO;
import empire.io.MapIO;
import empire.net.Net;
import empire.net.WebsocketNet;
import io.anuke.arc.ApplicationCore;
import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 16;
    public static final boolean debug = true, isAI = false, netDebug = false;

    public static Control control;
    public static UI ui;
    public static Renderer renderer;
    public static State state;
    public static Net net;
    public static ActionRelay actions;

    @Override
    public void setup(){
        //create state and modules for viewing/controlling that state
        net = new WebsocketNet();
        actions = new ActionRelay();

        net.setListener(actions);

        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));
        state.cards = CardIO.loadCards(state.world, Core.files.internal("maps/deck.txt"));
        state.cards.shuffle(); //shuffle cards when inputted

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());

        if(isAI){
            for(int i = 0; i < 3; i++){
                City startCity = Array.with(state.world.cities()).random();
                Player player;
                state.players.add(player = new Player("AI " + (i + 1),
                        state.world.tile(startCity.x, startCity.y), new Color().randHue(), state.grabCards()));
                player.local = true;
                AI ai = new AI(player, state);
                player.ai = ai;
            }
        }else{
            City startCity = Array.with(state.world.cities()).random();
            state.players.add(new Player("Loading...", state.world.tile(startCity.x, startCity.y), Color.PINK, state.grabCards()));
            state.players.peek().local = true;
        }
    }
}
