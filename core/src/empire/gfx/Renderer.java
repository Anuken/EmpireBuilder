package empire.gfx;

import empire.game.EventCard;
import empire.game.EventCard.FogEvent;
import empire.game.EventCard.HeavySnowEvent;
import empire.game.Player;
import empire.game.World.City;
import empire.game.World.Tile;
import empire.gfx.gen.WaterRenderer;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Time;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.*;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{
    private boolean doLerp = true;
    private float zoom = 4f;
    private Color clearColor = Color.valueOf("5d81e1");
    private Texture riverTexture;
    private FrameBuffer buffer;

    public Renderer(){
        Lines.setCircleVertices(30);

        Core.batch = new SpriteBatch();
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
        Core.atlas = new TextureAtlas("ui/uiskin.atlas");

        riverTexture = WaterRenderer.createWaterTexture(state.world);
        buffer = new FrameBuffer(state.world.width * 16, state.world.height * 16);
        buffer.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
    }

    @Override
    public void update(){
        Core.graphics.clear(clearColor);

        //update zoom based on input
        zoom += Core.input.axis(KeyCode.SCROLL)* 0.03f;
        zoom = Mathf.clamp(zoom, 0.2f, 20f);

        float speed = 15f * Time.delta();

        Vector2 movement = Tmp.v1.setZero();
        if(Core.input.keyDown(KeyCode.W)) movement.y += speed;
        if(Core.input.keyDown(KeyCode.A)) movement.x -= speed;
        if(Core.input.keyDown(KeyCode.S)) movement.y -= speed;
        if(Core.input.keyDown(KeyCode.D)) movement.x += speed;

        if(!movement.isZero()){
            //doLerp = false;
            Core.camera.position.add(movement.limit(speed));
        }

        if(doLerp){
            Vector2 v = control.toWorld(state.player().position);
            Core.camera.position.lerpDelta(v, 0.09f);
        }

        //update camera info
        Core.camera.resize(Core.graphics.getWidth() / zoom, Core.graphics.getHeight() / zoom);
        Draw.flush();

        buffer.begin();

        Core.graphics.clear(clearColor);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());

        drawWorld();
        drawRails();
        drawPlayers();
        drawControl();

        Draw.flush();
        buffer.end();

        Draw.color();
        Draw.proj(Core.camera.projection());
        Draw.blend(Blending.disabled);

        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(buffer.getTexture()), rwidth/2f, rheight/2f, rwidth, -rheight);

        Draw.blend();
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

                        cost += state.getTrackCost(last, tile);

                        if(state.isPassable(state.player(), tile) && state.canSpendRail(state.player(), cost)){
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

        for(EventCard card : state.player().eventCards){
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

    /** Draws all player icons on the board.*/
    void drawPlayers(){
        for(Player player : state.players){
            Vector2 world = control.toWorld(player.position.x, player.position.y);

            Draw.colorMul(player.color, 0.5f);
            Draw.rect("icon-arrow-right", world.x, world.y - 1, player.direction.angle());
            Draw.color(player.color);
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
                    Draw.colorMul(player.color, 0.4f);
                }else{
                    Draw.color(player.color);
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

    /** Draws the tiles of the world.*/
    void drawWorld(){
        Draw.color();

        //draw tiles
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Vector2 world = control.toWorld(x, y);
                float tx = world.x, ty = world.y;

                Draw.color();
                Draw.rect(Core.atlas.find("terrain-" + tile.type.name(), Core.atlas.find("terrain-plain")), tx, ty, tilesize, tilesize);
            }
        }

        Draw.color();
        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(riverTexture), rwidth/2f, rheight/2f, rwidth, -rheight);

        //draw cities
        for(City city : state.world.cities()){
            Vector2 world = control.toWorld(city.x, city.y);
            float tx = world.x, ty = world.y;
            TextureRegion region = Core.atlas.find("city-" + city.size.name());


            Draw.color();
            Draw.rect(region, tx, ty, region.getWidth() * tilesize/16f, region.getHeight() * tilesize/16f);

            if(state.player().hasGoodDelivery(city)){
                icon("icon-export", tx, ty, 10f, 10f);
            }

            if(state.player().hasGoodDemand(city)){
                icon("icon-open", tx, ty, -10f, 10f);
            }
        }

        Tile selected = control.tileMouse();

        if(selected != null && state.world.getCity(selected) != null){
            City city = state.world.getCity(selected);
            Vector2 world = control.toWorld(city.x, city.y);
            Draw.colorMul(Color.CORAL, 0.6f);
            Draw.rect("city-" + city.size.name() + "-select", world.x, world.y);
            Draw.color(Color.CORAL);
            Draw.rect("city-" + city.size.name() + "-select", world.x, world.y + 1);
        }
    }

    private void icon(String name, float x, float y, float offsetx, float offsety){
        float scale = tilesize / 16f;
        float size = scale * Core.atlas.find(name).getWidth();
        Draw.color(Color.DARK_GRAY);
        Draw.rect(name, x + offsetx*scale, y + offsety*scale - scale, size, size);
        Draw.color();
        Draw.rect(name, x + offsetx*scale, y + offsety*scale, size, size);
    }
}
