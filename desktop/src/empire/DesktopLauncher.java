package empire;

import empire.gfx.EmpireCore;
import io.anuke.arc.backends.lwjgl3.Lwjgl3Application;
import io.anuke.arc.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("EmpireBuilderRenderer");
		config.setMaximized(true);
		//config.setBackBufferConfig( 8, 8, 8, 8, 0, 0, 8);
		new Lwjgl3Application(new EmpireCore(), config);
	}
}
