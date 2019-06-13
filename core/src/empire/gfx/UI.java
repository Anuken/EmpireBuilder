package empire.gfx;

import empire.game.GameEvents.EventEvent;
import empire.game.GameEvents.WinEvent;
import empire.game.State;
import empire.gfx.ui.*;
import empire.net.Net;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.Events;
import io.anuke.arc.freetype.FreeTypeFontGenerator;
import io.anuke.arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import io.anuke.arc.freetype.FreeTypeFontGenerator.Hinting;
import io.anuke.arc.function.Consumer;
import io.anuke.arc.graphics.g2d.BitmapFont;
import io.anuke.arc.math.Interpolation;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.Scene;
import io.anuke.arc.scene.Skin;
import io.anuke.arc.scene.actions.Actions;
import io.anuke.arc.scene.ui.Dialog;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.Strings;
import io.anuke.arc.util.Time;

import static empire.gfx.EmpireCore.*;

/** Handles all overlaid UI for the game. */
public class UI implements ApplicationListener{
    public ChatFragment chat;
    public HudFragment hud;
    public ConnectFragment connect;

    public UI(){
        Skin skin = new Skin(Core.atlas);
        generateFonts(skin);
        skin.load(Core.files.internal("ui/uiskin.json"));

        Core.scene = new Scene(skin);
        Core.input.addProcessor(Core.scene);

        Events.on(WinEvent.class, event -> {
            ui.showDialog("[coral]" +event.player.name + " is victorious in " + state.turn + " turns!", dialog -> {
                dialog.cont.add(event.player.name + " has won the game, as they have\nconnected 7 major cities and gotten " + State.winMoneyAmount + " ECU!");
            });
        });

        Events.on(EventEvent.class, event -> {
            new EventDialog().show(event.card);
        });
    }

    /** Generates bitmap fonts based on screen size.*/
    void generateFonts(Skin skin){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Core.files.internal("ui/prose.ttf"));
        FreeTypeFontParameter param = new FreeTypeFontParameter();
        param.size = (int)(16 * Math.max(Unit.dp.scl(1f), 0.5f));
        param.gamma = 0f;
        param.hinting = Hinting.None;
        param.borderGamma = 0;
        param.borderWidth = 1f;
        param.spaceX = -1;

        BitmapFont font = generator.generateFont(param);
        font.getData().setScale(2f);
        font.getData().down += 11f;

        skin.add("default", font);
        skin.getFont("default").getData().markupEnabled = true;

        skin.add("chat", generator.generateFont(param));
        skin.getFont("chat").getData().markupEnabled = false;

        font = skin.getFont("chat");

        font.getData().setScale(2f);
        font.getData().down += 11f;
    }

    @Override
    public void init(){
        net.setErrorHandler(ex -> {
            net.close();

            if(ex.getMessage() != null && ex.getMessage().contains("Address already in use")){
                showDialog("[scarlet]Error", t -> t.cont.add("[coral]Port " + Net.port + " is already in use.\n[]Stop any other servers on the network."));
            }else{
                showDialog("[scarlet]Error", t -> Strings.parseException(ex, true));
            }
        });

        Group root = Core.scene.root;

        (hud = new HudFragment()).build(root);
        (chat = new ChatFragment()).build(root);
        (connect = new ConnectFragment()).build(root);
        if(debug) new DebugFragment().build(root);
    }

    @Override
    public void update(){
        Core.scene.act();
        Core.scene.draw();
        Time.update();
    }

    @Override
    public void resize(int width, int height){
        Core.scene.resize(width, height);
    }

    public void showFade(String text){
        Label label = new Label(text);
        Core.scene.table(t -> {
            t.add(label).padTop(200f);
            t.actions(Actions.fadeOut(2f, Interpolation.swingOut), Actions.remove());
        });
    }

    public void showDialog(String title, Consumer<Dialog> cons){
        Dialog dialog = new Dialog(title,"dialog");
        cons.accept(dialog);
        dialog.buttons.addButton("Close", dialog::hide).size(180f, 50f);
        dialog.show();
    }
}
