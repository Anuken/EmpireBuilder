package empire.gfx.gen;

import empire.game.World;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.Core;
import io.anuke.arc.collection.IntArray;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Pixmap;
import io.anuke.arc.graphics.Pixmap.Format;
import io.anuke.arc.graphics.Pixmaps;
import io.anuke.arc.graphics.Texture;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Fill;
import io.anuke.arc.graphics.glutils.FrameBuffer;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.ScreenUtils;
import io.anuke.arc.util.Time;

public class PixelMapImageGenerator{
    /** Size of a single tile in pixels.*/
    private static final int gsize = 16;
    /** Base color of the terrain types, indexed by ordinal.*/
    private static final Color[] terrainColor = {
        Color.ROYAL,
        Color.FOREST,
        Color.LIGHT_GRAY,
        Color.WHITE,
        Color.FOREST
    };
    /** Special transition colors. All should be 4-tuples: src, dest, replace, radius.*/
    private static final int[][] transitions = {
        transition(Terrain.plain, Terrain.water, Color.valueOf("6d8ceb"), 6),
        transition(Terrain.mountain, Terrain.water, Color.valueOf("6d8ceb"), 5),
        transition(Terrain.mountain, Terrain.plain, Color.valueOf("7a8a79"), 3),
        transition(Terrain.alpine, Terrain.plain, Color.LIGHT_GRAY, 2)
    };

    /** Utility function that simply returns a transition for a specific terrain type combination.*/
    private static int[] transition(Terrain from, Terrain to, Color dest, int radius){
        return new int[]{terrainColor[from.ordinal()].rgba(), terrainColor[to.ordinal()].rgba(), dest.rgba(), radius};
    }

    /** Generates an image of the map terrain.*/
    public static Texture generateTerrain(World world){
        Time.mark();

        //create a framebuffer to draw to, resize the projection matrix accordingly
        FrameBuffer buffer = new FrameBuffer(world.width * gsize, world.height * gsize);
        Draw.proj().setOrtho(0, 0, buffer.getWidth(), buffer.getHeight());
        Draw.flush();
        buffer.begin();
        //make background color the plains color
        Core.graphics.clear(terrainColor[Terrain.water.ordinal()]);
        for(int x = 0; x < world.width; x++){
            for(int y = 0; y < world.height; y++){
                float tx = x * gsize + (y%2)*gsize/2f, ty = y*gsize + gsize/2f;

                //draw tile colors
                Tile tile = world.tile(x, y);
                Draw.color(terrainColor[tile.type.ordinal()]);
                Fill.square(tx, ty, gsize/2f);
            }
        }
        //flush results, end buffer
        Draw.flush();
        Pixmap pix = ScreenUtils.getFrameBufferPixmap(0, 0, buffer.getWidth(), buffer.getHeight(), true);
        Pixmap result = medianBlur(pix, 6, 0.5);
        buffer.end();

        //cleanup resources
        pix.dispose();
        buffer.dispose();

        result = applyTransitions(result);

        Texture out = new Texture(result);
        result.dispose();

        Log.info("Time to generate image: {0}s", (Time.elapsed()/1000));
        return out;
    }

    /** Applies all terrain transitions to an input pixmap.*/
    static Pixmap applyTransitions(Pixmap in){
        for(int[] arr : transitions){
            Pixmap out = Pixmaps.copy(in);
            int from = arr[0], to = arr[1], dst = arr[2], radius = arr[3];

            for(int x = 0; x < in.getWidth(); x++){
                for(int y = 0; y < in.getHeight(); y++){
                    if(in.getPixel(x, y) != from){
                        continue;
                    }

                    for(int rx = -radius; rx <= radius; rx++){
                        for(int ry = -radius; ry <= radius; ry++){
                            if(Mathf.dst2(rx, ry) < radius*radius){
                                int worldX = Mathf.clamp(x + rx, 0, in.getWidth() - 1);
                                int worldY = Mathf.clamp(y + ry, 0, in.getHeight() - 1);
                                if(in.getPixel(worldX, worldY) == to){
                                    out.drawPixel(worldX, worldY, dst);
                                }
                            }
                        }
                    }
                }
            }

            in.dispose();
            in = out;
        }

        return in;
    }

    /** Median filter implementation. Samples pixels in a radius and returns the median value.*/
    static Pixmap medianBlur(Pixmap in, int radius, double percentile){
        Time.mark();
        Pixmap pix = new Pixmap(in.getWidth(), in.getHeight(), Format.RGBA8888);
        IntArray colors = new IntArray(radius*radius);
        for(int x = 0; x < in.getWidth(); x++){
            for(int y = 0; y < in.getHeight(); y++){
                colors.clear();

                for(int rx = -radius; rx <= radius; rx++){
                    for(int ry = -radius; ry <= radius; ry++){
                        if(Mathf.dst2(rx, ry) < radius*radius){
                            int worldX = Mathf.clamp(x + rx, 0, in.getWidth() - 1);
                            int worldY = Mathf.clamp(y + ry, 0, in.getHeight() - 1);
                            colors.add(in.getPixel(worldX, worldY));
                        }
                    }
                }

                //this sorts pixels in an arbitrary way, but it's consistent so I don't care
                colors.sort();
                pix.drawPixel(x, y, colors.get((int)(colors.size * percentile)));
            }
        }
        Log.info("Time to median blur: {0}s", Time.elapsed()/1000f);
        return pix;
    }
}
