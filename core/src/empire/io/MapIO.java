package empire.io;

import empire.game.World;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.collection.IntMap;
import io.anuke.arc.files.FileHandle;

import java.util.Scanner;

/** Loads maps from text files.*/
public class MapIO{
    /** Maps character to terrain type. */
    private static final IntMap<Terrain> terrainMap = IntMap.of(
        'o', Terrain.water,
        'm', Terrain.mountain,
        'a', Terrain.alpine,
        'p', Terrain.plain
    );

    public static World loadTiles(FileHandle file){
        Scanner scan = new Scanner(file.read(1024));
        int height = scan.nextInt(), width = scan.nextInt();

        Tile[][] tiles = new Tile[width][height];

        //scan through each x/y position and read the character there
        for(int ry = 0; ry < height; ry++){
            for(int x = 0; x < width; x++){
                char c = scan.next().charAt(0);
                //flip y-axis, as the file is read top to bottom
                int y = height - 1 - ry;

                Terrain terrain = terrainMap.get(c);
                tiles[x][y] = new Tile(terrain, x, y);
            }
        }

        return new World(tiles);
    }
}
