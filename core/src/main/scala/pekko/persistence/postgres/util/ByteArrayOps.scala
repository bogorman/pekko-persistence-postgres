/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package pekko.persistence.postgres.util

import java.io.{ ByteArrayInputStream, InputStream }
import java.util.Base64

object ByteArrayOps {
  implicit class ByteArrayImplicits(val that: Array[Byte]) extends AnyVal {
    def encodeBase64: String = Base64.getEncoder.encodeToString(that)
    def toInputStream: InputStream = new ByteArrayInputStream(that)
  }
}
