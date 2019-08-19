package controllers.Groups;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.Materializer;
import controllers.protocols.ClientServerGroupsProtocol.*;
import controllers.IO.InputParser.*;
import controllers.protocols.UserToUserGroupProtocol.*;


public class AdminGroup extends AbstractGroupActor {

    static public Props props(String user_name, ActorRef group_ref, ActorRef printer, Materializer mat) {
        return Props.create(AdminGroup.class, () -> new AdminGroup(user_name, group_ref, printer, mat));
    }

    public AdminGroup(String user_name,ActorRef group_ref,ActorRef printer,Materializer mat){
        super(user_name, group_ref, printer,mat);
        printer.tell(String.format("%s created successfully!",getGroupName()),null);
    }

    /**
     * the actor createReceive implementaion
     * @return Receive how handle:
     *  - group mute, remove, coadmin add, coadmin remove commends
     *  - all admin/coadmin behavior
     *  - all basic behavior
     */
    @Override
    public Receive createReceive(){
        return receiveBuilder()
                .match(GroupMuteInput.class, m ->
                        checkAndForward(m.toUser,new AdminMuteMessage(userName,m.time)))
                .match(GroupRemoveInput.class,m ->
                        checkAndForward(m.toUser,new AdminRemoveMessage(userName)))
                .match(GroupCoadminAddInput.class,m ->
                        checkAndForward(m.toUser, new CoadminPremoteMessage(userName)))
                .match(GroupCoadminRemoveInput.class,m ->
                        checkAndForward(m.toUser, new CoadminDemoteMessage(userName)))
                .build()
                .orElse(adminCoadminReceive())
                .orElse(basicReceive());
    }

    /**
     * stopping group manager actor is literally closing the group
     * so we need to inform the group server actor how inform all
     * other users and close the group.
     * in addition all the group files will be deleted
     */
    @Override
    public void postStop() {
        logAndTell(groupRef,new CloseGroupMessage(),self());
        deleteGroupFiles();

    }
}
