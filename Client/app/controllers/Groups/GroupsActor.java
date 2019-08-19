package controllers.Groups;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.stream.Materializer;
import controllers.logging.AbstractLogActor;
import controllers.protocols.ClientServerGroupsProtocol.*;
import controllers.IO.InputParser.*;
import controllers.protocols.UserToUserGroupProtocol.InviteAnsOkMessage;

import java.time.Duration;

public class GroupsActor extends AbstractLogActor {

    static public Props props(String user_name,ActorRef server,ActorRef printer,Materializer mat) {
        return Props.create(GroupsActor.class, () -> new GroupsActor(user_name,server,printer,mat));
    }

    private static SupervisorStrategy strategy =
            new OneForOneStrategy(
                    20,
                    Duration.ofSeconds(10),
                    DeciderBuilder.match(Exception.class, e -> SupervisorStrategy.resume())
                            .matchAny(o -> SupervisorStrategy.stop())
                            .build());

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    private final ActorRef server;
    private final ActorRef printer;
    private final String userName;
    private final Materializer mat;

    public GroupsActor(String userName, ActorRef server, ActorRef printer, Materializer mat){
        super();
        this.userName = userName;
        this.server = server;
        this.printer = printer;
        this.mat =  mat;
    }

    public Receive createReceive(){
        return receiveBuilder()
                .match(GroupCreateInput.class,m -> logAndTell(server,new CreateGroupMessage(m.groupName),getSelf()))
                .match(GroupLeaveInput.class,this::LeaveGroupHandler )
                .match(GroupTextInput.class,this::checkAndForward)
                .match(GroupFileInput.class,this::checkAndForward)
                .match(GroupInviteInput.class,this::checkAndForward)
                .match(GroupRemoveInput.class,this::checkAndForward)
                .match(GroupCoadminAddInput.class,this::checkAndForward)
                .match(GroupCoadminRemoveInput.class,this::checkAndForward)
                .match(GroupMuteInput.class,this::checkAndForward)
                .match(GroupUnmuteInput.class,this::checkAndForward)
                .match(CreateGroupApproveMessage.class, this::CreateGroupApproveHandler)
                .match(CreateGroupDenialMessage.class, this::CreateGroupDenialHandler)
                .match(InviteAnsOkMessage.class, m -> getContext().actorOf(MemberGroup.props(userName,getSender(),printer,mat),getSenderName()))
                .match(Terminated.class,r -> logDebug("group {} closed",r.actor().path().name()))
                .matchAny(o -> printer.tell("groupsActor no match",null))
                .build();
    }

    /**
     * this function receives parsed commend that need to be forward to some group to handle
     * if the user is member in this group the parsed commend will be forward
     * otherwise an error message will be printed
     * @param m: parsed commend that contain group name
     */
    private void checkAndForward(GroupInput m){
        getContext().findChild(m.groupName).ifPresentOrElse(child ->
                logAndTell(child,m,getSender()),
                () -> printer.tell(String.format("%s does not exist!",m.groupName),null));
    }

    /**
     * this function receives parsed group leave commend
     * if the user is member in this group the leave commend will be execute on the group
     * otherwise an error message will be printed
     * @param m: parsed group leave commend
     */
    private void LeaveGroupHandler(GroupLeaveInput m){
        getContext().findChild(m.groupName).ifPresentOrElse(child ->
                child.tell(PoisonPill.getInstance(),null),
                () -> printer.tell(String.format("%s is not in %s",userName,m.groupName),null));
    }

    /**
     * this function handle the approve from the server to create some group
     * the sender will we the server group that correspond to the new group
     * and a new adminGroup will be created.
     * @param m: CreateGroupApproveMessage
     */
    private void CreateGroupApproveHandler(CreateGroupApproveMessage m){
        logger.debug("Create group {} Approved",getSenderName());
        getContext().actorOf(AdminGroup.props(userName,getSender(),printer,mat),getSenderName());
    }

    /**
     * this function handle the Denial from the server to create some group,
     * appropriate message will be printed
     * @param m: reateGroupDenialMessage with the group name
     */
    private void CreateGroupDenialHandler(CreateGroupDenialMessage m){
        logger.debug("Create {} was Denial",m.groupName);
        printer.tell(String.format("%s already exists!",m.groupName),null);
    }

    /////////////////////////////UTILS/////////////////////////////
    private String getSenderName(){return getActorName(getSender());}
    private String getActorName(ActorRef ref){return ref.path().name();}




}
