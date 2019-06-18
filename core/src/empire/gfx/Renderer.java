package empire.gfx;

import empire.game.*;
import empire.game.DemandCard.Demand;
import empire.game.EventCard.*;
import empire.game.GameEvents.EndTurnEvent;
import empire.game.World.*;
import empire.gfx.gen.MapImageRenderer;
import io.anuke.arc.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.*;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.*;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{
    private float zoom = 4f;
    private Color clearColor = Color.valueOf("5d81e1");
    private Texture worldTexture;
    private FrameBuffer buffer;

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
    }

    @Override
    public void init(){
        worldTexture = MapImageRenderer.createMapTexture(state.world);
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

        //Core.camera.width = pw;
        //Core.camera.height = ph;
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
        drawPlayers();
        drawControl();
        drawOver();
    }

    void doMovement(){
        //update zoom based on input
        zoom += Core.input.axis(KeyCode.SCROLL)* 0.03f;
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

        Vector2 v = control.toWorld(state.player().position);
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
            Vector2 v = control.toWorld(player.position);

            font.setColor(player.color);
            font.draw(player.name, v.x, v.y + tilesize, Align.center);
        }
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
    }

    /** Draws player input on the board.*/
    void drawControl(){
        if(!Core.scene.hasMouse()){
            Tile other = control.tileMouse();
            if(other != null){
                Draw.color(1f, 1f, 1f, 0.2f);
                Vector2 world = control.toWorld(other);
                Fill.rect(world.x, world.y, tilesize, tilesize);
            }

            //draw rail track placement
            if(control.placeLoc != null && other != null){
                Tile last = control.placeLoc;
                int cost = 0;
                for(Tile tile : control.getTiles(control.placeLoc, other)){
                    if(tile != last){

                        Vector2 world = control.toWorld(last);
                        float fx = world.x, fy = world.y;
                        control.toWorld(tile);

                        if(state.isPassable(state.player(), tile) && state.canSpendTrack(state.player(), cost)
                        && !(state.world.getMajorCity(tile) == state.world.getMajorCity(last)
                                && state.world.getMajorCity(tile) != null)){
                            cost += state.getTrackCost(last, tile);
                            Draw.color(1f, 1f, 1f, 0.5f);
                        }else{
                            Draw.color(1f, 0.3f, 0.3f, 0.5f);
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
            if(!player.chosenLocation) continue;
            Vector2 world = control.toWorld(player.position.x, player.position.y);

            Draw.colorMul(player.color, 0.5f);
            Draw.rect("icon-arrow-right", world.x, world.y - 1, player.direction.angle());
            Draw.colorMul(player.color, 1.1f);
            Draw.rect("icon-arrow-right", world.x, world.y, player.direction.angle());
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

    void drawTrack(float fromX, float fromY, float toX, float toY){
        Lines.line(Core.atlas.find("track"), fromX, fromY, toX, toY, CapStyle.none, 0f);
    }

    /** Draws the cities of the world.*/
    void drawWorld(){
        Draw.color();
        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(worldTexture), rwidth/2f, rheight/2f, rwidth, -rheight);

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
