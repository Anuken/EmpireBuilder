package empire.gfx;

import empire.game.State;
import empire.io.MapIO;
import io.anuke.arc.ApplicationCore;
import io.anuke.arc.Core;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 10;

    public static UI ui;
    public static Renderer renderer;
    public static State state;

    @Override
    public void setup(){
        //create state and modules for viewing/controlling that state
        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));

        add(renderer = new Renderer());
        add(ui = new UI());
    }
}
