import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.createElement
import kotlin.js.Promise

/**
 * Encapsulates a notification section where messages can be added */
class Notification(id: String) {
  val element = document.getElementById(id)!!

  private fun addTextLine(message: String, status: String? = null) {
    val div = document.createElement("div")
    if(status != null)
      div.classList.add(status)
    div.appendChild(document.createTextNode(message))
    element.appendChild(div)
  }

  fun info(message: String) {
    addTextLine(message)
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
  val dynElt : dynamic = this
  if(value.isEmpty() || value == dynElt.__computedValue)
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
 * This is a "trick" to force the browser to download a file from an internally generated blob. */
fun downloadFile(filename: String, blob: Blob) {
  (document.createElement("a") {
    this as HTMLAnchorElement
    href = URL.createObjectURL(blob)
    target = "_blank"
    download = filename
  } as HTMLAnchorElement)//.click()
  println("downloading... ${URL.createObjectURL(blob)}")
}

typealias ErrorHandler = (error: Throwable) -> Unit

/**
 * Forces flattening the promise because Kotlin doesn't do it automatically
 */
inline fun <T> Promise<Promise<T>>.flatten() : Promise<T> {
  return this.then { it }
}

/**
 * Fetches the URL and processes the response (only when successful) via [onFulfilled]. If not successful or
 * rejection, then an exception is thrown (should be handled via [Promise.catch])
 */
fun <T> fetchURL(url: String,
                 method: String = "GET",
                 onFulfilled: (Response) -> Promise<T>) : Promise<T> {

  return window.fetch(url,
                       RequestInit(method = method))
      .then(
          onFulfilled = { response ->
            if (response.status == 200.toShort()) {
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
fun fetchJson(url: String, method: String = "GET") : Promise<dynamic> { return fetchURL(url, method) {it.json()} }

/**
 * Fetches the url as a blob
 */
fun fetchBlob(url: String, method: String = "GET") : Promise<Blob> { return fetchURL(url, method) {it.blob()} }

/**
 * https://api.github.com/repos/pongasoft/jamba/releases/latest returns json
 */
fun loadJambaZip() : Promise<Pair<String, Blob>> {
  // async fetch of the blob
  val zipURL = "/static/blank-plugin.zip"
  val jambaReleasesGithubAPI = "https://api.github.com/repos/pongasoft/jamba/releases/latest"
  println("fetching  ${zipURL}")
//  fetchJson(jambaReleasesGithubAPI).then { json : dynamic ->
//    println("detected version=${json.tag_name}")
//  }
  return fetchBlob(zipURL).then { blob -> Pair("v2.1.2", blob) }
}


fun init() {
  val elements = arrayOf("name",
                         "enable_vst2",
                         "enable_audio_unit",
                         "audio_unit_manufacturer_code",
                         "filename",
                         "filename",
                         "company",
                         "company_url",
                         "company_email",
                         "namespace",
                         "project_name",
                         "submit").associateBy({ it }) { id ->
    document.getElementById(id) as? HTMLInputElement
  }

  val notification = Notification("notification")

  notification.info("Loading latest Jamba Blank Plugin...")

  loadJambaZip()
      // zip loaded => build the cache
      .then { (version, zip) ->
        document.getElementById("jamba_version")?.textContent = "[$version]"
        buildCache(version, zip)
      }
      // cache built => install listener
      .then { cache ->
        notification.info("Loaded Jamba Blank Plugin version ${cache.jambaGitHash} - ${cache.fileCount} files.")

        // handle submitting the form
        elements["submit"]?.addListener("click") {
          cache.generatePlugin(form!!).then { (filename, blob) ->
            downloadFile(filename, blob)
          }
        }
      }
      // in case of error => report error and install different listener
      .catch {
        notification.error("Could not fetch blank plugin - ${it.message}. Try refreshing the page...")

        elements["submit"]?.addListener("click") {
          notification.error("Could not fetch blank plugin. Try refreshing the page...")
        }
      }

  // defines what happens when the plugin name is entered/changed
  elements["name"]?.onChange {
    elements["audio_unit_manufacturer_code"]?.setComputedValue(computeAudioUnitManufacturerCode(value))
    elements["namespace"]?.setComputedValue(computeNamespace(value, elements["company"]?.value))
    elements["project_name"]?.setComputedValue(computeProjectName(value, elements["company"]?.value))
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

fun main() {
  println("Hello world")
  init()
}