package controllers;

import akka.actor.*;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.*;
import controllers.logging.AbstractLogActorWithTimers;
import controllers.protocols.ClientServerProtocol.*;
import scala.concurrent.Await;
import scala.concurrent.Future;
import controllers.IO.InputParser.*;
import controllers.Groups.GroupsActor;
import controllers.InputActor.*;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * this actor class manage the active users list and the basic communication with the server such as:
 * - add/remove active users from the list
 * - connect/disconnect operations
 */
public class UsersActor extends AbstractLogActorWithTimers {
    private final Receive preConnect;
    private final Receive active;
    private final Receive disconnecting;
    static Materializer mat;
    private String myName;
    final ActorPath serverPath;
    private final ActorRef printer;
    private ActorRef serverRef;
    private ActorRef userSender;
    private final ActorRef messageReseiver;
    private final ActorRef inputActor;
    private  ActorRef groups;
    private long maxServerResposeTime;
    private final HashMap<String, ActorRef> activeUsers;

    static public Props props(ActorPath serverPath, ActorRef input_actor,ActorRef printer) {
        return Props.create(UsersActor.class, () -> new UsersActor(serverPath, input_actor,printer));
    }

    public UsersActor(ActorPath serverPath, ActorRef input_actor, ActorRef printer){
        super("usersActor");
        this.serverPath = serverPath;
        this.preConnect = createPreConnect();
        this.active = createActive();
        this.disconnecting = createDisconnecting();

        this.printer = printer;
        this.activeUsers = new HashMap<>();
        mat = ActorMaterializer.create(getContext());///////////////////////////////////////////////////////////////////////////

        this.inputActor = input_actor;
        this.messageReseiver = getContext().actorOf(MessageReceiver.props(this.inputActor,this.printer,mat),"messageReseiver");

        this.myName = null;
        maxServerResposeTime = 5000;

    }

    @Override
    public Receive createReceive() {
        return this.preConnect;
    }

    /**
     * this function return Receive that used before the user is connected
     * @return Receive how handle:
     *  - connect commend
     */
    private Receive createPreConnect(){
        return receiveBuilder().match(ConnectInput.class,input ->{
            if(input.userName != null){
                try {
                    Future<Object> fut = Patterns.ask(getContext().actorSelection(this.serverPath.child("conn")),new ConnectMessage(input.userName,getSelf(),messageReseiver),maxServerResposeTime);
                    Object ans = Await.result(fut, Duration.create(maxServerResposeTime, TimeUnit.MILLISECONDS));
                    if (ans instanceof  ConnectedSuccessfullyMessage) {
                        connectedSuccessfullyHandler((ConnectedSuccessfullyMessage)ans,input.userName);
                    }
                    else {
                        this.inputActor.tell(new ConnectDenial(input.userName),null);
                    }
                }catch (Exception e){
                    this.inputActor.tell(new ServerOffline(),null);
                }
            }

        }).build();
    }

    /**
     * this function return Receive that used after the user is connected
     * @return Receive how handle:
     *  - user text/file commends
     *  - group invite commend
     *  - add/remove active users that send by the server
     */
    private Receive createActive(){
        return receiveBuilder()
                .match(UserTextInput.class,this::checkAndForwardToUserSender)
                .match(UserFileInput.class,this::checkAndForwardToUserSender)
                .match(GroupInviteInput.class,this::groupInviteHandler)
                .match(DisconnectInput.class, m -> disconnectHandler())
                .match(AddUserMessage.class,this::addUserHandler)
                .match(SomeoneLeaveMessage.class,this::removeUserHandler)
                .matchAny(o -> {
                    printer.tell("input error: no match",null);
                    logDebug("input error: no match to {} from {}",o,getSender().path().name());
                })
                .build();
    }

    /**
     * this function return Receive that used after disconnect approved by the server
     * @return Receive how handle:
     *  - Terminated from groups
     *  - DisconnectFinished from server
     */
    private Receive createDisconnecting() {
        return receiveBuilder()
                .match(Terminated.class,r -> r.actor().equals(groups), r ->logAndTell(serverRef, new DisconnectFinalStage(myName), getSelf()))
                .match(DisconnectFinished.class, m -> disconnectFinishedHandler()).build();
    }

