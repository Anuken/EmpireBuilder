package empire.gfx;

import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.state;
import static empire.gfx.EmpireCore.tilesize;

/** Handles user input.*/
public class Control implements ApplicationListener{
    public Tile placeLoc = null;

    @Override
    public void init(){
        //Core.scene.table(t -> t.touchable(Touchable.enabled)).dragged((x, y) -> {
        //    Core.camera.position.sub(x / renderer.zoom, y / renderer.zoom);
        //});
    }

    @Override
    public void update(){

        if(Core.input.keyDown(KeyCode.MOUSE_LEFT)){
            Tile tile = tileMouse();
            if(placeLoc != null){
                if(state.canPlaceTrack(state.player(), placeLoc, tile)){
                    int cost = state.getTrackCost(placeLoc, tile);
                    if(state.canSpendRail(state.player(), cost)){
                        state.placeTrack(state.player(), placeLoc, tile);
                        placeLoc = tile;
                    }
                }
            }else{
                Player player = state.player();
                if(tile != null && (state.world.getMajorCity(tile) != null ||
                                    player.position == tile ||
                                    player.tracks.containsKey(tile))){
                    placeLoc = tile;
                }
            }
        }

        if(Core.input.keyRelease(KeyCode.MOUSE_LEFT)){
            placeLoc = null;
        }

        if(Core.input.keyTap(KeyCode.SPACE)){
            Tile tile = tileMouse();
            Player player = state.player();
            if(tile != null){
                //TODO movement animation
                int cost = state.distanceTo(player, tile);
                if(cost != -1 && cost + player.moved <= player.loco.speed){
                    player.position = tile;
                    player.moved += cost;
                }
            }
        }

        if(Core.input.keyTap(KeyCode.ESCAPE)){
            Core.app.exit();
        }
    }

    public Tile tileMouseMid(){
        return tileWorld(Core.input.mouseWorld().x - tilesize/2f, Core.input.mouseWorld().y - tilesize/2f);
    }

    //todo move to control
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

        return state.world.tileOpt(tx, ty);
    }

    /** Converts map coordinates to world coordinates.*/
    Vector2 toWorld(int x, int y){
        return Tmp.v1.set(x * tilesize + (y%2)*tilesize/2f, y*tilesize);
    }

    /** {@link #toWorld(int, int)} for tiles.*/
    Vector2 toWorld(Tile tile){
        return toWorld(tile.x, tile.y);
    }
}
