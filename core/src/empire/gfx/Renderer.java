package empire.gfx;

import empire.game.Player;
import empire.game.World.*;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Camera;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Fill;
import io.anuke.arc.graphics.g2d.Lines;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.util.Structs;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.state;
import static empire.gfx.EmpireCore.tilesize;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{
    private float zoom = 1f;

    public Renderer(){
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
    }

    @Override
    public void init(){
        Core.scene.table(t -> t.touchable(Touchable.enabled)).dragged((x, y) -> {
            Core.camera.position.sub(x / zoom, y / zoom);
        });
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

    @Override
    public void update(){
        Core.graphics.clear(Color.BLACK);

        //update zoom based on input
        zoom += Core.input.axis(KeyCode.SCROLL)* 0.03f;
        zoom = Mathf.clamp(zoom, 0.2f, 20f);

        //update camera info
        Core.camera.resize(Core.graphics.getWidth() / zoom, Core.graphics.getHeight() / zoom);
        Draw.proj(Core.camera.projection());

        drawWorld();
        drawPlayers();
        drawRails();

        Tile on = tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);
        //draw selected tile for debugging purposes
        if(on != null){
            Vector2 world = toWorld(on.x, on.y);

            Lines.stroke(4f);
            Draw.color(Color.PURPLE);
            Lines.square(world.x, world.y, tilesize/2f);
        }

        Draw.flush();
    }

    /** Draws all player icons on the board.*/
    void drawPlayers(){
        Draw.color(Color.WHITE);
        for(Player player : state.players){
            Vector2 world = toWorld(player.position.x, player.position.y);
            Draw.rect("icon-home", world.x, world.y, tilesize, tilesize);
        }
    }

    /** Draws all player rails by color.*/
    void drawRails(){
        for(Player player : state.players){
            Lines.stroke(2f, player.color);
            for(Track track : player.tracks){
                Vector2 vec = toWorld(track.from.x, track.from.y);
                float fx = vec.x, fy = vec.y;
                toWorld(track.to.x, track.to.y);

                Lines.line(fx, fy, vec.x, vec.y);
            }
        }
    }

    /** Draws the tiles of the world.*/
    void drawWorld(){
        //draw tiles
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Color color =
                    tile.type == Terrain.mountain ? Color.LIGHT_GRAY :
                    tile.type == Terrain.water ? Color.ROYAL :
                    tile.type == Terrain.alpine ? Color.GRAY :
                    Color.FOREST;

                Draw.color(color);
                Vector2 world = toWorld(x, y);
                float tx = world.x, ty = world.y;
                Fill.square(tx, ty, tilesize/2f);

                Draw.color(Color.BLACK);
                if(tile.type == Terrain.plain){
                    Fill.square(tx, ty, 4);
                }else if(tile.type == Terrain.mountain){
                    Fill.poly(tx, ty, 3, 9, 90);
                }else if(tile.type == Terrain.alpine){
                    Lines.stroke(2f);
                    Lines.poly(tx, ty, 3, 9, 0);
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
