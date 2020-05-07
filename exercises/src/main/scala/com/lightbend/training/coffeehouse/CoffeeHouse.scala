package com.lightbend.training.coffeehouse

import akka.actor.{Actor, ActorLogging, Props}

object CoffeeHouse {
  def props(): Props = {
    Props(new CoffeeHouse)
  }
}

class CoffeeHouse extends Actor with ActorLogging {

  // Log a message when the CoffeeHouse is instantiated
  log.debug("CoffeeHouse Open")

  override def receive: Receive = {
    case _ => sender() ! "Coffee Brewing"
  }

}
