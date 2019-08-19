package com.akka.server.protocols;

import akka.stream.SourceRef;
import akka.util.ByteString;
import com.akka.server.protocols.Abstracts.AbstractNamedMessage;


public class UserToUserProtocol {

    static public class TextMessage extends AbstractNamedMessage {
        public final String msg;

        public TextMessage(String senderName, String msg){
            super(senderName);
            this.msg = msg;
        }
    }

    static public class FileMessage extends AbstractNamedMessage {
        public final String fileName;
        public final SourceRef<ByteString> fileRef;

        public FileMessage(String senderName ,String fileName, SourceRef<ByteString> fileRef){
            super(senderName);
            this.fileName = fileName;
            this.fileRef = fileRef;
        }
    }
}
