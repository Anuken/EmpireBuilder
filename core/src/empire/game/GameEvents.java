package empire.game;

public class GameEvents{

    public static class WinEvent{
        public final Player player;

        public WinEvent(Player player){
            this.player = player;
        }
    }

    public static class EndTurnEvent{
        public final Player player;

        public EndTurnEvent(Player player){
            this.player = player;
        }
    }
}
