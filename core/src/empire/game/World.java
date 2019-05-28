package empire.game;

import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.math.geom.Point2;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Structs;

/** Holds information about the game's world, such as tiles and their costs.*/
public class World{
    /** Adjacent points for a tile.*/
    public static final Point2[] adjacencyEven = {
        new Point2(1, 0),
        new Point2(0, 1),
        new Point2(-1, 1),
        new Point2(-1, 0),
        new Point2(-1, -1),
        new Point2(0, -1),
    };

    public static final Point2[] adjacencyOdd = {
        new Point2(1, 0),
        new Point2(1, 1),
        new Point2(0, 1),
        new Point2(-1, 0),
        new Point2(0, -1),
        new Point2(1, -1),
    };

    private final Tile[][] tiles;
    private final ObjectMap<String, City> cities;
    private final Array<River> rivers;

    /** Width and height of the world, in tiles.*/
    public final int width, height;

    public World(Tile[][] tiles, Array<City> cities, Array<River> rivers){
        this.tiles = tiles;
        this.cities = new ObjectMap<>();
        this.rivers = rivers;
        width = tiles.length;
        height = tiles[0].length;

        //put cities into main map
        cities.each(c -> this.cities.put(c.name, c));
        //put each city into a tile so it's easy to access
        cities.each(c -> tiles[c.x][c.y].city = c);
    }

    public City getMajorCity(Tile tile){
        if(tile.city != null && tile.city.size == CitySize.major){
            return tile.city;
        }else{
            Point2 out = Structs.find(tile.getAdjacent(), p -> {
                Tile other = tileOpt(tile.x + p.x, tile.y + p.y);
                return other != null && other.city != null && other.city.size == CitySize.major;
            });
            if(out != null){
                return tile(tile.x + out.x, tile.y + out.y).city;
            }
        }
        return null;
    }

    /** Returns the unique index of a tile.*/
    public int index(Tile tile){
        return tile.x + tile.y * width;
    }

    /** Returns a city by name.*/
    public City getCity(String name){
        return cities.get(name);
    }

    /** Returns an array of all rivers.*/
    public Array<River> rivers(){
        return rivers;
    }

    /** Returns the cities in this world.*/
    public Iterable<City> cities(){
        return cities.values();
    }

    /** Returns a tile at a location. Never returns null.
     * Throws an exception when out of bounds.*/
    public Tile tile(int x, int y){
        return tiles[x][y];
    }

    /** Returns a tile at a location. May return null if out of bounds.*/
    public Tile tileOpt(int x, int y){
        if(!Structs.inBounds(x, y, width, height)){
            return null;
        }
        return tiles[x][y];
    }

    /** A single tile on the board.*/
    public static class Tile{
        public final Terrain type;
        public final int x, y;
        /** The city on this tile. May be null.*/
        public City city;
        /** The port at this city's location. May be null.*/
        public Port port;
        /** Whether this tile is adjacent to a river.*/
        public boolean river;
        /** Temporary search distance.*/
        public int searchDst;
        /** List of tiles that require crossing a river to get to from this tile.*/
        public Array<Tile> riverTiles;

        public Tile(Terrain type, int x, int y){
            this.type = type;
            this.x = x;
            this.y = y;
        }

        public Point2[] getAdjacent(){
            return y % 2 == 0 ? adjacencyEven : adjacencyOdd;
        }
    }

    /** A type of terrain for a tile.*/
    public enum Terrain{
        water, plain, mountain, alpine, port
    }

    /** Represents a port, from one location to another.*/
    public static class Port{
        /** The port names.*/
        public final String fromName, toName;
        /** The cost to travel this port in millions.*/
        public final int cost;
        /** The tiles this port connects.*/
        public final Tile from, to;

        public Port(String fromName, String toName, int cost, Tile from, Tile to){
            this.fromName = fromName;
            this.toName = toName;
            this.cost = cost;
            this.from = from;
            this.to = to;
        }
    }

    /** Represents a city on the map.*/
    public static class City{
        public final String name;
        /** This city's x/y position.*/
        public final int x, y;
        /** This city's size.*/
        public final CitySize size;
        /** Goods that this city has, as lowercase strings.*/
        public final Array<String> goods;

        public City(String name, int x, int y, CitySize size, Array<String> goods){
            this.name = name;
            this.x = x;
            this.y = y;
            this.size = size;
            this.goods = goods;
        }
    }

    /** Represents a river on the map.*/
    public static class River{
        /** Smooth oints that the river passes through in tile coordinates.*/
        public final Array<Vector2> points;
        /** River name.*/
        public final String name;

        public River(String name, Array<Vector2> points){
            this.points = points;
            this.name = name;
        }
    }

    /** The size of a city.
     * small = 1 space, circle.
     * medium = 1 space, square.
     * major = 7 total spaces, hexagon.*/
    public enum CitySize{
        small, medium, major
    }
}
