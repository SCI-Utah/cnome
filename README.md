# Cyclist

Cyclist is a visual interface companion for the Cyclus project. It provides an interactive workbench for investigating simulation results created by Cyclus. Cyclist is written in Java 8 and JavaFX. It can fetch data from MySQL and SQLite databases. 

## Installation

### Dependencies:
| package | version | download
| ------- | ------- | --------
| Java    | 8.0_25  | http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html
| JavaFX  | 8.0     | (JavaFX is part of Java 8)
| Ant     | 1.9+    | http://ant.apache.org/bindownload.cgi

#### Getting Java8 on Ubuntu

```
sudo add-apt-repository ppa:webupd8team/java
sudo apt-get update
sudo apt-get install oracle-java8-installer
```

### Deployment

Cyclist can be deployed as a jar file with an associated library directory or as a standalone application that includes 
the Java 8 VM.

#### Jar 
 
Run `ant` in the top directory. This will build cyclist.jar and a library directory in deploy/dist. You can copy the dist 
directory to any other machine (Mac, Linux, Windows) that has Java 8 installed.

To run Cyclist, you can use `java -jar Cyclist.jar` or double click on the jar. On OSX you can also use `open cyclist.jar`.

#### Standalone application

Run `ant app` in the top directory. In addition to the dist directory above it will also create a standalone application 
specific to the current platform under deploy/bundles:

| Platform | app
| ---------| ----
| OSX| Cyclist.app, Cyclist.dmg
| Windows | Cyclist.exe
| Linux   | Cyclist.rpm

Note that the standalone Cyclist application includes the Java 8 VM. The advantage is that it does not depends on the 
particular Java version (if any) that is installed on the target machine. The disadvantage is that the Java 8 VM is huge 
and the size of the application will be rather large. On a OSX, Cyclist.app is 264MB and Cyclist.dmg is 97MB.

## Running 

### The short version

#### Use `Data/Simulation` from the menubar to select one or more simulations.
1 Add a new database in the popup wizard
2 Connect to the database
3 select a simulation. Optionally add a short alias

#### Select a simulation
DnD a simulation id to the header of the Workspace. All the views in the workspace will switch to this simulation. 
Multiple simulations can be added to the workspace. In general the workspace will propagate only one simulation to all
its views but you can DnD a simulation to any view. A view that was directed to the specific simulation it will
stay with that simulation and will _not_ follow the workspace's selected simulation.

#### Selecting a View
You can either DnD a view from a Views panel or select one from the Views dropdown menu.

#### Table View
DnD a table from the Tables Panel into a table view. As a shortcut you can DnD a table onto the workspace and Cyclist will 
open a new Table View automatically.

#### Flow View
TBD

#### Inventory View
TBD

#### Chart View
TBD

#### Using your own tables
Cyclist is aware of a specific collection of tables that Cyclus dumps at the end of a simulation run. 
You can add additional tables using `Data/datatable`. 



