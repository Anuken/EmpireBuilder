package empire.gfx;

import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.input.KeyCode;

import static empire.gfx.EmpireCore.renderer;
import static empire.gfx.EmpireCore.state;

/** Handles user input.*/
public class Control implements ApplicationListener{
    public Tile placeLoc = null;

    @Override
    public void update(){

        if(Core.input.keyTap(KeyCode.SPACE)){
            Tile tile = renderer.tileMouse();
            if(placeLoc != null){
                if(state.canPlaceTrack(state.currentPlayer(), placeLoc, tile)){
                    state.placeTrack(state.currentPlayer(), placeLoc, tile);
                    placeLoc = tile;
                }
            }else{
                Player player = state.currentPlayer();
                if(tile != null && (player.position == tile || player.tracks.containsKey(tile))){
                    placeLoc = tile;
                }
            }

        }

        if(Core.input.keyTap(KeyCode.MOUSE_LEFT)){
            Tile tile = renderer.tileMouse();
            Player player = state.currentPlayer();
            if(tile != null && player.hasTrack(tile)){
                //TODO movement animation
                int cost = player.distanceTo(tile);
                if(cost + player.moved <= player.loco.speed){
                    player.position = tile;
                    player.moved += cost;
                }
            }
        }

        if(Core.input.keyTap(KeyCode.ESCAPE)){
            Core.app.exit();
        }
    }
}
