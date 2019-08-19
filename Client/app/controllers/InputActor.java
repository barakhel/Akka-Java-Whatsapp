package controllers;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import controllers.logging.AbstractLogActorWithTimers;
import controllers.protocols.UserToUserGroupProtocol.*;
import controllers.IO.InputParser.*;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * this Actor class manage the parsed user commend by forword them
 * to the suitable Actor from UsarActor, Group, or Printer.
 * in addition he enforce 'connect' commend before connection
 * and 'yes/no' input after invitation to some group
 *
 * to the application's home page.
 */
public class InputActor extends AbstractLogActorWithTimers {
    static public Props props(ActorPath serverBasePath) {

        return Props.create(InputActor.class, () -> new InputActor(serverBasePath));
    }

    /**
     * message format that UserActor send to this actor to inform that the connect approved
    */
    public static class ConnectApproved{
        public final String name;
        public final ActorRef groups_ref;
        public ConnectApproved(String name, ActorRef groups_ref){
            this.name = name;
            this.groups_ref = groups_ref;

        }
    }

    /**
     * message format that UserActor send to this actor to inform that the disconnect approved
     */
    static public class DisconnMsg{}

    /**
     * message format that UserActor send to this actor to inform that the connect denial
     */
    public static class ConnectDenial{
        public final String name;
        public ConnectDenial(String name){this.name = name;}
    }

    /**
     * message format that UserActor send to this actor to inform that server is offline and connect failed
     */
    public static class ServerOffline{}

    private ActorRef userAktor;
    private ActorRef groups;
    private ActorRef printer;

    private final Receive preInit;
    private final Receive preConnect;
    private final Receive connecting;
    private final Receive active;
    private final Receive invited;

    private final Queue<ActorRef> invitationQueue; //unhandled group invitation
    private final ActorPath serverBasePath;
    private String userName;


    public InputActor(ActorPath serverBasePath){
        super("inputActor");

        this.invitationQueue = new PriorityQueue<>();
        this.serverBasePath = serverBasePath;
        this.preInit = createPreInit();
        this.preConnect = createPreConnect();
        this.connecting = createConnecting();
        this.invited = createInvited();
        this.active = createActive();


    }

    @Override
    public Receive createReceive(){
        return preInit;
    }


    /**
     * this function return Receive that used before play connector established
     * @return Receive how handle:
     *  - ActorRef of the printer from play connector
     */
    private Receive createPreInit(){
        return receiveBuilder().match(ActorRef.class, ref ->{
            this.printer = ref;
            getContext().watch(this.printer);
            this.userAktor = getContext().actorOf(UsersActor.props(serverBasePath,getSelf(),this.printer),"userActor");
            getContext().become(preConnect);
        }).build();
    }

    /**
     * this function return Receive that used before user preform connect
     * @return Receive how handle:
     *  - user connect commend
     */
    private Receive createPreConnect(){
        return receiveBuilder()
                .match(ConnectInput.class,m ->{
                    forwordToUserAktor(m);
                    getContext().become(connecting);})
                .match(Terminated.class,r -> getContext().stop(getSelf()))
                .build()
                .orElse(createIllegal());
    }

    /**
     * this function return Receive that used after user preform connect
     * and before userActor return if connection approved
     * @return Receive how handle:
     *  - connection approved/denial message from userActor
     *  - server is offline message from userActor
     */
    public Receive createConnecting(){
        return receiveBuilder()
                .match(ConnectApproved.class, m->
                {
                    forwordToPrinter(String.format("%s has connected successfully!", m.name));
                    printer.tell(String.format("tit %s",m.name),null);
                    groups = m.groups_ref;
                    userName = m.name;
                    getContext().become(active);
                })
                .match(ConnectDenial.class, m->{
                    forwordToPrinter(String.format("%s is in use!", m.name));
                    getContext().become(preConnect);
                })
                .match(ServerOffline.class,m -> {
                    forwordToPrinter("server is offline!");
                    getContext().become(preConnect);
                })
                .match(Terminated.class,r -> getContext().stop(getSelf()))
                .build()
                .orElse(createIllegal());
    }

