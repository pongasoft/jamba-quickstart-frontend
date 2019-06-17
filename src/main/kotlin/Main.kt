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

  fun progress(message: String) {
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
  } as HTMLAnchorElement).click()
  println("downloading... ${URL.createObjectURL(blob)}")

}

/**
 * https://api.github.com/repos/pongasoft/jamba/releases/latest returns json
 */
fun loadJambaZip(onResult: (version: String, zip: Blob) -> Unit,
                 onFailure: (error: String) -> Unit) {
  // async fetch of the blob
  val zipURL = "/static/blank-plugin.zip"
  println("fetching  ${zipURL}")
  window.fetch(zipURL,
               RequestInit(method="GET"))
      .then { response ->
        if (response.status == 200.toShort()) {
          response.blob()
        } else {
          Promise.reject(HTTPException(response))
        }
      }
      .then { blob: Blob ->
        onResult("v2.1.2", blob)
      }
      .catch {
        onFailure(it.message ?: "Unknown error")
      }
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

  var cache : BlankPluginCache? = null

  loadJambaZip(
      onResult = { version, blob ->
        buildCache(version, blob).then {
          cache = it
          notification.success("Loaded Jamba Blank Plugin version $version - ${cache?.fileCount} files.")
        }
        document.getElementById("jamba_version")?.textContent = "[$version]"
      },
      onFailure = {
        notification.error("Could not fetch blank plugin - $it. Try refreshing the page...")
      }
  )

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

  // handle submitting the form
  elements["submit"]?.addListener("click") {
    if(cache == null)
      notification.error("No blank plugin found... try refreshing the page")
    else
      cache?.generatePlugin(form!!)?.then { (filename, blob) ->
        downloadFile(filename, blob)
      }
  }

  notification.progress("Fill out the name at least and click \"Generate\"")

}

fun main() {
  println("Hello world")
  init()
}