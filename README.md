Introduction
============

This project is the code that drives the [Jamba quickstart](https://jamba.dev/quickstart/web/) dynamic section.

This project is a pure javascript frontend implementation of the [jamba-quickstart-server](https://github.com/ypujante/jamba-quickstart-server) project.

This project was built with kotlin 1.4.10 and demonstrate a few interesting features

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

Build
=====

In development mode, simply run this command which automatically loads the web page in a browser window and listens to changes (in the IDEA you can select `BrowserDevelopmentRun` configuration):

```
./gradlew browserDevelopmentRun
```

For the production ready artifacts (under `build/distributions`)
```
./gradlew build
```

Note that the `deploy` task is being used locally to deploy only the necessary artifacts to their final destination prior to building the jamba.dev website (which uses Hugo). It can serve as an example to do something similar in your environment.

Release Notes
=============

#### 2020-11-17 - 1.2.0
* Migrated to Kotlin/js 1.4.10 
* Added preview files section and no longer automatically download the zip file

License
=======

Apache 2.0

