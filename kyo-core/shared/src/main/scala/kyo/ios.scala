package kyo

import kyo.loggers.Loggers

import java.io.Closeable
import java.util.concurrent.ThreadLocalRandom
import scala.Conversion
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.runtime.AbstractFunction0
import scala.util.Try
import scala.util.control.NonFatal

import kyo._
import core._
import core.internal._
import tries._
import options._
import locals._

object ios {

  trait Preempt extends Safepoint[IO, IOs] {
    def ensure(f: () => Unit): Unit
    def remove(f: () => Unit): Unit
    def apply[T, S](v: => T > (IOs with S)) =
      IOs(v)
  }
  object Preempt {
    val never: Preempt =
      new Preempt {
        def ensure(f: () => Unit) = ()
        def remove(f: () => Unit) = ()
        def apply()               = false
      }
  }

  type IO[+T] = T

  private[kyo] abstract class KyoIO[T, S]
      extends Kyo[IO, IOs, Unit, T, (IOs with S)] {
    final def value  = ()
    final def effect = ios.IOs
  }

  final class IOs private[ios] () extends Effect[IO, IOs] {

    private[this] val log = Loggers.init(getClass())

    val unit: Unit > IOs = ()

    /*inline(3)*/
    def value[T](v: T): T > IOs = v

    /*inline(3)*/
    def fail[T](ex: Throwable): T > IOs = IOs(throw ex)

    /*inline(3)*/
    def attempt[T, S](v: => T > S): Try[T] > S =
      Tries.run(Tries(v))

    private[kyo] /*inline(3)*/ def ensure[T, S]( /*inline(3)*/ f: => Unit > IOs)(v: => T > S)
        : T > (IOs with S) =
      type M2[_]
      type E2 <: Effect[M2, E2]
      lazy val run: Unit =
        try IOs.run(f)
        catch {
          case ex if NonFatal(ex) =>
            log.error(s"IOs.ensure function failed", ex)
        }
      val ensure = new AbstractFunction0[Unit] {
        def apply() = run
      }
      def ensureLoop(v: T > (IOs with S), p: Preempt): T > (IOs with S) =
        v match {
          case kyo: Kyo[M2, E2, Any, T, S with IOs] @unchecked =>
            new KyoCont[M2, E2, Any, T, S with IOs](kyo) {
              def apply() = run
              def apply(v: Any, s: Safepoint[M2, E2], l: Locals.State) =
                val np =
                  s match {
                    case s: Preempt =>
                      s.ensure(ensure)
                      s
                    case _ =>
                      p
                  }
                ensureLoop(kyo(v, s, l), np)
            }
          case _ =>
            p.remove(ensure)
            IOs(run).map(_ => v)
        }
      ensureLoop(v, Preempt.never)

    /*inline(3)*/
    def apply[T, S](
        /*inline(3)*/ f: => T > (IOs with S)
    ): T > (IOs with S) =
      new KyoIO[T, S] {
        def apply(v: Unit, s: Safepoint[IO, IOs], l: Locals.State) =
          f
      }

    /*inline(3)*/
    def run[T](v: T > IOs): T =
      val safepoint = Safepoint.noop[IO, IOs]
      @tailrec def runLoop(v: T > IOs): T =
        v match {
          case kyo: Kyo[IO, IOs, Unit, T, IOs] @unchecked =>
            runLoop(kyo((), safepoint, Locals.State.empty))
          case _ =>
            v.asInstanceOf[T]
        }
      runLoop(v)

    /*inline(3)*/
    def lazyRun[T, S](v: T > (IOs with S)): T > S =
      type M2[_]
      type E2 <: Effect[M2, E2]
      @tailrec def lazyRunLoop(v: T > (IOs with S)): T > S =
        val safepoint = Safepoint.noop[IO, IOs]
        v match {
          case kyo: Kyo[IO, IOs, Unit, T, S with IOs] @unchecked if (kyo.effect eq IOs) =>
            lazyRunLoop(kyo((), safepoint, Locals.State.empty))
          case kyo: Kyo[M2, E2, Any, T, S with IOs] @unchecked =>
            new KyoCont[M2, E2, Any, T, S](kyo) {
              def apply(v: Any, s: Safepoint[M2, E2], l: Locals.State) =
                lazyRunLoop(kyo(v, s, l))
            }
          case _ =>
            v.asInstanceOf[T]
        }

      lazyRunLoop(v)
  }
  val IOs: IOs = new IOs
}
