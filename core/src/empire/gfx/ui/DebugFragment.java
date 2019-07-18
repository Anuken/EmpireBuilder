package empire.gfx.ui;

import empire.ai.*;
import empire.game.World.Tile;
import empire.io.SaveIO;
import io.anuke.arc.Core;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.util.Align;

import static empire.gfx.EmpireCore.*;

public class DebugFragment{
    boolean calculating = false;

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
                t.row();
                t.addButton("Make Plan", () -> {
                    CurrentAI ai = new CurrentAI(state.player(), state);
                    calculating = true;
                    ai.previewPlan(str -> {
                        calculating = false;
                        ui.showDialog("Plan", d ->
                                d.cont.add(str
                                        .replace("{", "[coral]{")
                                        .replace("}", "}[]")
                                ).get().setAlignment(Align.left, Align.left));
                    });
                }).colspan(3).height(50f).width(250f).disabled(b ->  calculating);
            });

            group.fill(t -> {
                t.visible(() -> calculating);
                t.touchable(Touchable.disabled);
                t.table("dialogDim", i -> i.add("Calculating plan preview...").pad(10f));
            });
        }
    }
}
