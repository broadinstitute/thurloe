package thurloe.security

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object CSPDirective {
  private val cspHeader = RawHeader(
    "Content-Security-Policy",
    "default-src 'self'; script-src 'self' 'unsafe-inline'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'none';"
  )

  def addCSP(route: Route): Route = respondWithHeader(cspHeader) {
    route
  }
}
