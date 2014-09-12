/*
 * #%L
 * GatlingCql
 * %%
 * Copyright (C) 2014 Mikhail Stepura
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package io.github.gatling.cql

import scala.concurrent.duration.DurationInt

import com.datastax.driver.core.Cluster

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.github.gatling.cql.Predef._;

import com.datastax.driver.core.ConsistencyLevel

class CqlCompileTest extends Simulation {
  val keyspace = "test"
  val table_name = "test_table"
  val session = Cluster.builder().addContactPoint("127.0.0.1").build().connect(s"$keyspace")
  val cqlConfig = cql.session(session)
  
  session.execute(s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = { 'class' : 'SimpleStrategy', 'replication_factor': '1'}")
  session.execute(s"""CREATE TABLE IF NOT EXISTS $table_name (
		  id timeuuid,
		  num int,
		  str text,
		  PRIMARY KEY (id)
		);
      """)
  session.execute(f"CREATE INDEX IF NOT EXISTS $table_name%s_num_idx ON $table_name%s (num)")
  
  val prepared = session.prepare(s"INSERT INTO $table_name (id, num, str) values (now(), ?, ?)")
  
  val random = new util.Random
  val feeder = Iterator.continually(
      Map(
          "randomString" -> random.nextString(20), 
          "randomNum" -> random.nextInt()
          ))

  val scn = scenario("Two selects").repeat(1) {
    feed(feeder)
    .exec(cql("simple SELECT")
        .execute("SELECT * FROM test_table WHERE num = ${randomNum}"))  //Gatling EL for ${randomNum}"
    .exec(cql("prepared INSERT")
        .execute(prepared)
        .withParams(Integer.valueOf(random.nextInt()), "${randomString}")
        .consistencyLevel(ConsistencyLevel.ANY))
  }

  setUp(scn.inject(rampUsersPerSec(10) to 100 during (30 seconds)))
    .protocols(cqlConfig)

}