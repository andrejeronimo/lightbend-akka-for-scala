package com.lightbend.training.coffeehouse

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

import scala.concurrent.duration.FiniteDuration

object Guest {
  case object CoffeeFinished

  case object CaffeineException extends IllegalStateException

  def props(waiter: ActorRef,
            favoriteCoffee: Coffee,
            finishCoffeeDuration: FiniteDuration,
            caffeineLimit: Int): Props = {
    Props(new Guest(waiter, favoriteCoffee, finishCoffeeDuration, caffeineLimit))
  }
}

class Guest(waiter: ActorRef,
            favoriteCoffee: Coffee,
            finishCoffeeDuration: FiniteDuration,
            caffeineLimit: Int) extends Actor with ActorLogging with Timers {
  import Guest._

  private var coffeeCount: Int = 0

  // When a guest gets created, start the flow by ordering a coffee
  orderCoffee()

  override def postStop(): Unit = {
    log.info("Goodbye")
    super.postStop()
  }

  def receive: Receive = {
    case Waiter.CoffeeServed(coffee) =>
      coffeeCount += 1
      log.info(s"Enjoying my $coffeeCount yummy $coffee")

      // Send a `CoffeeFinished` message to self when the guest finishes drinking his coffee
      timers.startSingleTimer("coffee-finished", CoffeeFinished, finishCoffeeDuration)
    case CoffeeFinished if coffeeCount > caffeineLimit =>
      throw CaffeineException
    case CoffeeFinished =>
      orderCoffee()  // Every time the guests finishes his coffee he orders another one
  }

  /** Orders a coffee (by sending a `Waiter.ServeCoffee`message to the waiter actor) */
  private def orderCoffee(): Unit = {
    waiter ! Waiter.ServeCoffee(favoriteCoffee)
  }
}
