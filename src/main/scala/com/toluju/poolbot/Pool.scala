package com.toluju.poolbot

import scala.actors.Actor
import scala.collection.mutable.HashSet
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Toby Jungen
 */
class Pool[T](val factory:ConnectionFactory[T],
              val minPool:Int, val maxPool:Int, val keepAlive:Long){
  private val queue = new LinkedBlockingQueue[ConnWrap[T]]
  private val conns = new HashSet[ConnWrap[T]]

  for (i <- 0 until minPool) {
    addConn
  }

  private def addConn = {
    println("Adding connection")
    var conn = new ConnWrap(factory.create)
    queue.put(conn)
    conns += conn
  }

  private def removeConn(conn:ConnWrap[T]) = {
    println("Removing connection")
    //queue.remove(conn)
    conns.remove(conn)
    factory.close(conn.conn)
  }

  def take:T = {
    synchronized {
      if (queue.isEmpty && conns.size < maxPool) {
        addConn
      }

      queue.take.conn
    }
  }

  def give(conn:T) = {
    synchronized {
      queue.put(new ConnWrap(conn))

      var it = queue.iterator
      while (it.hasNext && queue.size > minPool) {
        val cur = it.next
        if (cur.expired) {
          it.remove
          removeConn(cur)
        }
      }
    }
  }

  def apply[R](fun: T => R) = {
    val conn = take
    val result = fun(conn)
    give(conn)
    result
  }

  def shutdown = {
    conns foreach { conn:ConnWrap[T] =>
      factory.close(conn.conn)
    }
  }

  private class ConnWrap[T](val conn:T) {
    val created = System.currentTimeMillis
    def expired = System.currentTimeMillis - created > keepAlive
  }
}

trait ConnectionFactory[T] {
  def create():T
  def close(conn:T):Unit
}

class PoolActor[T](val pool:Pool[T]) extends Actor {
  def act() = {
    loop {
      react {
        case Process(fun) => pool(fun)
        case Shutdown => exit()
      }
    }
  }
}

// Message types
case class Process[T, R](fun: T => R)
case class Shutdown()