import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.createElement
import kotlin.js.Promise

/**
 * Finds (may not exist) the content of a meta entry whose name is provided */
fun Document.findMetaContent(name: String): String? {
  return querySelector("meta[name='$name']")?.getAttribute("content")
}

/**
 * Encapsulates a notification section where messages can be added */
class Notification(id: String? = null) {
  val element = id?.let { document.getElementById(it) }

  private fun addTextLine(message: String, status: String? = null) {
    if (element != null) {
      val div = document.createElement("div")
      if (status != null)
        div.classList.add(status)
      div.appendChild(document.createTextNode(message))
      element.appendChild(div)
    } else {
      println("[${status ?: "info"}] $message")
    }
  }

  private fun addElement(elt: Element, status: String? = null) {
    if (element != null) {
      val div = document.createElement("div")
      if (status != null)
        div.classList.add(status)
      div.appendChild(elt)
      element.appendChild(div)
    }
  }

  fun info(message: String) {
    addTextLine(message)
  }

  fun info(elt: Element) {
    addElement(elt)
  }

  fun success(message: String) {
    addTextLine(message, "success")
  }

  fun error(message: String) {
    addTextLine(message, "error")
  }
}

/**
 * Used in promise rejection when detecting error (status code != 200)
 */
open class HTTPException(val status: Short, val errorMessage: String) : Exception("[$status] $errorMessage") {
  constructor(response: Response) : this(response.status, response.statusText)
}

/**
 * Adding a listener where the element is passed back in the closure as "this" for convenience */
fun HTMLInputElement.addListener(type: String, block: HTMLInputElement.(event: Event) -> Unit) {
  addEventListener(type, { event -> block(event) })
}

/**
 * Shortcut for change event */
fun HTMLInputElement.onChange(block: HTMLInputElement.(event: Event) -> Unit) {
  addListener("change", block)
}

/**
 * Add a __computedValue field to the element to store the value that was computed so that when it gets
 * recomputed it can be updated but ONLY in the event the user has not manually modified it
 */
fun HTMLInputElement.setComputedValue(computedValue: String) {
  val dynElt: dynamic = this
  if (value.isEmpty() || value == dynElt.__computedValue)
    value = computedValue
  dynElt.__computedValue = computedValue
}

/**
 * Helper to compute an audio manufacturer code from the plugin name
 */
fun computeAudioUnitManufacturerCode(pluginName: String?): String {
  if (pluginName == null || pluginName.isEmpty())
    return ""

  return pluginName.substring(0..3).padEnd(4, 'x').capitalize()
}

/**
 * Helper to compute a default namespace from plugin name and company
 */
fun computeNamespace(pluginName: String?, company: String?): String {
  if (pluginName == null || pluginName.isEmpty())
    return ""

  return if (company == null || company.isEmpty())
    "VST::$pluginName"
  else
    "$company::VST::$pluginName"
}

/**
 * Helper to compute a default project name from plugin name and company
 */
fun computeProjectName(pluginName: String?, company: String?): String {
  if (pluginName == null || pluginName.isEmpty())
    return ""

  return if (company == null || company.isEmpty())
    "$pluginName-plugin"
  else
    "$company-$pluginName-plugin"
}

/**
 * Generate the download link. */
fun generateDownloadAnchor(filename: String, blob: Blob) : HTMLAnchorElement {
  return document.createElement("a") {
    this as HTMLAnchorElement
    href = URL.createObjectURL(blob)
    target = "_blank"
    download = filename
  } as HTMLAnchorElement
}

/**
 * Forces flattening the promise because Kotlin doesn't do it automatically
 */
inline fun <T> Promise<Promise<T>>.flatten(): Promise<T> {
  return this.then { it }
}

/**
 * Fetches the URL and processes the response (only when successful) via [onFulfilled]. If not successful or
 * rejection, then an exception is thrown (should be handled via [Promise.catch])
 */
