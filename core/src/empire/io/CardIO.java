package empire.io;

import empire.game.Card;
import empire.game.DemandCard;
import empire.game.DemandCard.Demand;
import empire.game.EventCard;
import empire.game.EventCard.*;
import empire.game.World;
import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.files.FileHandle;
import io.anuke.arc.function.Supplier;

import java.util.Scanner;

public class CardIO{
    private static final ObjectMap<String, Supplier<EventCard>> cardMaps = ObjectMap.of(
        "INLAND_STRIKE", (Supplier) InlandStrikeEvent::new,
        "COASTAL_STRIKE", (Supplier) CoastalStrikeEvent::new,
        "GENERAL_RAIL_STRIKE", (Supplier) GeneralRailStrikeEvent::new,
        "EXCESS_PROFITS_TAX", (Supplier) ExcessProfitsTaxEvent::new,
        "DERAILMENT", (Supplier) DerailmentEvent::new,
        "HEAVY_SNOW", (Supplier) HeavySnowEvent::new,
        "HEAVY_RAINS", (Supplier) HeavyRainsEvent::new,
        "FOG", (Supplier) FogEvent::new,
        "FLOOD", (Supplier) FloodEvent::new
    );

    /** Loads a deck of cards. Uses a world to look up cities by name.*/
    public static Array<Card> loadCards(World world, FileHandle file){
        Scanner scan = new Scanner(file.read(1024));
        Array<Card> out = new Array<>();

        String next = scan.next();
        if(!next.equals("#DEMANDS")){
            throw new IllegalArgumentException("Expecting demands, but got " + next);
        }

        //read demand cards
        while(!(next = scan.next()).equals("#EVENTS")){

            //card number, not useful here
            Demand[] demands = new Demand[3];
            for(int i = 0; i < 3; i++){
                String city = scan.next().toLowerCase();
                if(world.getCity(city) == null){
                    throw new IllegalArgumentException("No city with name " + city + " found in card #" + next);
                }

                String good = scan.next().toLowerCase();
                int profit = scan.nextInt();
                demands[i] = new Demand(good, world.getCity(city), profit);
            }

            out.add(new DemandCard(demands));
        }

        //read event cards
        while(scan.hasNext()){
            int index = scan.nextInt();
            String type = scan.next();
            if(!cardMaps.containsKey(type)){
                throw new IllegalArgumentException("Unknown event \"" + type + "\"!");
            }
            EventCard card = cardMaps.get(type).get();
            card.load(scan);
            out.add(card);
        }

        return out;
    }
}
