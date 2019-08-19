package controllers.Groups;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import controllers.protocols.Abstracts.AbstractNamedMessage;
import controllers.protocols.ClientServerGroupsProtocol.*;
import controllers.protocols.UserToUserGroupProtocol.*;
import controllers.IO.InputParser.*;
import java.time.Duration;

public class MemberGroup extends AbstractGroupActor {

    static public Props props(String user_name,ActorRef group_ref,ActorRef printer,Materializer mat) {
        return Props.create(MemberGroup.class, () -> new MemberGroup(user_name,group_ref,printer,mat));
    }

    static private class MutedTImeIsUp{
        public final Object key;
        public  MutedTImeIsUp(Object key){this.key = key;}
    }


    private final Receive prevUser;
    private final Receive prevMuted;
    private final Receive prevCoadmin;
    private final Receive initStage;
    private Object timeKey; //keep mute timer valid
    private boolean removed; //to distinguish between remove and leave in postStop

    public MemberGroup(String user_name, ActorRef group_ref, ActorRef printer, Materializer mat) {
        super(user_name,group_ref,printer,mat);
        this.prevCoadmin = coadminReceive();
        this.prevUser = userReceive();
        this.prevMuted = mutedReceive();
        this.initStage = initReceive();
        this.removed = false;
    }

    public Receive createReceive() {
        return initStage;
    }

    /**
     * this function return Receive for coadmin
     * @return Receive how handle:
     *  - group Mute, remove,  commends
     *  - group coadmin remove message
     *  - all admin/coadmin behavior
     *  - all basic non admin member behavior
     */
    private Receive coadminReceive(){
        return receiveBuilder()
                .match(GroupMuteInput.class, m ->
                        checkAndForward(m.toUser,new MuteMessage(userName,m.time)))
                .match(GroupRemoveInput.class, m ->
                        checkAndForward(m.toUser,new RemoveFromGroupMessage(userName)))
                .match(CoadminDemoteMessage.class,this::coadminDemoteHandler)
                .build()
                .orElse(adminCoadminReceive())
                .orElse(basicMembersReceive());
    }

    /**
     * this function return Receive for user
     * @return Receive how handle:
     *  - all not muted behavior
     *  - - all muted/user behavior
     *  - all basic non admin member behavior
     */
    private Receive userReceive(){
       return notMutedReceive()
               .orElse(MutedOrUserReceive())
               .orElse(basicMembersReceive());
    }

    /**
     * this function return Receive for muted
     * @return Receive how handle:
     *  - group send text/file commends
     *  - group mute, unmute messages
     *  - self mute time is up message
     *  - all muted/user behavior
     *  - all basic non admin member behavior
     */
    private Receive mutedReceive(){
        return receiveBuilder()
                .match(GroupTextInput.class, m -> mutedTextFileInputHandler())
                .match(GroupFileInput.class, m -> mutedTextFileInputHandler())
                .match(UnmuteMessage.class,this::unmutedHandler)
                .match(MutedTImeIsUp.class,this::mutedTImeIsUpHandler)
                .build()
                .orElse(basicMembersReceive())
                .orElse(MutedOrUserReceive());

    }

    /**
     * this function return Receive for init stage
     * @return Receive how handle:
     *  - server group message with source to all group members
     *  - unexpected messages
     */
    private Receive initReceive(){
        return receiveBuilder().match(MembersMessage.class, m ->
                initMembersHandler(m.sorceSupp))
                .matchAny(o-> logger.warning("unexpected message"))
                .build();
    }

    /**
     * this function return Receive that common to all not admin members
     * @return Receive how handle:
     *  - group mute/unmute messages that send by admin
     *  - group close message
     *  - all basic members behavior
     */
    private Receive basicMembersReceive(){
        return receiveBuilder()
                .match(AdminMuteMessage.class, this::mutedHandler)
                .match(AdminRemoveMessage.class,this::removeHandler)
                .match(CloseGroupMessage.class, m -> closeHandler())
                .match(GroupCoadminAddInput.class, m -> printNotAdmin())
                .match(GroupCoadminRemoveInput.class, m -> printNotAdmin())
                .build().orElse(basicReceive());
    }

    private void printNotAdmin(){
        String msg = String.format("You are not admin in %s!",getGroupName());
        logAndTell(printer,msg,null);
    }

