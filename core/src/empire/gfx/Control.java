package empire.gfx;

import empire.game.Actions.*;
import empire.game.World.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.Array;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.util.*;

import static empire.gfx.EmpireCore.*;

/** Handles user input.*/
public class Control implements ApplicationListener{
    private Array<Tile> outArray = new Array<>();
    private ThreadLocal<Vector2> vec = new ThreadLocal<>();
    public Tile placeLoc = null;

    @Override
    public void update(){

        //choose start pos
        if(Core.input.keyTap(KeyCode.MOUSE_LEFT) && EmpireCore.net.active() && state.player().local && !state.player().chosenLocation){
            Tile tile = tileMouse();
            if(tile != null && state.world.getCity(tile) != null){
                City city = state.world.getCity(tile);
                new ChooseStart(){{
                    location = state.world.tile(city.x, city.y);
                }}.act();
            }
        }

        //begin placing on mouse down
        if(Core.input.keyTap(KeyCode.MOUSE_LEFT) && !Core.scene.hasMouse() && state.player().local && state.player().chosenLocation){
            Tile tile = tileMouse();
            if(tile != null && state.canBeginTrack(state.player(), tile)){
                placeLoc = tile;
            }
        }

        //place lines on mouse up
        if(Core.input.keyRelease(KeyCode.MOUSE_LEFT) && state.player().local){
            Tile other = tileMouse();
            if(placeLoc != null && other != null && canPlaceLine(placeLoc, other)){
                for(Tile tile : getTiles(placeLoc, other)){
                    if(state.canPlaceTrack(state.player(), placeLoc, tile)){
                        int cost = state.getTrackCost(placeLoc, tile);
                        if(state.canSpendTrack(state.player(), cost)){
                            //placing tracks is a special case, so it is executed locally as well
                            new PlaceTrack(){{
                                from = placeLoc;
                                to = tile;
                            }}.act();
                        }
                    }
                    placeLoc = tile;
                }
            }
            placeLoc = null;
        }

        if(Core.input.keyTap(KeyCode.MOUSE_RIGHT) && state.player().local && !state.isPreMovement() && !Core.scene.hasMouse()){
            Tile tile = tileMouse();
            if(tile != null){
                Array<Tile> tiles = state.calculateMovement(state.player(), tile);
                for(Tile next : tiles){
                    //skip duplicates
                    if(next == state.player().position) continue;;
                    //move until the player can't
                    if(!state.canMove(state.player(), next)){
                        break;
                    }

                    //actually call the move event
                    new Move(){{
                        to = next;
                    }}.act();
                }
            }
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
        if(vec.get() == null) vec.set(new Vector2());
        return vec.get().set(x * tilesize + (y%2)*tilesize/2f, y*tilesize);
    }

    /** {@link #toWorld(int, int)} for tiles.*/
    public Vector2 toWorld(Tile tile){
        return toWorld(tile.x, tile.y);
    }
}
