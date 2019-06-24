package empire;

import empire.gfx.EmpireCore;
import io.anuke.arc.backends.lwjgl3.*;
import io.anuke.arc.collection.Array;

public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Eurorails");
		config.setMaximized(true);

		try{
			Array<String> args = Array.with(arg);
			EmpireCore.isAI = args.contains("-ai");
			EmpireCore.testEfficiency = args.contains("-test");
			EmpireCore.debug = args.contains("-debug");
			EmpireCore.seeded = args.contains("-seeded");
			EmpireCore.snapshots = args.contains("-snapshots");
			EmpireCore.snapshotView = args.contains("-snapshotview");

			new Lwjgl3Application(new EmpireCore(), config);
			EmpireCore.net.close();
		}catch(Throwable t){
			t.printStackTrace();
			EmpireCore.net.close();
		}
	}
}
