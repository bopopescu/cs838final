/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql

import java.lang.reflect.Modifier

import scala.reflect.{ClassTag, classTag}

import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, encoderFor}
import org.apache.spark.sql.catalyst.expressions.{DecodeUsingSerializer, BoundReference, EncodeUsingSerializer}
import org.apache.spark.sql.types._

/**
 * Used to convert a JVM object of type `T` to and from the internal Spark SQL representation.
 *
 * Encoders are not intended to be thread-safe and thus they are allow to avoid internal locking
 * and reuse internal buffers to improve performance.
 *
 * @since 1.6.0
 */
trait Encoder[T] extends Serializable {

  /** Returns the schema of encoding this type of object as a Row. */
  def schema: StructType

  /** A ClassTag that can be used to construct and Array to contain a collection of `T`. */
  def clsTag: ClassTag[T]
}

/**
 * Methods for creating encoders.
 *
 * @since 1.6.0
 */
object Encoders {

  /**
   * An encoder for nullable boolean type.
   * @since 1.6.0
   */
  def BOOLEAN: Encoder[java.lang.Boolean] = ExpressionEncoder()

  /**
   * An encoder for nullable byte type.
   * @since 1.6.0
   */
  def BYTE: Encoder[java.lang.Byte] = ExpressionEncoder()

  /**
   * An encoder for nullable short type.
   * @since 1.6.0
   */
  def SHORT: Encoder[java.lang.Short] = ExpressionEncoder()

  /**
   * An encoder for nullable int type.
   * @since 1.6.0
   */
  def INT: Encoder[java.lang.Integer] = ExpressionEncoder()

  /**
   * An encoder for nullable long type.
   * @since 1.6.0
   */
  def LONG: Encoder[java.lang.Long] = ExpressionEncoder()

  /**
   * An encoder for nullable float type.
   * @since 1.6.0
   */
  def FLOAT: Encoder[java.lang.Float] = ExpressionEncoder()

  /**
   * An encoder for nullable double type.
   * @since 1.6.0
   */
  def DOUBLE: Encoder[java.lang.Double] = ExpressionEncoder()

  /**
   * An encoder for nullable string type.
   * @since 1.6.0
   */
  def STRING: Encoder[java.lang.String] = ExpressionEncoder()

  /**
   * (Scala-specific) Creates an encoder that serializes objects of type T using Kryo.
   * This encoder maps T into a single byte array (binary) field.
   *
   * T must be publicly accessible.
   *
   * @since 1.6.0
   */
  def kryo[T: ClassTag]: Encoder[T] = genericSerializer(useKryo = true)

  /**
   * Creates an encoder that serializes objects of type T using Kryo.
   * This encoder maps T into a single byte array (binary) field.
   *
   * T must be publicly accessible.
   *
   * @since 1.6.0
   */
  def kryo[T](clazz: Class[T]): Encoder[T] = kryo(ClassTag[T](clazz))

  /**
   * (Scala-specific) Creates an encoder that serializes objects of type T using generic Java
   * serialization. This encoder maps T into a single byte array (binary) field.
   *
   * Note that this is extremely inefficient and should only be used as the last resort.
   *
   * T must be publicly accessible.
   *
   * @since 1.6.0
   */
  def javaSerialization[T: ClassTag]: Encoder[T] = genericSerializer(useKryo = false)

  /**
   * Creates an encoder that serializes objects of type T using generic Java serialization.
   * This encoder maps T into a single byte array (binary) field.
   *
   * Note that this is extremely inefficient and should only be used as the last resort.
   *
   * T must be publicly accessible.
   *
   * @since 1.6.0
   */
  def javaSerialization[T](clazz: Class[T]): Encoder[T] = javaSerialization(ClassTag[T](clazz))

  /** Throws an exception if T is not a public class. */
  private def validatePublicClass[T: ClassTag](): Unit = {
    if (!Modifier.isPublic(classTag[T].runtimeClass.getModifiers)) {
      throw new UnsupportedOperationException(
        s"${classTag[T].runtimeClass.getName} is not a public class. " +
          "Only public classes are supported.")
    }
  }

  /** A way to construct encoders using generic serializers. */
  private def genericSerializer[T: ClassTag](useKryo: Boolean): Encoder[T] = {
    if (classTag[T].runtimeClass.isPrimitive) {
      throw new UnsupportedOperationException("Primitive types are not supported.")
    }

    validatePublicClass[T]()

    ExpressionEncoder[T](
      schema = new StructType().add("value", BinaryType),
      flat = true,
      toRowExpressions = Seq(
        EncodeUsingSerializer(
          BoundReference(0, ObjectType(classOf[AnyRef]), nullable = true), kryo = useKryo)),
      fromRowExpression =
        DecodeUsingSerializer[T](
          BoundReference(0, BinaryType, nullable = true), classTag[T], kryo = useKryo),
      clsTag = classTag[T]
    )
  }

  /**
   * An encoder for 2-ary tuples.
   * @since 1.6.0
   */
  def tuple[T1, T2](
      e1: Encoder[T1],
      e2: Encoder[T2]): Encoder[(T1, T2)] = {
    ExpressionEncoder.tuple(encoderFor(e1), encoderFor(e2))
  }

  /**
   * An encoder for 3-ary tuples.
   * @since 1.6.0
   */
  def tuple[T1, T2, T3](
      e1: Encoder[T1],
      e2: Encoder[T2],
      e3: Encoder[T3]): Encoder[(T1, T2, T3)] = {
    ExpressionEncoder.tuple(encoderFor(e1), encoderFor(e2), encoderFor(e3))
  }

  /**
   * An encoder for 4-ary tuples.
   * @since 1.6.0
   */
  def tuple[T1, T2, T3, T4](
      e1: Encoder[T1],
      e2: Encoder[T2],
      e3: Encoder[T3],
      e4: Encoder[T4]): Encoder[(T1, T2, T3, T4)] = {
    ExpressionEncoder.tuple(encoderFor(e1), encoderFor(e2), encoderFor(e3), encoderFor(e4))
  }

  /**
   * An encoder for 5-ary tuples.
   * @since 1.6.0
   */
  def tuple[T1, T2, T3, T4, T5](
      e1: Encoder[T1],
      e2: Encoder[T2],
      e3: Encoder[T3],
      e4: Encoder[T4],
      e5: Encoder[T5]): Encoder[(T1, T2, T3, T4, T5)] = {
    ExpressionEncoder.tuple(
      encoderFor(e1), encoderFor(e2), encoderFor(e3), encoderFor(e4), encoderFor(e5))
  }
}
