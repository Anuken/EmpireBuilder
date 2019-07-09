package empire.gfx;

import empire.ai.NextAI;
import empire.game.Actions.*;
import empire.game.*;
import empire.game.DemandCard.Demand;
import empire.game.EventCard.*;
import empire.game.GameEvents.EndTurnEvent;
import empire.game.World.*;
import empire.gfx.fx.ActionFx;
import empire.gfx.fx.ActionFx.*;
import empire.gfx.gen.MapImageRenderer;
import empire.gfx.shaders.Bloom;
import io.anuke.arc.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.*;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.*;

/** Renders the game world and input on the board.*/
public class Renderer implements ApplicationListener{
    private float zoom = 4f;
    private Color clearColor = Color.valueOf("5d81e1");
    private Texture worldTexture;
    private FrameBuffer buffer;
    private Bloom bloom;
    private AIVisualizer visualizer = new AIVisualizer();
    private Queue<ActionFx> fx = new Queue<>();

    public boolean doLerp = true;

    public Renderer(){
        Lines.setCircleVertices(30);

        Core.batch = new SpriteBatch();
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
        Core.atlas = new TextureAtlas("ui/uiskin.atlas");

        buffer = new FrameBuffer(state.world.width * 16, state.world.height * 16);
        buffer.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

        Events.on(EndTurnEvent.class, event -> {
            doLerp = true;
        });

        makeBloom();
        registerEffects();
    }

    private void registerEffects(){
        Events.on(Move.class, f -> fx.addLast(new MoveFx(f.player, f.player.position, f.to)));
        Events.on(PlaceTrack.class, f -> fx.addLast(new TrackFx(f.player, f.player.position, f.to)));
    }

    private void makeBloom(){
        if(bloom != null){
            bloom.dispose();
        }
        bloom = new Bloom(Core.graphics.getWidth() / 4, Core.graphics.getHeight() / 4,
                false, true, true);
        bloom.setClearColor(0f, 0f, 0f, 0f);
    }

    @Override
    public void init(){
        worldTexture = MapImageRenderer.createMapTexture(state.world);

        if(state.player().ai != null){
            ((NextAI)state.player().ai).setListener(visualizer);
        }
    }

    @Override
    public void update(){
        Core.graphics.clear(clearColor);

        if(net.active() && !ui.chat.chatOpen()){
            doMovement();
        }

        //update camera info
        Core.camera.resize(Core.graphics.getWidth() / zoom, Core.graphics.getHeight() / zoom);
        Draw.flush();

        buffer.begin();
        Core.graphics.clear(clearColor);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        drawBuffered();
        Draw.flush();
        buffer.end();

        //TODO round camera size for pixel perfect display
        /*
        float pzoom = (zoom > 1 ? (int)zoom : Mathf.round(zoom, 0.25f));

        float pw = Core.camera.width, ph = Core.camera.height;
        Core.camera.width = Core.graphics.getWidth() / pzoom;
        Core.camera.height = Core.graphics.getHeight() / pzoom;
        Core.camera.update();*/

        Draw.color();
        Draw.proj(Core.camera.projection());
        Draw.blend(Blending.disabled);

        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(buffer.getTexture()), rwidth/2f, rheight/2f, rwidth, -rheight);
        Draw.blend();

        ScreenRecorder.record();

        Draw.flush();
        bloom.capture();
        visualizer.draw();
        Draw.flush();
        bloom.render();

