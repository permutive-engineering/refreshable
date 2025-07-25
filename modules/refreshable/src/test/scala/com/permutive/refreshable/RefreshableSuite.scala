/*
 * Copyright 2022-2025 Permutive Ltd. <https://permutive.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.permutive.refreshable

import scala.concurrent.duration._

import cats.arrow.FunctionK
import cats.effect._
import cats.effect.testkit.TestControl
import cats.syntax.all._

import munit.CatsEffectSuite
import retry._

class RefreshableSuite extends CatsEffectSuite {

  def suite(factory: RefreshableFactory)(implicit loc: munit.Location): Unit = {

    test(s"${factory.name} - Uses initial value if available") {
      factory
        .resource[Int](
          refresh = IO.pure(1),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = Some(2),
          retryPolicy = None
        )
        .use { r =>
          r.get.assertEquals(CachedValue.Success(1))
        }
    }

    test(s"${factory.name} - Retries on failure") {

      val cacheTTL = 2.seconds

      val run = IO.ref(0).flatMap { state =>
        factory
          .resource[Int](
            // Initial evaluation succeeds but first refresh will fail and need to be retried
            refresh = state.getAndUpdate(_ + 1).flatTap { curr =>
              IO.raiseError(Boom).whenA(curr === 1)
            },
            cacheDuration = _ => cacheTTL,
            onRefreshFailure = { case _ => IO.unit },
            onExhaustedRetries = { case _ => IO.unit },
            onNewValue = None,
            defaultValue = Some(0),
            retryPolicy = None
          )
          .use { r =>
            // Wait long enough for multiple retries
            IO.sleep(3.seconds) >> r.get.assertEquals(CachedValue.Success(2))
          }
      }

      TestControl.executeEmbed(run)
    }

    test(s"${factory.name} - Exhausted retries") {

      val cacheTTL = 1.seconds

      val run = IO.ref(0).flatMap { state =>
        factory
          .resource[Int](
            // Initial evaluation succeeds but all refreshes fail
            refresh = state.getAndUpdate(_ + 1).flatTap { curr =>
              IO.raiseError(Boom).whenA(curr > 0)
            },
            cacheDuration = _ => cacheTTL,
            onRefreshFailure = { case _ => IO.unit },
            onExhaustedRetries = { case _ => IO.unit },
            onNewValue = None,
            defaultValue = Some(2),
            retryPolicy = None
          )
          .use { r =>
            // Wait long enough for retries to be exhausted
            IO.sleep(cacheTTL * 5) >> r.get
              .assertEquals(CachedValue.Error(0, Boom))
          }
      }

      TestControl.executeEmbed(run)
    }

    test(s"${factory.name} - Uses default value if construction fails") {
      factory
        .resource[Int](
          refresh = IO.raiseError(Boom),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = Some(2),
          retryPolicy = None
        )
        .use { r =>
          r.value.assertEquals(2)
        }
    }

    test(
      s"${factory.name} - Throws if construction fails and no default value provided"
    ) {
      factory
        .resource[Int](
          refresh = IO.raiseError(Boom),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = None,
          retryPolicy = None
        )
        .use_
        .intercept[Boom.type]
    }

    test(s"${factory.name} - Cancelation") {

      val cacheTTL = 1.second

      val run = IO.ref(0).flatMap { state =>
        factory
          .resource[Int](
            refresh = state.getAndUpdate(_ + 1),
            cacheDuration = _ => cacheTTL,
            onRefreshFailure = { case _ => IO.unit },
            onExhaustedRetries = { case _ => IO.unit },
            onNewValue = None,
            defaultValue = None,
            retryPolicy = None
          )
          .use { r =>
            r.cancel
              .assertEquals(true) >> r.get
              .assertEquals(CachedValue.Cancelled(0)) >>
              IO.sleep(cacheTTL * 2) >>
              // Check that the background fiber really is dead and not still refreshing
              r.get.assertEquals(CachedValue.Cancelled(0))
          }
      }

      TestControl.executeEmbed(run)
    }

    test(s"${factory.name} - Cancelation race") {
      val run = factory
        .resource[Int](
          refresh = IO.pure(1),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = None,
          retryPolicy = None
        )
        .use { r =>
          r.cancel.both(r.cancel).flatMap { res =>
            IO(assert(Set(true -> false, false -> true).contains(clue(res))))
          } >> r.get
            .assertEquals(CachedValue.Cancelled(1))
        }

      run.replicateA_(100)
    }

    test(s"${factory.name} - Restart when not canceled") {
      factory
        .resource[Int](
          refresh = IO.pure(1),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = None,
          retryPolicy = None
        )
        .use { r =>
          r.restart.assertEquals(false)
        }
    }

    test(s"${factory.name} - Cancel then restart") {

      val cacheTTL = 1.second

      val run = factory
        .resource[Int](
          refresh = IO.pure(0),
          cacheDuration = _ => cacheTTL,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = None,
          retryPolicy = None
        )
        .use { r =>
          r.cancel >>
            r.get.assertEquals(CachedValue.Cancelled(0)) >>
            r.restart.assertEquals(true) >> IO.sleep(cacheTTL * 2) >>
            r.get.assertEquals(CachedValue.Success(0))
        }

      TestControl.executeEmbed(run)
    }

    test(s"${factory.name} - Restart race") {
      val run = factory
        .resource[Int](
          refresh = IO.pure(0),
          cacheDuration = _ => 1.second,
          onRefreshFailure = { case _ => IO.unit },
          onExhaustedRetries = { case _ => IO.unit },
          onNewValue = None,
          defaultValue = None,
          retryPolicy = None
        )
        .use { r =>
          r.cancel >> r.restart.both(r.restart).flatMap { res =>
            IO(assert(Set(true -> false, false -> true).contains(clue(res))))
          } >> IO.sleep(5.seconds) >>
            r.get.assertEquals(CachedValue.Success(0))
        }

      TestControl.executeEmbed(run).replicateA_(100)
    }

    test(s"${factory.name} - onRefreshFailure is invoked if refresh fails") {

      val cacheTTL = 1.second

      val run = IO.ref(false).flatMap { ref =>
        factory
          .resource[Int](
            refresh = IO.raiseError(Boom),
            cacheDuration = _ => cacheTTL,
            onRefreshFailure = { case _ => ref.set(true) },
            onExhaustedRetries = { case _ => IO.unit },
            onNewValue = None,
            defaultValue = Some(5),
            retryPolicy = None
          )
          .use { _ =>
            IO.sleep(cacheTTL * 2) >> ref.get.assertEquals(true)
          }
      }

      TestControl.executeEmbed(run)
    }

    test(
      s"${factory.name} - onExhaustedRetries is invoked if refresh policy is exhausted"
    ) {

      val retryPeriod = 1.second

      val run = IO.ref(false).flatMap { ref =>
        factory
          .resource[Int](
            refresh = IO.raiseError(Boom),
            cacheDuration = _ => 5.millis,
            onRefreshFailure = { case _ => IO.unit },
            onExhaustedRetries = { case _ => ref.set(true) },
            onNewValue = None,
            defaultValue = Some(5),
            retryPolicy = Some(
              RetryPolicies
                .constantDelay[IO](retryPeriod)
                .join(RetryPolicies.limitRetries(1))
            )
          )
          .use { _ =>
            IO.sleep(retryPeriod * 2) >> ref.get.assertEquals(true)
          }
      }

      TestControl.executeEmbed(run)

    }

    test(s"${factory.name} - onNewValue is invoked with the expected values") {

      val run = IO.ref(0).flatMap { state =>
        IO.ref((0, 0.millis)).flatMap { result =>
          factory
            .resource[Int](
              refresh = state.getAndUpdate(_ + 1),
              cacheDuration = _ => 2.seconds,
              onRefreshFailure = { case _ => IO.unit },
              onExhaustedRetries = { case _ => IO.unit },
              onNewValue = Some((next: Int, d: FiniteDuration) => result.set(next -> d)),
              defaultValue = None,
              retryPolicy = None
            )
            .use { _ =>
              IO.sleep(3.seconds) >> result.get.assertEquals(1 -> 2.seconds)
            }
        }
      }

      TestControl.executeEmbed(run)
    }

    test(
      s"${factory.name} - no longer retries once retry policy is exhausted"
    ) {
      val run = IO.monotonic.flatMap { start =>
        factory
          .resource[Int](
            // Refresh fails for the first 2 seconds then succeeds
            refresh = IO.monotonic
              .map(_ - start)
              .flatMap(t => IO.raiseError(Boom).whenA(t < 2.seconds))
              .as(1),
            cacheDuration = _ => 1.millis,
            onRefreshFailure = { case _ => IO.unit },
            onExhaustedRetries = { case _ => IO.unit },
            onNewValue = None,
            defaultValue = Some(5),
            retryPolicy = Some(
              // Retries will stop after 1 second
              RetryPolicies
                .constantDelay[IO](200.millis)
                .join(RetryPolicies.limitRetries(5))
            )
          )
          .use { r =>
            // Refresh initially fails
            r.get.assertEquals(CachedValue.Error(5, Boom)) >>
              // Wait >>> retry period
              IO.sleep(10.seconds) >>
              // Retry would succeed now but fiber should have exited after it exhausted the retry policy
              // so we should still see `CachedValue.Error(5, Boom)`
              r.get.assertEquals(
                CachedValue.Error(5, Boom)
              )
          }
      }

      TestControl.executeEmbed(run)

    }

    test(s"${factory.name} - newValueSelector changes resulting value") {

      val run =
        factory
          .resource[Int](
            refresh = IO(1),
            cacheDuration = _ => 2.seconds,
            onRefreshFailure = { case _ =>
              IO.unit
            },
            onExhaustedRetries = { case _ =>
              IO.unit
            },
            combine = Some((oldV: CachedValue[Int], newV: CachedValue[Int]) => IO(oldV.value + newV.value))
          )
          .use { refreshable =>
            IO.sleep(3.seconds) >> refreshable.value.assertEquals(2)
          }

      TestControl.executeEmbed(run)
    }
  }

  suite(Default)
  suite(Updates)
  suite(MapK)

  test("See all updates") {
    val run = IO.ref(0).flatMap { state =>
      Refreshable.builder(state.getAndUpdate(_ + 1)).resource.use { r =>
        r.updates
          .take(5)
          .compile
          .toList
          .assertEquals(List.range(0, 5).map(CachedValue.Success(_)))
      }
    }

    TestControl.executeEmbed(run)
  }

  object Default extends RefreshableFactory {

    override val name: String = "default"

    override def resource[A](
        refresh: IO[A],
        cacheDuration: A => FiniteDuration,
        onRefreshFailure: PartialFunction[(Throwable, RetryDetails), IO[Unit]],
        onExhaustedRetries: PartialFunction[Throwable, IO[Unit]],
        onNewValue: Option[(A, FiniteDuration) => IO[Unit]] = None,
        combine: Option[(CachedValue[A], CachedValue[A]) => IO[A]] = None,
        defaultValue: Option[A] = None,
        retryPolicy: Option[RetryPolicy[IO]] = None
    ): Resource[IO, Refreshable[IO, A]] = {
      val b1 = Refreshable
        .builder(refresh)
        .cacheDuration(cacheDuration)
        .onRefreshFailure(onRefreshFailure)
        .onExhaustedRetries(onExhaustedRetries)

      val b2 = onNewValue.fold(b1)(v => b1.onNewValue(v))

      val b3 = combine.fold(b2)(v => b2.combine(v))

      val b4 = defaultValue.fold(b3)(v => b3.defaultValue(v))

      retryPolicy.fold(b4)(v => b4.retryPolicy(v)).resource
    }

  }

  object Updates extends RefreshableFactory {

    override val name: String = "updates"

    override def resource[A](
        refresh: IO[A],
        cacheDuration: A => FiniteDuration,
        onRefreshFailure: PartialFunction[(Throwable, RetryDetails), IO[Unit]],
        onExhaustedRetries: PartialFunction[Throwable, IO[Unit]],
        onNewValue: Option[(A, FiniteDuration) => IO[Unit]] = None,
        combine: Option[(CachedValue[A], CachedValue[A]) => IO[A]] = None,
        defaultValue: Option[A] = None,
        retryPolicy: Option[RetryPolicy[IO]] = None
    ): Resource[IO, Refreshable[IO, A]] = {
      val b1 = Refreshable
        .builder(refresh)
        .cacheDuration(cacheDuration)
        .onRefreshFailure(onRefreshFailure)
        .onExhaustedRetries(onExhaustedRetries)

      val b2 = onNewValue.fold(b1)(v => b1.onNewValue(v))

      val b3 = combine.fold(b2)(v => b2.combine(v))

      val b4 = defaultValue.fold(b3)(v => b3.defaultValue(v))

      retryPolicy.fold(b4)(v => b4.retryPolicy(v)).resource

    }

  }

  object MapK extends RefreshableFactory {

    override val name: String = "mapK"

    override def resource[A](
        refresh: IO[A],
        cacheDuration: A => FiniteDuration,
        onRefreshFailure: PartialFunction[(Throwable, RetryDetails), IO[Unit]],
        onExhaustedRetries: PartialFunction[Throwable, IO[Unit]],
        onNewValue: Option[(A, FiniteDuration) => IO[Unit]] = None,
        combine: Option[(CachedValue[A], CachedValue[A]) => IO[A]] = None,
        defaultValue: Option[A] = None,
        retryPolicy: Option[RetryPolicy[IO]] = None
    ): Resource[IO, Refreshable[IO, A]] = Default
      .resource(
        refresh, cacheDuration, onRefreshFailure, onExhaustedRetries, onNewValue, combine, defaultValue, retryPolicy
      )
      .map(_.mapK(FunctionK.id[IO]))

  }

  trait RefreshableFactory {

    def name: String

    def resource[A](
        refresh: IO[A],
        cacheDuration: A => FiniteDuration,
        onRefreshFailure: PartialFunction[(Throwable, RetryDetails), IO[Unit]],
        onExhaustedRetries: PartialFunction[Throwable, IO[Unit]],
        onNewValue: Option[(A, FiniteDuration) => IO[Unit]] = None,
        combine: Option[(CachedValue[A], CachedValue[A]) => IO[A]] = None,
        defaultValue: Option[A] = None,
        retryPolicy: Option[RetryPolicy[IO]] = None
    ): Resource[IO, Refreshable[IO, A]]

  }

  object Boom extends RuntimeException("BOOM")

}
