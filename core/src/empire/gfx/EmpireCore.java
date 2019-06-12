package empire.gfx;

import empire.ai.*;
import empire.game.GameEvents.WinEvent;
import empire.game.*;
import empire.game.World.City;
import empire.io.*;
import empire.net.Net;
import empire.net.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.Array;
import io.anuke.arc.function.BiFunction;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.*;
import io.anuke.arc.util.Timer.Task;

/** Main class for graphical renderer. Initializes state and its renderers.*/
public class EmpireCore extends ApplicationCore{
    /** Size of each hex tile in pixels. */
    public static final int tilesize = 16;
    public static final int totalAI = 1;
    public static final int testTurns = 150;
    public static final boolean debug = true, isAI = true, netDebug = false,
                                seeded = true, testEfficiency = true;
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
                if(seeded){
                    Mathf.random.setSeed(1);
                }
                City startCity = state.world.getCity("bern");
                Log.info("Picked start city {0}", startCity.name);
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
                boolean[] hasWon = {false};

                Events.on(WinEvent.class, e -> {
                    hasWon[0] = true;
                });

                Core.app.post(() -> {
                    int turns = 0;
                    for(int i = 0; i < testTurns; i++){
                        state.player().ai.act();
                        turns ++;
                        if(hasWon[0]){
                            break;
                        }
                    }
                    if(hasWon[0]){
                        Log.info("Won in {0} turns.", turns);
                    }else{
                        Log.info("Final profit in {0} turns: {1}", testTurns, state.player().money);
                    }
                    Core.app.post(() -> {
                        FrameBuffer buffer = new FrameBuffer(state.world.width * tilesize, state.world.height * tilesize);

                        buffer.begin();
                        Core.graphics.clear(Color.BLACK);
                        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
                        renderer.drawBuffered();
                        Draw.flush();
                        ScreenUtils.saveScreenshot(Core.files.local("screenshot_" + state.player().ai.getClass().getSimpleName() + ".png"),
                                0, 0, buffer.getWidth(), buffer.getHeight());
                        buffer.end();

                        Core.app.exit();
                    });
                });
            }else{
                boolean[] cancelled = {false};
                Timer.schedule(new Task(){
                    @Override
                    public void run(){
                        if(state.player().ai != null){
                            if(Core.input.keyDown(KeyCode.SPACE)){
                                cancelled[0] = true;
                            }

                            if(!cancelled[0]){
                                state.player().ai.act();
                            }

                        }
                    }
                }, 2f, 1f);
            }
        }
    }
}
