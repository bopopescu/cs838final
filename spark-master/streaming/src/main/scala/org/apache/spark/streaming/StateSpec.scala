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

package org.apache.spark.streaming

import com.google.common.base.Optional
import org.apache.spark.annotation.Experimental
import org.apache.spark.api.java.{JavaPairRDD, JavaUtils}
import org.apache.spark.api.java.function.{Function2 => JFunction2, Function4 => JFunction4}
import org.apache.spark.rdd.RDD
import org.apache.spark.util.ClosureCleaner
import org.apache.spark.{HashPartitioner, Partitioner}

/**
 * :: Experimental ::
 * Abstract class representing all the specifications of the DStream transformation
 * `trackStateByKey` operation of a
 * [[org.apache.spark.streaming.dstream.PairDStreamFunctions pair DStream]] (Scala) or a
 * [[org.apache.spark.streaming.api.java.JavaPairDStream JavaPairDStream]] (Java).
 * Use the [[org.apache.spark.streaming.StateSpec StateSpec.apply()]] or
 * [[org.apache.spark.streaming.StateSpec StateSpec.create()]] to create instances of
 * this class.
 *
 * Example in Scala:
 * {{{
 *    def trackingFunction(data: Option[ValueType], wrappedState: State[StateType]): EmittedType = {
 *      ...
 *    }
 *
 *    val spec = StateSpec.function(trackingFunction).numPartitions(10)
 *
 *    val emittedRecordDStream = keyValueDStream.trackStateByKey[StateType, EmittedDataType](spec)
 * }}}
 *
 * Example in Java:
 * {{{
 *    StateSpec<KeyType, ValueType, StateType, EmittedDataType> spec =
 *      StateSpec.<KeyType, ValueType, StateType, EmittedDataType>function(trackingFunction)
 *                    .numPartition(10);
 *
 *    JavaTrackStateDStream<KeyType, ValueType, StateType, EmittedType> emittedRecordDStream =
 *      javaPairDStream.<StateType, EmittedDataType>trackStateByKey(spec);
 * }}}
 */
@Experimental
sealed abstract class StateSpec[KeyType, ValueType, StateType, EmittedType] extends Serializable {

  /** Set the RDD containing the initial states that will be used by `trackStateByKey` */
  def initialState(rdd: RDD[(KeyType, StateType)]): this.type

  /** Set the RDD containing the initial states that will be used by `trackStateByKey` */
  def initialState(javaPairRDD: JavaPairRDD[KeyType, StateType]): this.type

  /**
   * Set the number of partitions by which the state RDDs generated by `trackStateByKey`
   * will be partitioned. Hash partitioning will be used.
   */
  def numPartitions(numPartitions: Int): this.type

  /**
   * Set the partitioner by which the state RDDs generated by `trackStateByKey` will be
   * be partitioned.
   */
  def partitioner(partitioner: Partitioner): this.type

  /**
   * Set the duration after which the state of an idle key will be removed. A key and its state is
   * considered idle if it has not received any data for at least the given duration. The state
   * tracking function will be called one final time on the idle states that are going to be
   * removed; [[org.apache.spark.streaming.State State.isTimingOut()]] set
   * to `true` in that call.
   */
  def timeout(idleDuration: Duration): this.type
}


/**
 * :: Experimental ::
 * Builder object for creating instances of [[org.apache.spark.streaming.StateSpec StateSpec]]
 * that is used for specifying the parameters of the DStream transformation `trackStateByKey`
 * that is used for specifying the parameters of the DStream transformation
 * `trackStateByKey` operation of a
 * [[org.apache.spark.streaming.dstream.PairDStreamFunctions pair DStream]] (Scala) or a
 * [[org.apache.spark.streaming.api.java.JavaPairDStream JavaPairDStream]] (Java).
 *
 * Example in Scala:
 * {{{
 *    def trackingFunction(data: Option[ValueType], wrappedState: State[StateType]): EmittedType = {
 *      ...
 *    }
 *
 *    val emittedRecordDStream = keyValueDStream.trackStateByKey[StateType, EmittedDataType](
 *        StateSpec.function(trackingFunction).numPartitions(10))
 * }}}
 *
 * Example in Java:
 * {{{
 *    StateSpec<KeyType, ValueType, StateType, EmittedDataType> spec =
 *      StateSpec.<KeyType, ValueType, StateType, EmittedDataType>function(trackingFunction)
 *                    .numPartition(10);
 *
 *    JavaTrackStateDStream<KeyType, ValueType, StateType, EmittedType> emittedRecordDStream =
 *      javaPairDStream.<StateType, EmittedDataType>trackStateByKey(spec);
 * }}}
 */
