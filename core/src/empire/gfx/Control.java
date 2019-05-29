package empire.gfx;

import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.collection.Array;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.util.Structs;
import io.anuke.arc.util.Tmp;

import static empire.gfx.EmpireCore.state;
import static empire.gfx.EmpireCore.tilesize;

/** Handles user input.*/
public class Control implements ApplicationListener{
    private Bresenham2 bres = new Bresenham2();
    private Array<Tile> outArray = new Array<>();
    public Tile placeLoc = null;

    @Override
    public void init(){
        //Core.scene.table(t -> t.touchable(Touchable.enabled)).dragged((x, y) -> {
        //    Core.camera.position.sub(x / renderer.zoom, y / renderer.zoom);
        //});
    }

    @Override
    public void update(){

        if(Core.input.keyTap(KeyCode.MOUSE_LEFT)){
            Tile tile = tileMouse();
            if(tile != null && state.canBeginTrack(state.player(), tile)){
                placeLoc = tile;
            }
            /*
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
                if(tile != null && state.canBeginTrack(player, tile)){
                    placeLoc = tile;
                }
            }*/
        }

        if(Core.input.keyRelease(KeyCode.MOUSE_LEFT)){
            Tile other = tileMouse();
            if(placeLoc != null && other != null && canPlaceLine(placeLoc, other)){
                for(Tile tile : getTiles(placeLoc, other)){
                    if(state.canPlaceTrack(state.player(), placeLoc, tile)){
                        int cost = state.getTrackCost(placeLoc, tile);
                        if(state.canSpendRail(state.player(), cost)){
                            state.placeTrack(state.player(), placeLoc, tile);
                        }
                    }
                    placeLoc = tile;
                }
            }
            placeLoc = null;
        }

        if(Core.input.keyTap(KeyCode.SPACE) && !state.isPreMovement()){
            Tile tile = tileMouse();
            Player player = state.player();
            if(tile != null){
                //TODO movement animation + sequence of tiles moved on
                Array<Tile> path = state.movePlayer(player, tile);
            }
        }

        if(Core.input.keyTap(KeyCode.ESCAPE)){
            Core.app.exit();
        }
    }

    public Array<Tile> getTiles(Tile from, Tile to){
        outArray.clear();
        Tile current = from;
        Tmp.v2.set(toWorld(from));
        Tmp.v3.set(toWorld(to));
        while(current != to && current != null){
            outArray.add(current);
            Tile tile = current;
            Point2 min = Structs.findMin(current.getAdjacent(), point -> {
                Tile other = state.world.tileOpt(tile.x + point.x, tile.y + point.y);
                if(other == null || Mathf.dst(tile.x, tile.y, from.x, from.y) >
                        Mathf.dst(other.x, other.y, from.x, from.y)){
                    return Integer.MAX_VALUE;
                }else if(other == to){
                    return -Integer.MAX_VALUE;
                }

                Tmp.v4.set(toWorld(other));

                return Intersector.distanceSegmentPoint(Tmp.v2, Tmp.v3, Tmp.v4);
            });
            current = state.world.tileOpt(tile.x + min.x, tile.y + min.y);
        }
        if(!outArray.isEmpty() && outArray.peek() != to){
            outArray.add(to);
        }
        return outArray;
    }

    public boolean canPlaceLine(Tile from, Tile to){
        for(Tile tile : getTiles(from, to)){
            if(tile == null || !state.isPassable(state.player(), tile)){
                return false;
            }
        }
        return true;
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
    public Vector2 toWorld(int x, int y){
        return Tmp.v1.set(x * tilesize + (y%2)*tilesize/2f, y*tilesize);
    }

    /** {@link #toWorld(int, int)} for tiles.*/
    public Vector2 toWorld(Tile tile){
        return toWorld(tile.x, tile.y);
    }
}
