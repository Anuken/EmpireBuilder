package empire.gfx.ui;

import empire.game.Actions.Connect;
import empire.game.Player;
import empire.gfx.EmpireCore;
import io.anuke.arc.Application.ApplicationType;
import io.anuke.arc.Core;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.Colors;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Dialog;
import io.anuke.arc.scene.ui.TextButton;

import static empire.gfx.EmpireCore.*;

public class ConnectFragment{

    public void build(Group group){
        //connect info
        group.fill("dialogDim", t -> {
            String[] host = {"206.21.122.239"};

            Connect connect = new Connect();
            connect.name = System.getProperty("user.name");
            connect.color = new Color().rand();
            t.visible(() -> !EmpireCore.net.active());
            t.touchable(Touchable.enabled);

            t.table("button", c -> {
                c.margin(15f);
                c.left().defaults().left();
                c.add("[coral]Connect").colspan(2);
                c.row();
                c.add("Host: ").padRight(5f);
                c.addField(host[0], name -> host[0] = name).size(230f, 50f);
                c.row();
                c.add("Name: ").padRight(5f);
                c.addField(connect.name, name -> connect.name = name).size(230f, 50f);
                c.row();
                c.add("Color: ").padRight(5f);
                c.table(colors -> {
                    colors.left();
                    colors.table("button", p -> p.addImage("white")
                            .update(i -> i.setColor(connect.color)).size(30f)).size(50f);
                    colors.addButton("Pick...", () -> {
                        Dialog dialog = new Dialog("Colors");
                        ObjectSet<Color> seen = new ObjectSet<>();
                        int i = 0;
                        for(String key : Colors.getColors().orderedKeys()){
                            Color color = Colors.getColors().get(key);
                            if(color == Color.BLACK || color == Color.CLEAR || seen.contains(color)) continue;
                            seen.add(color);
                            dialog.cont.addImageButton("white", 30f, () -> {
                                connect.color.set(color);
                                dialog.hide();
                            }).size(50f).get().getImage().setColor(color);
                            if(++i % 6 == 0){
                                dialog.cont.row();
                            }
                        }

                        dialog.show();
                    }).grow();
                }).height(50f).fillX();

                c.row();

                c.table(buttons -> {
                    buttons.addImageTextButton("Connect", "icon-plus", 16*2, () -> {
                        actions.beginConnect(connect, host[0], () -> {
                            ui.showFade("Connected!");
                        }, e -> {
                            e.printStackTrace();
                            ui.showDialog("[scarlet]Error", re -> re.cont.add(e.getMessage()));
                        });
                    }).growX().height(50f);

                    if(Core.app.getType() != ApplicationType.WebGL){
                        TextButton b = buttons.addImageTextButton("Host", "icon-home", 14 * 2, () -> {
                            Player player = state.players.first();
                            player.local = true;
                            player.name = connect.name;
                            player.color = connect.color;
                            net.host();
                            ui.hud.refresh();
                        }).growX().height(50f).get();

                        if(debug){
                            b.fireClick();
                        }
                    }
                }).growX().padTop(20f).height(50f).colspan(2);

            });
        });
    }
}
