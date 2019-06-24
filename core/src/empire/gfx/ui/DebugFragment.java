package empire.gfx.ui;

import empire.game.World.Tile;
import empire.io.SaveIO;
import io.anuke.arc.Core;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.util.Align;

import static empire.gfx.EmpireCore.*;

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

        if(snapshotView){
            String[] name = {"snapshots"};
            group.fill(t -> {
                t.bottom().left();
                t.addField(name[0], text -> name[0] = text).colspan(3).height(50f).width(250f);
                t.row();
                t.addButton("<", () -> {
                    try{
                        SaveIO.load(state, Core.files.local(name[0]).child("turn-" + (state.turn - 1) + ".json"));
                    }catch(Exception e){
                        ui.showFade(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }).size(50f);

                t.table("button", f -> f.label(() -> "Turn " + state.turn)).width(150f).height(50f);

                t.addButton(">", () -> {
                    try{
                        SaveIO.load(state, Core.files.local(name[0]).child("turn-" + (state.turn + 1) + ".json"));
                    }catch(Exception e){
                        ui.showFade(e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }).size(50f);
            });
        }
    }
}
