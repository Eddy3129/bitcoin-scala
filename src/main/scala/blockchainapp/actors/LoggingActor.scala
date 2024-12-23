// src/main/scala/blockchainapp/actors/LoggingActor.scala

package blockchainapp.actors

import akka.actor.{Actor, Props}
import Messages._

class LoggingActor(logType: String) extends Actor {
  private var logs: List[String] = List()

  def receive: Receive = {
    case log: String =>
      logs = logs :+ log
      println(s"LoggingActor [$logType]: $log")
      // Publish logs based on type
      logType match {
        case "validation" =>
          if (log.startsWith("Accepted Transaction") || log.startsWith("Rejected Transaction")) {
            context.system.eventStream.publish(ValidationLogUpdate(log))
          }
        case "mining" =>
          context.system.eventStream.publish(MiningLogUpdate(log))
        case _ =>
        // Handle other log types if necessary
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
