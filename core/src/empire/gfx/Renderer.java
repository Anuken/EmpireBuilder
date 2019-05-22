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
import io.anuke.arc.graphics.g2d.Lines;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Log;

import static empire.gfx.EmpireCore.*;

/** Renders the game world, handles user input interactions if needed. */
public class Renderer implements ApplicationListener{

    public Renderer(){
        Core.camera = new Camera();
        Core.camera.position.set(state.world.width * tilesize/2f, state.world.height*tilesize/2f);
    }

    @Override
    public void update(){
        Core.graphics.clear(Color.BLACK);

        Core.camera.update();
        Draw.proj(Core.camera.projection());

        World world = state.world;

        for(int x = 0; x < world.width; x++){
            for(int y = 0; y < world.height; y++){
                Tile tile = world.tile(x, y);
                Color color =
                        tile.type == Terrain.mountain ? Color.WHITE :
                        tile.type == Terrain.water ? Color.CLEAR :
                        Color.WHITE;

                Draw.color(color);
                //Fill.square(x * tilesize, y*tilesize, tilesize/2f);
                float tx = x * tilesize * 1.5f + (y % 2) * (tilesize * 1.5f / 2f);
                float ty = y * tilesize/2.4f;
                //Draw.rect("hex", tx, ty, tilesize, tilesize*2f);
                Fill.poly(tx, ty, 6, tilesize/2f);

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

        Draw.flush();
    }

    @Override
    public void resize(int width, int height){
        Core.camera.resize(width, height);
    }

}
