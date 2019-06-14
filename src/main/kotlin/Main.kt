import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.browser.document
import kotlin.dom.createElement

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
    val zip = JSZip()
    zip.file("Hello.txt", "Hello World\n")
    zip.file("Hello2.txt", "Hello World 2\n")
    val options = object : JSZipGeneratorOptions {}.apply { type = "blob" }
    zip.generateAsync(options)
        .then {
          it as Blob
          println(it)
          downloadFile("test-jszip.zip", it)
        }
  }

  notification.progress("Fill out the name at least and click \"Generate\"")

}

fun main() {
  println("Hello world")
  init()
}