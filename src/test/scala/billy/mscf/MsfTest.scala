package billy.mscf

import org.scalatest.GivenWhenThen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scalaz.Maybe
import scalaz.Maybe.{ Empty, Just }
import scalaz.effect.IO

class MsfSpec extends AnyFunSpec with GivenWhenThen {

  describe("MSF") {
    it("should produce an output and continuation when stepped") {
      Given("a MSF with a known output and continuation")
      val squaringMsf: Msf[Maybe, Double, Double] = Msf.arr { x => x * x }

      When("the MSF is stepped with a known input")
      val maybeOutAndCont = Msf.step(squaringMsf)(2.0d)

      Then("the output and continuation should be as expected")
      maybeOutAndCont match {
        case Just((out, cont)) => assert(out == 4 && cont == squaringMsf)
        case Empty()           => fail()
      }
    }
  }

}
