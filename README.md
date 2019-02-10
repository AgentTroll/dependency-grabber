# `dependency-grabber`

This was originally intended to be a server-client type deal
where the plugin would grab dependencies from a server
daemon, but apparently that didn't work out. I ended up
using ASM to modify the server class in order to filter out
specific plugins instead, but the name stuck. This project
should really be named "dependency-filter."

From my time working at Intermissum, one of the things that
was especially irritating was how long it took for the
server to boot. This made testing plugins a pretty
excruciating experience, and I felt that if I could avoid
gunking up my server with plugins that I didn't necessarily
need for testing this one specific feature, it would go a
long way to improving my productivity. I could also have
invested in a new CPU, and this whole debacle might not be
as significant of a problem, but whatever.

# Design

This is a Java agent that attaches to the server before
startup and transforms the `CraftServer` class. It will scan
the plugins directory and build a list of plugins 
considering the list of inclusions and exclusions. It will
then use ASM to generate an `if` chain that skips all
plugins except for those that are present in the list. Then,
the class will be instrumented before the server starts.

# Building

``` shell
git clone https://github.com/AgentTroll/dependency-grabber.git
cd dependency-grabber
mvn clean install
```

The jar will be located in the `target` directory.

# Usage

Either build the jar yourself or download from the releases
page. Copy the jar into the server's root directory, where
the Spigot jar is located.

You must use the following to start your server:

``` shell
java -DdgIncludes=<PLUGIN_NAMES> -cp <JAR_NAME>:<TOOLS_JAR> -javaagent:<DEPENDENCY_GRABBER_JAR_NAME> org.bukkit.craftbukkit.Main
```

While the `-DdgIncludes` is optional, it will help build the
list of required plugins if you need to test a specific
plugin. It is a comma separated list, so you can use `Essentials,WorldEdit`
to load those two plugins as well as their dependencies.

The `JAR_NAME` is just the name of the Spigot jar file. It
is normally `spigot-1.8.8.jar` for a raw BuildTools jar.

The `TOOLS_JAR` is the path to your tools.jar. It is
included with Oracle's JDK in the `lib` directory.

The `DEPENDENCY_GRABBER_JAR_NAME` is the name of the
jarfile, by default, `dependencygrabber.jar`.

# Demo

``` shell
agenttroll@agenttroll:~/server$ cp ~/IdeaProjects/dependency-grabber/target/dependencygrabber.jar . && java -DdgIncludes=Essentials,Multiverse-Core -cp spigot-1.8.8.jar:/home/agenttroll/.sdkman/candidates/java/8.0.181-oracle/lib/tools.jar -javaagent:dependencygrabber.jar org.bukkit.craftbukkit.Main
Feb 10, 2019 3:47:56 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Loaded config.
Feb 10, 2019 3:47:56 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Includes: []
Feb 10, 2019 3:47:56 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Excludes: []
Feb 10, 2019 3:47:56 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Soft depends? true
Feb 10, 2019 3:47:56 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Additional includes: [Essentials, Multiverse-Core]
Feb 10, 2019 3:47:57 PM com.gmail.woodyc40.dependencygrabber.DependencyGrabber grabDependencies
INFO: Final load set: [Essentials, Vault, Multiverse-Core]
Feb 10, 2019 3:47:57 PM com.gmail.woodyc40.dependencygrabber.Transformer transform
INFO: Redefining class org.bukkit.craftbukkit.v1_8_R3.CraftServer...
Feb 10, 2019 3:47:57 PM com.gmail.woodyc40.dependencygrabber.Transformer$1 visitMethod
INFO: Detected loadPlugins()
Feb 10, 2019 3:47:57 PM com.gmail.woodyc40.dependencygrabber.Transformer$1 visitMethod
INFO: Detected enablePlugins()
Feb 10, 2019 3:47:57 PM com.gmail.woodyc40.dependencygrabber.Transformer transform
INFO: Redefinition complete.
Loading libraries, please wait...
[15:48:01 INFO]: Starting minecraft server version 1.8.8
```

# Notes

* This only works with Spigot 1.8.8. PaperSpigot is NOT
compatible
* You should avoid running this on a production server. This
is designed to be a development tool only.
* This is NOT a plugin, this is a Java agent

# Credits

Built with [IntellIJ IDEA](https://www.jetbrains.com/idea/)

Uses [ASM](https://asm.ow2.io/)

Some code from the [PaperSpigot](https://papermc.io/) source 
has been used to build the plugin filter