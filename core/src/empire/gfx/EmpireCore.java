package empire.gfx;

import empire.game.Player;
import empire.game.State;
import empire.game.World.City;
import empire.io.CardIO;
import empire.io.MapIO;
import io.anuke.arc.ApplicationCore;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 16;

    public static Control control;
    public static UI ui;
    public static Renderer renderer;
    public static State state;

    @Override
    public void setup(){
        //create state and modules for viewing/controlling that state
        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));
        state.demandCards = CardIO.loadCards(state.world, Core.files.internal("maps/deck.txt"));
        state.demandCards.shuffle(); //shuffle cards when inputted

        City startCity = state.world.getCity("berlin");
        City otherCity = state.world.getCity("leipzig");

        state.players.add(new Player(state.world.tile(startCity.x, startCity.y), Color.PINK, state.grabCards()));
        state.players.add(new Player(state.world.tile(otherCity.x, otherCity.y), Color.GOLD, state.grabCards()));

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());
    }
}
