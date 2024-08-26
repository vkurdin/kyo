package kyo

import Var.internal.*
import kyo.Tag
import kyo.kernel.*
import scala.annotation.nowarn

sealed trait Var[V] extends ArrowEffect[Const[Op[V]], Const[V]]

object Var:

    /** Obtains the current value of the 'Var'. */
    inline def get[V](using inline tag: Tag[Var[V]], inline frame: Frame): V < Var[V] =
        use[V](identity)

    final class UseOps[V](dummy: Unit) extends AnyVal:
        /** Invokes the provided function with the current value of the `Var`. */
        inline def apply[A, S](inline f: V => A < S)(
            using
            inline tag: Tag[Var[V]],
            inline frame: Frame
        ): A < (Var[V] & S) =
            ArrowEffect.suspendMap[V](tag, Get: Op[V])(f)
    end UseOps

    inline def use[V]: UseOps[V] = UseOps(())

    /** Sets a new value and returns the previous one. */
    inline def set[V](inline value: V)(using inline tag: Tag[Var[V]], inline frame: Frame): V < Var[V] =
        ArrowEffect.suspend[Unit](tag, value: Op[V])

    /** Sets a new value and returns `Unit`. */
    inline def setDiscard[V](inline value: V)(using inline tag: Tag[Var[V]], inline frame: Frame): Unit < Var[V] =
        ArrowEffect.suspendMap[Unit](tag, value: Op[V])(_ => ())

    /** Applies the update function and returns the new value. */
    @nowarn("msg=anonymous")
    inline def update[V](inline f: V => V)(using inline tag: Tag[Var[V]], inline frame: Frame): V < Var[V] =
        ArrowEffect.suspend[V](tag, (v => f(v)): Update[V])

    /** Applies the update function and returns `Unit`. */
    @nowarn("msg=anonymous")
    inline def updateDiscard[V](inline f: V => V)(using inline tag: Tag[Var[V]], inline frame: Frame): Unit < Var[V] =
        ArrowEffect.suspendMap[Unit](tag, (v => f(v)): Update[V])(_ => ())

    private inline def runWith[V, A: Flat, S, B, S2](state: V)(v: A < (Var[V] & S))(
        inline f: (V, A) => B < S2
    )(using inline tag: Tag[Var[V]], inline frame: Frame): B < (S & S2) =
        ArrowEffect.handle.state(tag, state, v)(
            [C] =>
                (input, state, cont) =>
                    input match
                        case input: Get.type =>
                            (state, cont(state))
                        case input: Update[V] @unchecked =>
                            val nst = input(state)
                            (nst, cont(nst))
                        case input: V @unchecked =>
                            (input, cont(state)),
            done = f
        )

    /** Handles the effect and discards the 'Var' state. */
    def run[V, A: Flat, S](state: V)(v: A < (Var[V] & S))(using Tag[Var[V]], Frame): A < S =
        runWith(state)(v)((_, result) => result)

    /** Handles the effect and returns a tuple with the final `Var` state and the computation's result. */
    def runTuple[V, A: Flat, S](state: V)(v: A < (Var[V] & S))(using Tag[Var[V]], Frame): (V, A) < S =
        runWith(state)(v)((state, result) => (state, result))

    object internal:
        type Op[V] = Get.type | V | Update[V]
        object Get
        abstract class Update[V]:
            def apply(v: V): V
    end internal

end Var
