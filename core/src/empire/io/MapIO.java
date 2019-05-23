package empire.io;

import empire.game.World;
import empire.game.World.*;
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
        expect(scan, "#TILES");

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

        expect(scan, "#CITIES");

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

        expect(scan, "#PORTS");

        //read in port names and destinations
        String nextPort;
        while(!(nextPort = scan.next()).equals("#RIVERS")){
            int cost = Integer.parseInt(nextPort);
            String fromName = scan.next();
            int fromy = height - 1 - scan.nextInt(), fromx = scan.nextInt();
            String toName = scan.next();
            int toy = height - 1 - scan.nextInt(), tox = scan.nextInt();

            Tile from = tiles[fromx][fromy];
            Tile to = tiles[tox][toy];
            Port port = new Port(fromName, toName, cost, from, to);
            from.port = port;
            to.port = port;
        }

        Runnable parseRivers = () -> {
            String next;
            while((next = scan.nextLine()).contains("|")){
                String[] split = next.split(" ");
                int y = height - 1 - Integer.parseInt(split[0]);
                for(int i = 2; i < split.length; i++){
                    int x = Integer.parseInt(split[i]);
                    tiles[x][y].river = true;
                }
            }
        };

        String next = scan.next();
        while(!next.equals("#LAKES")){ //'next' is the river name right now
            String name = next;
            //parse l-side
            expect(scan, "l");
            scan.nextLine();
            parseRivers.run();

            //parse r-side
            expect(scan, "r");
            scan.nextLine();

            parseRivers.run();
            next = scan.next();
        }

        scan.close();

        return new World(tiles, cities);
    }

    /** Utility function to make sure sections of a text file are the correct input.*/
    private static void expect(Scanner scan, String expected){
        String next = scan.next();
        if(!next.equals(expected)){
            throw new IllegalArgumentException("Invalid input. Expected: '" + expected+ "'; Actual: '" + next + "'");
        }
    }
}
