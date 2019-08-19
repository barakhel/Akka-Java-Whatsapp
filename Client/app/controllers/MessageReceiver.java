package controllers;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import controllers.logging.AbstractLogActor;
import controllers.protocols.UserToUserProtocol.*;
import controllers.protocols.UserToUserGroupProtocol.InviteMessage;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * this actor class handle all the user text/file and group invitation that sends to this user
 */
public class MessageReceiver extends AbstractLogActor {
    static public Props props(ActorRef inputActor, ActorRef printer, Materializer mat) {

        return Props.create(MessageReceiver.class, () -> new MessageReceiver(inputActor,printer,mat));
    }

    static public class DisconnMsg{}

    private final ActorRef printer;
    private final ActorRef inputActor;
    private final Materializer mat;
    private final Receive preConected;
    private final Receive active;
    private String myName;
    private String filesDir;

    public MessageReceiver(ActorRef inputActor,ActorRef printer,Materializer mat){
        super("messageReceiver");
        this.inputActor = inputActor;
        this.printer = printer;
        this.mat = mat;
        this.filesDir = "";
        this.preConected = createPreConnected();
        this.active = createActive();
    }

    public Receive createReceive(){
        return preConected;
    }

    /**
     * this function return Receive that used before the user connected
     * @return Receive how handle:
     *  - string: username
     */
    private Receive createPreConnected(){
        return receiveBuilder()
                .match(String.class,name -> {
                    this.myName = name;
                    filesDir = util.createdir(Path.of("files",name,"userFiles"));
                    getContext().become(active);

                }).build();
    }

    /**
     * this function return Receive that used after the user connected
     * @return Receive how handle:
     *  - user text/file messages
     *  - group Invite  messages
     */
    private Receive createActive(){
        return receiveBuilder()
                .match(TextMessage.class, this::userTextHandle)
                .match(FileMessage.class,this::userFileHandler)
                .match(InviteMessage.class,m -> logAndTell(inputActor,m,getSender()))
                .match(DisconnMsg.class, m -> {
                    cleanDir();
                    this.filesDir = null;
                    getContext().become(preConected);
                })
                .build();
    }

    private void userTextHandle(TextMessage m){
        printUserformat(m.senderName,m.msg);
    }

    /**
     * this function save the file from the sourceRef in the message
     * to the user file director and print his path
     * @param m: FileMessage with file sourceRef
     */
    private void userFileHandler(FileMessage m){
        Path dst = util.findUnusedName(filesDir,m.fileName);
        m.fileRef.getSource().runWith(FileIO.toPath(dst),mat)
                .thenRun(() -> {
                    String msg = String.format("File received: %s",dst.toString());
                    printUserformat(m.senderName,msg);});
    }


    /**
     * print message in user format
     * @param sender: name of the sender
     * @param msg: message to be print
     */
    private void printUserformat(String sender,String msg){
       String msgFormat = String.format("[%s][%s][%s] %s",util.GetCurrTime(),this.myName,sender,msg);
        logAndTell(printer,msgFormat,null);

    }

    private void cleanDir(){
        if(!filesDir.equals("")) {
            util.deleteFileIfExsits(Paths.get("files", myName).toString());
            filesDir = "";
        }
    }

    @Override
    public void postStop() {
        cleanDir();
    }

}