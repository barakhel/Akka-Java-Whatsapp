package com.akka.server;

import akka.actor.AbstractActorWithTimers;
import akka.actor.PoisonPill;
import akka.actor.Props;
import java.nio.file.Path;
import java.time.Duration;

/**
 * counting the 'done' message from members how received the file successfully
 * when the counter become zero or ofter maxMilli time the actor stop and delete the
 * temp file
 */
public class FileSendCounter extends AbstractActorWithTimers {
    static public Props props(Path tmpPath,int count,long maxMilli) {
        return Props.create(FileSendCounter.class, () -> new FileSendCounter(tmpPath,count,maxMilli));
    }

    private final Path tmpPath;
    private int count;

    public FileSendCounter(Path tmpPath,int count,long maxMilli){
        this.tmpPath = tmpPath;
        this.count = count;
        getTimers().startSingleTimer(new Object(), PoisonPill.getInstance(), Duration.ofMillis(maxMilli)); //set the timer
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("ok", m -> { //members send 'done'
                    if (--count == 0)
                        getContext().stop(getSelf());
                }).build();
    }

    @Override
    public void postStop(){
        util.deleteFileIfExsits(tmpPath.toString()); //delete tmp file
    }

}
