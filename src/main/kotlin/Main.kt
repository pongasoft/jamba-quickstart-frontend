import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.createElement
import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Element
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams
import org.w3c.files.Blob
import kotlin.js.Promise

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
            // this makes sure that the last entry is visible if the notification has a scroll bar
            element.scrollTop = element.scrollHeight.toDouble()
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
            // this makes sure that the last entry is visible if the notification has a scroll bar
            element.scrollTop = element.scrollHeight.toDouble()
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
 * Helper to compute an audio manufacturer code from the plugin name
 */
fun computeAudioUnitManufacturerCode(pluginName: String?): String {
    if (pluginName == null || pluginName.isEmpty())
        return ""

    return pluginName.substring(0..3).padEnd(4, 'x')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
fun generateDownloadAnchor(filename: String, blob: Blob): HTMLAnchorElement {
    return document.createElement("a") {
        this as HTMLAnchorElement
        href = URL.createObjectURL(blob)
        target = "_blank"
        download = filename
    } as HTMLAnchorElement
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
            name = "company",
            label = "Company",
            desc = "Name of the company (your name if not company)"
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
            name = "macos_deployment_target",
            label = "macOS Target",
            defaultValue = "10.14",
            desc = "The macOS deployment target (default to 10.14 / Mojave)"
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
    td("name") { entry.label?.let { label { htmlFor = entry.name; +entry.label } } }
    td("control") {
        input(type = entry.type, name = entry.name) {
            id = entry.name

            entry.maxLength?.let { maxLength = entry.maxLength.toString() }

            if (entry.type == InputType.checkBox) {
                checked = entry.checked ?: true
            }

            if (entry.defaultValue != null) {
                value = entry.defaultValue
                +entry.defaultValue
            }

            if (entry.disabled != null) {
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

    val jambaFormID = document.findMetaContent("X-jamba-form-id") ?: "jamba-quickstart-form"

    document.getElementById(jambaFormID)
        ?.replaceWith(
            createHTML(
                entries.iterator(),
                elementId = jambaFormID,
                classes = document.findMetaContent("X-jamba-form-class")
            )
        )

    val elements = entries.associateBy({ it.name }) { entry ->
        document.getElementById(entry.name) as? HTMLInputElement
    }

    // sets the state of the submit button depending on whether all values have been filled out
    fun maybeEnableSubmit() {
        elements["submit"]?.disabled = elements.values.any { it?.value?.isEmpty() ?: false }
    }

    val notification = Notification("notification")

    document.findMetaContent("X-jamba-notification-welcome-message")?.let { message ->
        message.split('|').forEach { notification.info(it) }
    }

    val version = findJambaVersion()

    if (version == null) {
        notification.error("Could not determine Jamba version... please refresh the page")
        return
    }

    document.getElementById("jamba_version")?.textContent = "[$version]"

    val jambaPluginMgrPromise = JambaPluginMgr.load(version)

    elements["submit"]?.addListener("click") {
        notification.info("Loading Jamba Blank Plugin...")
        jambaPluginMgrPromise
            .then { mgr ->
                notification.info("Loaded Jamba Blank Plugin version ${mgr.jambaGitHash} - ${mgr.fileCount} files.")

                val plugin = mgr.createJambaPlugin(form!!)

                val tree = mgr.generateFileTree(plugin)

               // add links to preview all files included with the RE
               fun renderFilePreview(path: String) {
                   // render the content
                   tree[path]?.html?.invoke()?.let { content ->
                       document.replaceElement("jamba-plugin-preview-files-content", content)
                   }

                   // regenerate the list of links
                   document.replaceElement("jamba-plugin-preview-files-links",
                       document.create.div {
                           ul {
                               tree.keys.sortedBy { it.lowercase() }.forEach { p ->
                                   li(if(path == p) "active" else null) {
                                       if(p != path) {
                                           a {
                                               onClickFunction = {
                                                   renderFilePreview(p)
                                               }
                                               +p
                                           }
                                       } else {
                                               +p
                                       }
                                   }

                               }
                           }
                       }
                   )
               }


                // render CMakeLists.txt
                renderFilePreview(document.findMetaContent("X-jamba-default-preview-file") ?: "CMakeLists.txt")

                // we reveal the rest of the page
                document.show("jamba-plugin")

                mgr.generatePlugin("${plugin.name}-src", tree).then { zip ->
                    notification.info("Generated ${zip.filename}.")
                    val downloadAnchor = generateDownloadAnchor(zip.filename, zip.content)
                    downloadAnchor.text = zip.filename
                    document.replaceElement("jamba-plugin-download-link", downloadAnchor)
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
    }

    // defines what happens when the company is entered/changed
    elements["company"]?.onChange {
        elements["namespace"]?.setComputedValue(computeNamespace(elements["name"]?.value, value))
        elements["project_name"]?.setComputedValue(computeProjectName(elements["name"]?.value, value))
        elements["company_url"]?.setComputedValue("https://www.$value.com")
        elements["company_email"]?.setComputedValue("support@$value.com")
    }

    // hide Step.2+ if the form is changed
    elements.forEach { (name, elt) ->
        if (name != "submit")
            elt?.onChange {
                document.hide("jamba-plugin")
                maybeEnableSubmit()
            }
    }
}

/**
 * Javascript entry point
 */
fun main() {
    init()
}