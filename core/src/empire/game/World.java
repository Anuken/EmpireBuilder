package empire.game;

import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.math.geom.Point2;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.util.Strings;
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

    /** All the rivers of the map.*/
    public final Array<River> rivers;
    /** All the lakes of the map, as polygons.*/
    public final Array<Lake> lakes;
    /** All the seas of the map.*/
    public final Array<Sea> seas;
    /** Width and height of the world, in tiles.*/
    public final int width, height;

    public World(Tile[][] tiles, Array<City> cities, Array<River> rivers, Array<Lake> lakes, Array<Sea> seas){
        this.tiles = tiles;
        this.cities = new ObjectMap<>();
        this.rivers = rivers;
        this.lakes = lakes;
        this.seas = seas;
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

    /** Returns any city on a tile, even if it is part of a major city.*/
    public City getCity(Tile tile){
        if(tile.city != null){
            return tile.city;
        }
        return getMajorCity(tile);
    }

    /** Returns whether a tile is adjacent to another tile.*/
    public boolean isAdjacent(Tile from, Tile to){
        return Structs.contains(from.getAdjacent(), p -> tileOpt(from.x + p.x, from.y + p.y) == to);
    }

    /** Returns the unique index of a tile.*/
    public int index(Tile tile){
        return tile.x + tile.y * width;
    }

    /** Returns a tile by index.*/
    public Tile tile(int index){
        return tile(index % width, index / width);
    }

    /** Returns a sea by name. Throws an exception if not found.*/
    public Sea getSea(String name){
        Sea river = seas.find(r -> r.name.equals(name));
        if(river == null){
            throw new IllegalArgumentException("No seas found with name: \"" + name + "\"");
        }
        return river;
    }

    /** Returns a city by name. Throws an exception if not found.*/
    public City getCity(String name){
        if(!cities.containsKey(name)){
            throw new IllegalArgumentException("No city found with name: \"" + name + "\"");
        }
        return cities.get(name);
    }

    /** Returns a river by name. Throws an exception if not found.*/
    public River getRiver(String name){
        River river = rivers.find(r -> r.name.equals(name));
        if(river == null){
            throw new IllegalArgumentException("No river found with name: \"" + name + "\"");
        }
        return river;
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

    /** Iterates through the connections of a tile, taking into account ports.
     * Also takes into account tracks of this player.*/
    public void connectionsOf(State state, Player player, Tile tile, boolean otherPlayers, Consumer<Tile> adjacent){
        //water has no connections
        if(tile.type == Terrain.water){
            return;
        }

        City city = getMajorCity(tile);

        for(Point2 point : tile.getAdjacent()){
            Tile other = tileOpt(tile.x + point.x, tile.y + point.y);
            if(other != null){
                //case 1: same major city
                if(city != null && getMajorCity(other) == city){
                    adjacent.accept(other);
                    //case 2: tracks between these two points
                }else if(player.tracks.containsKey(tile) && player.tracks.get(tile).contains(other)){
                    adjacent.accept(other);
                }else if(otherPlayers){
                    //TODO this is laughably inefficient
                    for(Player otherplayer : state.players){
                        if(otherplayer.tracks.containsKey(tile) && otherplayer.tracks.get(tile).contains(other)){
                            adjacent.accept(other);
                            break;
                        }
                    }
                }
            }
        }

        //ports work both ways like rails
        if(tile.port != null && (tile.port.from == tile)){
            adjacent.accept(tile.port.to);
        }

        if(tile.port != null && (tile.port.to == tile)){
            adjacent.accept(tile.port.from);
        }
    }

    /** A single tile on the board.*/
    public static class Tile{
        public final Terrain type;
        public final int x, y;
        /** The city on this tile. May be null.*/
        public City city;
        /** The port at this city's location. May be null.*/
        public Port port;
        /** The sea area on this tile; may be null.*/
        public Sea sea;
        /** Temporary search parent.*/
        public Tile searchParent;
        /** List of crossings to other tiles.*/
        public Array<WaterCrossing> crossings;
        /** Whether this tile is inland, e.g. 3 tiles from shore.*/
        public boolean inland = true, border = false;

        public Tile(Terrain type, int x, int y){
            this.type = type;
            this.x = x;
            this.y = y;
        }

        /** Returns whether this tile is of type alpine or mountain. */
        public boolean isMountainous(){
            return type == Terrain.alpine || type == Terrain.mountain;
        }

        /** Returns adjacent points to this tile. */
        public Point2[] getAdjacent(){
            return y % 2 == 0 ? adjacencyEven : adjacencyOdd;
        }

        public int distanceTo(int ox, int oy){
            if(x == ox){
                return Math.abs(y - oy);
            }else if(y == oy){
                return Math.abs(x - ox);
            }else{
                //TODO this is incorrect
                return (int)Mathf.dst(ox, oy);
            }
        }

        public int distanceTo(Tile other){
            return distanceTo(other.x, other.y);
        }

        /** Returns the direction needed to travel from this tile to the other.
         * Will return null if these tiles are not adjacent.*/
        public Direction directionTo(Tile other){
            Point2[] adjacent = getAdjacent();
            for(int i = 0; i < 6; i++){
                if(x + adjacent[i].x == other.x && y + adjacent[i].y == other.y){
                    return Direction.all[i];
                }
            }
            return null;
        }

        @Override
        public String toString(){
            return x + "," + y + ",t=" + type;
        }
    }

    /** A type of terrain for a tile.*/
    public enum Terrain{
        water, plain, mountain, alpine, port
    }

    /** Defines a crossing between two tiles that intersects a body of water.*/
    public static class WaterCrossing{
        /** Cost of this crossing in ECU.*/
        public final int cost;
        /** River that this crosses; may be null.*/
        public final River river;
        /** The destination tile.*/
        public final Tile to;

        public WaterCrossing(int cost, River river, Tile to){
            this.cost = cost;
            this.river = river;
            this.to = to;
        }
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
        /** City name in lower case.*/
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

        public String formalName(){
            return Strings.capitalize(name);
        }
    }

    /** Represents a sea area on the map.*/
    public static class Sea{
        /** This sea's short name.*/
        public final String name;
        /** The anchor position, i.e. where text should be displayed.*/
        public final int x, y;
        /** All expansion points from which this sea gets flood filled.*/
        public Array<Point2> expansions = new Array<>();

        public Sea(String name, int x, int y){
            this.name = name;
            this.x = x;
            this.y = y;
            expansions.add(new Point2(x, y));
        }

        /** Returns this sea's formal name.
         * If the basic name is just one word, 'sea' is added to the end.
         */
        public String formalName(){
            return name.contains("_") ? Strings.capitalize(name) : name + " Sea";
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

    /** Represents a lake on the map as a polygon.*/
    public static class Lake{
        public final Array<Vector2> points;
        public final short[] indices;

        public Lake(Array<Vector2> points, short[] indices){
            this.points = points;
            this.indices = indices;
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
