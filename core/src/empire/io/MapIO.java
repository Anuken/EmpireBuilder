package empire.io;

import empire.game.World;
import empire.game.World.*;
import io.anuke.arc.collection.*;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.function.Supplier;
import io.anuke.arc.math.EarClippingTriangulator;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.*;
import io.anuke.arc.util.Log;

import java.util.Scanner;

/** Loads maps from text files.*/
public class MapIO{
    private static final ConvexHull hull = new ConvexHull();
    /** Maps character to terrain type. */
    private static final IntMap<Terrain> terrainMap = IntMap.of(
        'o', Terrain.water,
        'm', Terrain.mountain,
        'a', Terrain.alpine,
        'p', Terrain.plain,
        'x', Terrain.port
    );

    public static World loadTiles(FileHandle file){
        ObjectSet<String> allgoods = new ObjectSet<>();

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
            String name = scan.next().toLowerCase();
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
            allgoods.addAll(goods);
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

        Supplier<Array<Point2>> parseRivers = () -> {
            Array<Point2> rivers = new Array<>();
            String next;
            while(scan.hasNextLine() && (next = scan.nextLine()).contains("|")){
                String[] split = next.split(" ");
                int y = height - 1 - Integer.parseInt(split[0]);
                for(int i = 2; i < split.length; i++){
                    int x = Integer.parseInt(split[i]);
                    rivers.add(new Point2(x, y));
                }
            }
            return rivers;
        };

        Array<River> rivers = new Array<>();

        String next = scan.next();
        while(!next.equals("#LAKES")){ //'next' is the river name right now
            String name = next;
            //parse l-side
            expect(scan, "l");
            scan.nextLine();
            Array<Point2> left = parseRivers.get();

            //parse r-side
            expect(scan, "r");
            scan.nextLine();

            Array<Point2> right = parseRivers.get();
            next = scan.next();

            if(left.isEmpty() || right.isEmpty()) continue;

            linkWaterTiles(tiles, left, right);

            //create smoothed river polyline
            Array<Vector2> mv = (left.size > right.size ? left : right).map(p -> new Vector2(p.x, p.y));
            Array<Vector2> ov = (left.size <= right.size ? left : right).map(p -> new Vector2(p.x, p.y));
            Array<Vector2> out = new Array<>();
            for(Vector2 v : mv){
                Vector2 closest = Geometry.findClosest(v.x, v.y, ov);
                out.add(closest.cpy().add(v).scl(0.5f));
            }

            for(Vector2 v : ov){
                Vector2 closest = Geometry.findClosest(v.x, v.y, mv);
                out.add(closest.cpy().add(v).scl(0.5f));
            }
            Array<Vector2> copy = new Array<>(out);
            Vector2 edge = out.first();
            out.remove(edge);
            Vector2 nextEdge;
            while((nextEdge = Geometry.findClosest(edge.x, edge.y, out)) != null){
                edge = nextEdge;
                out.remove(edge);
            }

            out.clear();
            Vector2 start = edge;
            out.add(start);
            copy.remove(start);
            while((start = Geometry.findClosest(start.x, start.y, copy)) != null){
                out.add(start);
                copy.remove(start);
            }

            int smoothIterations = 3;

            //smooth the polylines in several iterations
            for(int s = 0; s < smoothIterations; s++){
                Array<Vector2> smoothed = new Array<>();

                for(int i = 0; i < out.size; i++){
                    smoothed.add(out.get(i).cpy());
                    if(i < out.size - 1){
                        smoothed.add(out.get(i).cpy().lerp(out.get(i + 1), 0.5f));
                    }
                }

                for(int i = 0; i < out.size; i++){
                    Vector2 lastv = smoothed.get(Mathf.clamp(i * 2 - 1, 0, smoothed.size-1));
                    Vector2 nextv = smoothed.get(Mathf.clamp(i * 2 + 1, 0, smoothed.size-1));
                    smoothed.get(i*2).set(lastv).lerp(nextv, 0.5f);
                }

                out = smoothed;
            }

            rivers.add(new River(name, out));
        }

        //TODO read lakes and inlets

        //read lakes
        Array<Lake> lakes = new Array<>();
        next = scan.next();

        while(!next.equals("#INLETS")){
            if(!next.equals("l")){
                throw new IllegalArgumentException("Expecting l.");
            }
            scan.nextLine();
            Array<Point2> left = parseRivers.get();
            Array<Point2> right = parseRivers.get();

            linkWaterTiles(tiles, left, right);
            lakes.add(makeLake(left, right));
            next = scan.next();
        }

        //read inlets

        while(scan.hasNext()){
            expect(scan, "l");
            scan.nextLine();
            Array<Point2> left = parseRivers.get();
            Array<Point2> right = parseRivers.get();

            linkWaterTiles(tiles, left, right);
            lakes.add(makeLake(left, right));
        }

        scan.close();

        //todo make icons for these and remove debugging statement
        Log.info("Total goods: {0}\n{1}", allgoods.size, allgoods);

        return new World(tiles, cities, rivers, lakes);
    }

    /** Adds the appropriate water tile costs.*/
    private static void linkWaterTiles(Tile[][] tiles, Array<Point2> left, Array<Point2> right){
        for(Point2 p : left){
            Tile tile = tiles[p.x][p.y];
            for(Point2 adj : tile.getAdjacent()){
                if(right.contains(test -> test.equals(p.x + adj.x, p.y + adj.y))){
                    if(tile.riverTiles == null){
                        tile.riverTiles = new Array<>();
                        tile.riverTiles.add(tiles[p.x + adj.x][p.y + adj.y]);
                    }
                }
            }
        }
    }

    /** Creates a lake from some points.*/
    private static Lake makeLake(Array<Point2> left, Array<Point2> right){
        FloatArray points = new FloatArray();
        left.each(p -> points.add(p.x, p.y));
        right.each(p -> points.add(p.x, p.y));

        int smoothIterations = 3;
        //compute convex hull of points
        FloatArray polygon = hull.computePolygon(points, false);
        Array<Vector2> out = polygon.toVector2Array();

        //smooth it out across several iterations
        for(int s = 0; s < smoothIterations; s++){
            Array<Vector2> smoothed = new Array<>();

            for(int i = 0; i < out.size; i++){
                smoothed.add(out.get(i).cpy());
                if(i < out.size - 1){
                    smoothed.add(out.get(i).cpy().lerp(out.get(i + 1), 0.5f));
                }
            }

            for(int i = 0; i < out.size; i++){
                Vector2 lastv = smoothed.get(Mathf.mod(i * 2 - 1, smoothed.size-1));
                Vector2 nextv = smoothed.get(Mathf.mod(i * 2 + 1, smoothed.size-1));
                smoothed.get(i*2).set(lastv).lerp(nextv, 0.5f);
            }

            out = smoothed;
        }

        //compute polygon indices
        polygon = Geometry.vectorsToFloats(out);
        short[] indices = new EarClippingTriangulator().computeTriangles(polygon).toArray();

        return new Lake(polygon.toVector2Array(), indices);
    }

    /** Utility function to make sure sections of a text file are the correct input.*/
    public static void expect(Scanner scan, String expected){
        String next = scan.next();
        if(!next.equals(expected)){
            throw new IllegalArgumentException("Invalid input. Expected: '" + expected+ "'; Actual: '" + next + "'");
        }
    }
}
