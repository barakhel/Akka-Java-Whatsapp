package controllers.logging;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * AbstractActor with custom log tools
 */
public abstract class AbstractLogActor extends AbstractActor{
    protected final LoggingAdapter logger;
    private String log_name;

    /**
     * constructor with specific log name
     * @param log_name
     */
    protected AbstractLogActor(String log_name){
        this.log_name = log_name;
        logger = Logging.getLogger(getContext().getSystem(),this.getClass());

    }

    /**
     * constructor with Actor name as log name
     */
    protected AbstractLogActor(){
        this.log_name = getSelf().path().name();
        logger = Logging.getLogger(getContext().getSystem(),this.getClass());
    }

    /**
     * this function preform a tell to some ActorRef and record this in the actor log
     * @param dst: NotNull ActorRef to preform tell on him
     * @param m: message to send to dst
     * @param sender: the ActorRef sender
     */
    protected void logAndTell(ActorRef dst, Object m, ActorRef sender){
        logDebug("send {} to {}",m.getClass().getName(),dst.path().name());
        dst.tell(m,sender);
    }

    protected void logDebug(String template){
        logger.debug(addName(template));
    }

    protected void logDebug(String template,Object arg1){
        logger.debug(addName(template),arg1);
    }
    protected void logDebug(String template,Object arg1,Object arg2){
        logger.debug(addName(template),arg1,arg2);
    }

    protected void logDebug(String template,Object arg1,Object arg2,Object arg3){
        logger.debug(addName(template),arg1,arg2,arg3);
    }

    protected void logDebug(String template,Object arg1,Object arg2,Object arg3,Object arg4){
        logger.debug(addName(template),arg1,arg2,arg3,arg4);
    }

    protected void logWarn(String template){
        logger.warning(addName(template));
    }

    protected void logWarn(String template,Object arg1){
        logger.warning(addName(template),arg1);
    }
    protected void logWarn(String template,Object arg1,Object arg2){
        logger.warning(addName(template),arg1,arg2);
    }

    protected void logWarn(String template,Object arg1,Object arg2,Object arg3){
        logger.warning(addName(template),arg1,arg2,arg3);
    }

    protected void logWarn(String template,Object arg1,Object arg2,Object arg3,Object arg4){
        logger.warning(addName(template),arg1,arg2,arg3,arg4);
    }

    protected void logError(String template){
        logger.error(addName(template));
    }

    protected void logError(String template,Object arg1){
        logger.error(addName(template),arg1);
    }
    protected void logError(String template,Object arg1,Object arg2){
        logger.error(addName(template),arg1,arg2);
    }

    protected void logError(String template,Object arg1,Object arg2,Object arg3){
        logger.error(addName(template),arg1,arg2,arg3);
    }

    protected void logError(String template,Object arg1,Object arg2,Object arg3,Object arg4){
        logger.error(addName(template),arg1,arg2,arg3,arg4);
    }

    /**
     * @param template: template to the logger
     * @return the template with the actor name in the beginning
     */
    private String addName(String template){
        return String.format("[%s][%s] %s",getContext().getSystem().name(),this.log_name,template);
    }
}
