package io.vamp.core.persistence.jdbc

import java.nio.charset.StandardCharsets

import io.vamp.core.model.artifact.Dialect
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

object DialectSerializer {

  implicit val formats = DefaultFormats

  def serialize(dialects: Map[Dialect.Value, Any]): Array[Byte] = write(dialects.map({ case (key, value) =>
    key.toString.toLowerCase -> value
  })).getBytes(StandardCharsets.UTF_8)

  def deserialize(blob: Array[Byte]): Map[Dialect.Value, Any] = {
    val dialects = read[Any](new String(blob, StandardCharsets.UTF_8)).asInstanceOf[Map[String, Any]]
    dialects.map{ case (k,v) => Dialect.asValue(k) -> v}.collect{ case (Some(k), v) => k -> v}
  }
}
