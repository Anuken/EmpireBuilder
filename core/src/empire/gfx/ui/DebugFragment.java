package empire.gfx.ui;

import empire.game.World.Tile;
import io.anuke.arc.Core;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.util.Align;

import static empire.gfx.EmpireCore.control;

public class DebugFragment{

    public void build(Group group){
        group.addChild(new Label(""){{
            touchable(Touchable.disabled);
            update(() -> {
                setText("");
                Tile tile = control.tileMouse();
                if(tile != null){
                    setText(tile.x + "," + tile.y);
                }
                pack();
                setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.bottomRight);
            });
        }});
    }
}
