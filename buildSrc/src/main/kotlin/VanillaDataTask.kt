import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.util.zip.ZipInputStream

/** Downloads the vanilla server jar and extracts its `data/` directory (recipes, loot tables, worldgen, tags). */
abstract class VanillaDataTask : DefaultTask() {
    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun extract() {
        val version = minecraftVersion.get()
        val manifest = readJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        val versionUrl =
            (manifest["versions"] as List<*>)
                .map { it as Map<*, *> }
                .firstOrNull { it["id"] == version }
                ?.get("url") as String?
                ?: error("Minecraft $version not found in the version manifest")
        val downloads = readJson(versionUrl)["downloads"] as Map<*, *>
        val serverUrl = (downloads["server"] as Map<*, *>)["url"] as String

        val output =
            outputDirectory
                .get()
                .asFile
                .toPath()
                .normalize()
        output.toFile().deleteRecursively()

        // The downloaded jar is a bundler; the real server jar (with data/) is nested inside it.
        val serverJarEntry = "META-INF/versions/$version/server-$version.jar"
        ZipInputStream(URI(serverUrl).toURL().openStream()).use { bundler ->
            generateSequence { bundler.nextEntry }.first { it.name == serverJarEntry }
            val server = ZipInputStream(bundler)
            for (entry in generateSequence { server.nextEntry }) {
                if (entry.isDirectory || !entry.name.startsWith("data/")) continue
                val target = output.resolve(entry.name).normalize()
                check(target.startsWith(output)) { "Unexpected entry ${entry.name}" }
                target.parent.toFile().mkdirs()
                target.toFile().outputStream().use { server.copyTo(it) }
            }
        }
    }

    private fun readJson(url: String) = URI(url).toURL().openStream().use { JsonSlurper().parse(it) } as Map<*, *>
}
