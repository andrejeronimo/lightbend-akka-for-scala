package com.lightbend.training.coffeehouse

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

object CoffeeHouse {
  case class CreateGuest(favoriteCoffee: Coffee)

  def props(): Props = Props(new CoffeeHouse)
}

class CoffeeHouse extends Actor with ActorLogging {
  import CoffeeHouse._

  // Log a message when the CoffeeHouse is instantiated
  log.debug("CoffeeHouse Open")

  private val waiter: ActorRef = createWaiter()

  /** Creates a guest */
  protected def createGuest(favoriteCoffee: Coffee): ActorRef = {
    context.actorOf(Guest.props(waiter, favoriteCoffee))
  }

  /** Creates a waiter */
  protected def createWaiter(): ActorRef = {
    context.actorOf(Waiter.props(), "waiter")
  }

  override def receive: Receive = {
    case CreateGuest(favoriteCoffee) => createGuest(favoriteCoffee)
  }

}
