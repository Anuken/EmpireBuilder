package empire.gfx;

import empire.game.Player;
import empire.game.State;
import empire.game.World.City;
import empire.io.MapIO;
import io.anuke.arc.ApplicationCore;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 60;

    public static Control control;
    public static UI ui;
    public static Renderer renderer;
    public static State state;

    @Override
    public void setup(){
        //create state and modules for viewing/controlling that state
        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));

        City startCity = state.world.cities().first();

        Player player = new Player(state.world.tile(startCity.x, startCity.y), Color.RED);
        state.players.add(player);

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());
    }
}
