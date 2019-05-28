package empire.gfx.gen;

import empire.game.World;
import empire.game.World.River;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.CapStyle;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Lines;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.math.geom.Vector2;

public class RiverRenderer{
    private static final int gsize = 16;

    public static Texture renderRivers(World world){

        //create a framebuffer to draw to, resize the projection matrix accordingly
        FrameBuffer buffer = new FrameBuffer(world.width * gsize, world.height * gsize);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        Draw.flush();
        buffer.begin();
        Core.graphics.clear(Color.CLEAR);

        for(int i = 0; i < 2; i++){
            if(i == 0){
                Lines.stroke(5f, Color.valueOf("5d81e1"));
            }else{
                Lines.stroke(2f, Color.valueOf("4f5ec5"));
            }
            for(River river : world.rivers()){

                Vector2[] last = {null};
                river.points.each(v -> {
                    if(last[0] != null){
                        Lines.line(last[0].x*gsize, last[0].y*gsize, v.x*gsize, v.y*gsize, CapStyle.round);
                    }
                    last[0] = v;
                });
            }
        }


        //flush results, end buffer
        Draw.flush();
        buffer.end();
        buffer.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return buffer.getTexture();
    }
}