fun <T> fetchURL(
    url: String,
    method: String = "GET",
    onFulfilled: (Response) -> Promise<T>
): Promise<T> {

  return window.fetch(
      url,
      RequestInit(method = method)
  )
      .then(
          onFulfilled = { response ->
            if (response.ok && response.status == 200.toShort()) {
              onFulfilled(response)
            } else {
              Promise.reject(HTTPException(response))
            }
          }
      ).flatten()
}

/**
 * Fetches the url as json content. Note the use of `dynamic` since json is "free" form
 */
fun fetchJson(url: String, method: String = "GET"): Promise<dynamic> {
  return fetchURL(url, method) { it.json() }
}

/**
 * Fetches the url as a blob
 */
fun fetchBlob(url: String, method: String = "GET"): Promise<Blob> {
  return fetchURL(url, method) { it.blob() }
}

/**
 * Encapsulates the entries that the user fills out to customize the blank plugin
 */
data class OptionEntry(
    val name: String,
    val label: String? = null,
    val type: InputType = InputType.text,
    val checked: Boolean? = null,
    val defaultValue: String? = null,
    val desc: String? = null,
    val maxLength: Int? = null,
    val disabled: Boolean? = null
)

/**
 * All entries
 */
val entries =
    arrayOf(
        OptionEntry(
            name = "name",
            label = "Plugin Name",
            desc = "Must be a valid C++ class name"
        ),
        OptionEntry(
            name = "enable_vst2",
            type = InputType.checkBox,
            label = "Enable VST2",
            checked = false,
            desc = "Makes the plugin compatible with both VST2 and VST3"
        ),
        OptionEntry(
            name = "enable_audio_unit",
            type = InputType.checkBox,
            label = "Enable Audio Unit",
            desc = "Generates an (additional) Audio Unit compatible plugin"
        ),
        OptionEntry(
            name = "audio_unit_manufacturer_code",
            label = "Audio Unit Manufacturer",
            desc = "Must be 4 characters with (at least) one capital letter",
            maxLength = 4
        ),
        OptionEntry(
            name = "filename",
            label = "Filename",
            desc = "The name used for the plugin file (building the plugin will generate <Filename>.VST3)"
        ),
        OptionEntry(
            name = "company",
            label = "Company",
            desc = "Name of the company (your name if not company)"
        ),
        OptionEntry(
            name = "company_url",
            label = "Company URL",
            desc = "A URL for the company (a link to reach you if not a company)"
        ),
        OptionEntry(
            name = "company_email",
            label = "Company Email",
            desc = "An email address for the company (your email if not a company)"
        ),
        OptionEntry(
            name = "namespace",
            label = "C++ namespace",
            desc = "Although recommended, you can leave blank if you do not want to use a namespace"
        ),
        OptionEntry(
            name = "project_name",
            label = "Project name",
            desc = "Name of the project itself (which will be the name of the zip file generated)"
        ),
        OptionEntry(
            name = "download_vst_sdk",
            type = InputType.checkBox,
            label = "Download VST SDK",
            checked = false,
            desc = "Automatically downloads the VST SDK required to use Jamba"
        ),
        OptionEntry(
            name = "submit",
            type = InputType.button,
            defaultValue = "Generate blank plugin",
            disabled = true
        )
    )


/**
 * Extension function to handle `OptionEntry`
 */
fun TBODY.optionEntry(entry: OptionEntry): Unit = tr {
  td("name") { entry.label ?.let { label { htmlFor = entry.name; +entry.label } } }
  td("control") {
    input(type = entry.type, name = entry.name) {
      id = entry.name

      entry.maxLength?.let { maxLength = entry.maxLength.toString() }

      if (entry.type == InputType.checkBox) {
          checked = entry.checked ?: true
      }

      if(entry.defaultValue != null) {
        value = entry.defaultValue
        +entry.defaultValue
      }

      if(entry.disabled != null) {
        disabled = entry.disabled
      }
    }
  }
  td("desc") { entry.desc?.let { +entry.desc } }
}

/**
 * Creates the html form for the page
 */
