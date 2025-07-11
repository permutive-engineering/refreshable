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

sealed trait CachedValue[A] {

  def value: A

  def map[B](f: A => B): CachedValue[B] = this match {
    case CachedValue.Success(value)      => CachedValue.Success(f(value))
    case CachedValue.Error(value, error) => CachedValue.Error(f(value), error)
    case CachedValue.Cancelled(value)    => CachedValue.Cancelled(f(value))
  }

}

object CachedValue {

  final case class Success[A](value: A) extends CachedValue[A]

  final case class Error[A](value: A, error: Throwable) extends CachedValue[A]

  final case class Cancelled[A](value: A) extends CachedValue[A]

}
