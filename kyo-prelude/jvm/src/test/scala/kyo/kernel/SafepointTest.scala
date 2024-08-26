package kyo.kernel

import kyo.*
import kyo.Tag
import kyo.Tagged.*
import scala.concurrent.Await
import scala.concurrent.Future

class SafepointTest extends Test:

    def fork[A: Flat](f: => A < Any): A < Any =
        Effect.defer {
            val future = Future(f.eval)
            Await.result(future, timeout.toScala): A
        }

    "does not allow capturing across threads" in {
        val computation = Effect.defer {
            val runtime = summon[Safepoint]
            fork {
                Effect.defer {
                    val newSafepoint = summon[Safepoint]
                    assert(runtime ne newSafepoint)
                }
            }
        }

        computation.eval
    }

    "allows resuming in the same thread" in {
        val computation = Effect.defer {
            val runtime = summon[Safepoint]
            Effect.defer {
                val sameSafepoint = summon[Safepoint]
                assert(runtime eq sameSafepoint)
            }
        }

        computation.eval
    }

    "suspends when Safepoint is from a different thread" in {
        var capturedSafepoint: Safepoint = null
        val computation = Effect.defer {
            capturedSafepoint = summon[Safepoint]
            ()
        }

        computation.eval

        val differentThreadComputation = fork {
            Effect.defer {
                val currentSafepoint = summon[Safepoint]
                assert(capturedSafepoint ne currentSafepoint)
            }
        }

        differentThreadComputation.eval
    }

    "handles nested deferrals correctly" in {
        val computation = Effect.defer {
            val outerSafepoint = summon[Safepoint]
            Effect.defer {
                val innerSafepoint = summon[Safepoint]
                assert(outerSafepoint eq innerSafepoint)
            }
        }

        computation.eval
    }

    "propagates Safepoint through flatMap" in {
        val computation = Effect.defer {
            val initialSafepoint = summon[Safepoint]
            Effect.defer {
                42
            }.flatMap { value =>
                Effect.defer {
                    val laterSafepoint = summon[Safepoint]
                    assert(initialSafepoint eq laterSafepoint)
                    assert(value == 42)
                }
            }
        }

        computation.eval
    }

    "maintains different Safepoints across forks" in {
        val computation = Effect.defer {
            val runtime1 = summon[Safepoint]
            fork {
                Effect.defer {
                    val runtime2 = summon[Safepoint]
                    assert(runtime1 ne runtime2)
                }
            }
        }

        computation.eval
    }

    "no leak between forked executions" in run {
        var capturedSafepoint: Safepoint = null

        val computation1 = Effect.defer {
            capturedSafepoint = Safepoint.get
            42
        }

        val computation2 = Effect.defer {
            val currentSafepoint = Safepoint.get
            assert(currentSafepoint ne capturedSafepoint)
            84
        }

        for
            _      <- fork(computation1.eval)
            result <- computation2
        yield assert(result == 84)
        end for
    }

    "no new Safepoint for nested eval calls" in run {
        val outerComputation = Effect.defer {
            val outerSafepoint = Safepoint.get
            val innerComputation = Effect.defer {
                val innerSafepoint = Safepoint.get
                assert(innerSafepoint eq outerSafepoint)
                21
            }
            innerComputation.eval * 2
        }

        outerComputation.map(result => assert(result == 42))
    }

    "capture Safepoint in closures" in run {
        var capturedSafepoint: Safepoint = null

        val computation = Effect.defer {
            capturedSafepoint = Safepoint.get
            () => Safepoint.get // Return a closure that accesses Safepoint
        }

        val closure = computation.eval

        Effect.defer {
            val currentSafepoint = Safepoint.get
            assert(currentSafepoint eq capturedSafepoint)
            assert(closure() eq currentSafepoint)
        }
    }

    "forced runtime leak" in {
        val runtime = Safepoint.get

        val res =
            fork {
                given Safepoint = runtime
                (1: Int < Any).map(_ + 1).map(_ + 2)
            }

        assert(res.eval == 4)
    }

    "forced runtime leak + eval" in {
        val runtime = Safepoint.get

        val res =
            fork {
                given Safepoint = runtime
                (1: Int < Any).map(_ + 1).map(_ + 2).eval
            }

        assert(res.eval == 4)
    }

    "interceptors" - {
        abstract class TestInterceptor extends Safepoint.Interceptor:
            def addEnsure(f: () => Unit): Unit    = {}
            def removeEnsure(f: () => Unit): Unit = {}

        "immediate" - {

            "use the interceptor" in {
                var executed = false
                val interceptor = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        executed = true
                        true

                Safepoint.immediate(interceptor)((1: Int < Any).map(_ + 1).eval)
                assert(executed)
            }

            "restore previous interceptor" in {
                var count = 0
                val interceptor1 = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        count += 1
                        true

                val interceptor2 = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        count += 10
                        true

                Safepoint.immediate(interceptor1) {
                    Safepoint.immediate(interceptor2)((1: Int < Any).map(_ + 1).eval)
                }.eval

                assert(count == 11)
            }
        }

        "propagating" - {
            "through suspensions" in {
                var count = 0
                val interceptor = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        count += 1
                        true

                def suspendingFunction(): Int < Any =
                    Effect.defer(42).map(_ + 1)

                Safepoint.propagating(interceptor) {
                    for
                        _ <- suspendingFunction()
                        _ <- suspendingFunction()
                    yield 42
                }.eval

                assert(count == 4)
            }

            "restores previous interceptor after completion" in {
                var outerCount = 0
                var innerCount = 0

                val outerInterceptor = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        outerCount += 1
                        true

                val innerInterceptor = new TestInterceptor:
                    def ensure(f: () => Unit): Unit = ()
                    def enter(frame: Frame, value: Any): Boolean =
                        innerCount += 1
                        true

                def suspendingFunction(): Int < Any =
                    Effect.defer(42).map(_ + 1)

                Safepoint.immediate(outerInterceptor) {
                    Safepoint.propagating(innerInterceptor) {
                        for
                            _ <- suspendingFunction()
                            _ <- suspendingFunction()
                        yield 42
                    }.map(_ => suspendingFunction())
                }.eval

                assert(outerCount == 0)
                assert(innerCount == 4)
            }
        }

        "example logging interceptor" in {
            import scala.collection.mutable.ArrayBuffer

            class LoggingInterceptor extends TestInterceptor:
                val logs = ArrayBuffer.empty[String]

                def enter(frame: Frame, value: Any): Boolean =
                    val parsed = frame.parse
                    logs += s"Entering ${parsed.methodName} with value: $value"
                    true
                end enter

                def exit(): Unit =
                    logs += "Exiting method"
            end LoggingInterceptor

            val interceptor = new LoggingInterceptor()

            def computation(x: Int): Int < Any =
                Effect.defer {
                    x + 1
                }.map { y =>
                    Effect.defer {
                        y * 2
                    }
                }

            val result = Safepoint.propagating(interceptor) {
                for
                    a <- computation(5)
                    b <- computation(a)
                yield b
            }.eval

            assert(result == 26)

            val expectedLogs = Seq(
                "Entering computation with value: 6",
                "Entering ? with value: 12",
                "Entering computation with value: 13",
                "Entering $anonfun with value: 26"
            )

            assert(interceptor.logs == expectedLogs)
        }

        "example wall-clock profiling interceptor" in {
            import scala.collection.mutable.Stack

            class ProfilingInterceptor extends TestInterceptor:
                val starts = Stack.empty[(Frame, Long)]
                var log    = Stack.empty[(Frame, Long)]

                def enter(frame: Frame, value: Any): Boolean =
                    if starts.nonEmpty then
                        val (frame, start) = starts.pop()
                        log.push((frame, System.nanoTime() - start))
                    starts.push((frame, System.nanoTime()))
                    true
                end enter

            end ProfilingInterceptor

            val interceptor = new ProfilingInterceptor

            def computation(x: Int): Int < Any =
                Effect.defer {
                    Thread.sleep(1)
                    x + 1
                }.map { y =>
                    Effect.defer {
                        Thread.sleep(1)
                        y * 2
                    }
                }

            val result = Safepoint.propagating(interceptor) {
                for
                    a <- computation(5)
                    b <- computation(a)
                yield b
            }.eval

            assert(result == 26)

            interceptor.log.foreach { case (frame, duration) =>
                assert(duration > 0)
                assert(duration < 5000000)
            }

            assert(interceptor.log.size == 3)
            assert(interceptor.log.exists(_._1.parse.methodName == "computation"))
        }
    }

    "ensure" - {
        sealed trait TestEffect1 extends ArrowEffect[Const[Int], Const[Int]]

        "executes cleanup after successful completion" in {
            var cleaned = false
            val result = Safepoint.ensure { cleaned = true } {
                42
            }
            assert(result.eval == 42)
            assert(cleaned)
        }

        "executes cleanup after exception" in {
            var cleaned = false
            assertThrows[RuntimeException] {
                Safepoint.ensure { cleaned = true } {
                    throw new RuntimeException("Test exception")
                }.eval
            }
            assert(cleaned)
        }

        "nested ensures" in {
            var outer = false
            var inner = false
            val result = Safepoint.ensure { outer = true } {
                Safepoint.ensure { inner = true } {
                    42
                }
            }
            assert(result.eval == 42)
            assert(outer)
            assert(inner)
        }

        "cleanup functions execute in reverse order" in {
            val order = scala.collection.mutable.ArrayBuffer[Int]()
            val result = Safepoint.ensure { order += 1 } {
                Safepoint.ensure { order += 2 } {
                    Safepoint.ensure { order += 3 } {
                        42
                    }
                }
            }
            assert(result.eval == 42)
            assert(order == Seq(3, 2, 1))
        }

        "works with effects" in {
            var cleaned = false
            val result = Safepoint.ensure { cleaned = true } {
                for
                    x <- ArrowEffect.suspend[Int](Tag[TestEffect1], 5)
                    y <- ArrowEffect.suspend[Int](Tag[TestEffect1], 6)
                yield x + y
            }
            val handled = ArrowEffect.handle(Tag[TestEffect1], result)([C] => (input, cont) => cont(input))
            assert(handled.eval == 11)
            assert(cleaned)
        }

        "executes cleanup when effect fails" in {
            var cleaned = false
            val result = Safepoint.ensure { cleaned = true } {
                for
                    _ <- ArrowEffect.suspend[Int](Tag[TestEffect1], 5)
                    _ <- ArrowEffect.suspend[Int](Tag[TestEffect1], throw new RuntimeException("Test failure"))
                yield 42
            }
            assertThrows[RuntimeException] {
                ArrowEffect.handle(Tag[TestEffect1], result)([C] => (input, cont) => cont(input)).eval
            }
            assert(cleaned)
        }

        "works with defer" in {
            var cleaned = false
            val suspended = Safepoint.ensure { cleaned = true } {
                Effect.defer(42)
            }
            assert(!cleaned)
            assert(suspended.eval == 42)
            assert(cleaned)
        }

        "executes thunk only once" in {
            var count = 0
            val effect = Safepoint.ensure {
                count += 1
            } {
                42
            }
            assert(effect.eval == 42)
            assert(count == 1)
        }

        "executes thunk on normal completion" in {
            var executed = false
            val effect = Safepoint.ensure {
                executed = true
            } {
                "result"
            }
            assert(effect.eval == "result")
            assert(executed)
        }

        "executes thunk on exception" in {
            var executed = false
            assertThrows[RuntimeException] {
                Safepoint.ensure {
                    executed = true
                } {
                    throw new RuntimeException("Test exception")
                }.eval
            }
            assert(executed)
        }

        "nested ensure executes all thunks" in {
            var outer = 0
            var inner = 0
            val effect = Safepoint.ensure {
                outer += 1
            } {
                Safepoint.ensure {
                    inner += 1
                } {
                    "nested result"
                }
            }
            assert(effect.eval == "nested result")
            assert(outer == 1)
            assert(inner == 1)
        }

        "multiple evaluations execute thunk only once" in {
            var count = 0
            val effect = Safepoint.ensure {
                count += 1
            } {
                count
            }
            assert(effect.eval == 0)
            assert(effect.eval == 0)
            assert(count == 1)
        }

        "executes thunk only once with map" in {
            var count = 0
            val effect = Safepoint.ensure {
                count += 1
            } {
                42
            }.map { value =>
                Safepoint.ensure {
                    count += 1
                } {
                    value * 2
                }
            }
            assert(effect.eval == 84)
            assert(count == 2)
        }

        "with interceptor" - {

            class TestInterceptor extends Safepoint.Interceptor:
                var ensuresAdded: List[() => Unit]             = Nil
                var ensuresRemoved: List[() => Unit]           = Nil
                override def addEnsure(f: () => Unit): Unit    = ensuresAdded = f :: ensuresAdded
                override def removeEnsure(f: () => Unit): Unit = ensuresRemoved = f :: ensuresRemoved
                def enter(frame: Frame, value: Any): Boolean   = true
                def exit(): Unit                               = {}
            end TestInterceptor

            "passes ensure function to Interceptor" in {
                val interceptor = new TestInterceptor

                def testEnsure[A, S](v: => A < S)(using Frame): A < S =
                    Safepoint.ensure(())(v)

                Safepoint.immediate(interceptor) {
                    testEnsure {
                        assert(interceptor.ensuresAdded.size == 1)
                        assert(interceptor.ensuresRemoved.isEmpty)
                        42
                    }.eval
                }

                assert(interceptor.ensuresAdded.size == 1)
                assert(interceptor.ensuresRemoved.size == 1)
                assert(interceptor.ensuresAdded.equals(interceptor.ensuresRemoved))
            }

            "calls removeEnsure on completion" in {
                val interceptor = new TestInterceptor

                def testEnsure[A, S](v: => A < S)(using Frame): A < S =
                    Safepoint.ensure(())(v)

                Safepoint.immediate(interceptor) {
                    testEnsure {
                        42
                    }.eval
                }

                assert(interceptor.ensuresRemoved.size == 1)
            }

            "handles nested ensures" in {
                val interceptor = new TestInterceptor

                def testEnsure[A, S](v: => A < S)(using Frame): A < S =
                    Safepoint.ensure(())(v)

                Safepoint.immediate(interceptor) {
                    testEnsure {
                        testEnsure {
                            testEnsure {
                                42
                            }
                        }
                    }.eval
                }

                assert(interceptor.ensuresAdded.size == 3)
                assert(interceptor.ensuresRemoved.size == 3)
            }

            "interceptor can call the ensure function multiple times but it evaluates once" in {
                var count            = 0
                var interceptorCalls = 0

                val interceptor = new Safepoint.Interceptor:
                    override def addEnsure(f: () => Unit): Unit =
                        interceptorCalls += 1
                        f()
                        f()
                    end addEnsure

                    override def removeEnsure(f: () => Unit): Unit = {}
                    def enter(frame: Frame, value: Any): Boolean   = true
                    def exit(): Unit                               = {}

                val effect = Safepoint.propagating(interceptor) {
                    Safepoint.ensure {
                        count += 1
                    } {
                        42
                    }
                }

                assert(effect.eval == 42)
                assert(count == 1)
                assert(interceptorCalls == 1)
            }
            "executes ensure function without interceptor" in {
                var ensureExecuted    = false
                var interceptorActive = false

                val interceptor = new Safepoint.Interceptor:
                    override def addEnsure(f: () => Unit): Unit    = {}
                    override def removeEnsure(f: () => Unit): Unit = {}
                    def enter(frame: Frame, value: Any): Boolean   = true
                    def exit(): Unit                               = {}

                val effect = Safepoint.propagating(interceptor) {
                    Safepoint.ensure {
                        ensureExecuted = true
                        interceptorActive = Safepoint.get.interceptor != null
                    } {
                        42
                    }
                }

                assert(effect.eval == 42)
                assert(ensureExecuted)
                assert(!interceptorActive)
            }

            "executes ensure function without interceptor even on exception" in {
                var ensureExecuted    = false
                var interceptorActive = false

                val interceptor = new Safepoint.Interceptor:
                    override def addEnsure(f: () => Unit): Unit    = {}
                    override def removeEnsure(f: () => Unit): Unit = {}
                    def enter(frame: Frame, value: Any): Boolean   = true
                    def exit(): Unit                               = {}

                assertThrows[RuntimeException] {
                    Safepoint.propagating(interceptor) {
                        Safepoint.ensure {
                            ensureExecuted = true
                            interceptorActive = Safepoint.get.interceptor != null
                        } {
                            throw new RuntimeException("Test exception")
                        }
                    }.eval
                }
                assert(ensureExecuted)
                assert(!interceptorActive)
            }
        }
    }

end SafepointTest
