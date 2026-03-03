# minecraft-world-generator

Step-by-step guide to run world generator

1. Clone repo
2. Run "./gradlew runServer" inside the world-generator directory to create run directory
   - the first start will take 5-10 minutes
   - it will fail, i just needs to create the run directory
4. open the eula.txt in the run directory and change "eula=false" to "eula=true"
5. Copy config.xml from "world-generator/src/main/resources" into the newly created "run" directory
6. Run "gradle runServer" once again
7. Wait for the console to say "World-Generator-Plugin Created world 'custom_world' with CustomChunkGenerator."
8. Start Minecraft (Version 1.21.4 required)
9. Go to Multiplayer
10. Type in "localhost" in direct connection and connect

Currently stopping server and restarting does not work. So to play this again, you will need to delete the "custom_world" directory in the run directory and then run "gradle runServer" again

To generate a specified location, change the "coordinates" variable in the "config.xml" in the run directory.
How to choose correct coordinates:
1. select any coordinate. (for example 50.631373, 10.919207)
2. select another coordinate, this coordinate has to be to the left and above the previous coordinate. (for example 50.634721, 10.923499)
3. put them into the coordinates variable in the "config.xml" in the run directory.
4. remove all Spaces in between (for example <coordinates>50.631373,10.919207,50.634721,10.923499</coordinates>

Don't seperate the two coordinates by more than a few kilometers, otherwise the server might take a long time or run out of heap space

Variables in the config.xml can be changed however you want, but they have to remain in the correct format

