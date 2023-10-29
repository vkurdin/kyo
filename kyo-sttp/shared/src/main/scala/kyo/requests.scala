package kyo

import kyo._
import kyo.concurrent.fibers._
import kyo.consoles._
import kyo.envs._
import kyo.ios._
import sttp.client3
import sttp.client3.Empty
import sttp.client3.Request
import sttp.client3.RequestT
import sttp.client3.Response
import sttp.client3.UriContext
import sttp.client3.basicRequest

import scala.concurrent.Future
import kyo.tries.Tries
import sttp.client3.SttpBackend
import kyo.internal.KyoSttpMonad

object requests {

  type BasicRequest = RequestT[Empty, Either[String, String], Any]

  abstract class Backend {
    def send[T](r: Request[T, Any]): Response[T] > (Fibers with IOs)
  }

  object Backend {
    implicit val default: Backend = PlatformBackend.default
  }

  type Requests >: Requests.Effects <: Requests.Effects

  object Requests {

    type Effects = Envs[Backend] with Fibers with IOs with Tries

    private val envs = Envs[Backend]

    def run[T, S](b: Backend)(v: T > (Requests with S)): T > (Fibers with IOs with Tries with S) =
      Tries(envs.run[T, Fibers with IOs with Tries with S](b)(v))

    def run[T, S](v: T > (Requests with S))(implicit
        b: Backend
    ): T > (Fibers with IOs with Tries with S) =
      run[T, S](b)(v)

    def apply[T, S](req: Request[T, Any] > S): Response[T] > (Requests with S) =
      envs.get.map(b => req.map(b.send))

    def apply[T, S](f: BasicRequest => Request[T, Any] > S): Response[T] > (Requests with S) =
      apply((f(basicRequest)))
  }
}
