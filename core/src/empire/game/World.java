package empire.game;

/** Holds information about the game's world, such as tiles and their costs.*/
public class World{
    private final Tile[][] tiles;

    /** Width and height of the world, in tiles.*/
    public final int width, height;

    public World(Tile[][] tiles){
        this.tiles = tiles;
        width = tiles.length;
        height = tiles[0].length;
    }

    /** Returns a tile at a location.*/
    public Tile tile(int x, int y){
        return tiles[x][y];
    }

    /** A single tile on the board.*/
    public static class Tile{
        public final Terrain type;
        public final int x, y;

        public Tile(Terrain type, int x, int y){
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    /** A type of terrain for a tile.*/
    public enum Terrain{
        water, plain, mountain, alpine
    }
}
