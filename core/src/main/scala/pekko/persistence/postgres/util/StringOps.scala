/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.util

import java.util.Base64

object StringOps {
  implicit class StringImplicits(val that: String) extends AnyVal {
    def toByteArray: Array[Byte] = Base64.getDecoder.decode(that)
  }
}
