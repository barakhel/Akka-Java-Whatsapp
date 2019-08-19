package com.akka.server.protocols.Abstracts;

public class AbstractNamedMessage implements RemoteMessageInterface {
    public final String senderName;

    public AbstractNamedMessage(String senderName){
        this.senderName = senderName;
    }
}
