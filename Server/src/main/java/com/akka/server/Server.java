package com.akka.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Server {
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("systemServer");
        final ActorRef conn = system.actorOf(Connector.props(),"conn");
    }
}
