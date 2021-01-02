import org.w3c.dom.HTMLFormElement
import org.w3c.files.Blob
import org.w3c.xhr.FormData
import kotlin.js.Date
import kotlin.js.Promise

class JambaPlugin(val name: String, val tokens: Map<String, String>)

/**
 * The zip file that gets generated with the content of the blank plugin */
class JambaPluginZip(val filename: String, val content: Blob)

/**
 * Maintains the information for each file in the zip archive. Will use permission and date to generate the
 * outcome archive.
 */
class JambaPluginFile(val relativePath: String, val date: Date?, val unixPermissions: Int?, val content: String)

class FileTreeEntry(val content: String, val file: JambaPluginFile)

/**
 * A file tree is a map indexed by the full path to the file (ex: `src/cpp/Plugin.h`) */
typealias FileTree = Map<String, FileTreeEntry>

/**
 * Caches the files in memory */
class JambaPluginMgr(
    val jambaGitHash: String,
    val files: Array<out JambaPluginFile>,
    val UUIDGenerator: () -> String
) {
    companion object {

        /**
         * Main API to create [REMgr]. Loads the `plugin-<version>.zip` file hence it returns a promise.*/
        fun load(version: String): Promise<JambaPluginMgr> =
            fetchBlob("assets/jamba-blank-plugin-$version.zip").then { blob ->
                // This code is taken from the gist https://gist.github.com/jed/982883
                val UUIDv4: dynamic =
                    js("function (a){return a?(a^crypto.getRandomValues(new Uint8Array(1))[0]%16>>a/4).toString(16):([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,UUIDv4)}")

                val zip = JSZip()

                zip.loadAsync(blob).then {

                    val promises = mutableListOf<Promise<JambaPluginFile>>()

                    zip.forEach { path, file ->
                        val relativePath = path.substringAfter("blank-plugin/")
                        if (!(relativePath.startsWith("__MACOSX") ||
                                    relativePath.startsWith(".idea") ||
                                    relativePath.endsWith(".DS_Store"))
                        ) {
                            val p = file.async("string").then { content ->
                                JambaPluginFile(relativePath, file.date, file.unixPermissions, content.toString())
                            }
                            promises.add(p)
                        }
                    }

                    Promise.all(promises.toTypedArray()).then { array ->
                        JambaPluginMgr(version, array, UUIDv4)
                    }
                }.flatten()
            }.flatten()
    }

    /**
     * Number of files that make the plugin
     */
    val fileCount: Int get() = files.size

    /**
     * Generate a UUID as a C notation
     */
    fun generateUUID(): String {
        val hex = UUIDGenerator().replace("-", "")
        return "0x${hex.substring(0..7)}, 0x${hex.substring(8..15)}, 0x${hex.substring(16..23)}, 0x${hex.substring(24..31)}"
    }

    fun createJambaPlugin(form: HTMLFormElement): JambaPlugin {
        val data = FormData(form)
        val params = data.keys().asSequence().associateBy({ it }, { e -> data.get(e).toString() })

        val name = params["name"] ?: throw Exception("Plugin name must be provided")

        // Converts a string to a boolean
        fun convertToBoolean(s: String?): Boolean {
            if (s == null)
                return false

            val ts = s.toLowerCase()

            return !(ts == "false" || ts == "no" || ts == "off")
        }


        val newTokens = params.toMutableMap()

        val setToken = { key: String, value: String ->
            newTokens.getOrPut(key, { value })
        }

        val setBooleanToken = { key: String ->
            newTokens[key] = if (convertToBoolean(newTokens[key])) "ON" else "OFF"
        }

        val pluginName = setToken("name", "Plugin")
        setToken("jamba_git_hash", jambaGitHash)

        setToken("processor_uuid", generateUUID())
        setToken("controller_uuid", generateUUID())
        setToken("debug_processor_uuid", generateUUID())
        setToken("debug_controller_uuid", generateUUID())
        setToken("year", Date().getFullYear().toString())
        setToken("jamba_root_dir", "\${CMAKE_CURRENT_LIST_DIR}/../../pongasoft/jamba")
        setToken("local_jamba", "#")
        setToken("remote_jamba", "")
        setToken(
            "target", when (val company = newTokens["company"]) {
                null -> pluginName
                "" -> pluginName
                else -> "${company}_$pluginName"
            }
        )
        setBooleanToken("enable_vst2")
        setBooleanToken("enable_audio_unit")
        setBooleanToken("download_vst_sdk")

        return JambaPlugin(name, newTokens.mapKeys { (k, _) -> "[-$k-]" })
    }

    fun generateFileTree(plugin: JambaPlugin): FileTree {
        return mapOf(*files.map { file ->
            val processedName = file.relativePath.replace("__Plugin__", plugin.name)
            var processedContent = file.content
            processedContent = processedContent.replace("[--", "[-") // escape sequence in python script
            for ((tokenName, tokenValue) in plugin.tokens) {
                processedContent = processedContent.replace(tokenName, tokenValue)
            }
            Pair(processedName, FileTreeEntry(processedContent, file))
        }.toTypedArray())
    }

    fun generatePlugin(root: String, tree: FileTree): Promise<JambaPluginZip> {

        val zip = JSZip()

        val rootDir = zip.folder(root)

        tree.forEach { (name, entry) ->
            val fileOptions = object : JSZipFileOptions {}.apply {
                date = entry.file.date
                unixPermissions = entry.file.unixPermissions
            }
            rootDir.file(name, entry.content, fileOptions)
        }

        val options = object : JSZipGeneratorOptions {}.apply {
            type = "blob"
            platform = "UNIX"
        }

        return zip.generateAsync(options).then {
            JambaPluginZip("$root.zip", it as Blob)
        }
    }
}