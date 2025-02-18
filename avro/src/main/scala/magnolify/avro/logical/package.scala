/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package magnolify.avro

import org.apache.avro.LogicalTypes.LogicalTypeFactory

import java.time.{Instant, LocalDateTime, LocalTime, ZoneOffset}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import org.apache.avro.{LogicalType, LogicalTypes, Schema}

package object logical {
  object micros {
    implicit val afTimestampMicros: AvroField[Instant] =
      AvroField.logicalType[Long](LogicalTypes.timestampMicros())(us =>
        Instant.ofEpochMilli(us / 1000)
      )(_.toEpochMilli * 1000)

    implicit val afTimeMicros: AvroField[LocalTime] =
      AvroField.logicalType[Long](LogicalTypes.timeMicros())(us =>
        LocalTime.ofNanoOfDay(us * 1000)
      )(_.toNanoOfDay / 1000)

    // `LogicalTypes.localTimestampMicros` is Avro 1.10.0+
    implicit val afLocalTimestampMicros: AvroField[LocalDateTime] =
      AvroField.logicalType[Long](new LogicalType("local-timestamp-micros"))(us =>
        LocalDateTime.ofInstant(Instant.ofEpochMilli(us / 1000), ZoneOffset.UTC)
      )(_.toInstant(ZoneOffset.UTC).toEpochMilli * 1000)
  }

  object millis {
    implicit val afTimestampMillis: AvroField[Instant] =
      AvroField.logicalType[Long](LogicalTypes.timestampMillis())(Instant.ofEpochMilli)(
        _.toEpochMilli
      )

    implicit val afTimeMillis: AvroField[LocalTime] =
      AvroField.logicalType[Int](LogicalTypes.timeMillis())(ms =>
        LocalTime.ofNanoOfDay(ms * 1000000L)
      )(t => (t.toNanoOfDay / 1000000).toInt)

    // `LogicalTypes.localTimestampMillis` is Avro 1.10.0+
    implicit val afLocalTimestampMillis: AvroField[LocalDateTime] =
      AvroField.logicalType[Long](new LogicalType("local-timestamp-millis"))(ms =>
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC)
      )(_.toInstant(ZoneOffset.UTC).toEpochMilli)
  }

  object bigquery {
    // datetime is a custom logical type and must be registered
    private final val DateTimeTypeName = "datetime"
    private final val DateTimeLogicalTypeFactory: LogicalTypeFactory = (schema: Schema) =>
      new org.apache.avro.LogicalType(DateTimeTypeName)

    /**
     * Register custom logical types with avro, which is necessary to correctly parse a custom
     * logical type from string. If registration is omitted, the returned string schema will be
     * correct, but the logicalType field will be null. The registry is global mutable state, keyed
     * on the type name.
     */
    def registerLogicalTypes(): Unit =
      org.apache.avro.LogicalTypes.register(DateTimeTypeName, DateTimeLogicalTypeFactory)

    // DATETIME
    // YYYY-[M]M-[D]D[ [H]H:[M]M:[S]S[.DDDDDD]]
    private val DatetimePrinter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
    private val DatetimeParser = new DateTimeFormatterBuilder()
      .append(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      .appendOptional(
        new DateTimeFormatterBuilder()
          .append(DateTimeFormatter.ofPattern(" HH:mm:ss"))
          .appendOptional(DateTimeFormatter.ofPattern(".SSSSSS"))
          .toFormatter
      )
      .toFormatter
      .withZone(ZoneOffset.UTC)

    // NUMERIC
    // https://cloud.google.com/bigquery/docs/reference/standard-sql/data-types#numeric-type
    implicit val afBigQueryNumeric: AvroField[BigDecimal] = AvroField.bigDecimal(38, 9)

    // TIMESTAMP
    implicit val afBigQueryTimestamp: AvroField[Instant] = micros.afTimestampMicros

    // DATE: `AvroField.afDate`

    // TIME
    implicit val afBigQueryTime: AvroField[LocalTime] = micros.afTimeMicros

    // DATETIME -> sqlType: DATETIME
    implicit val afBigQueryDatetime: AvroField[LocalDateTime] =
      AvroField.logicalType[String](new org.apache.avro.LogicalType(DateTimeTypeName))(s =>
        LocalDateTime.from(DatetimeParser.parse(s))
      )(DatetimePrinter.format)
  }
}
