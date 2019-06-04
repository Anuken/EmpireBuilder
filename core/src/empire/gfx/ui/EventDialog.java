package empire.gfx.ui;

import empire.game.EventCard;
import empire.gfx.EmpireCore;
import io.anuke.arc.scene.ui.Dialog;

public class EventDialog extends Dialog{

    public EventDialog(){
        super("");
    }

    public void show(EventCard card){
        if(EmpireCore.debug) return;

        title.setText("Event:[coral] " + card.name() + "!");
        cont.clearChildren();
        buttons.clearChildren();

        cont.add(card.description(EmpireCore.state.player())).width(400f).wrap().pad(15f);

        buttons.addButton("OK", this::hide).size(100f, 60f);

        show();
    }
}
