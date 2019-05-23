package empire.gfx;

import io.anuke.arc.ApplicationListener;
import io.anuke.arc.Core;
import io.anuke.arc.input.KeyCode;

/** Handles user input.*/
public class Control implements ApplicationListener{
    public boolean isPlacingLines;

    @Override
    public void update(){

        if(Core.input.keyTap(KeyCode.S)){

        }
    }
}
