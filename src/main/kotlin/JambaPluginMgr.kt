import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.files.Blob
import org.w3c.xhr.FormData
import kotlin.js.Date
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.dom.addClass
import kotlinx.html.code
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.pre
import kotlinx.html.span
import org.w3c.dom.HTMLImageElement

/**
 * Defines content processing (token replacement) */
interface ContentProcessor {
    fun processText(text: String) : String
    fun processPath(path: String) : String
}

class TokenBasedContentProcessor(tokens: Map<String, String>) : ContentProcessor
{
    private val textTokens = tokens.mapKeys { (k, _) -> "[-$k-]" }
    private val pathTokens = tokens.mapKeys { (k, _) -> "__${k}__" }

    // simply replace each token in the content
    private fun processContent(content: String, tokens: Map<String, String>): String {
        var processedContent = content
        for((tokenName, tokenValue) in tokens) {
          processedContent = processedContent.replace(tokenName, tokenValue)
        }
        return processedContent
    }

    override fun processText(text: String): String {
        return processContent(text, textTokens)
    }

    override fun processPath(path: String): String {
        return processContent(path, pathTokens)
    }
}

class JambaPlugin(val name: String, tokens: Map<String, String>) {
    private val contentProcessor = TokenBasedContentProcessor(tokens)

    fun getContentProcessor() : ContentProcessor = contentProcessor
}

/**
 * The zip file that gets generated with the content of the blank plugin */
class JambaPluginZip(val filename: String, val content: Blob)

/**
 * Maintains the information for each file in the zip archive. Will use permission and date to generate the
 * outcome archive.
 */
class FileTreeEntry(val html: () -> HTMLElement, val zip: Promise<Any>, val resource: StorageResource)

/**
 * A file tree is a map indexed by the full path to the file (ex: `src/cpp/Plugin.h`) */
typealias FileTree = Map<String, FileTreeEntry>

/**
 * Unique ID */
class UniqueID(private val uuid: String) {
    fun asSnapshotID() = uuid.uppercase()
    fun asCString() = "0x${uuid.substring(0..7)}, 0x${uuid.substring(8..15)}, 0x${uuid.substring(16..23)}, 0x${uuid.substring(24..31)}"
}

/**
 * Caches the files in memory */
class JambaPluginMgr(
    val jambaGitHash: String,
    val jambaDownloadUrlHash: String?,
    val storage: Storage,
    val UUIDGenerator: () -> String
) {
    companion object {

        /**
         * Main API to create [REMgr]. Loads the `plugin-<version>.zip` file hence it returns a promise.*/
        fun load(version: String, downloadUrlHash: String?): Promise<JambaPluginMgr> {
            val UUIDv4: dynamic = js("""
                (function() {
                  return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, function(c) {
                    return (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16);
                  });
                })
            """)
            return Storage.load(version).then { JambaPluginMgr(version, downloadUrlHash, it, UUIDv4) }
        }
    }

    /**
     * Number of files that make the plugin
     */
    val fileCount: Int get() = storage.resources.size

    /**
     * Generate a UUID as a C notation
     */
    fun generateUUID() = UniqueID(UUIDGenerator().replace("-", ""))

    fun createJambaPlugin(form: HTMLFormElement): JambaPlugin {
        val data = FormData(form)
        val params = data.keys().asSequence().associateBy({ it }, { e -> data.get(e).toString() })

        val name = params["name"] ?: throw Exception("Plugin name must be provided")

        // Converts a string to a boolean
        fun convertToBoolean(s: String?): Boolean {
            if (s == null)
                return false

            val ts = s.lowercase()

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
        setToken("Plugin", pluginName)
        setToken("jamba_git_hash", jambaGitHash)
        setToken("jamba_download_url_hash", jambaDownloadUrlHash ?: "")

        val processorUniqueID = generateUUID()

        setToken("processor_uuid", processorUniqueID.asCString())
        setToken("snapshot_uuid", processorUniqueID.asSnapshotID())
        setToken("controller_uuid", generateUUID().asCString())
        setToken("debug_processor_uuid", generateUUID().asCString())
        setToken("debug_controller_uuid", generateUUID().asCString())
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
        setBooleanToken("enable_audio_unit")
        setBooleanToken("download_vst_sdk")

        return JambaPlugin(name, newTokens)
    }

    /**
     * Generates the `<img>` element for the static image resource */
    private fun generateStaticImgContent(imageResource: ImageResource) = with(document.createElement("img")) {
        this as HTMLImageElement
        src = imageResource.image.src
        document.findMetaContent("X-re-quickstart-re-files-preview-classes")?.let { addClass(it) }
        this
    }

    fun generateFileTree(plugin: JambaPlugin): FileTree {

        val contentProcessor = plugin.getContentProcessor()

        val resources = storage.resources.map { resource ->
            val path = contentProcessor.processPath(resource.path).removePrefix("blank-plugin/")
            Pair(path,
                FileTreeEntry(
                    resource = resource,
                    html = {
                        when (resource) {
                            is FileResource -> document.create.div("highlight") {
                                pre("chroma") {
                                    code("language-text") {
                                        attributes["data-lang"] = "text"
                                        +contentProcessor.processText(resource.content)
                                    }
                                    span("copy-to-clipboard") {
                                        attributes["title"] = "Copy to clipboard"
                                    }
                                }
                            }

                            is ImageResource -> generateStaticImgContent(resource)
                        }
                    },
                    zip = when(resource) {
                        is FileResource -> Promise.resolve(contentProcessor.processText(resource.content))
                        is ImageResource -> Promise.resolve(resource.blob)
                    }
                )
            )
        }
        
        return mapOf(*resources.toTypedArray())
    }

    fun generatePlugin(root: String, tree: FileTree): Promise<JambaPluginZip> {

        val zip = JSZip()
        val rootDir = zip.folder(root)

        // helper class to pass down the `then` chain
        class ZipEntry(val name: String, val resource: StorageResource?, val content: Any)

        return Promise.all(tree.map { (name, entry) ->
            entry.zip.then { ZipEntry(name, entry.resource, it) }
        }.toTypedArray()).then { array ->
            // addresses issue https://github.com/Stuk/jszip/issues/369# with date being UTC
            val now = Date().let { Date(it.getTime() - it.getTimezoneOffset() * 60000) }

            array.forEach { entry ->
                val fileOptions = object : JSZipFileOptions {}.apply {
                  date = entry.resource?.date ?: now
                  unixPermissions = entry.resource?.unixPermissions
                }
                rootDir.file(entry.name, entry.content, fileOptions)
            }
        }.then {
            // generate the zip
            val options = object : JSZipGeneratorOptions {}.apply {
                type = "blob"
                platform = "UNIX"
            }

            zip.generateAsync(options)
        }.then {
            // return as a pair
            JambaPluginZip("$root.zip", it as Blob)
        }
    }
}