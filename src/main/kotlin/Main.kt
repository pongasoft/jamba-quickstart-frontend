import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.browser.document
import kotlin.dom.createElement

/**
 * This is a "trick" to force the browser to download a file: create an anchor element and click it. For security
 * reasons this would not work if the uri is not the same domain. */
fun downloadFile(filename: String, blob: Blob) {
  (document.createElement("a") {
    this as HTMLAnchorElement
    href = URL.createObjectURL(blob)
    target = "_blank"
    download = filename
  } as HTMLAnchorElement)//.click()
  println("downloading... ${URL.createObjectURL(blob)}")

}

fun main() {
  println("Hello world")
  val zip = JSZip()
  zip.file("Hello.txt", "Hello World\n")
  zip.file("Hello2.txt", "Hello World 2\n")
  val options = object : JSZipGeneratorOptions {}
  options.type = "blob"
  zip.generateAsync(options)
      .then {
        it as Blob
        println(it)
        downloadFile("test-jszip.zip", it)
      }
}