    /**
     * this function inform inputActor that the user indeed disconnected,
     * cleanup activeUsers list and print disconnected message
     */
    private void disconnectFinishedHandler(){
        logAndTell(inputActor,new InputActor.DisconnMsg(),null);
        String msg = String.format("%s has been disconnected successfully!",myName);
        logAndTell(printer,msg,null);
        activeUsers.clear();
        myName = null;
        getContext().become(preConnect);
    }

    /**
     * fill the active user list with the users from the source ref
     * given by the server.
     * @param sr: sourceRef of active user names and there message receiver ActorRef
     */
    private void initActiveUsershHandler(SourceRef<AddUserMessage> sr){
        sr.getSource().runWith(Sink.foreach(this::addUserHandler),mat)
                .thenRun(() -> {
                    getContext().become(this.active);
                    logger.debug("become active");
                    inputActor.tell(new ConnectApproved(this.myName,this.groups),null);
                });
    }

    /**
     * add new user to active user list
     * @param user
     */
    private void addUserHandler(AddUserMessage user){

        activeUsers.put(user.userName,user.ref);
        logDebug("add {} to active users",user.userName);
    }

    /**
     * remove user from active user list
     * @param user: SomeoneLeaveMessage with user name to remove
     */
    private void removeUserHandler(SomeoneLeaveMessage user){
        activeUsers.remove(user.userName);
        logDebug("add {} to active users",user.userName);
    }


    /**
     * this function handle group invite commend.
     * if the target exist in active users, the message will be forward to groups
     * with target as the sender.
     * otherwise an error will be printed
     * @param m: parsed invite commend
     */
    private void groupInviteHandler(GroupInviteInput m){
        ActorRef to_ref = activeUsers.get(m.toUser);
        if(to_ref != null)
            groups.tell(m,to_ref);
        else
            printer.tell(String.format("%s does not exist!",m.toUser),null);
    }

    /**
     * this function handle connected Successfully
     *  * she set the user name.
     *  * create userSender and groups actors.
     *  * inform messageReseiver actor about the new name
     * -
     * @param m: ConnectedSuccessfullyMessage
     * @param userName: approved user name
     */
    private void connectedSuccessfullyHandler(ConnectedSuccessfullyMessage m,String userName){
        this.serverRef = m.serverRef;
        myName = userName;
        this.userSender = getContext().actorOf(UserMessageSender.props(myName,printer,mat),myName);
        this.groups = getContext().actorOf(GroupsActor.props(myName,this.serverRef,printer,mat),"groups");
        this.messageReseiver.tell(myName,null);

        initActiveUsershHandler(m.sorceSupp);
    }

    /**
     *  * this function handle the first step to disconnect
     *  at first a message is send to server th inform about the disconnecting
     *  if server is offline all the operation faild
     *  otherwise PoisonPill is send to group and userSender, and DisconnMsg is send to messageReseiver.
     *  then group is been watch and UserActor become disconnecting
     */
    private void disconnectHandler(){
        if(Patterns.ask(serverRef,new DisconnectMessage(myName), java.time.Duration.ofMillis(maxServerResposeTime)).toCompletableFuture().isCompletedExceptionally())
            logAndTell(printer,"server is offline! try again later!",null);
        else {
            getContext().watch(groups);
            logAndTell(groups, PoisonPill.getInstance(), null);
            logAndTell(userSender,PoisonPill.getInstance(),null);
            logAndTell(messageReseiver,new MessageReceiver.DisconnMsg(),null);
            getContext().become(disconnecting);
        }
    }


    ///////////////////////////////UTILS///////////////////////////////////////
    private void checkAndForwardToUserSender(UserInput m){
        checkAndForward(userSender,m);
    }

    /**
     * this function check if the target name in the message exist,
     * if so forward the message to ref with the ActorRef of target as
     * the sender.
     * otherwise an error message will be printed
     * @param ref: how to forward
     * @param m: parsed commend
     */
    private void checkAndForward(ActorRef ref,UserInput m){
        ActorRef toRef = activeUsers.get(m.toUser);
        if(toRef != null)
            logAndTell(ref,m,toRef);
        else
            logAndTell(printer,String.format("%s does not exist!",m.toUser),null);
    }
}

