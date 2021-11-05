package com.daddykotex

import java.io.File
import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives._
import akka.stream.scaladsl._
import akka.stream.IOResult
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Random

class FormRoutes()(implicit as: ActorSystem[_]) extends Directives {
  type FormUploadResult = (
      Map[FileInfo, Vector[File]],
      Map[String, Vector[String]]
  )
  private val emptyForm: FormUploadResult = (Map.empty, Map.empty)
  private val filesAndFields: Directive[FormUploadResult] =
    entity(as[Multipart.FormData]).flatMap { formData =>
      extractRequestContext.flatMap { ctx =>
        implicit val mat = ctx.materializer
        implicit val ec = ctx.executionContext

        // on the left side we emit file parts
        // on the right side we emit field parts
        val incoming: Source[Either[(FileInfo, File), (String, String)], Any] =
          formData.parts
            .mapAsyncUnordered(5) { part =>
              part.filename match {
                case Some(value) =>
                  val fileInfo =
                    FileInfo(part.name, value, part.entity.contentType)
                  val prefix = Option(fileInfo.fileName)
                    .filter(_.nonEmpty)
                    .getOrElse(Random.nextString(5))
                  val dest = File.createTempFile(prefix, ".tmp")
                  part.entity.dataBytes
                    .runWith(FileIO.toPath(dest.toPath))
                    .flatMap {
                      case x if x.wasSuccessful =>
                        Future.successful(Left(fileInfo -> dest))
                      case x => Future.failed(x.getError)
                    }
                case None =>
                  part.entity.dataBytes
                    .runFold(ByteString.empty)(_ ++ _)
                    .map(_.utf8String)
                    .map(data => Right(part.name -> data))
              }
            }

        // we accumulate everything in two maps
        val uploadedF: Future[FormUploadResult] =
          incoming.runWith(Sink.fold(emptyForm) {
            case ((files, fields), Right(fieldName -> fieldValue)) =>
              val inserted: Vector[String] =
                fields
                  .get(fieldName)
                  .map(_ :+ fieldValue)
                  .getOrElse(Vector(fieldValue))
              (files, fields.updated(fieldName, inserted))
            case ((files, fields), Left(fileInfo -> file)) =>
              val inserted: Vector[File] =
                files.get(fileInfo).map(_ :+ file).getOrElse(Vector(file))

              (files.updated(fileInfo, inserted), fields)
          })

        onSuccess(uploadedF)
      }
    }

  private val htmlForm =
    (get & pathEndOrSingleSlash) {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, dummyForm))
    }

  private val formEndpoint = (post & path("form")) {
    withoutSizeLimit {
      filesAndFields { case (files, fields) =>
        for ((fi, filesPerField) <- files) {
          for (file <- filesPerField) {
            println(
              s"File ${fi.fileName} for field ${fi.fieldName} at ${file.getAbsolutePath()}"
            )
          }
        }
        for ((field, values) <- fields) {
          for (v <- values) {
            println(s"Field $field has value: ${v}")
          }
        }
        complete(StatusCodes.OK)
      }
    }
  }

  val routes = htmlForm ~ formEndpoint

  private val dummyForm = """|
  |<form action="http://localhost:8080/form" method="POST" enctype="multipart/form-data">
  |  <div>
  |    <label>11:</label>
  |    <input type="text" name="field1" />
  |  </div>
  |
  |  <div>
  |    <label>2:</label>
  |    <input type="text" name="field2" />
  |  </div>
  |
  |  <div>
  |    <label>1 again:</label>
  |    <input type="text" name="field1" />
  |  </div>
  |
  |  <div>
  |    <label>File 1:</label>
  |    <input type="file" name="file1" />
  |  </div>
  |
  |  <div>
  |    <label>File 2:</label>
  |    <input type="file" name="file2" />
  |  </div>
  |
  |  <button>Submit</button>
  |</form>""".stripMargin
}
