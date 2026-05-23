def cl = new java.net.URLClassLoader([new File("build/classes/java/client"), new File("build/classes/java/main")].collect { it.toURI().toURL() } as java.net.URL[], this.class.classLoader)
def c = cl.loadClass("com.teoe.wdl.TestCompile")
c.newInstance().dump()
