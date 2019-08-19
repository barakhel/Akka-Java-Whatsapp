package controllers.Groups;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import controllers.logging.AbstractLogActorWithTimers;
import controllers.protocols.ClientServerGroupsProtocol.*;
import controllers.IO.InputParser.*;
import controllers.protocols.UserToUserGroupProtocol.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import controllers.util;

/**
 * this class is the for the admin and every group member
 * and contain there common property and actions
 * in addition this class extends AbstractLogActorWithTimers
 * for logging and timer usage
 */
public abstract class AbstractGroupActor extends AbstractLogActorWithTimers {

    protected final HashMap<String,ActorRef> members;
    protected final ActorRef printer;
    protected final String userName;
    protected final ActorRef groupRef;
    protected final Materializer mat;
    private String filesDir;

    public AbstractGroupActor(String userName, ActorRef groupRef, ActorRef printer, Materializer mat){
        super();
        members = new HashMap<>();
        this.printer = printer;
        this.userName = userName;
        this.groupRef = groupRef;
        this.mat = mat;
        groupRef.tell(new AddToGroupMessage(userName),getSelf());
        filesDir = util.createdir(Paths.get("files",userName,"groupsFile",getGroupName()));

    }

    protected Receive basicReceive(){
        return receiveBuilder()
                .match(TextGroupMessage.class, m -> printGroupFormat(m.sender,m.msg))
                .match(FileGroupMessage.class,this::receiveFileHandler)
                .match(AddToGroupMessage.class,m -> members.put(m.senderName,getSender()))
                .match(RemoveFromGroupMessage.class, m ->
                        printSomeoneRemoved(removeMember(m.senderName)))
                .match(LeaveGroupMessage.class,m ->
                        printSomeoneleft(removeMember(m.senderName)))
                .match(ActionNotAllowedMassage.class, m -> logAndTell(printer,m.msg,null))
                .build();
    }

    /**
     * @return Receive how handle:
     *  - group text commends
     *  - group file commends
     */
    protected Receive sendGroupMsgReceive(){
        return receiveBuilder()
                .match(GroupTextInput.class, m -> groupRef.tell(new TextGroupMessage(userName,m.msg),getSelf()))
                .match(GroupFileInput.class,this::sendFileHandler)
                .build();
    }

    /**
     * this function return Receive that common to all not Muted members
     * @return Receive how handle:
     *  - group Unmute Message
     *  - all send Group commends
     */
    protected Receive notMutedReceive(){
        return receiveBuilder()
                .match(UnmuteMessage.class, m-> unmuteNotMutedHandler())
                .build()
                .orElse(sendGroupMsgReceive());
    }

    /**
     * this function return Receive that common to admin and coadmin members
     * @return Receive how handle:
     *  - group Unmute and Invite commends and is answer
     *  - Mute and remove Message that send by coadmin
     *  - all not muted behavior
     */
    protected Receive adminCoadminReceive(){
        return receiveBuilder()
                .match(GroupUnmuteInput.class, this::unmuteInputHandler)//////////////////////
                .match(GroupInviteInput.class,this::inviteHandler)
                .match(InviteAnsYesMessage.class, m -> logAndTell(getSender(),new InviteAnsOkMessage(), groupRef))
                .match(InviteAnsNoMessage.class, m -> logDebug("{} Refuse to the invitation",m.senderName))
                .match(MuteMessage.class, m ->{
                    String msg = String.format("coadmin cannot mute coadmin/admin in %s!",userName);
                        repeatActionNotAllowed(msg);})
                .match(RemoveMessage.class, m ->{
                    String msg = String.format("coadmin cannot remove coadmin/admin in %s!",userName);
                    repeatActionNotAllowed(msg);})
                .build()
                .orElse(notMutedReceive());
    }

    ////////////////////HANDLERS////////////////////////////
    private void unmuteInputHandler(GroupUnmuteInput m){
       ActorRef toRef = members.get(m.toUser);
       if(toRef != null)
           logAndTell(toRef,new UnmuteMessage(userName),getSelf());
    }

    private void inviteHandler(GroupInviteInput m ){
        if(members.keySet().contains(m.toUser)){
            logAndTell(printer,String.format("%s is already in %s!",m.toUser,m.groupName),null);
            return;
        }
        logAndTell(getSender(),new InviteMessage(userName),getSelf());
    }

    private void unmuteNotMutedHandler(){
        repeatActionNotAllowed(String.format("%s is not muted!",userName));
    }

    private void sendFileHandler(GroupFileInput m){
        if(util.fileExsits(m.filePath)) {
            Path path = Paths.get(m.filePath);
            String fileName = path.getFileName().toString();
            Patterns.pipe(util.createFSRef(path,mat).thenApply(s -> new FileGroupMessage(userName, fileName, s)), context().dispatcher())
                    .to(groupRef).future();
        }
        else {
            logAndTell(printer,String.format("%s does not exist!",m.filePath),null);
        }
    }

    private void receiveFileHandler(FileGroupMessage m){
        ActorRef fileCounter = getSender();
        Path dst = util.findUnusedName(filesDir,m.fileName);
        m.fileRef.getSource().runWith(FileIO.toPath(dst),mat)
                .thenRun(() -> {
                    String msg = String.format("File received: %s",dst.toString());
                    printGroupFormat(m.senderName,msg);
                    fileCounter.tell("ok",null);});
    }

    ///////////////////PRINTERS///////////////////////////

    private void printSomeoneRemoved(String name){
        printer.tell(String.format("%s removed from %s",name, getGroupName()),null);
    }

    private void printSomeoneleft(String name) {
        printer.tell(String.format("%s has left %s!", name, getGroupName()), null);
    }

    protected void printGroupFormat(String sender_name,String msg){
        logAndTell(printer,String.format("[%s][%s][%s] %s",util.GetCurrTime(),getGroupName(),sender_name,msg),null);
    }

    protected void repeatActionNotAllowed(String msg){
        logAndTell(getSender(),new ActionNotAllowedMassage(msg),getSelf());
    }

    private String removeMember(String memName){
        members.remove(memName);
        return memName;
    }

    /////////////////////////////UTILS////////////////////////
    protected String getGroupName(){
        return getSelf().path().name();
    }

    private String getmemberName(ActorRef memRef){

        for(Map.Entry<String, ActorRef> e : members.entrySet())
            if(memRef.equals(e.getValue()))
                return e.getKey();
        return null;
    }

    protected void checkAndForward(String memName, Object m){
        ActorRef memRef = members.get(memName);
        if(memRef != null)
            logAndTell(memRef,m,getSelf());
        else
            logAndTell(printer,String.format("%s does not exist!",memName),null);
    }

    protected void deleteGroupFiles(){
        if(filesDir != "")
            util.deleteFileIfExsits(filesDir);
    }


}
