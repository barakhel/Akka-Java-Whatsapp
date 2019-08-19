package controllers.IO;

import akka.actor.ActorRef;
import akka.actor.Props;
import controllers.logging.AbstractLogActorWithTimers;
import java.time.Duration;

/**
 *this actor class used as ActorFlow to connect between the js code
 * and the user program actors using websocket
 */
public class PlayConnector extends AbstractLogActorWithTimers {
    static public Props props(ActorRef printer, ActorRef supervise) {
        return Props.create(PlayConnector.class, () -> new PlayConnector(printer, supervise));
    }

    /**
     * message sends to the websocket for keeping him alive
     */
    static public class ping{}

    private final ActorRef printer; //ActorRef that used as a channel to send messages to the js
    private final ActorRef supervise; //user program supervise


    public PlayConnector(ActorRef printer, ActorRef supervise) {
        super("PlayConnector");
        this.printer = printer;
        this.supervise = supervise;
        //getContext().watch(printer);
        this.supervise.tell(this.printer,null); //message to the user program supervise to inform that websocket established
        getTimers().startPeriodicTimer(new Object(),new ping(), Duration.ofSeconds(20)); //to keep websocket alive
    }

    /**
     * @return Receive that handle:
     *  - input commend by parsing them ans send to user program supervise.
     *  - ping by sending ping to the websocket to keep him alive.
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                //.match(Terminated.class, r -> getContext().stop(self()))
                .match(ping.class,m -> printer.tell("ping",null))
                .matchEquals("",m-> {})
                .match(String.class, s ->{
                    Object o = InputParser.parseInput(s);
                    supervise.tell(InputParser.parseInput(s),null);
                    logger.debug("{} to {}",s,o.getClass().getName());
                })
                .matchAny(o -> logger.warning("unexpected mail {}",o))
                .build();
    }

    /**
     * close the user program if websocket is dead
     * because the user close the window or some other problem
     */

}
