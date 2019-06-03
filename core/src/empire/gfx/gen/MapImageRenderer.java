package empire.gfx.gen;

import empire.game.World;
import empire.game.World.Lake;
import empire.game.World.River;
import empire.game.World.Sea;
import empire.game.World.Tile;
import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.Texture.TextureFilter;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.math.geom.Geometry;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Align;

import static empire.gfx.EmpireCore.*;

public class MapImageRenderer{
    private static final int gsize = 16;

    public static Texture createMapTexture(World world){

        //create a framebuffer to draw to, resize the projection matrix accordingly
        FrameBuffer buffer = new FrameBuffer(world.width * gsize, world.height * gsize);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        Draw.flush();
        buffer.begin();
        Core.graphics.clear(Color.CLEAR);

        Draw.color();

        //draw tiles
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Vector2 tworld = control.toWorld(x, y);
                float tx = tworld.x, ty = tworld.y;
                Draw.rect(Core.atlas.find("terrain-" + tile.type.name(), Core.atlas.find("terrain-plain")), tx, ty, tilesize, tilesize);
            }
        }

        Draw.color();

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
        Lines.stroke(2.5f, Color.valueOf("4f5ec5"));
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

        BitmapFont font = Core.scene.skin.getFont("default");
        font.getData().setScale(1f);
        font.setColor(Color.CORAL);

        //draw ports
        for(int x = 0; x < state.world.width; x++){
            for(int y = 0; y < state.world.height; y++){
                Tile tile = state.world.tile(x, y);
                Vector2 vec = control.toWorld(x, y);
                float tx = vec.x, ty = vec.y;
                if(tile.port != null && tile.port.from == tile){
                    Vector2 to = control.toWorld(tile.port.to.x, tile.port.to.y);
                    Lines.stroke(3f, Color.DARK_GRAY);
                    Lines.dashLine(tx, ty, to.x, to.y, (int)(to.dst(tx, ty) / 4));
                    Fill.circle(tx, ty, 3f);
                    Fill.circle(to.x, to.y, 3f);
                    font.draw(tile.port.cost + "", (to.x + tx)/2f, (to.y + ty)/2f + 4, Align.center);
                }
            }
        }

        font.setColor(Color.WHITE);

        for(Sea sea : world.seas){
            Vector2 v = control.toWorld(sea.x, sea.y);
            font.draw(sea.formalName(), v.x, v.y + tilesize, Align.center);
        }
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);

        //flush results, end buffer
        Draw.flush();
        buffer.end();
        buffer.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        return buffer.getTexture();
    }
}
