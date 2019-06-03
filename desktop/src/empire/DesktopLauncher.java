package empire;

import empire.gfx.EmpireCore;
import io.anuke.arc.backends.lwjgl3.Lwjgl3Application;
import io.anuke.arc.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Eurorails");
		config.setMaximized(true);

		try{
			new Lwjgl3Application(new EmpireCore(), config);
			EmpireCore.net.close();
		}catch(Throwable t){
			t.printStackTrace();
			EmpireCore.net.close();
		}
	}
}
