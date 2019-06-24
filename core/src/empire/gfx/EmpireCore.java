package empire.gfx;

import empire.ai.*;
import empire.game.*;
import empire.game.World.City;
import empire.io.*;
import empire.net.Net;
import empire.net.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.Array;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.function.BiFunction;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.math.Mathf;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 16;
    public static boolean debug = false, isAI = false, netDebug = false,
                                seeded, snapshots, snapshotView;
    public static final BiFunction<Player, State, AI> aiType = NextAI::new;
    public static FileHandle snapshotDirectory;

    public static Control control;
    public static UI ui;
    public static Renderer renderer;
    public static State state;
    public static Net net;
    public static ActionRelay actions;
    public static AIScheduler scheduler;

    @Override
    public void setup(){
        debug = true;
        snapshotView = true;
        seeded = true;

        net = new WebsocketNet();
        actions = new ActionRelay();

        net.setListener(actions);

        createState();

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());

        createPlayer();
    }

    void createPlayer(){
        if(isAI){
            if(seeded){
                Mathf.random.setSeed(1);
            }
            City startCity = state.world.getCity("bern");
            Player player;
            state.players.add(player = new Player("AI 1", state.world.tile(startCity.x, startCity.y),
                    new Color().randHue(), state.grabCards()));
            player.local = true;
            player.ai = aiType.get(player, state);

            //add a turn scheduler for this AI
            add(scheduler = new AIScheduler(player.ai));
        }else{
            City startCity = Array.with(state.world.cities()).random();
            state.players.add(new Player("Loading...", state.world.tile(startCity.x, startCity.y), Color.PINK, state.grabCards()));
            state.players.peek().local = true;

            //create dummy AI scheduler that does nothing
            scheduler = new AIScheduler();
        }
    }

    void createState(){
        snapshotDirectory = Core.files.local("snapshots_" +
                new SimpleDateFormat("yyyy_MM_dd-HH|mm|ss").format(new Date()));

        state = new State();
        state.world = MapIO.loadTiles(Core.files.internal("maps/eurorails.txt"));
        state.cards = CardIO.loadCards(state.world, Core.files.internal("maps/deck.txt"));

        //set up shuffle seed if applicable
        if(seeded){
            Mathf.random.setSeed(0);
        }
        state.cards.shuffle(); //shuffle cards when inputted
    }
}
