package empire.gfx;

import empire.game.Player;
import empire.game.World.City;
import empire.game.World.CitySize;
import empire.game.World.Tile;
import empire.gfx.gen.WaterRenderer;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Camera;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Time;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.*;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{
    private boolean doLerp = true;
    private float zoom = 1f;
    private Color clearColor = Color.valueOf("5d81e1");
    private Texture riverTexture;

    public Renderer(){
        Core.batch = new SpriteBatch();
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
        Core.atlas = new TextureAtlas("ui/uiskin.atlas");

        riverTexture = WaterRenderer.createWaterTexture(state.world);
    }

    @Override
    public void update(){
        Core.graphics.clear(clearColor);

        //update zoom based on input
        zoom += Core.input.axis(KeyCode.SCROLL)* 0.03f;
        zoom = Mathf.clamp(zoom, 0.2f, 20f);

        float speed = 30f * Time.delta();

        Vector2 movement = Tmp.v1.setZero();
        if(Core.input.keyDown(KeyCode.W)) movement.y += speed;
        if(Core.input.keyDown(KeyCode.A)) movement.x -= speed;
        if(Core.input.keyDown(KeyCode.S)) movement.y -= speed;
        if(Core.input.keyDown(KeyCode.D)) movement.x += speed;

        if(!movement.isZero()){
            doLerp = false;
            Core.camera.position.add(movement.limit(speed));
        }

        if(doLerp){
            Vector2 v = control.toWorld(state.player().position);
            Core.camera.position.lerpDelta(v, 0.09f);
        }

        //update camera info
        Core.camera.resize(Core.graphics.getWidth() / zoom, Core.graphics.getHeight() / zoom);
        Draw.proj(Core.camera.projection());

        drawWorld();
        drawRails();
        drawPlayers();
        drawControl();

        Draw.flush();
    }

    /** Draws player input on the board.*/
    void drawControl(){
        //draw selected tile for debugging purposes
        if(control.placeLoc != null){
            Vector2 world = control.toWorld(control.placeLoc.x, control.placeLoc.y);

            Lines.stroke(4f, Color.PURPLE);
            Lines.square(world.x, world.y, tilesize/2f);

            Tile other = control.tileMouse();
            if(other != null && state.canPlaceTrack(state.player(), control.placeLoc, other)){
                Draw.color(Color.YELLOW);
                control.toWorld(other.x, other.y);
                Lines.square(world.x, world.y, tilesize/2f);
            }
        }
    }

    /** Draws all player icons on the board.*/
    void drawPlayers(){
        Draw.color(Color.WHITE);
        for(Player player : state.players){
            Vector2 world = control.toWorld(player.position.x, player.position.y);
            Draw.color(player.color);
            Draw.rect("icon-trash", world.x, world.y, tilesize, tilesize);
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
                    Draw.color(0f, 0f, 0f, 0.5f);
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
                        drawTrack(fx, fy - 3, vec.x, vec.y - 3);
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

        //TODO cache ports for better performance
        //draw ports
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Vector2 world = control.toWorld(x, y);
                float tx = world.x, ty = world.y;
                if(tile.port != null && tile.port.from == tile){
                    Vector2 to = control.toWorld(tile.port.to.x, tile.port.to.y);
                    Lines.stroke(3f, Color.NAVY);
                    Lines.line(tx, ty, to.x, to.y);
                }
            }
        }

        Draw.color();
        float rwidth = state.world.width * tilesize, rheight = state.world.height * tilesize;
        Draw.rect(Draw.wrap(riverTexture), rwidth/2f, rheight/2f, rwidth, -rheight);

        //draw cities
        for(City city : state.world.cities()){
            Vector2 world = control.toWorld(city.x, city.y);
            float tx = world.x, ty = world.y;

            Lines.stroke(4f, Color.CORAL);
            if(city.size == CitySize.small){
                Lines.circle(tx, ty, tilesize/2f);
            }else if(city.size == CitySize.medium){
                Lines.square(tx, ty, tilesize/2f);
            }else{
                Lines.poly(tx, ty, 6, tilesize);
            }

            if(state.player().hasGoodDelivery(city)){
                icon("icon-export", tx, ty, 10f, 10f);
            }

            if(state.player().hasGoodDemand(city)){
                icon("icon-open", tx, ty, -10f, 10f);
            }
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