    /**
     * this function return Receive that common to all not admin members
     * @return Receive how handle:
     *  - group invite, coadmin add/remove, mute, unmute commends
     *  - group coadmin add/remove messages
     */
    private Receive MutedOrUserReceive(){
        return receiveBuilder()
                .match(GroupInviteInput.class, this::printNotAdminOrCoadmin)
                .match(GroupRemoveInput.class, this::printNotAdminOrCoadmin)
                //.match(GroupCoadminAddInput.class, this::printNotAdminOrCoadmin)
                //.match(GroupCoadminRemoveInput.class, this::printNotAdminOrCoadmin)
                .match(GroupMuteInput.class, this::printNotAdminOrCoadmin)
                .match(GroupUnmuteInput.class, this::printNotAdminOrCoadmin)
                .match(MuteMessage.class,this::mutedHandler)
                .match(CoadminPremoteMessage.class,this::coadminPremoteHandler)
                .match(CoadminDemoteMessage.class, m -> {
                    String msg = String.format("%s is not coadmin in %s!",userName,getGroupName());
                    repeatActionNotAllowed(msg);})
                .build();
    }

    //////////////////////////////////////HANDLERS///////////////////////////////////////
    private void mutedTextFileInputHandler(){
        String msg = String.format("you are muted in %s, sending text/file is not allowed!",getGroupName());
        logAndTell(printer,msg,null);
    }

    /**
     * member become coadmin.
     * muted timer that maybe exist become invalid
     * @param m: CoadminPremoteMessage
     */
    private void coadminPremoteHandler(CoadminPremoteMessage m){
        timeKey = new Object(); //invalidate muted timer
        String msg = String.format("You have been promoted to co-admin in %s!",getGroupName());
        logAndTell(printer,msg,null);
        getContext().become(prevCoadmin);
    }

    private void printNotAdminOrCoadmin(GroupToUserInput m){
        logAndTell(printer,String.format("You are neither an admin nor a co-admin of %s!",getGroupName()),null);
    }

    /**
     *the user will become muted until the time in the message is up or until someone will unmute him
     * if the user is already muted the old time will become invalid
     * @param m: MuteMessage with sender name and time in milliseconds to be muted
     */
    private void mutedHandler(MuteMessage m){
        logger.debug("become muted:  muted by {}",m.senderName);
        String msg = String.format("You have been muted for %d in %s by %s!",m.milliTime,getGroupName(),m.senderName);
        logAndTell(printer,msg,null);
        getTimers().cancelAll(); //cencel old mute timers
        timeKey = new Object(); //invalidate old mute timers that maybe before this message but wasn't handle yet
        getTimers().startSingleTimer(timeKey,new MutedTImeIsUp(timeKey), Duration.ofMillis(m.milliTime)); //define new valid timer
        getContext().become(prevMuted);
    }

    private void coadminDemoteHandler(CoadminDemoteMessage m){
        String msg = String.format("You have been demoted to user in %s!",getGroupName());
        logAndTell(printer,msg,null);
        getContext().become(prevUser);
    }

    /**
     * check if the muted timer message is valid, if so the member become user
     * @param m: MutedTImeIsUp
     */
    private void mutedTImeIsUpHandler(MutedTImeIsUp m){
        logger.debug("become unmuted: time is up");
        if(m.key.equals(this.timeKey)) {
            getContext().become(prevUser);
            printer.tell("You have been unmuted! Muting time is up!",null);///////////////////////////////////////
        }
    }

    /**
     * cencel and invalidate muted timer, and become user
     * @param m: UnmuteMessage with sender name
     */
    private void unmutedHandler(UnmuteMessage m){
        logger.debug("become unmuted:  unmuted by {}",m.senderName);
        getContext().become(prevUser);
        getTimers().cancelAll();
        timeKey = new Object();
        String msg = String.format("You have been promote to user in %s by %s!",self().path().name(),m.senderName);
        printGroupFormat(m.senderName,msg);
    }

    private void removeHandler(AbstractNamedMessage m){
        String msg = String.format("You have been removed from %s by %s!",self().path().name(),m.senderName);
        printGroupFormat(m.senderName,msg);
        removed = true;
        getContext().stop(self());
    }

    private void closeHandler(){
        String msg = String.format("%s admin has closed %s!",getGroupName(),getGroupName());
        logAndTell(printer,msg,getSelf());
        getContext().stop(self());
    }

    /**
     * using the sourse ref from the server to get the group members
     * @param sr: SourceRef of InitAddToGroupMessage with group members and there group refs
     */
    private void initMembersHandler(SourceRef<InitAddToGroupMessage> sr){
        sr.getSource().runForeach(e -> members.put(e.senderName,e.ref),mat)
                .thenRun(() -> {
                    printer.tell(String.format("Welcome to %s!",getGroupName()),null);
                    getContext().become(prevUser);
                    logger.debug("started as user");
                });
    }

    /**
     * handle the exit from group by appropriate message and deleting all group files
     */
    @Override
    public void postStop() {
        if(removed)
            logAndTell(groupRef,new RemoveFromGroupMessage(userName),getSelf()); //remove by some admin/coadmin case
        else
            logAndTell(groupRef, new LeaveGroupMessage(userName), getSelf()); //leave group or disconnect case
        deleteGroupFiles();

    }


}
