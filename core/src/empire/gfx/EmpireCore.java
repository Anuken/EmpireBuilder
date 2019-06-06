package empire.gfx;

import empire.ai.AI;
import empire.ai.PlannedAI;
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
import io.anuke.arc.function.BiFunction;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.Timer;
import io.anuke.arc.util.Timer.Task;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 16;
    public static final int totalAI = 1;
    public static final int testTurns = 100;
    public static final boolean debug = true, isAI = true, netDebug = false,
                                seeded = true, testEfficiency = false;
    public static final BiFunction<Player, State, AI> aiType = PlannedAI::new;

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

        //set up shuffle seed if applicable
        if(seeded){
            Mathf.random.setSeed(0);
        }
        state.cards.shuffle(); //shuffle cards when inputted

        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());

        if(isAI){
            for(int i = 0; i < totalAI; i++){
                City startCity = Array.with(state.world.cities()).random();
                Player player;
                state.players.add(player = new Player("AI " + (i + 1),
                        state.world.tile(startCity.x, startCity.y), new Color().randHue(), state.grabCards()));
                player.local = true;
                player.ai = aiType.get(player, state);
                if(seeded){
                    player.chosenLocation = true;
                }
            }
        }else{
            City startCity = Array.with(state.world.cities()).random();
            state.players.add(new Player("Loading...", state.world.tile(startCity.x, startCity.y), Color.PINK, state.grabCards()));
            state.players.peek().local = true;
        }

        if(isAI){
            if(testEfficiency){
                Core.app.post(() -> {
                    for(int i = 0; i < testTurns; i++){
                        state.player().ai.act();
                    }
                    Log.info("Final profit in {0} turns: {1}", testTurns, state.player().money);
                    Core.app.exit();
                });
            }else{
                Timer.schedule(new Task(){
                    @Override
                    public void run(){
                        if(state.player().ai != null){
                            if(!Core.input.keyDown(KeyCode.SPACE)) state.player().ai.act();
                        }
                    }
                }, 2f, 1f);
            }
        }
    }
}
