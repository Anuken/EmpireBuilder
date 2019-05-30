package empire.gfx;

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

        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));
        state.cards = CardIO.loadCards(state.world, Core.files.internal("maps/deck.txt"));
        state.cards.shuffle(); //shuffle cards when inputted

        City startCity = Array.with(state.world.cities()).random();
        City otherCity = Array.with(state.world.cities()).random();

        state.players.add(new Player("Me", state.world.tile(startCity.x, startCity.y), Color.PINK, state.grabCards()));
        state.players.add(new Player("You", state.world.tile(otherCity.x, otherCity.y), Color.GOLD, state.grabCards()));

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());
    }
}
