package magnolia.scalacheck

import magnolia._
import mercator.Monadic
import org.scalacheck._

import scala.language.experimental.macros

object ArbDerivation {
  type Typeclass[T] = Arbitrary[T]

  def combine[T](caseClass: CaseClass[Typeclass, T]): Typeclass[T] = Arbitrary {
    Gen.lzy(caseClass.constructMonadic(_.typeclass.arbitrary)(monadicGen))
  }

  def dispatch[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] = Arbitrary {
    Gen.oneOf(sealedTrait.subtypes.map(_.typeclass.arbitrary)).flatMap(identity)
  }

  private val monadicGen: Monadic[Gen] = new Monadic[Gen] {
    override def point[A](value: A): Gen[A] = Gen.const(value)

    override def flatMap[A, B](from: Gen[A])(fn: A => Gen[B]): Gen[B] = from.flatMap(fn)

    override def map[A, B](from: Gen[A])(fn: A => B): Gen[B] = from.map(fn)
  }

  implicit def gen[T]: Arbitrary[T] = macro Magnolia.gen[T]
}