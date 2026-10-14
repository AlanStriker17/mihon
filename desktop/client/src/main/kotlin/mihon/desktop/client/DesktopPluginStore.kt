package mihon.desktop.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.util.jar.JarFile

class DesktopPluginStore(
    private val pluginDirectory: Path = Path.of(System.getProperty("user.home"), ".mihon", "plugins"),
) {
    fun installedPlugins(): List<Path> {
        Files.createDirectories(pluginDirectory)
        return Files.list(pluginDirectory).use { paths ->
            paths.filter {
                Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                    it.fileName.toString().endsWith(".jar", ignoreCase = true)
            }.sorted().toList()
        }
    }

    fun install(source: Path): Path {
        require(source.fileName.toString().endsWith(".jar", ignoreCase = true)) {
            "Select a desktop plugin JAR file"
        }
        require(Files.isRegularFile(source)) { "The selected plugin file does not exist" }
        require(Files.size(source) in 1..MAX_PLUGIN_SIZE_BYTES) {
            "Plugin JAR must be between 1 byte and 100 MB"
        }
        JarFile(source.toFile()).use { jar ->
            require(jar.getJarEntry(SERVICE_PROVIDER_PATH) != null) {
                "The JAR does not declare a desktop source plugin provider"
            }
        }

        Files.createDirectories(pluginDirectory)
        val destination = pluginDirectory.resolve(source.fileName.toString()).normalize()
        require(destination.parent == pluginDirectory.normalize()) { "Invalid plugin file name" }
        if (source.toAbsolutePath().normalize() != destination.toAbsolutePath().normalize()) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
        return destination
    }

    fun remove(plugin: Path) {
        require(plugin.fileName.toString().endsWith(".jar", ignoreCase = true)) {
            "Invalid desktop plugin file"
        }
        val destination = pluginDirectory.resolve(plugin.fileName.toString()).normalize()
        require(destination.parent == pluginDirectory.normalize()) { "Invalid plugin file name" }
        Files.deleteIfExists(destination)
    }

    private companion object {
        const val MAX_PLUGIN_SIZE_BYTES = 100L * 1024 * 1024
        const val SERVICE_PROVIDER_PATH = "META-INF/services/mihon.desktop.source.SourcePluginProvider"
    }
}
