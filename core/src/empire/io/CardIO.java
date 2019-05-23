package empire.io;

import empire.game.DemandCard;
import empire.game.DemandCard.Demand;
import empire.game.World;
import io.anuke.arc.collection.Array;
import io.anuke.arc.files.FileHandle;

import java.util.Scanner;

public class CardIO{

    /** Loads a deck of cards. Uses a world to look up cities by name.*/
    public static Array<DemandCard> loadCards(World world, FileHandle file){
        Scanner scan = new Scanner(file.read(1024));
        Array<DemandCard> out = new Array<>();

        while(scan.hasNext()){
            //card number, not useful here
            String num = scan.next();
            Demand[] demands = new Demand[3];
            for(int i = 0; i < 3; i++){
                String city = scan.next().toLowerCase();
                if(world.getCity(city) == null){
                    throw new IllegalArgumentException("No city with name " + city + " found in card #" + num);
                }

                String good = scan.next().toLowerCase();
                int profit = scan.nextInt();
                demands[i] = new Demand(good, world.getCity(city), profit);
            }

            out.add(new DemandCard(demands));
        }

        return out;
    }
}
