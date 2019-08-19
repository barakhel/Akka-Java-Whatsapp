package com.akka.server;

import akka.NotUsed;
import akka.actor.*;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.StreamRefs;
import akka.stream.javadsl.Source;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import akka.pattern.Patterns;
import com.akka.server.protocols.ClientServerGroupsProtocol.*;
import com.akka.server.protocols.ClientServerProtocol.*;

/**
 * this actor class manage the server connections
 * and in charge of updating the the active users on
 * connection/disconnections of users and
 * creating new groups
 */
public class Connector extends AbstractLogActor {
    private final HashMap<String, UserRefs> activeUsers;
    private final Set<String> disconnectingUsers;
    private final Materializer mat;
    private String tmpFileDir; //path to where tmp file will be saved, for now only group file will be save in this path
    static public Props props() {
        return Props.create(Connector.class, () -> new Connector());
    }

    private class ForceDisconnect{
        public final String userName;
        public ForceDisconnect(String userName){this.userName = userName;}
    }


    /**
     * structure for internal usage in activeUsers.
     */
    private class UserRefs{
        public final ActorRef  mainRef;
        public final ActorRef reseiveRef;

        public UserRefs(ActorRef main_ref, ActorRef reseive_ref){
            this.mainRef = main_ref;
            this.reseiveRef = reseive_ref;
        }

    }

    public Connector(){
        super();
        activeUsers = new HashMap<>();
        disconnectingUsers = new TreeSet<>();
        mat = ActorMaterializer.create(getContext());
        tmpFileDir = util.createdir(Paths.get("tmpfile"));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ConnectMessage.class, m -> {
                    if(!disconnectingUsers.contains(m.senderName) && activeUsers.putIfAbsent(m.senderName,new UserRefs(m.mainRef,m.receiveRef)) == null) {
                        System.out.println("server: "+getSender().path().address() + " connected");
                        connectHandler(m);
                    }
                    else {
                        System.out.println("server: "+getSender().path() + " connected failed");
                        getSender().tell(new ConnectedFailedMessage(), getSelf());
                    }
                })
                .match(DisconnectMessage.class,this::disconnectHandler)
                .match(CreateGroupMessage.class,this::CreateGroupHandler)
                .match(DisconnectFinalStage.class,this::disconnectFinalStageHandler)
                .match(ForceDisconnect.class,m -> {removeActiveUser(m.userName);})
                .build();
    }

    /**
     * this function create an sourceRef of the active users and send a message user that he his connected
     * and the sourceRef.
     * in addition inform all other active users on the new user.
     * @param m: ConnectMessage
     */
    private void connectHandler(ConnectMessage m) {
        getContext().watchWith(m.mainRef,new ForceDisconnect(m.senderName));
        Source<AddUserMessage,NotUsed> uStream = Source.from(this.activeUsers.entrySet())
                .filter(user -> !user.getKey().equals(m.senderName))
                .map(e -> new AddUserMessage(e.getKey(),e.getValue().reseiveRef));
        CompletionStage<SourceRef<AddUserMessage>> logsRef = uStream.runWith(StreamRefs.sourceRef(),mat);
        Patterns.pipe(logsRef.thenApply(s -> new ConnectedSuccessfullyMessage(getSelf(),s)), context().dispatcher())
                .to(getSender()).future();

        Source.from(this.activeUsers.entrySet()).runForeach(u -> u.getValue().mainRef.tell(new AddUserMessage(m.senderName,m.receiveRef),null),mat);
    }

    /**
     * if group name is already exist then send create group denial message to the user how ask to create the group.
     * otherwise create new group with the requested name and inform the user.
     * @param m CreateGroupMessage with group name
     */
    private void CreateGroupHandler(CreateGroupMessage m){
        System.out.println(String.format("%s want to create group %s",getSender().path().name(),m.groupName));
        if(getContext().findChild(m.groupName).isPresent()) {
            System.out.println("group name exist");
            getSender().tell(new CreateGroupDenialMessage(m.groupName), null);
        }
        else {
            System.out.println(String.format("create group %s!!",m.groupName));
            getContext().actorOf(GroupRouter.props(getSender(),tmpFileDir, mat),m.groupName);
        }
    }

    /**
     * remove the user from the active users and inform all other users.
     * the name of the removed user saved until the disconnecting procedure done
     * to prevent other user to connect with this name befor the disconnecting procedure is over
     * @param m: DisconnectMessage
     */
    private void disconnectHandler(DisconnectMessage m){
        ActorRef ref = removeActiveUser(m.senderName);
        if(ref != null) {
            disconnectingUsers.add(m.senderName);
            ref.tell("yes", null);
        }
    }

    /**
     * the final disconnecting procedure stage, after this the user will be disconnected
     * and is name will be available.
     * if the use is in 'disconnectingUsers' then he simply removed and disconnected finished message will be sent
     * to the user.
     * otherwise this imply that the user forced to disconnect and he simply removed from active users
     * without saving is name and without disconnected finished message
     * @param m: DisconnectFinalStage
     */
    private void disconnectFinalStageHandler(DisconnectFinalStage m){
        logDebug("user {} is disconnected",m.senderName);
        System.out.println(String.format("%s disconnected",m.senderName));
        if(disconnectingUsers.remove(m.senderName)) {
            getContext().unwatch(getSender());
            getSender().tell(new DisconnectFinished(), null);
        }
        else
            removeActiveUser(m.senderName);
    }

    /**
     * remove the given user name from active users if exist and inform all other active users.
     * @param userName: some active user name
     * @return
     *          * if user name exist in activeUsers then return his main actorRef
     *          * otherwise return null
     */
    private ActorRef removeActiveUser(String userName){
        UserRefs uref = activeUsers.remove(userName);
        if(uref == null){ //check if exist in active users
            logError("user {} not found",userName);
            return null;
        }
        Source.from(this.activeUsers.entrySet()) //inform all other users
                .runForeach(u -> u.getValue().mainRef.tell(new SomeoneLeaveMessage(userName),null),mat);
        logDebug("user {} disconnecting",userName);
        return uref.mainRef;
    }



}
