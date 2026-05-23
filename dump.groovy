def cl = new java.net.URLClassLoader([new File("build/classes/java/client"), new File("build/classes/java/main"), new File(System.getProperty("user.home") + "/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.8-net.fabricmc.yarn.1_21_8.1.21.8+build.1-v2/minecraft-merged-1.21.8-net.fabricmc.yarn.1_21_8.1.21.8+build.1-v2.jar")].collect { it.toURI().toURL() } as java.net.URL[], this.class.classLoader)
def c = cl.loadClass("net.minecraft.entity.Entity")
println c.getDeclaredMethods().findAll { it.name.contains("Data") }
