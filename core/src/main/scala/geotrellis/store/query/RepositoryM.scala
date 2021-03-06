/*
 * Copyright 2020 Azavea
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

package geotrellis.store.query

import cats.syntax.apply._
import cats.syntax.semigroupk._
import cats.{FlatMap, Semigroup, SemigroupK}

trait RepositoryM[M[_], G[_], T] {
  def store: M[G[T]]
  def find(query: Query): M[G[T]]
}

object RepositoryM {
  implicit def semigroupRepositoryM[M[_]: FlatMap, G[_]: SemigroupK, T]: Semigroup[RepositoryM[M, G, T]] =
    Semigroup.instance { (l, r) =>
      new RepositoryM[M, G, T] {
        def store: M[G[T]] = l.store.map2(r.store)(_ <+> _)
        def find(query: Query): M[G[T]] = l.find(query).map2(r.find(query))(_ <+> _)
      }
    }
}
