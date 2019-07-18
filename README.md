# EmpireBuilder

This is the repository for the 2019 SSRP; making an AI for Eurorails.

This project uses Gradle as its build system. To run anything, you need the Java 8 JDK installed.

# Running

Running the game can be done with `gradlew desktop:run`. This launches the game with no options.
Note that there is no AI in this mode.

### Running Options

To enable an option, add it after the run command. For example, running
`gradlew desktop:run -Pdebug`
enables debug mode.

|Option|Description|
|-Pai|Activates AI control.|
|-Pdebug|Activates some debug features, as well as hosting the game on startup.|
|-Pseeded|Runs the game on the same seed (card order).|
|-Psnapshots|Enables snapshot controls. Snapshots are saved in the `assets` folder|

# Building

To build an output jar file in `build/libs/desktop-1.0.jar`:
`gradlew desktop:dist`

# Directory Structure

Core source code is located in `core/src/empire`.
- Code for the AI is in the `ai` package. `CurrentAI` is the core AI class.
- Code for the game itself is located in `game`. `State` handles game state and has most of the control methods.
- Code for graphical effects and rendering is in `gfx`.
- Code for networking is in `net`, and code for reading data input files is in `io`.

All input files and other assets are in `core/assets/`. Raw unpacked files are available in `core/assets-raw/` if you need them.
Run `./gradlew pack` to pack these sprites into a spritesheet.

# Map/Deck Data

Data for the map is in `core/assets/maps/eurorails.txt`. Text data for the deck is in `core/assets/maps/deck.txt`.
While the card data should be self explanatory, you can see how it is parse in `core/src/empire/io/CardIO.java`.
Code for parsing the map can be seen in `core/src/empire/io/MapIO.java`.

# The AI

All the code for controlling AI is located in `core/src/empire/ai/CurrentAI.java`.
While most of the code should already be documented, there are a few methods of special importance that may need to be explained:

- `#act()` runs every frame, and is responsible for updating all the AI's systems and performing moves.
    - If there is currently no plan and nothing being calculated, the AI launches a task to update the plan **in an new thread**.
    - If a plan is currently being calculated, it does nothing.
    - If a plan is available, the AI attempts to execute it.
- `#executePlan()` handles the actual execution of the plan, going through each action and attempting to perform it.
    - This handles upgrading the locomotive, linking cities, moving, placing track, loading/unloading, etc.
    - Actions outside the plan may also be executed (e.g. loading up random cargo just in case it happens to get a card for it).
- `#updatePlan()` is a blocking method to find the best plan and set it up.
    - This method goes through all combinations of plans, then substitutes them for combinations of actions (load/unload).
    - Each plan is evaluated and the best one (smallest cost) is selected.
- `Plan#cost()` evaluates the cost of a plan.
    - This is done by adding up the total money spent placing track, multiplying it by some number and subtracting the total profit * some multiplier
    - The "profit multiplier" is `CurrentAI#demandCostScale`.
- Cost of placing track between locations is calculated in `ai/Astar.java`.
    - This is measured in movement points.
    - Cost of moving on already-placed track is 1 movement point
    - Cost of placing track/spending money is `1 + (cost) * multiplier`, where `multiplier` is defined in `Astar#costScale()` - currently 6.
- To get the final cost of a plan, the total profit multiplied by the profit multiplier is subtracted from the A* path cost.
    - The plan of least cost is picked.