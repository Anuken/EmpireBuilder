# EmpireBuilder

This is the repository for the 2019 SSRP; making an AI for Eurorails.

This project uses Gradle as its build system. To run anything, you need the Java 8 JDK installed.

# Building

To run:
`gradlew run`

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