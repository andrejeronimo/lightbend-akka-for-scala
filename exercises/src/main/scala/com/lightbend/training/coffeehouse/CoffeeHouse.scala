package com.lightbend.training.coffeehouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import scala.concurrent.duration._

object CoffeeHouse {
  case class CreateGuest(favoriteCoffee: Coffee)

  def props(): Props = Props(new CoffeeHouse)
}

class CoffeeHouse extends Actor with ActorLogging {
  import CoffeeHouse._

  // Log a message when the CoffeeHouse is instantiated
  log.debug("CoffeeHouse Open")

  private val finishCoffeeDuration: FiniteDuration = context.system.settings.config.getDuration("coffee-house.guest.finish-coffee-duration", TimeUnit.MILLISECONDS).millis
  private val prepareCoffeeDuration: FiniteDuration = context.system.settings.config.getDuration("coffee-house.barista.prepare-coffee-duration", TimeUnit.MILLISECONDS).millis

  // CoffeeHouse barista and waiter
  private val barista: ActorRef = createBarista()
  private val waiter: ActorRef = createWaiter()

  /** Creates a guest */
  protected def createGuest(favoriteCoffee: Coffee): ActorRef = {
    context.actorOf(Guest.props(waiter, favoriteCoffee, finishCoffeeDuration))
  }

  /** Creates a waiter */
  protected def createWaiter(): ActorRef = {
    context.actorOf(Waiter.props(barista), "waiter")
  }

  /** Creates a barista */
  protected def createBarista(): ActorRef = {
    context.actorOf(Barista.props(prepareCoffeeDuration), "barista")
  }

  override def receive: Receive = {
    case CreateGuest(favoriteCoffee) => createGuest(favoriteCoffee)
  }

}
