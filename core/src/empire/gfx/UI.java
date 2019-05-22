package empire.gfx;

import empire.game.World.Tile;
import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.freetype.FreeTypeFontGenerator;
import io.anuke.arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import io.anuke.arc.graphics.g2d.SpriteBatch;
import io.anuke.arc.graphics.g2d.TextureAtlas;
import io.anuke.arc.scene.Scene;
import io.anuke.arc.scene.Skin;
import io.anuke.arc.scene.ui.Label;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.Align;

import static empire.gfx.EmpireCore.renderer;

/** Handles all overlaid UI for the game. */
public class UI implements ApplicationListener{

    public UI(){
        Skin skin = new Skin(Core.atlas = new TextureAtlas("ui/uiskin.atlas"));
        generateFonts(skin);
        skin.load(Core.files.internal("ui/uiskin.json"));

        Core.batch = new SpriteBatch();
        Core.scene = new Scene(skin);
        Core.input.addProcessor(Core.scene);
    }

    /** Generates bitmap fonts based on screen size.*/
    void generateFonts(Skin skin){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Core.files.internal("ui/expressway.ttf"));
        FreeTypeFontParameter param = new FreeTypeFontParameter();
        param.size = (int)(20 * Math.max(Unit.dp.scl(1f), 0.5f));
        param.borderWidth = 1f;
        param.spaceX = -1;

        skin.add("default", generator.generateFont(param));
        skin.getFont("default").getData().markupEnabled = true;
    }

    @Override
    public void init(){
        Core.scene.add(new Label(""){{
            update(() -> {
                Tile on = renderer.tileWorld(Core.input.mouseWorld().x, Core.input.mouseWorld().y);

                if(on != null && on.city != null){
                    setText(on.city.name + "\n[LIGHT_GRAY]" +
                            on.city.goods.toString("\n").replace("[", "").replace("]", ""));
                }else{
                    setText("");
                }

                pack();

                setPosition(Core.input.mouseX(), Core.input.mouseY(), Align.topRight);
            });
        }});
    }

    @Override
    public void update(){
        Core.scene.act();
        Core.scene.draw();
    }
}
