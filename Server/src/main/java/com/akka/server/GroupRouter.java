package com.akka.server;

import akka.NotUsed;
import akka.actor.*;
import akka.pattern.Patterns;
import akka.routing.*;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import com.akka.server.protocols.Abstracts.AbstractNamedMessage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;
import com.akka.server.protocols.ClientServerGroupsProtocol.*;

/**
 * manage one group and use router to send most of the updates and messages
 * to the group.
 * file groups send in a different manner because the server use unique sourceRef
 * to each user to support parallelism and reliability.
 */
public class GroupRouter extends AbstractActor {
    static public Props props(ActorRef admin,String tmpFileRoot, Materializer mat) {
        return Props.create(GroupRouter.class, () -> new GroupRouter(admin,tmpFileRoot,mat));
    }
    private Router router;
    private final HashMap<ActorRef,String> members;
    private final Materializer mat;
    private final String tmpFileDir; //save temp file in this path

    public GroupRouter(ActorRef admin,String tmpFileRoot, Materializer mat){
        router = new Router(new BroadcastRoutingLogic());
        this.mat = mat;
        members = new HashMap<>();
        this.tmpFileDir = util.createdir(Paths.get(tmpFileRoot,getGroupName()));
        admin.tell(new CreateGroupApproveMessage(),self());

    }

    public Receive createReceive(){
        return receiveBuilder()
                .match(AddToGroupMessage.class,this::addHandler)
                .match(RemoveFromGroupMessage.class,this::removeHandler)
                .match(LeaveGroupMessage.class,this::removeHandler)
                .match(CloseGroupMessage.class,this::closeHandler)
                .match(TextGroupMessage.class, m -> router.route(m,sender()))
                .match(FileGroupMessage.class,this::sendGroupFile)
                .build();
    }

    /**
     * add member to group.
     * if there is other member in the group, meaning this is not the admin,
     * inform other members and send the members to the new member.
     * @param m: AddToGroupMessage with member user name and group actorRef
     */
    private void addHandler(AddToGroupMessage m){
        if(!members.isEmpty()) {
            sendMembersMessage();
            AddToGroupMessage addmsg = new AddToGroupMessage(m.senderName);
            router.route(addmsg,getSender());
        }
        System.out.println("new routee");
        System.out.println(getSender().path().toString());
        router = router.addRoutee(getSender());
        members.put(getSender(),m.senderName);
        router.routees().iterator().toStream().print(",");
        System.out.println(router.logic().toString());
    }

    /**
     * remove member from group and inform other members if exist
     * @param m: Message extend AbstractNamedMessage with member user name
     */
    private void removeHandler(AbstractNamedMessage m){
        router = router.removeRoutee(getSender());
        members.remove(getSender());
        router.route(m,sender());
    }

    /**
     * close the group and inform other members if exist
     * append only if the admin leave the group
     * @param m: CloseGroupMessage
     */
    private void closeHandler(CloseGroupMessage m){
        System.out.println("Close group");
        router = router.removeRoutee(getSender());
        members.remove(getSender());

        if(!members.isEmpty())
            router.route(m,getSender());

        getContext().stop(getSelf());
    }

    /**
     * send message to new group member with sourceRef of the other member in the group.
     */
    private void sendMembersMessage() {
        ActorRef sender = getSender();
        Source<InitAddToGroupMessage, NotUsed> uStream = Source.from(this.members.entrySet())
                .map(e -> new InitAddToGroupMessage(e.getValue(),e.getKey()));
        CompletionStage<SourceRef<InitAddToGroupMessage>> members_Ref = uStream.runWith(StreamRefs.sourceRef(),mat);
        Patterns.pipe(members_Ref.thenApply(MembersMessage::new), context().dispatcher())
                .to(sender).future();

    }

    /**
     * download the file temporary from the sender sourceRef and send to each user
     * unique sourceRef of the file and a common FileSendCounter actor to manage the cleanup
     * of the tmp file.
     * @param m: FileGroupMessage with sender name and  sourceRef of the file
     */
    private void sendGroupFile(FileGroupMessage m){
        Path tmpPath = util.findUnusedName(tmpFileDir,m.fileName);
        m.fileRef.getSource().runWith(FileIO.toPath(tmpPath),mat).thenRun(() -> {
            final ActorRef countRef = getContext().actorOf(FileSendCounter.props(tmpPath,members.size(),300000));
                Source.from(members.keySet()).runForeach(mem -> sendFileMessage(mem, m.senderName,tmpPath,m.fileName,countRef),mat);
    });
    }

    /**
     * helper to sendGroupFile, weap the sender name, file name and sourceRef and pipe it to target group member
     * @param mem: target ActorRef
     * @param senderName: user name of the original sender
     * @param path: path of the tmp file in the server
     * @param fileName: origin file name
     * @param countRef: FileSendCounter actorRef
     */
    private void sendFileMessage(ActorRef mem,String senderName,Path path,String fileName,ActorRef countRef){
        Patterns.pipe(util.createFSRef(path,mat).thenApply(s -> new FileGroupMessage(senderName, fileName, s)), context().dispatcher())
                .to(mem,countRef).future();

    }

    private String getGroupName(){
        return getSelf().path().name();
    }
}

