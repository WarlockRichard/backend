/*
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

package utils.listmeta.pagination

import org.scalacheck.Gen
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import testutils.ScalazDisjunctionMatchers

/**
  * Test for pagination.
  */
class PaginationTest extends PlaySpec with ScalaCheckDrivenPropertyChecks with ScalazDisjunctionMatchers {

  "create" should {
    "create pagination when both size and number specified" in {
      forAll(Gen.choose(0, 1000), Gen.choose(1, 1000)) { (size: Int, number: Int) =>
        val pagination = PaginationRequestParser.parse(Map("size" -> size.toString, "number" -> number.toString))

        pagination mustBe Right(Pagination.WithPages(size, number))
      }
    }

    "create pagination when only size specified" in {
      forAll { (size: Int) =>
        whenever(size >= 0) {
          val pagination = PaginationRequestParser.parse(Map("size" -> size.toString))

          pagination mustBe Right(Pagination.WithPages(size, 1))
        }
      }
    }

    "create pagination without pages when number not specified" in {
      val pagination = PaginationRequestParser.parse(Map())

      pagination mustBe Right(Pagination.WithoutPages)
    }

    "return error if size is less than zero" in {
      forAll { (size: Int) =>
        whenever(size < 0) {
          val pagination = PaginationRequestParser.parse(Map("size" -> size.toString))

          pagination mustBe a[Left[_, _]]
        }
      }
    }

    "return error if number is less or equal zero" in {
      forAll { (number: Int) =>
        whenever(number <= 0) {
          val pagination = PaginationRequestParser.parse(Map("number" -> number.toString, "size" -> "1"))

          pagination mustBe a[Left[_, _]]
        }
      }
    }

    "return error if size is unparseable" in {
      forAll { (size: String) =>
        whenever(!size.matches("\\d+")) {
          val pagination = PaginationRequestParser.parse(Map("size" -> size))

          pagination mustBe a[Left[_, _]]
        }
      }
    }

    "return error if number is unparseable" in {
      forAll { (number: String) =>
        whenever(!number.matches("\\d+")) {
          val pagination = PaginationRequestParser.parse(Map("number" -> number, "size" -> "1"))

          pagination mustBe a[Left[_, _]]
        }
      }
    }
  }
}
