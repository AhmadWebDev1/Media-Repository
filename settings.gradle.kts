include(":core")

// Load all modules under /lib
File(rootDir, "lib").eachDir {
    val libName = it.name
    include(":lib-$libName")
    project(":lib-$libName").projectDir = File("lib/$libName")
}

if (System.getenv("CI") == null) {
    // Local development (full project build)

    /**
     * Add or remove modules to load as needed for local development here.
     */
    loadAllIndividualExtensions()
    // loadIndividualExtension("all", "komga")
} else {
    // Running in CI (GitHub Actions)

    loadAllIndividualExtensions()
}

fun loadAllIndividualExtensions() {
    File(rootDir, "src").eachDir { dir1 ->
        dir1.eachDir { dir2 ->
            dir2.eachDir { subdir ->
                val name = ":extensions:individual:${dir1.name}:${dir2.name}:${subdir.name}"
                include(name)
                project(name).projectDir = File("src/${dir1.name}/${dir2.name}/${subdir.name}")
            }
        }
    }
}

fun loadIndividualExtension(lang: String, theme: String, name: String) {
    val projectName = ":extensions:individual:$lang:$theme:$name"
    include(projectName)
    project(projectName).projectDir = File("src/${lang}/${theme}/${name}")
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
