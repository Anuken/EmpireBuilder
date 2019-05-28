package empire.gfx.gen;

import empire.game.World;
import empire.game.World.Lake;
import empire.game.World.River;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.CapStyle;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Fill;
import io.anuke.arc.graphics.g2d.Lines;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.math.geom.Vector2;

public class WaterRenderer{
    private static final int gsize = 16;

    public static Texture createWaterTexture(World world){

        //create a framebuffer to draw to, resize the projection matrix accordingly
        FrameBuffer buffer = new FrameBuffer(world.width * gsize, world.height * gsize);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        Draw.flush();
        buffer.begin();
        Core.graphics.clear(Color.CLEAR);

        Runnable drawRivers = () -> {
            for(River river : world.rivers){
                Vector2[] last = {null};
                river.points.each(v -> {
                    if(last[0] != null){
                        Lines.line(last[0].x*gsize, last[0].y*gsize, v.x*gsize, v.y*gsize, CapStyle.round);
                    }
                    last[0] = v;
                });
            }
        };

        Lines.stroke(5f, Color.valueOf("5d81e1"));

        //draw light lakes/rivers
        drawRivers.run();

        for(Lake lake : world.lakes){
            Draw.color(Color.valueOf("5d81e1"));

            for(int i = 0; i < lake.indices.length; i += 3){
                Vector2 v1 = lake.points.get(lake.indices[i]);
                Vector2 v2 = lake.points.get(lake.indices[i + 1]);
                Vector2 v3 = lake.points.get(lake.indices[i + 2]);
                Fill.tri(v1.x * gsize, v1.y * gsize, v2.x * gsize, v2.y * gsize, v3.x * gsize, v3.y * gsize);
            }
        }

        //now draw darker stuff on top
        Lines.stroke(2f, Color.valueOf("4f5ec5"));
        drawRivers.run();

        for(Lake lake : world.lakes){
            Vector2 center = new Vector2();
            Geometry.polygonCentroid(Geometry.vectorsToFloats(lake.points).toArray(),
                    0, lake.points.size*2, center);

            Draw.color(Color.valueOf("4f5ec5"));
            float alpha = 0.25f;

            for(int i = 0; i < lake.indices.length; i += 3){
                Vector2 v1 = lake.points.get(lake.indices[i]).cpy().lerp(center, alpha);
                Vector2 v2 = lake.points.get(lake.indices[i+1]).cpy().lerp(center, alpha);
                Vector2 v3 = lake.points.get(lake.indices[i+2]).cpy().lerp(center, alpha);
                Fill.tri(v1.x*gsize, v1.y*gsize, v2.x*gsize, v2.y*gsize, v3.x*gsize, v3.y*gsize);
            }
        }


        //flush results, end buffer
        Draw.flush();
        buffer.end();
        buffer.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return buffer.getTexture();
    }
}
