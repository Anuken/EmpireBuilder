package empire.gfx.fx;

import empire.game.Player;
import empire.game.World.Tile;
import io.anuke.arc.util.*;

/** Defines a type of movement effect which affects a player's visual position on the board.
 * Does not influence any logic outside of rendering.*/
public abstract class ActionFx{
    public float lifetime = 10f, time;

    /** Updates an effect.*/
    public void update(){
        time += 1f / lifetime * Time.delta();

        draw();
    }

    /** Does any necessary drawing or updating of the effect, internally. */
    public abstract void draw();

    public static class MoveFx extends ActionFx{
        public final Player player;
        public final Tile from, to;

        public MoveFx(Player player, Tile from, Tile to){
            this.player = player;
            this.from = from;
            this.to = to;
        }

        @Override
        public void draw(){
            player.visualpos.set(from).lerp(Tmp.v2.set(to), time);
        }
    }

    public static class TrackFx extends ActionFx{
        public final Player player;
        public final Tile from, to;

        public TrackFx(Player player, Tile from, Tile to){
            this.player = player;
            this.from = from;
            this.to = to;
        }

        @Override
        public void draw(){

        }
    }
}
