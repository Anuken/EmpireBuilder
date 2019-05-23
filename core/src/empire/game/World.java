package empire.game;

import io.anuke.arc.collection.Array;

/** Holds information about the game's world, such as tiles and their costs.*/
public class World{
    private final Tile[][] tiles;
    private final Array<City> cities;

    /** Width and height of the world, in tiles.*/
    public final int width, height;

    public World(Tile[][] tiles, Array<City> cities){
        this.tiles = tiles;
        this.cities = cities;
        width = tiles.length;
        height = tiles[0].length;

        //put each city into a tile so it's easy to access
        cities.each(c -> tiles[c.x][c.y].city = c);
    }

    /** Returns the cities in this world.*/
    public Array<City> cities(){
        return cities;
    }

    /** Returns a tile at a location.*/
    public Tile tile(int x, int y){
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

        public Tile(Terrain type, int x, int y){
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    /** A type of terrain for a tile.*/
    public enum Terrain{
        water, plain, mountain, alpine, port
    }

    /** A single track to connect two tiles.*/
    public static class Track{
        public final Tile from, to;

        public Track(Tile from, Tile to){
            this.from = from;
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

    /** The size of a city.
     * small = 1 space, circle.
     * medium = 1 space, square.
     * major = 7 total spaces, hexagon.*/
    public enum CitySize{
        small, medium, major
    }
}