    /**
     * this function return Receive that used after the user got invitation to some group
     * if by the time the user respond to the invitation other invitation was send
     * then they will be save in a queue and will be handle right after the respond
     * @return Receive how handle:
     *  - yes/no answer to the current invitation
     *  - group Invite  messages
     */
    public Receive createInvited(){
        return receiveBuilder()
                .matchEquals("yes",m -> invitationAnsHandler(new InviteAnsYesMessage(userName)))
                .matchEquals("no",m -> invitationAnsHandler(new InviteAnsNoMessage(userName)))
                .match(InviteMessage.class, this::invitedHandler)
                .match(DisconnMsg.class, m -> getContext().become(preConnect))
                .match(Terminated.class,r -> getContext().stop(getSelf()))
                .matchAny(o -> forwordToPrinter("your can answer yes/no to the Invitation"))
                .build();
    }

    /**
     * this function return Receive that used after the user connect approved
     * he forward each commend to the appropriate actor
     * @return Receive how handle:
     *  - all commends
     *  - groups invite messages
     */
    private Receive createActive(){
        return receiveBuilder()
                .match(UserTextInput.class, this::forwordToUserAktor)
                .match(UserFileInput.class, this::forwordToUserAktor)
                .match(GroupInviteInput.class, this::forwordToUserAktor)
                .match(DisconnectInput.class, this::forwordToUserAktor)
                .match(GroupTextInput.class, this::forwordToGroups)
                .match(GroupFileInput.class, this::forwordToGroups)
                .match(GroupRemoveInput.class, this::forwordToGroups)
                .match(GroupCreateInput.class, this::forwordToGroups)
                .match(GroupLeaveInput.class, this::forwordToGroups)
                .match(GroupCoadminAddInput.class, this::forwordToGroups)
                .match(GroupCoadminRemoveInput.class, this::forwordToGroups)
                .match(GroupMuteInput.class, this::forwordToGroups)
                .match(GroupUnmuteInput.class, this::forwordToGroups)
                .match(InviteMessage.class, m ->{
                    forwordToPrinter(String.format("You have been invited to %s, Accept?",getSender().path().name()));
                    invitedHandler(m);
                    getContext().become(invited);

                })
                .match(DisconnMsg.class, m -> {
                    printer.tell("tit guest",null);
                    getContext().become(preConnect);
                })
                .match(Terminated.class,r -> getContext().stop(getSelf()))
                .build()
                .orElse(createIllegal());
    }

    /**
     * this function return Receive that used to handle all illegal commends
     * @return Receive how handle:
     *  - illegalI parsed commends
     *  - unexpected mails
     */
    private Receive createIllegal(){
        return receiveBuilder()
                .match(IllegalInput.class,this::illegalInputHandler)
                .matchAny(this::receiveUnexpected)
                .build();
    }


    ////////////////////////////////HANDLERS///////////////////////////////
    private void illegalInputHandler(IllegalInput m){
        logDebug("Illegal input: {}, in {}",m.input,m.where);
        printer.tell(String.format("Illegal input: %s",m.input),null);
    }

    private void invitedHandler(InviteMessage m){
        invitationQueue.add(getSender());
    }

    private void receiveUnexpected(Object o){
        logWarn("Unexpected receive {}",o);
    }

    /**
     * handle answer to the current group invitation
     * if there is more invitation in the queue they will be handled
     * otherwise the user will become active
     * @param ans: InviteAnsYesMessage or InviteAnsNoMessage
     */
    private void invitationAnsHandler(Object ans){
        invitationQueue.poll().tell(ans,groups);
        if(invitationQueue.isEmpty())
            getContext().become(active);
        else
            forwordToPrinter(String.format("You have been invited to %s, Accept?",invitationQueue.peek().path().name()));

    }

    /////////////////////////////UTILS//////////////////////////////////////
    private void forwordTo(ActorRef ref,Object m){
        logAndTell(ref,m,null);
    }

    private void forwordToUserAktor(Object m){
        forwordTo(userAktor,m);
    }
    private void forwordToGroups(Object m){
        forwordTo(groups,m);
    }

    private void forwordToPrinter(String msg){
        forwordTo(printer,msg);
    }

    @Override
    public void postStop() {
    getContext().getSystem().terminate();
    }

}
