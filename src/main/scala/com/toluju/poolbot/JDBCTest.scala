package com.toluju.poolbot

import java.sql.Connection
import java.util.UUID
import scala.util.Random

object JDBCTest {
  def insert(i:Int)(conn:Connection) = {
    val stmt = conn.prepareStatement("insert into users (id, name) values (?, ?)")
    stmt.setInt(1, i)
    stmt.setString(2, UUID.randomUUID.toString)
    stmt.executeUpdate
    stmt.close
  }

  def select(random:Random)(conn:Connection) = {
    val id = random.nextInt(10)
    val stmt = conn.prepareStatement("select name from users where id=?")
    stmt.setInt(1, id)
    val rs = stmt.executeQuery
    if (rs.next) {
      println("Name for id " + id + ": " + rs.getString("name"))
    }
    else {
      println("Id not found: " + id)
    }
    rs.close
    stmt.close
  }

  def main(args: Array[String]) {
    val factory = new JDBCConnectionFactory("jdbc:mysql://localhost/poolbot_test", "poolbot", "poolbot")
    val pool = new Pool(factory, 2, 4, 10000)
    val actor = new PoolActor(pool).start

    for (i <- 0 until 10) {
      actor ! Process(insert(i))
    }

    val random = new Random

    for (i <- 0 until 10) {
      actor ! Process(select(random))
    }

    actor ! Shutdown

    for (i <- 0 until 10) {
      pool(select(random))
    }

    pool.shutdown
  }
}