package com.paypal.stingray.common.tests.enumeration

import com.paypal.stingray.common.enumeration._
import com.paypal.stingray.common.validation._
import org.specs2.Specification

/**
 * Created by IntelliJ IDEA.
 * 
 * com.paypal.stingray.common.tests.enumeration
 * 
 * User: aaron
 * Date: 12/17/12
 * Time: 4:57 PM
 */
class EnumerationSpecs extends Specification { def is =
  "EnumerationSpecs".title                                                                                              ^
  "readEnum should return Some if the string is a valid enum"                                                           ! readEnumReturnsSome ^ end ^
  "readEnum should return None if the string is an invalid enum"                                                        ! readEnumReturnsNone ^ end ^
  "toEnum should return the enum value if the string is a valid enum"                                                   ! toEnumReturns ^ end ^
  "toEnum should throw if the string is an invalid enum value"                                                          ! toEnumThrows ^ end

  private sealed abstract class MyEnum extends Enumeration
  private object MyEnum1 extends MyEnum {
    override lazy val stringVal = "myenum1"
  }
  private object MyEnum2 extends MyEnum {
    override lazy val stringVal = "myenum2"
  }
  private implicit val reader = lowerEnumReader(MyEnum1, MyEnum2)

  private def readEnumReturnsSome = {
    MyEnum1.stringVal.readEnum[MyEnum] must beSome.like {
      case e => e must beEqualTo(MyEnum1)
    }
  }

  private def readEnumReturnsNone = {
    "%s-INVALID".format(MyEnum1.stringVal).readEnum[MyEnum] must beNone
  }

  private def toEnumReturns = {
    MyEnum1.stringVal.toEnum[MyEnum] must beEqualTo(MyEnum1)
  }

  private def toEnumThrows = {
    validating("%s-INVALID".format(MyEnum1.stringVal).toEnum[MyEnum]).toOption must beNone
  }

}
