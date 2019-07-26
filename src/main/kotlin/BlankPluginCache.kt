import org.w3c.dom.HTMLFormElement
import org.w3c.files.Blob
import org.w3c.xhr.FormData
import kotlin.js.Date
import kotlin.js.Promise

/**
 * Maintains the information for each file in the zip archive. Will use permission and date to generate the
 * outcome archive.
 */
data class BlankPluginFile(val relativePath: String, val date: Date?, val unixPermissions: Int?, val content: String)

fun buildCache(version: String, blob: Blob): Promise<BlankPluginCache> {

  // This code is taken from the gist https://gist.github.com/jed/982883
  val UUIDv4: dynamic =
    js("function (a){return a?(a^crypto.getRandomValues(new Uint8Array(1))[0]%16>>a/4).toString(16):([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g,UUIDv4)}")

  val zip = JSZip()

  return zip.loadAsync(blob).then {

    val promises = mutableListOf<Promise<BlankPluginFile>>()

    zip.forEach { path, file ->
      val relativePath = path.substringAfter("blank-plugin/")
      if(!(relativePath.startsWith("__MACOSX") ||
              relativePath.startsWith(".idea") ||
              relativePath.endsWith(".DS_Store"))) {
        val p = file.async("string").then { content ->
          BlankPluginFile(relativePath, file.date, file.unixPermissions, content.toString())
        }
        promises.add(p)
      }
    }

    Promise.all(promises.toTypedArray()).then { array ->
      BlankPluginCache(version, array, UUIDv4)
    }
  }.flatten()
}

/**
 * Defines the api used by js iterators (which can be used in for..of construct) */
external interface JSIteratorNextResult<T> {
  val done : Boolean?
  val value : T
}

/**
 * Defines the api used by js iterators (which can be used in for..of construct) */
external interface JSIterator<T> {
  fun next() : JSIteratorNextResult<T>
}

/**
 * This wraps a javascript [iterator](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Iteration_protocols)
 * into a Kotlin [Iterator]
 */
fun <T> jsIterator(jsObject: JSIterator<T>) : Iterator<T> {
  return object : AbstractIterator<T>() {
    override fun computeNext() {
      val n = jsObject.next()
      if (n.done == null || n.done == false)
        setNext(n.value)
      else
        done()
    }
  }
}

// add .keys() method
fun FormData.keys() : Iterator<String> = jsIterator(this.asDynamic().keys())

/**
 * Converts a string to a boolean */
private fun convertToBoolean(s: String?): Boolean {
  if (s == null)
    return false

  val ts = s.toLowerCase()

  return !(ts == "false" || ts == "no" || ts == "off")
}

/**
 * Caches the files in memory */
class BlankPluginCache(val jambaGitHash: String, val files: Array<out BlankPluginFile>, val UUIDGenerator: () -> String) {

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

  /**
   * Processes each entry in the cache through the token replacement mechanism and invoke action
   * with the result */
  fun forEachFile(tokens: Map<String, String>, action: (BlankPluginFile, String, String) -> Unit) {

    val newTokens = tokens.toMutableMap()

    val setToken = { key: String, value: String ->
      newTokens.getOrPut(key, {value})
    }

    val setBooleanToken = { key: String ->
      newTokens[key] = if(convertToBoolean(newTokens[key])) "ON" else "OFF"
    }

    val pluginName = setToken("name", "Plugin")
    setToken("jamba_git_hash", jambaGitHash)

    val ns = tokens["namespace"]?.trim()

    if(ns == null || ns.isEmpty()) {
      newTokens["namespace_start"] = ""
      newTokens["namespace_end"] = ""
    } else {
      newTokens["namespace_start"] = ns.split("::").joinToString(separator = "\n") { "namespace $it {" }
      newTokens["namespace_end"] = ns.split("::").joinToString(separator = "\n") { "}" }
    }

    setToken("processor_uuid", generateUUID())
    setToken("controller_uuid", generateUUID())
    setToken("year", Date().getFullYear().toString())
    setToken("jamba_root_dir", "../../pongasoft/jamba")
    setToken("local_jamba", "#")
    setToken("remote_jamba", "")
    setToken("target", when(val company = newTokens["company"]) {
      null -> pluginName
      ""   -> pluginName
      else -> "${company}_$pluginName"
    } )
    setBooleanToken("enable_vst2")
    setBooleanToken("enable_audio_unit")
    setBooleanToken("download_vst_sdk")

    val t = newTokens.mapKeys { (k,_) -> "[-$k-]" }
    files.forEach { file ->
      val processedName = file.relativePath.replace("__Plugin__", pluginName)
      var processedContent = file.content
      for((tokenName, tokenValue) in t) {
        processedContent = processedContent.replace(tokenName, tokenValue)
      }
      action(file, processedName, processedContent)
    }
  }

  fun generatePlugin(form: HTMLFormElement) : Promise<Pair<String, Blob>> {
    val data = FormData(form)
    val params = data.keys().asSequence().associateBy({it}, { e -> data.get(e).toString() })

    val name = params["name"]

    if(name == null)
      return Promise.reject(Exception("Plugin name must be provided"))

    val root = "$name-src"

    val zip = JSZip()

    val rootDir = zip.folder(root)

    forEachFile(params) { file, entry, content ->
      val fileOptions = object : JSZipFileOptions {}.apply {
        date = file.date
        unixPermissions = file.unixPermissions
      }
      rootDir.file(entry, content, fileOptions)
    }

    val options = object : JSZipGeneratorOptions {}.apply {
      type = "blob"
      platform = "UNIX"
    }

    return zip.generateAsync(options).then {
      Pair("$root.zip", it as Blob)
    }
  }
}