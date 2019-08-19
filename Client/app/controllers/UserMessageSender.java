package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.util.ByteString;
import controllers.IO.InputParser.*;
import controllers.logging.AbstractLogActor;
import controllers.protocols.UserToUserProtocol.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;

/**
 * this actor class handle all the sending of user text/file to other users
 */
public class UserMessageSender extends AbstractLogActor {

    static public Props props(String myName, ActorRef printer, Materializer mat) {
        return Props.create(UserMessageSender.class, () -> new UserMessageSender(myName,printer,mat));
    }

    private final String myName;
    private final ActorRef printer;
    private final Materializer mat;

    public UserMessageSender(String myName,ActorRef printer, Materializer mat){
        super("messageSender");
        this.myName = myName;
        this.printer = printer;
        this.mat = mat;

    }
    /**
     * @return Receive how handle:
     *  - user text/file commends
     */
    public Receive createReceive(){
        return receiveBuilder()
                .match(UserTextInput.class, m ->
                        getSender().tell(new TextMessage(myName,m.msg),getSelf()))
                .match(UserFileInput.class,this::sendFileHandler )
                .build();
    }

    /**
     * this function handle the user file sending
     * if the file exist a message with sourceRef of the file will sent to the target
     * otherwise an error message will be printed
     * @param m
     */
    private void sendFileHandler(UserFileInput m){
        if(util.fileExsits(m.filePath)) {
            Path path = Paths.get(m.filePath);
            String fileName = path.getFileName().toString();
            Source<ByteString, CompletionStage<IOResult>> fileStream = FileIO.fromPath(path);
            CompletionStage<SourceRef<ByteString>> fileRef = fileStream.runWith(StreamRefs.sourceRef(), mat);
            Patterns.pipe(fileRef.thenApply(s -> new FileMessage(myName, fileName, s)), context().dispatcher())
                    .to(getSender()).future();
        }
        else {
            logAndTell(printer,String.format("%s does not exist!",m.filePath),null);
        }
    }
}

