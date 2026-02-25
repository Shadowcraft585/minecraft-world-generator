# minecraft-world-generator

Step-by-step guide to run world generator

1. Clone repo
2. Run "gradle runServer" inside the world-generator directory to create run directory
3. Copy config.xml from "world-generator/src/main/resources" into the newly created "run" directory
4. Run "gradle runServer" once agin
5. Wait for the console to say "World-Generator-Plugin Created world 'custom_world' with CustomChunkGenerator."
6. Start Minecraft
7. Go to Multiplayer
8. Type in "localhost" in direct connection and connect

Currently stopping server and restarting does not work. So to play this again, you will need to delete the "custom_world" directory in the run directory and then run "gradle runServer" again

To generate a specified location, change the "coordinates" variable in the "config.xml" in the run directory.
How to choose correct coordinates:
1. select any coordinate. (for example 50.631373, 10.919207)
2. select another coordinate, this coordinate has to be to the left and above the previous coordinate. (for example 50.634721, 10.923499)
3. put them into the coordinates variable in the "config.xml" in the run directory.
4. remove all Spaces in between (for example <coordinates>50.631373,10.919207,50.634721,10.923499</coordinates>

Dont seperate the two coordinates by more than a few kilometers, otherwise the server might take a long time or run out of heap space

Variables in the config.xml can be changed however you want, but they have to remain in the correct format

