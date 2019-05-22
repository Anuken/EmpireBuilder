package empire.io;

import empire.game.World;
import empire.game.World.City;
import empire.game.World.CitySize;
import empire.game.World.Terrain;
import empire.game.World.Tile;
import io.anuke.arc.collection.Array;
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
        'p', Terrain.plain,
        'x', Terrain.port
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
                if(terrain == null){
                    throw new IllegalArgumentException("Unknown terrain type: " + c);
                }
                tiles[x][y] = new Tile(terrain, x, y);
            }
        }

        //now read cities
        Array<City> cities = new Array<>();
        int citynum = scan.nextInt();

        for(int i = 0; i < citynum; i++){
            String name = scan.next();
            int size = scan.nextInt();
            int y = height - 1 - scan.nextInt();
            int x = scan.nextInt();
            int goodnum = scan.nextInt();
            Array<String> goods = new Array<>();
            for(int j = 0; j < goodnum; j++){
                //make sure to read in goods in lower case for consistency;
                //they can be capitalized later
                goods.add(scan.next().toLowerCase());
            }
            cities.add(new City(name, x, y, CitySize.values()[size-1], goods));
        }

        return new World(tiles, cities);
    }
}
