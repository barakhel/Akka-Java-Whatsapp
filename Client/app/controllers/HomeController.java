package controllers;

import akka.actor.*;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import controllers.IO.PlayConnector;
import org.webjars.play.WebJarsUtil;
import play.libs.streams.ActorFlow;
import play.mvc.*;
import javax.inject.Inject;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 * this implementation support multi users on the same play server
 * with independent Actor system and remote capability.
 */
public class HomeController extends Controller {
    private final WebJarsUtil webJarsUtil;
    private final Materializer mat;
    private final ActorPath SERVER_PATH = ActorPath.fromString(String.format("akka.tcp://systemServer@%s:%d/user/","127.0.0.1",3553));
    private final org.slf4j.Logger logger;
    private AtomicInteger sysCounter; //for unique actorSystem name
    private final ConcurrentLinkedQueue<ActorSystem> sysQueue;

    @Inject
    public HomeController(Materializer mat,WebJarsUtil webJarsUtil){
        this.mat = mat;
        logger = org.slf4j.LoggerFactory.getLogger("Controller");
        this.webJarsUtil = webJarsUtil;
        this.sysQueue = new ConcurrentLinkedQueue<>();
        sysCounter = new AtomicInteger(0);
        logger.info("Controller started");
    }


    public Result index(Http.Request request) {
        logger.debug("someone connect");
        sysQueue.add(ActorSystem.create(String.format("sys%d",sysCounter.getAndIncrement())));
        String url = routes.HomeController.connector().webSocketURL(request);
        return Results.ok(views.html.index.render(url, webJarsUtil));
    }

    /**
     *Initializing new user actor system and websocket with ActorFlow to communicate with the user actor system
     *     ***the creation of the actorSystem is in index because i ran into some class load problems in creating actorSystem in this function***
     * @return WebSocket to communicate with js code.
     */
    public WebSocket connector() {
        return WebSocket.Text.accept(
                request -> {
                    final ActorSystem system = sysQueue.poll();
                    final ActorRef inputActor = system.actorOf(InputActor.props(SERVER_PATH));
                    return ActorFlow.actorRef(out -> PlayConnector.props(out, inputActor), 256, OverflowStrategy.dropBuffer(), system, mat);
                }
        );
    }


}
