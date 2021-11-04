package com.daddykotex

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives

class FormRoutes()(implicit as: ActorSystem[_]) extends Directives {
  val routes = get {
    complete("hello")
  }
}
