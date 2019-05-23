package empire.gfx;

import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.input.KeyCode;

import static empire.gfx.EmpireCore.renderer;
import static empire.gfx.EmpireCore.state;

/** Handles user input.*/
public class Control implements ApplicationListener{
    public boolean isPlacingLines;
    public Tile currentPlaceLoc = null;

    @Override
    public void update(){

        if(Core.input.keyTap(KeyCode.SPACE)){
            Tile tile = renderer.tileMouse();
            if(currentPlaceLoc != null){
                if(state.canPlaceTrack(currentPlaceLoc, tile)){
                    state.placeTrack(state.currentPlayer(), currentPlaceLoc, tile);
                    currentPlaceLoc = tile;
                }
            }else{
                if(tile != null){
                    currentPlaceLoc = tile;
                }
            }

        }

        if(Core.input.keyTap(KeyCode.ESCAPE)){
            Core.app.exit();
        }
    }
}
