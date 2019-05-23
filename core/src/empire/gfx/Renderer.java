package empire.gfx;

import empire.game.Player;
import empire.game.World.City;
import empire.game.World.CitySize;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import empire.gfx.gen.MapImageGenerator;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Camera;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.util.Structs;
import io.anuke.arc.util.Time;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.*;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{
    private boolean doLerp = true;
    private float zoom = 1f;
    private Texture mapTexture;

    public Renderer(){
        Core.batch = new SpriteBatch();
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
        Core.atlas = new TextureAtlas("ui/uiskin.atlas");

        mapTexture = MapImageGenerator.generateTerrain(state.world);
    }

    @Override
    public void init(){
        Core.scene.table(t -> t.touchable(Touchable.enabled)).dragged((x, y) -> {
            Core.camera.position.sub(x / zoom, y / zoom);
        });
    }

    public Tile tileMouse(){
        return tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
    }

    /** Returns the tile at world coordinates, or null if this is out of bounds.*/
    public Tile tileWorld(float x, float y){
        x += tilesize/2f;
        y += tilesize/2f;
        int tx = (int)(x/tilesize);
        int ty = (int)(y/tilesize);
        if(ty % 2 == 1){ //translate to match hex coords
            tx = (int)((x - tilesize/2f)/tilesize);
        }

        //return null if it is out of bounds
        if(!Structs.inBounds(tx, ty, state.world.width, state.world.height)){
            return null;
        }

        return state.world.tile(tx, ty);
    }

    /** Converts map coordinates to world coordinates.*/
    Vector2 toWorld(int x, int y){
        return Tmp.v1.set(x * tilesize + (y%2)*tilesize/2f, y*tilesize);
    }

    /** {@link #toWorld(int, int)} for tiles.*/
    Vector2 toWorld(Tile tile){
        return toWorld(tile.x, tile.y);
    }

    @Override
    public void update(){
        Core.graphics.clear(Color.ROYAL);

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
            Vector2 v = toWorld(state.currentPlayer().position);
            Core.camera.position.lerpDelta(v, 0.1f);
        }

        //update camera info
        Core.camera.resize(Core.graphics.getWidth() / zoom, Core.graphics.getHeight() / zoom);
        Draw.proj(Core.camera.projection());

        drawWorld();
        drawPlayers();
        drawRails();
        drawControl();

        Draw.flush();
    }

    /** Draws player input on the boad.*/
    void drawControl(){
        //draw selected tile for debugging purposes
        if(control.placeLoc != null){
            Vector2 world = toWorld(control.placeLoc.x, control.placeLoc.y);

            Lines.stroke(4f, Color.PURPLE);
            Lines.square(world.x, world.y, tilesize/2f);

            Tile other = tileMouse();
            if(state.canPlaceTrack(state.currentPlayer(), control.placeLoc, other)){
                Draw.color(Color.YELLOW);
                toWorld(other.x, other.y);
                Lines.square(world.x, world.y, tilesize/2f);
            }
        }
    }

    /** Draws all player icons on the board.*/
    void drawPlayers(){
        Draw.color(Color.WHITE);
        for(Player player : state.players){
            Vector2 world = toWorld(player.position.x, player.position.y);
            Draw.color(player.color);
            Draw.rect("icon-trash", world.x, world.y, tilesize, tilesize);
        }
    }

    /** Draws all player rails by color.*/
    void drawRails(){
        for(Player player : state.players){
            Lines.stroke(4f, player.color);
            player.eachTrack((from, to) -> {
                Vector2 vec = toWorld(from.x, from.y);
                float fx = vec.x, fy = vec.y;
                toWorld(to.x, to.y);
                Lines.line(fx, fy, vec.x, vec.y);
            });
        }
    }

    /** Draws the tiles of the world.*/
    void drawWorld(){
        //draw background map image
        Draw.color();
        float mw = state.world.width * tilesize, mh = state.world.height * tilesize;
        Draw.rect(Draw.wrap(mapTexture), mw/2, mh/2 - tilesize/2f, mw, mh);

        //draw tiles
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Vector2 world = toWorld(x, y);
                float tx = world.x, ty = world.y;

                if(tile.river){
                    Draw.color(Color.ROYAL);
                    Lines.stroke(8f);
                    Lines.spikes(tx, ty, 1, 14, 4, 45);
                }
                Draw.color(Color.BLACK);
                if(tile.type == Terrain.plain){
                    Fill.square(tx, ty, 4);
                }else if(tile.type == Terrain.mountain){
                    Fill.poly(tx, ty, 3, 9, 90);
                }else if(tile.type == Terrain.alpine){
                    Lines.stroke(2f);
                    Lines.poly(tx, ty, 3, 9, 0);
                }

                if(tile.port != null && tile.port.from == tile){
                    Vector2 to = toWorld(tile.port.to.x, tile.port.to.y);
                    Lines.stroke(3f, Color.NAVY);
                    Lines.line(tx, ty, to.x, to.y);
                }
            }
        }

        //draw cities
        for(City city : state.world.cities()){
            Vector2 world = toWorld(city.x, city.y);
            float tx = world.x, ty = world.y;

            Lines.stroke(4f, Color.CORAL);
            if(city.size == CitySize.small){
                Lines.circle(tx, ty, tilesize/2f);
            }else if(city.size == CitySize.medium){
                Lines.square(tx, ty, tilesize/2f);
            }else{
                Lines.poly(tx, ty, 6, tilesize);
            }
        }
    }


}