@Experimental
object StateSpec {
  /**
   * Create a [[org.apache.spark.streaming.StateSpec StateSpec]] for setting all the specifications
   * of the `trackStateByKey` operation on a
   * [[org.apache.spark.streaming.dstream.PairDStreamFunctions pair DStream]].
   *
   * @param trackingFunction The function applied on every data item to manage the associated state
   *                         and generate the emitted data
   * @tparam KeyType      Class of the keys
   * @tparam ValueType    Class of the values
   * @tparam StateType    Class of the states data
   * @tparam EmittedType  Class of the emitted data
   */
  def function[KeyType, ValueType, StateType, EmittedType](
      trackingFunction: (Time, KeyType, Option[ValueType], State[StateType]) => Option[EmittedType]
    ): StateSpec[KeyType, ValueType, StateType, EmittedType] = {
    ClosureCleaner.clean(trackingFunction, checkSerializable = true)
    new StateSpecImpl(trackingFunction)
  }

  /**
   * Create a [[org.apache.spark.streaming.StateSpec StateSpec]] for setting all the specifications
   * of the `trackStateByKey` operation on a
   * [[org.apache.spark.streaming.dstream.PairDStreamFunctions pair DStream]].
   *
   * @param trackingFunction The function applied on every data item to manage the associated state
   *                         and generate the emitted data
   * @tparam ValueType    Class of the values
   * @tparam StateType    Class of the states data
   * @tparam EmittedType  Class of the emitted data
   */
  def function[KeyType, ValueType, StateType, EmittedType](
      trackingFunction: (Option[ValueType], State[StateType]) => EmittedType
    ): StateSpec[KeyType, ValueType, StateType, EmittedType] = {
    ClosureCleaner.clean(trackingFunction, checkSerializable = true)
    val wrappedFunction =
      (time: Time, key: Any, value: Option[ValueType], state: State[StateType]) => {
        Some(trackingFunction(value, state))
      }
    new StateSpecImpl(wrappedFunction)
  }

  /**
   * Create a [[org.apache.spark.streaming.StateSpec StateSpec]] for setting all
   * the specifications of the `trackStateByKey` operation on a
   * [[org.apache.spark.streaming.api.java.JavaPairDStream JavaPairDStream]].
   *
   * @param javaTrackingFunction The function applied on every data item to manage the associated
   *                             state and generate the emitted data
   * @tparam KeyType      Class of the keys
   * @tparam ValueType    Class of the values
   * @tparam StateType    Class of the states data
   * @tparam EmittedType  Class of the emitted data
   */
  def function[KeyType, ValueType, StateType, EmittedType](javaTrackingFunction:
      JFunction4[Time, KeyType, Optional[ValueType], State[StateType], Optional[EmittedType]]):
    StateSpec[KeyType, ValueType, StateType, EmittedType] = {
    val trackingFunc = (time: Time, k: KeyType, v: Option[ValueType], s: State[StateType]) => {
      val t = javaTrackingFunction.call(time, k, JavaUtils.optionToOptional(v), s)
      Option(t.orNull)
    }
    StateSpec.function(trackingFunc)
  }

  /**
   * Create a [[org.apache.spark.streaming.StateSpec StateSpec]] for setting all the specifications
   * of the `trackStateByKey` operation on a
   * [[org.apache.spark.streaming.api.java.JavaPairDStream JavaPairDStream]].
   *
   * @param javaTrackingFunction The function applied on every data item to manage the associated
   *                             state and generate the emitted data
   * @tparam ValueType    Class of the values
   * @tparam StateType    Class of the states data
   * @tparam EmittedType  Class of the emitted data
   */
  def function[KeyType, ValueType, StateType, EmittedType](
      javaTrackingFunction: JFunction2[Optional[ValueType], State[StateType], EmittedType]):
    StateSpec[KeyType, ValueType, StateType, EmittedType] = {
    val trackingFunc = (v: Option[ValueType], s: State[StateType]) => {
      javaTrackingFunction.call(Optional.fromNullable(v.get), s)
    }
    StateSpec.function(trackingFunc)
  }
}


/** Internal implementation of [[org.apache.spark.streaming.StateSpec]] interface. */
private[streaming]
case class StateSpecImpl[K, V, S, T](
    function: (Time, K, Option[V], State[S]) => Option[T]) extends StateSpec[K, V, S, T] {

  require(function != null)

  @volatile private var partitioner: Partitioner = null
  @volatile private var initialStateRDD: RDD[(K, S)] = null
  @volatile private var timeoutInterval: Duration = null

  override def initialState(rdd: RDD[(K, S)]): this.type = {
    this.initialStateRDD = rdd
    this
  }

  override def initialState(javaPairRDD: JavaPairRDD[K, S]): this.type = {
    this.initialStateRDD = javaPairRDD.rdd
    this
  }

  override def numPartitions(numPartitions: Int): this.type = {
    this.partitioner(new HashPartitioner(numPartitions))
    this
  }

  override def partitioner(partitioner: Partitioner): this.type = {
    this.partitioner = partitioner
    this
  }

  override def timeout(interval: Duration): this.type = {
    this.timeoutInterval = interval
    this
  }

  // ================= Private Methods =================

  private[streaming] def getFunction(): (Time, K, Option[V], State[S]) => Option[T] = function

  private[streaming] def getInitialStateRDD(): Option[RDD[(K, S)]] = Option(initialStateRDD)

  private[streaming] def getPartitioner(): Option[Partitioner] = Option(partitioner)

  private[streaming] def getTimeoutInterval(): Option[Duration] = Option(timeoutInterval)
}
