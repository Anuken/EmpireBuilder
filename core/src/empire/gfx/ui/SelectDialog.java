package empire.gfx.ui;

import io.anuke.arc.function.Consumer;
import io.anuke.arc.scene.ui.Dialog;

public class SelectDialog{

    public static void show(Consumer<Dialog> cons){
        Dialog dialog = new Dialog("","dialog");
        cons.accept(dialog);
        dialog.buttons.addButton("Cancel", dialog::show).size(180f, 50f);
        dialog.show();
    }
}
