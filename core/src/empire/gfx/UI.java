package empire.gfx;

import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.freetype.FreeTypeFontGenerator;
import io.anuke.arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import io.anuke.arc.graphics.g2d.SpriteBatch;
import io.anuke.arc.graphics.g2d.TextureAtlas;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.scene.Scene;
import io.anuke.arc.scene.Skin;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.util.Log;

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

    @Override
    public void init(){
        Core.scene.table(t -> t.touchable(Touchable.enabled)).dragged((x, y) -> {
            Core.camera.position.sub(x, y);
        });
    }

    /** Generates bitmap fonts based on screen size.*/
    void generateFonts(Skin skin){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Core.files.internal("ui/expressway.ttf"));
        FreeTypeFontParameter param = new FreeTypeFontParameter();
        param.size = (int)(20 * Math.max(Unit.dp.scl(1f), 0.5f));

        skin.add("default", generator.generateFont(param));
        skin.getFont("default").getData().markupEnabled = true;
    }

    @Override
    public void update(){
        Core.scene.act();
        Core.scene.draw();
    }
}
