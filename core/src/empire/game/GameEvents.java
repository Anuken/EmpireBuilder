package empire.game;

public class GameEvents{

    public static class WinEvent{
        public final Player player;

        public WinEvent(Player player){
            this.player = player;
        }
    }

    public static class EventEvent{
        public final EventCard card;

        public EventEvent(EventCard card){
            this.card = card;
        }
    }

    public static class EndTurnEvent{
        public final Player player, nextPlayer;

        public EndTurnEvent(Player player, Player nextPlayer){
            this.player = player;
            this.nextPlayer = nextPlayer;
        }
    }
}
