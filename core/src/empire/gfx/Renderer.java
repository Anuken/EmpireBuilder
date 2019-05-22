package empire.gfx;

import empire.game.World;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Camera;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Fill;

import static empire.gfx.EmpireCore.tilesize;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{

    public Renderer(){
        Core.camera = new Camera();
    }

    @Override
    public void update(){
        Draw.proj(Core.camera.projection());

        World world = EmpireCore.state.world;

        for(int x = 0; x < world.width; x++){
            for(int y = 0; y < world.height; y++){
                Tile tile = world.tile(x, y);
                Color color =
                        tile.type == Terrain.mountain ? Color.WHITE :
                        tile.type == Terrain.water ? Color.ROYAL :
                        Color.OLIVE;

                Draw.color(color);
                Fill.poly(x * tilesize + (y%2)*tilesize/2f, y * tilesize, 6, tilesize/2f);
            }
        }

        Draw.flush();
    }

    @Override
    public void resize(int width, int height){
        Core.camera.resize(width, height);
    }

}