        //Core.camera.width = pw;
        //Core.camera.height = ph;
    }

    @Override
    public void resize(int width, int height){
        makeBloom();
    }

    public void takeWorldScreenshot(){
        FrameBuffer buffer = new FrameBuffer(state.world.width * tilesize, state.world.height * tilesize);

        buffer.begin();
        Core.graphics.clear(Color.BLACK);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        renderer.drawBuffered();
        Draw.flush();
        ScreenUtils.saveScreenshot(Core.files.local("screenshot_" + state.player().ai.getClass().getSimpleName() + ".png"),
                0, 0, buffer.getWidth(), buffer.getHeight());
        buffer.end();
    }

    /** Draws everything in the world that goes in the pixel buffer.*/
    void drawBuffered(){
        drawWorld();
        drawRails();
        drawCities();
        drawPlayers();
        drawControl();
        drawOver();
    }

    void doMovement(){
        //update zoom based on input
        zoom += Core.input.axis(KeyCode.SCROLL) * 0.03f;
        zoom = Mathf.clamp(zoom, 0.2f, 20f);

        float speed = 15f * Time.delta();

        Vector2 movement = Tmp.v4.setZero();
        if(Core.input.keyDown(KeyCode.W) || Core.input.keyDown(KeyCode.UP)) movement.y += speed;
        if(Core.input.keyDown(KeyCode.A) || Core.input.keyDown(KeyCode.LEFT)) movement.x -= speed;
        if(Core.input.keyDown(KeyCode.S) || Core.input.keyDown(KeyCode.DOWN)) movement.y -= speed;
        if(Core.input.keyDown(KeyCode.D) || Core.input.keyDown(KeyCode.RIGHT)) movement.x += speed;

        if(!movement.isZero()){
            Core.camera.position.add(movement.limit(speed));
            doLerp = false;
        }

        Vector2 v = state.player().visualpos;
        if(doLerp && state.player().chosenLocation && !scheduler.isThinking()){
            Core.camera.position.lerpDelta(v, 0.06f);
        }
    }

    /** Draw player names and such.*/
    void drawOver(){
        BitmapFont font = Core.scene.skin.getFont("default");
        font.getData().setScale(1f);
        for(Player player : state.players){
            if(!player.chosenLocation) continue;
            Vector2 v = player.visualpos;

            font.setColor(player.color);
            font.draw(player.name, v.x, v.y + tilesize, Align.center);
        }
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
    }

    /** Draws player input on the board.*/
    void drawControl(){
        //draw stuff in queue
        int queueUsed = 0;

        for(Tile[] queue : control.getQueued()){
            Vector2 world = control.toWorld(queue[0]);
            float fx = world.x, fy = world.y;
            control.toWorld(queue[1]);

            queueUsed += state.getTrackCost(queue[0], queue[1]);

            Draw.color(state.canSpendTrack(state.player(), queueUsed) ?
                    state.player().color : Color.SCARLET, 0.43f);
            drawTrack(fx, fy, world.x, world.y);
        }

        if(!Core.scene.hasMouse()){
            Tile other = control.tileMouse();
            if(other != null){
                Draw.color(1f, 1f, 1f, 0.2f);
                Vector2 world = control.toWorld(other);
                Fill.rect(world.x, world.y, tilesize, tilesize);
            }

            //draw stuff selected
            if(control.placeLoc != null && other != null){
                Tile last = control.placeLoc;
                for(Tile tile : control.selectedTiles()){
                    if(tile != last
                            && !state.world.samePort(tile, last)
                            && !state.world.sameCity(tile, last)){

                        Vector2 world = control.toWorld(last);
                        float fx = world.x, fy = world.y;
                        control.toWorld(tile);

                        if(state.isPassable(state.player(), tile)){
                            Draw.color(1f, 1f, 1f, 0.5f);
                        }else{
                            Draw.color(1f, 1f, 1f, 0.5f);
                        }

                        drawTrack(fx, fy, world.x, world.y);
                    }
                    last = tile;
                }
            }
        }

        for(Player player : state.players){
            for(EventCard card : player.eventCards){
                if(card instanceof HeavySnowEvent){
                    int dst = ((HeavySnowEvent) card).dst;
                    City city = ((HeavySnowEvent) card).city;

                    Vector2 world = control.toWorld(city.x, city.y);

                    Lines.stroke(2f, Color.WHITE);
                    Lines.dashCircle(world.x, world.y, dst * tilesize);
                }else if(card instanceof FogEvent){
                    int dst = ((FogEvent) card).dst;
                    City city = ((FogEvent) card).city;

                    Vector2 world = control.toWorld(city.x, city.y);

                    Lines.stroke(2f, Color.LIGHT_GRAY);
                    Lines.dashCircle(world.x, world.y, dst * tilesize);
                }
            }
        }
    }

    /** Draws all player icons on the board.*/
    void drawPlayers(){
        for(Player player : state.players){
            player.visualrot = Mathf.slerp(player.visualrot, player.direction.angle(), 0.2f * Time.delta());

            if(!player.chosenLocation) continue;
            Vector2 world = player.visualpos;

            Draw.colorMul(player.color, 0.5f);
            Draw.rect("icon-arrow-right", world.x, world.y - 1, player.visualrot);
            Draw.colorMul(player.color, 1.1f);
            Draw.rect("icon-arrow-right", world.x, world.y, player.visualrot);
        }

        if(!fx.isEmpty()){
            ActionFx f = fx.first();
            f.update();
            if(f.time >= 1f){
                fx.removeFirst();
            }
        }
    }

    /** Draws all player rails by color.*/
    void drawRails(){
        TextureRegion track = Core.atlas.find("track");
        for(Player player : state.players){
            Lines.stroke(track.getHeight() * tilesize/16f);
            for(int i = 0; i < 2; i++){
                int fi = i;
                if(i == 0){
                    Draw.colorMul(player.color, 0.3f);
                }else{
                    Draw.colorMul(player.color, 0.8f);
                }
                player.eachTrack((from, to) -> {
                    if(state.world.index(from) < state.world.index(to)){
                        return;
                    }
                    Vector2 vec = control.toWorld(from.x, from.y);
                    float fx = vec.x, fy = vec.y;
                    control.toWorld(to.x, to.y);

                    if(fi == 0){
                        drawTrack(fx, fy - 1, vec.x, vec.y - 1);
                    }else{
                        drawTrack(fx, fy, vec.x, vec.y);
                    }
                });
            }
        }
    }

    public void drawTrack(float fromX, float fromY, float toX, float toY){
        Lines.stroke(Core.atlas.find("track").getHeight() * tilesize/16f);
        Lines.line(Core.atlas.find("track"), fromX, fromY, toX, toY, CapStyle.none, 0f);
    }

    void drawWorld(){
        Draw.color();
        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(worldTexture), rwidth/2f, rheight/2f, rwidth, -rheight);
    }

    void drawCities(){
        Player player = state.localPlayer();

        if(player == null) throw new IllegalArgumentException("Local player can't be null.");

        //draw cities
        for(City city : state.world.cities()){
            Vector2 world = control.toWorld(city.x, city.y);
            float tx = world.x, ty = world.y;
            TextureRegion region = Core.atlas.find("city-" + city.size.name());

            Draw.color();
            Draw.rect(region, tx, ty, region.getWidth() * tilesize/16f, region.getHeight() * tilesize/16f);

            if(player.hasGoodDelivery(city)){
                icon("icon-export", tx, ty, 10f, 10f);
            }

            if(player.hasGoodDemand(city)){
                icon("icon-open", tx, ty, -10f, 10f);
            }
        }

        Tile selected = control.tileMouse();

        if(selected != null && state.world.getCity(selected) != null){
            City city = state.world.getCity(selected);
            drawCitySelect(city);

            Lines.stroke(2f, Color.WHITE);
            //draw good delivery line based on cities who can supply this good
            if(player.hasGoodDelivery(city)
            ){
                for(DemandCard card : player.demandCards){
                    for(Demand demand : card.demands){
                        if(demand.city == city){
                            String good = demand.good;
                            for(City provider : state.world.cities()){
                                if(provider.goods.contains(good)){
                                    drawCitySelect(provider);
                                    Vector2 world = control.toWorld(city.x, city.y);
                                    float sx = world.x, sy = world.y;
                                    control.toWorld(provider.x, provider.y);
                                    float ex = world.x, ey = world.y;

                                    int divisions = (int)(Mathf.dst(sx, sy, ex, ey) / 5);
                                    float angle = Angles.angle(sx, sy, ex, ey);
                                    Vector2 trns = Tmp.v2.trns(angle, 16f);
                                    sx += trns.x;
                                    sy += trns.y;
                                    ex -= trns.x;
                                    ey -= trns.y;

                                    //dash line + arrow here
                                    Draw.colorMul(Color.CORAL, 0.6f);
                                    Lines.dashLine(sx, sy - 1, ex, ey - 1, divisions);
                                    Draw.rect("icon-open", sx, sy - 1, angle - 90 + 180);
                                    Draw.color(Color.CORAL);
                                    Lines.dashLine(sx, sy, ex, ey, divisions);
                                    Draw.rect("icon-open", sx, sy, angle - 90 + 180);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Draws the select box for a city.*/
    private void drawCitySelect(City city){
        Vector2 world = control.toWorld(city.x, city.y);
        Draw.colorMul(Color.CORAL, 0.6f);
        Draw.rect("city-" + city.size.name() + "-select", world.x, world.y);
        Draw.color(Color.CORAL);
        Draw.rect("city-" + city.size.name() + "-select", world.x, world.y + 1);
    }

    /** Draws a white icon with a gray shadow.*/
    private void icon(String name, float x, float y, float offsetx, float offsety){
        float scale = tilesize / 16f;
        float size = scale * Core.atlas.find(name).getWidth();
        Draw.color(Color.DARK_GRAY);
        Draw.rect(name, x + offsetx*scale, y + offsety*scale - scale, size, size);
        Draw.color();
        Draw.rect(name, x + offsetx*scale, y + offsety*scale, size, size);
    }
}
