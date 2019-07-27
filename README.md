Introduction
============

This project is the code that drives the [Jamba quickstart](https://jamba.dev/quickstart/web/) dynamic section.

This project is a pure javascript frontend implementation of the [jamba-quickstart-server](https://github.com/ypujante/jamba-quickstart-server) project.

This project was built with kotlin 1.3.41 and demonstrate a few interesting features

List of demonstrated features (kotlin)
--------------------------------------

* writing javascript code in kotlin
* integrating with an external library (jszip)
* load and generate a zip file
* adding an event listener ("change", "click")
* posting a form via the "fetch" api and extracting/processing the json response (including error handling)
* adding dom elements
* wrapping the javascript `iterator` api into kotlin `Iterator`
* generating a UUID in javascript
* use of `Promise`
* downloading a file via javascript

List of demonstrated features (build)
-------------------------------------

* Use of Docker to create a small container serving files both from the source code (static files) and from the build directory (generated from compilation) => allows to iterate over static code without even having to recompile for example.
 

Build
=====

In development mode, simply run this command (start a web server after compilation)

```
./gradlew webserver-start
```

For production release
```
./gradlew -Prelease=true build
```

License
=======

Apache 2.0