fun createHTML(entries: Iterator<OptionEntry>, elementId: String? = null, classes: String? = null): HTMLElement {
  val form = document.create.form(method = FormMethod.post, classes = classes) {
    elementId?.let { id = elementId }
    table {
      tbody {
        entries.forEach { optionEntry(it) }
      }
    }
  }

  return form
}

/**
 * Defining a type alias for convenience
 */
typealias PJambaZip = Promise<Pair<String, Blob>>

/**
 * Loads the zip file containing the template for the blank plugin (with replacement tokens)
 */
fun loadJambaZip(version: String): PJambaZip {
  return fetchBlob("assets/jamba-blank-plugin-$version.zip").then { blob -> Pair(version, blob) }
}

/**
 * Tries to determine the jamba version (from the query string, html meta tag)
 */
fun findJambaVersion(): String? {
  // 1. try to locate the version number as a query string
  val fromQueryStringVersion = URLSearchParams(window.location.search).get("version")
  if (fromQueryStringVersion != null)
    return fromQueryStringVersion

  // 2. from a meta tag in the html
  return document.findMetaContent("X-jamba-latest-release")
}

/**
 * Main method called when the page loads.
 */
fun init() {

  val jambaFormID = document.findMetaContent("X-jamba-form-id") ?: "jamba-form"

  document.getElementById(jambaFormID)
      ?.replaceWith(createHTML(entries.iterator(),
                               elementId = jambaFormID,
                               classes = document.findMetaContent("X-jamba-form-class")))

  val elements = entries.associateBy({ it.name }) { entry ->
    document.getElementById(entry.name) as? HTMLInputElement
  }

  val notification = Notification("notification")

  document.findMetaContent("X-jamba-notification-welcome-message")?.let {message ->
    message.split('|').forEach { notification.info(it) }
  }

  val version = findJambaVersion()

  if (version == null) {
    notification.error("Could not determine Jamba version... please refresh the page")
    return
  }

  val jambaZip: PJambaZip by lazy { loadJambaZip(version) }

  elements["submit"]?.addListener("click") {
    notification.info("Loading Jamba Blank Plugin...")
    jambaZip
        .then { (version, zip) ->
          document.getElementById("jamba_version")?.textContent = "[$version]"
          buildCache(version, zip)
        }
        .then { cache ->
          notification.info("Loaded Jamba Blank Plugin version ${cache.jambaGitHash} - ${cache.fileCount} files.")
          cache.generatePlugin(form!!).then { (filename, blob) ->
            notification.info("Plugin [$filename] generated successfully. Downloading...")
            val downloadAnchor = generateDownloadAnchor(filename, blob)
            document.findMetaContent("X-jamba-download-link")?.let {
              downloadAnchor.text = it
              notification.info(downloadAnchor)
            }
            downloadAnchor.click()
            notification.success("Download complete (check you download folder).")
          }
        }
        // in case of error => report error and install different listener
        .catch {
          println(it)
          notification.error("Could not fetch blank plugin - ${it.message}. Try refreshing the page...")
        }
  }

  // defines what happens when the plugin name is entered/changed
  elements["name"]?.onChange {
    elements["audio_unit_manufacturer_code"]?.setComputedValue(computeAudioUnitManufacturerCode(value))
    elements["namespace"]?.setComputedValue(computeNamespace(value, elements["company"]?.value))
    elements["project_name"]?.setComputedValue(computeProjectName(value, elements["company"]?.value))
    elements["filename"]?.setComputedValue(value)
    elements["submit"]?.disabled = value.isEmpty()
  }

  // defines what happens when the company is entered/changed
  elements["company"]?.onChange {
    elements["namespace"]?.setComputedValue(computeNamespace(elements["name"]?.value, value))
    elements["project_name"]?.setComputedValue(computeProjectName(elements["name"]?.value, value))
    elements["company_url"]?.setComputedValue("https://www.$value.com")
    elements["company_email"]?.setComputedValue("support@$value.com")
  }
}

/**
 * Javascript entry point
 */
fun main() {
  init()
}