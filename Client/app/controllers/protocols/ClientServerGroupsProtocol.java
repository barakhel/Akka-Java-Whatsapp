package controllers.protocols;

import akka.actor.ActorRef;
import akka.stream.SourceRef;
import akka.util.ByteString;
import controllers.protocols.Abstracts.AbstractNamedMessage;
import controllers.protocols.Abstracts.RemoteMessageInterface;


public class ClientServerGroupsProtocol {
    static private abstract class AbstractToServer implements RemoteMessageInterface {
        public final String groupName;
        public final String sender;
        public AbstractToServer(String groupName,String sender){
            this.groupName = groupName;
            this.sender = sender;
        }
    }

    static private abstract class AbstractToUser implements  RemoteMessageInterface{
        public final String sender;
        public AbstractToUser(String sender){
            this.sender = sender;
        }
    }

    static public class CreateGroupMessage implements RemoteMessageInterface {
        public final String groupName;
        public CreateGroupMessage(String groupName){
            this.groupName = groupName;
        }
    }
    static public class CreateGroupApproveMessage implements RemoteMessageInterface{
    }

    static public class MembersMessage implements RemoteMessageInterface{
        public final SourceRef sorceSupp;
        public MembersMessage(SourceRef sorceSupp){this.sorceSupp = sorceSupp;}
    }

    static public class CreateGroupDenialMessage implements RemoteMessageInterface{
        public final String groupName;
        public CreateGroupDenialMessage(String groupName){
            this.groupName = groupName;
        }
    }

    static public class LeaveGroupMessage extends AbstractNamedMessage{
        public LeaveGroupMessage(String sender){super(sender);}
    }

    static public class RemoveFromGroupMessage extends AbstractNamedMessage {
        public RemoveFromGroupMessage(String sender){super(sender);}
    }

    static public class InitAddToGroupMessage extends AbstractNamedMessage {
        public final ActorRef ref;
        public InitAddToGroupMessage(String sender,ActorRef ref){
            super(sender);
            this.ref = ref;
        }
    }

    static public class AddToGroupMessage extends AbstractNamedMessage {
        public AddToGroupMessage(String sender){super(sender);}
    }

    static public class CloseGroupMessage implements RemoteMessageInterface{}

    static public class SendTextGroupMessage extends AbstractToServer{
        public final String msg;
        public SendTextGroupMessage(String groupName,String sender,String msg){
            super(groupName,sender);
            this.msg = msg;
        }
    }

    static public class FileGroupMessage extends AbstractNamedMessage{
        public final String fileName;
        public final SourceRef<ByteString> fileRef;
        public FileGroupMessage(String sender,String  fileName, SourceRef<ByteString> fileRef){
            super(sender);
            this.fileName = fileName;
            this.fileRef = fileRef;
        }
    }

    static public class TextGroupMessage extends AbstractToUser{
        public final String msg;
        public TextGroupMessage(String sender,String msg){
            super(sender);
            this.msg = msg;
        }
    }

}
