package com.akka.server.protocols;

import akka.actor.ActorRef;
import akka.stream.SourceRef;
import com.akka.server.protocols.Abstracts.*;

public class ClientServerProtocol {
    static public class ConnectMessage extends AbstractNamedMessage {
        public final ActorRef mainRef;
        public final ActorRef receiveRef;
        public ConnectMessage(String senderName, ActorRef mainRef, ActorRef receiveRef ){
            super(senderName);
            this.mainRef = mainRef;
            this.receiveRef = receiveRef;
        }
    }

    static public class DisconnectMessage extends AbstractNamedMessage{
        public DisconnectMessage(String senderName){super(senderName);}
    }

    static public class DisconnectFinalStage extends AbstractNamedMessage{
        public DisconnectFinalStage(String senderName){super(senderName);}
    }

    static public class DisconnectFinished implements RemoteMessageInterface {}

    static public class ConnectedSuccessfullyMessage implements RemoteMessageInterface {
        public final ActorRef serverRef;
        public final SourceRef sorceSupp;
        public ConnectedSuccessfullyMessage(ActorRef serverRef, SourceRef sorceSupp){
            this.serverRef = serverRef;
            this.sorceSupp = sorceSupp;
        }
    }
    static public class ConnectedFailedMessage implements RemoteMessageInterface{}

    static public class SomeoneLeaveMessage implements RemoteMessageInterface{
        public final String userName;
        public SomeoneLeaveMessage(String userName){
            this.userName = userName;
        }
    }

    static public class AddUserMessage implements RemoteMessageInterface{
        public final String userName;
        public final ActorRef ref;
        public AddUserMessage(String userName, ActorRef ref){
            this.userName = userName;
            this.ref = ref;
        }
    }





}
