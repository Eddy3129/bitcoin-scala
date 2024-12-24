// src/main/scala/blockchainapp/actors/LoggingActor.scala

package blockchainapp.actors

import akka.actor.{Actor, Props}
import Messages.{ValidationLogUpdate, MiningLogUpdate, GetLogs}

class LoggingActor(logType: String) extends Actor {
  private var logs: List[String] = List()

  def receive: Receive = {
    case log: String =>
      logs = logs :+ log
      logType match {
        case "validation" => context.system.eventStream.publish(ValidationLogUpdate(log))
        case "mining" => context.system.eventStream.publish(MiningLogUpdate(log))
        case _ => // Do nothing
      }
    case GetLogs =>
      sender() ! logs
    case _ =>
      println(s"LoggingActor [$logType] received unknown message.")
  }
}

object LoggingActor {
  def props(logType: String): Props = Props(new LoggingActor(logType))
}
