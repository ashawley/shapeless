/*
 * Copyright (c) 2014-16 Miles Sabin
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

package shapeless

import org.junit.Test
import org.junit.Assert._

import ops.record._
import record._
import syntax.singleton._
import test._
import testutil._
import union._

object LabelledGenericTestsAux {
  case class Book(author: String, title: String, id: Int, price: Double)
  case class ExtendedBook(author: String, title: String, id: Int, price: Double, inPrint: Boolean)
  case class BookWithMultipleAuthors(title: String, id: Int, authors: String*)

  val tapl = Book("Benjamin Pierce", "Types and Programming Languages", 262162091, 44.11)
  val tapl2 = Book("Benjamin Pierce", "Types and Programming Languages (2nd Ed.)", 262162091, 46.11)
  val taplExt = ExtendedBook("Benjamin Pierce", "Types and Programming Languages", 262162091, 44.11, true)
  val dp = BookWithMultipleAuthors(
    "Design Patterns", 201633612,
    "Erich Gamma", "Richard Helm", "Ralph Johnson", "John Vlissides"
  )

  val taplRecord =
    (sym"author" ->> "Benjamin Pierce") ::
    (sym"title"  ->> "Types and Programming Languages") ::
    (sym"id"     ->>  262162091) ::
    (sym"price"  ->>  44.11) ::
    HNil

  val dpRecord =
    (sym"title"   ->> "Design Patterns") ::
    (sym"id"      ->> 201633612) ::
    (sym"authors" ->> Seq("Erich Gamma", "Richard Helm", "Ralph Johnson", "John Vlissides")) ::
    HNil

  type BookRec = Record.`sym"author" -> String, sym"title" -> String, sym"id" -> Int, sym"price" -> Double`.T
  type BookKeys = Keys[BookRec]
  type BookValues = Values[BookRec]

  type BookWithMultipleAuthorsRec = Record.`sym"title" -> String, sym"id" -> Int, sym"authors" -> Seq[String]`.T


  sealed trait Tree
  case class Node(left: Tree, right: Tree) extends Tree
  case class Leaf(value: Int) extends Tree

  sealed trait AbstractNonCC
  class NonCCA(val i: Int, val s: String) extends AbstractNonCC
  class NonCCB(val b: Boolean, val d: Double) extends AbstractNonCC

  class NonCCWithCompanion private (val i: Int, val s: String)
  object NonCCWithCompanion {
    def apply(i: Int, s: String) = new NonCCWithCompanion(i, s)
    def unapply(s: NonCCWithCompanion): Option[(Int, String)] = Some((s.i, s.s))
  }

  class NonCCLazy(prev0: => NonCCLazy, next0: => NonCCLazy) {
    lazy val prev = prev0
    lazy val next = next0
  }
}

object ScalazTaggedAux {
  import labelled.FieldType

  type Tagged[A, T] = { type Tag = T; type Self = A }
  type @@[T, Tag] = Tagged[T, Tag]

  trait CustomTag
  case class Dummy(i: Int @@ CustomTag)
  case class DummyTagged(b: Boolean, i: Int @@ CustomTag)

  trait TC[T] {
    def apply(): String
  }

  object TC {
    implicit val intTC: TC[Int] =
      new TC[Int] {
        def apply() = "Int"
      }

    implicit val booleanTC: TC[Boolean] =
      new TC[Boolean] {
        def apply() = "Boolean"
      }

    implicit val taggedIntTC: TC[Int @@ CustomTag] =
      new TC[Int @@ CustomTag] {
        def apply() = s"TaggedInt"
      }

    implicit val hnilTC: TC[HNil] =
      new TC[HNil] {
        def apply() = "HNil"
      }

    implicit def hconsTCTagged[K <: Symbol, H, HT, T <: HList](implicit
      key: Witness.Aux[K],
      headTC: Lazy[TC[H @@ HT]],
      tailTC: Lazy[TC[T]]
    ): TC[FieldType[K, H @@ HT] :: T] =
      new TC[FieldType[K, H @@ HT] :: T] {
        def apply() = s"${key.value.name}: ${headTC.value()} :: ${tailTC.value()}"
      }

    implicit def hconsTC[K <: Symbol, H, T <: HList](implicit
      key: Witness.Aux[K],
      headTC: Lazy[TC[H]],
      tailTC: Lazy[TC[T]]
    ): TC[FieldType[K, H] :: T] =
      new TC[FieldType[K, H] :: T] {
        def apply() = s"${key.value.name}: ${headTC.value()} :: ${tailTC.value()}"
      }

    implicit def projectTC[F, G](implicit
      lgen: LabelledGeneric.Aux[F, G],
      tc: Lazy[TC[G]]
    ): TC[F] =
      new TC[F] {
        def apply() = s"Proj(${tc.value()})"
      }
  }
}

class LabelledGenericTests {
  import LabelledGenericTestsAux._

  @Test
  def testProductBasics: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)
    typed[BookRec](b0)
    assertEquals(taplRecord, b0)

    val b1 = gen.from(b0)
    typed[Book](b1)
    assertEquals(tapl, b1)

    val keys = b0.keys
    assertEquals(sym"author".narrow :: sym"title".narrow :: sym"id".narrow :: sym"price".narrow :: HNil, keys)

    val values = b0.values
    assertEquals("Benjamin Pierce" :: "Types and Programming Languages" :: 262162091 :: 44.11 :: HNil, values)
  }

  @Test
  def testProductWithVarargBasics: Unit = {
    val gen = LabelledGeneric[BookWithMultipleAuthors]

    val b0 = gen.to(dp)
    typed[BookWithMultipleAuthorsRec](b0)
    assertEquals(dpRecord, b0)

    val keys = b0.keys
    assertEquals(sym"title".narrow :: sym"id".narrow :: sym"authors".narrow :: HNil, keys)

    val values = b0.values
    assertEquals(
      "Design Patterns" :: 201633612 :: Seq("Erich Gamma", "Richard Helm", "Ralph Johnson", "John Vlissides") :: HNil,
      values
    )
  }

  @Test
  def testGet: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)

    val e1 = b0.get(sym"author")
    typed[String](e1)
    assertEquals("Benjamin Pierce", e1)

    val e2 = b0.get(sym"title")
    typed[String](e2)
    assertEquals( "Types and Programming Languages", e2)

    val e3 = b0.get(sym"id")
    typed[Int](e3)
    assertEquals(262162091, e3)

    val e4 = b0.get(sym"price")
    typed[Double](e4)
    assertEquals(44.11, e4, Double.MinPositiveValue)
  }

  @Test
  def testApply: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)

    val e1 = b0(sym"author")
    typed[String](e1)
    assertEquals("Benjamin Pierce", e1)

    val e2 = b0(sym"title")
    typed[String](e2)
    assertEquals( "Types and Programming Languages", e2)

    val e3 = b0(sym"id")
    typed[Int](e3)
    assertEquals(262162091, e3)

    val e4 = b0(sym"price")
    typed[Double](e4)
    assertEquals(44.11, e4, Double.MinPositiveValue)
  }

  @Test
  def testAt: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)

    val v1 = b0.at(0)
    typed[String](v1)
    assertEquals("Benjamin Pierce", v1)

    val v2 = b0.at(1)
    typed[String](v2)
    assertEquals( "Types and Programming Languages", v2)

    val v3 = b0.at(2)
    typed[Int](v3)
    assertEquals(262162091, v3)

    val v4 = b0.at(3)
    typed[Double](v4)
    assertEquals(44.11, v4, Double.MinPositiveValue)
  }

  @Test
  def testUpdated: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)

    val b1 = b0.updated(sym"title", "Types and Programming Languages (2nd Ed.)")
    val b2 = b1.updated(sym"price", 46.11)

    val updated = gen.from(b2)
    assertEquals(tapl2, updated)
  }

  @Test
  def testUpdateWith: Unit = {
    val gen = LabelledGeneric[Book]

    val b0 = gen.to(tapl)

    val b1 = b0.updateWith(sym"title")(_+" (2nd Ed.)")
    val b2 = b1.updateWith(sym"price")(_+2.0)

    val updated = gen.from(b2)
    assertEquals(tapl2, updated)
  }

  @Test
  def testExtension: Unit = {
    val gen = LabelledGeneric[Book]
    val gen2 = LabelledGeneric[ExtendedBook]

    val b0 = gen.to(tapl)
    val b1 = b0 + (sym"inPrint" ->> true)

    val b2 = gen2.from(b1)
    typed[ExtendedBook](b2)
    assertEquals(taplExt, b2)
  }

  @Test
  def testCoproductBasics: Unit = {
    type TreeUnion = Union.`sym"Leaf" -> Leaf, sym"Node" -> Node`.T

    val gen = LabelledGeneric[Tree]

    val t = Node(Node(Leaf(1), Leaf(2)), Leaf(3))
    val gt = gen.to(t)
    typed[TreeUnion](gt)
  }

  @Test
  def testAbstractNonCC: Unit = {
    val ncca = new NonCCA(23, "foo")
    val nccb = new NonCCB(true, 2.0)
    val ancc: AbstractNonCC = ncca

    type NonCCARec = Record.`sym"i" -> Int, sym"s" -> String`.T
    type NonCCBRec = Record.`sym"b" -> Boolean, sym"d" -> Double`.T
    type AbsUnion = Union.`sym"NonCCA" -> NonCCA, sym"NonCCB" -> NonCCB`.T

    val genA = LabelledGeneric[NonCCA]
    val genB = LabelledGeneric[NonCCB]
    val genAbs = LabelledGeneric[AbstractNonCC]

    val rA = genA.to(ncca)
    assertTypedEquals[NonCCARec](sym"i" ->> 23 :: sym"s" ->> "foo" :: HNil, rA)

    val rB = genB.to(nccb)
    assertTypedEquals[NonCCBRec](sym"b" ->> true :: sym"d" ->> 2.0 :: HNil, rB)

    val rAbs = genAbs.to(ancc)
    val injA = Coproduct[AbsUnion](sym"NonCCA" ->> ncca)
    assertTypedEquals[AbsUnion](injA, rAbs)

    val fA = genA.from(sym"i" ->> 13 :: sym"s" ->> "bar" :: HNil)
    typed[NonCCA](fA)
    assertEquals(13, fA.i)
    assertEquals("bar", fA.s)

    val fB = genB.from(sym"b" ->> false :: sym"d" ->> 3.0 :: HNil)
    typed[NonCCB](fB)
    assertEquals(false, fB.b)
    assertEquals(3.0, fB.d, Double.MinPositiveValue)

    val injB = Coproduct[AbsUnion](sym"NonCCB" ->> nccb)
    val fAbs = genAbs.from(injB)
    typed[AbstractNonCC](fAbs)
    assertTrue(fAbs.isInstanceOf[NonCCB])
    assertEquals(true, fAbs.asInstanceOf[NonCCB].b)
    assertEquals(2.0, fAbs.asInstanceOf[NonCCB].d, Double.MinPositiveValue)
  }

  @Test
  def testNonCCWithCompanion: Unit = {
    val nccc = NonCCWithCompanion(23, "foo")

    val rec = (sym"i" ->> 23) :: (sym"s" ->> "foo") :: HNil
    type NonCCRec = Record.`sym"i" -> Int, sym"s" -> String`.T

    val gen = LabelledGeneric[NonCCWithCompanion]

    val r = gen.to(nccc)
    assertTypedEquals[NonCCRec](rec, r)

    val f = gen.from(sym"i" ->> 13 :: sym"s" ->> "bar" :: HNil)
    typed[NonCCWithCompanion](f)
    assertEquals(13, f.i)
    assertEquals("bar", f.s)
  }

  @Test
  def testNonCCLazy: Unit = {
    lazy val (a: NonCCLazy, b: NonCCLazy, c: NonCCLazy) =
      (new NonCCLazy(c, b), new NonCCLazy(a, c), new NonCCLazy(b, a))

    val rec = sym"prev" ->> a :: sym"next" ->> c :: HNil
    type LazyRec = Record.`sym"prev" -> NonCCLazy, sym"next" -> NonCCLazy`.T

    val gen = LabelledGeneric[NonCCLazy]

    val rB = gen.to(b)
    assertTypedEquals[LazyRec](rec, rB)

    val fD = gen.from(sym"prev" ->> a :: sym"next" ->> c :: HNil)
    typed[NonCCLazy](fD)
    assertEquals(a, fD.prev)
    assertEquals(c, fD.next)
  }

  @Test
  def testScalazTagged: Unit = {
    import ScalazTaggedAux._

    implicitly[TC[Int @@ CustomTag]]
    implicitly[TC[Boolean]]

    implicitly[Generic[Dummy]]
    implicitly[LabelledGeneric[Dummy]]

    implicitly[TC[Dummy]]

    implicitly[TC[DummyTagged]]

    // Note: Further tests in LabelledGeneric211Tests
  }
}
