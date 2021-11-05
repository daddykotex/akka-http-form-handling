# Simple example to stream multi-part forms

## Usage

Run with `sbt run`.

Even better, if you use `sbt-revolver` you'll get automatic reloading.

The server will start on `8080` and you can talk to it with curl:

```
curl 'http://localhost:8080'
```

## Multipart forms

This repository exists as a reference for someone who might have this question:

* I want to get a hold of all the files uploaded in a form as well as the form fields, how can I do that with akka-http.

I have already written about this [here](https://davidfrancoeur.com/post/akka-http-multipart-form/). This is a complex problem.

akka-http has supports for multipart forms via a few directives, see: https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/file-upload-directives/index.html. The downside is that when you use those directives, they consume the request body and discard the fields. You can theorically combine the too, but you will get errors as the request size incread. Why? Because as long as the request is small enough, it's loaded as a `Strict` request (in-memory) and you can use two directive that parse the body without a problem. But when the framework decides it's best not too load it in memory, the second directive parsing the body will fail (in unexpected ways).

In this repo, we'll implement a `/forms` endpoint that does that:

* stream incoming request into our directive
* file parts are written to disk
* fields are kept in a map
* when parsing is complete you get a `Map[String, List[File]]` for files and a `Map[String, List[String]]` for fields.

Open up [http://localhost:8080](http://localhost:8080) in your browser. Use the HTML form to test the endpoint.

You can also use curl, like so:

```bash
# let's upload three files
> ls -l1 .| head -3
collections_2.12-1.2.8.jar
jawn-parser_2.12-0.10.4.jar
scala-library-2.12.7.jar

> curl 'http://localhost:8080/form' \
  -F 'jar=@collections_2.12-1.2.8.jar' \
  -F 'jar=@jawn-parser_2.12-0.10.4.jar' \
  -F 'scala=@scala-library-2.12.7.jar' \
  -F 'version=2'
```

When you submit a `multipart/form-data`, the implementation will stream the files part to disk, and it will accumulate fields into a `Map`. When using the directive, you'll get a `Map[FileInfo, Vector[File]]` where the key are the file part info (field name, content-type, file name). The value is a `Vector` because a `multipart/form-data` can contain the same file part multiple times. The same thing goes for the fields, where you get a `Map[String, Vector[String]]`