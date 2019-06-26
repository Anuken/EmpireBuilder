package empire.gfx;

import empire.ai.*;
import empire.game.*;
import empire.game.Actions.*;
import empire.game.EventCard.DerailmentEvent;
import empire.game.World.*;
import io.anuke.arc.*;
import io.anuke.arc.collection.Array;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.geom.Vector2;

import static empire.gfx.EmpireCore.*;

/** Handles user input.*/
public class Control implements ApplicationListener{
    private Array<Tile> outArray = new Array<>();
    private Astar astar = new Astar(null);

    private Array<Tile[]> placement = new Array<>();
    private Tracks placementTracks = new Tracks();
    private Array<Tile> selectTiles = new Array<>();
    private float placementCost = 0;

    private ThreadLocal<Vector2> vec = new ThreadLocal<>();
    public Tile placeLoc = null, cursor = null;

    @Override
    public void update(){
        if(tileMouse() != null && tileMouse() != cursor && placeLoc != null){
            cursor = tileMouse();
            updatePath();
        }

        cursor = tileMouse();

        if(debug && Core.input.keyTap(KeyCode.P)){
            ((EventCard)state.cards.select(c -> c instanceof DerailmentEvent).random()).apply(state, state.player());
        }

        if(debug && Core.input.keyTap(KeyCode.GRAVE)){
            new NextAI(state.player(), state).test();
        }

        if(cursor != null){
            //choose start pos
            if(Core.input.keyTap(KeyCode.MOUSE_LEFT) && EmpireCore.net.active()
                    && state.player().local && !state.player().chosenLocation
                    && state.world.getCity(cursor) != null){
                City city = state.world.getCity(cursor);
                new ChooseStart(){{
                    location = state.world.tile(city.x, city.y);
                }}.act();
            }

            //begin placing on mouse down
            if(Core.input.keyTap(KeyCode.MOUSE_LEFT) && !Core.scene.hasMouse()
                    && state.player().local
                    && state.player().chosenLocation
                    && (state.canBeginTrack(state.player(), cursor)
                        || placementTracks.has(cursor.x, cursor.y))){
                placeLoc = cursor;
            }

            //add track to queue on mouse up
            if(Core.input.keyRelease(KeyCode.MOUSE_LEFT) && state.player().local){
                Tile last = placeLoc;
                for(Tile tile : selectTiles){
                    if(last != tile
                            && !state.world.sameCity(last, tile)
                            && !state.world.samePort(last, tile)
                            && !state.player().hasTrack(last, tile)){
                        placement.add(new Tile[]{last, tile});
                        placementTracks.add(last.x, last.y, tile.x, tile.y);
                    }
                    last = tile;
                }

                updateCost();

                selectTiles.clear();

                placeLoc = null;
            }

            if(Core.input.keyTap(KeyCode.MOUSE_RIGHT) && state.player().local && !state.isPreMovement() && !Core.scene.hasMouse()){
                Array<Tile> tiles = state.calculateMovement(state.player(), cursor);
                for(Tile next : tiles){
                    //skip duplicates
                    if(next == state.player().position) continue;
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

    private void updatePath(){
        if(state.world.isAdjacent(placeLoc, cursor)){
            selectTiles.clear();
            selectTiles.add(placeLoc, cursor);
        }else{
            astar.setPlayer(state.localPlayer());
            astar.astar(placeLoc, cursor, selectTiles);
        }
    }

    private void updateCost(){
        placementCost = placement.sum(p -> state.getTrackCost(p[0], p[1]));
    }

    /** Places all the tiles that are queued.
     * Clears the queue afterwards.*/
    public void placeQueued(){
        for(Tile[] pair : placement){
            if(state.canPlaceTrack(state.player(), pair[0], pair[1])){
                new PlaceTrack(){{
                    from = pair[0];
                    to = pair[1];
                }}.act();
            }
        }

        placement.clear();
    }

    public float queueCost(){
        return placementCost;
    }

    /** Returns an array of queued tiles.*/
    public Array<Tile[]> getQueued(){
        return placement;
    }

    /** Returns a tentative array of tiles to place.*/
    public Array<Tile> selectedTiles(){
        return selectTiles;
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
