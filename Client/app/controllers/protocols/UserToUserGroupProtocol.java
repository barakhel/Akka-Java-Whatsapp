package controllers.protocols;

import controllers.protocols.Abstracts.*;

public class UserToUserGroupProtocol {


    static public class MuteMessage extends AbstractNamedMessage{
        public final int milliTime;

        public MuteMessage(String senderName, int milliTime){
            super(senderName);
            this.milliTime = milliTime;
        }
    }
    static public class UnmuteMessage extends AbstractNamedMessage{
        public UnmuteMessage(String senderName){super(senderName);}
    }

    static public class CoadminPremoteMessage extends AbstractNamedMessage{
        public CoadminPremoteMessage(String senderName){super(senderName);}
    }

    static public class CoadminDemoteMessage extends AbstractNamedMessage{
        public CoadminDemoteMessage(String senderName){super(senderName);}
    }

    static public class InviteMessage extends AbstractNamedMessage{
        public InviteMessage(String senderName){super(senderName);}
    }

    static public class RemoveMessage extends AbstractNamedMessage {
        public RemoveMessage(String senderName){super(senderName);}
    }

    static public class AdminMuteMessage extends MuteMessage{
        public AdminMuteMessage(String senderName,int milliTime){super(senderName,milliTime);}
    }

    static public class AdminRemoveMessage extends AbstractNamedMessage {
        public AdminRemoveMessage(String senderName){super(senderName);}
    }

    static public class InviteAnsYesMessage extends AbstractNamedMessage{
        public InviteAnsYesMessage(String senderName){super(senderName);}
    }
    static public class InviteAnsNoMessage extends AbstractNamedMessage{
        public InviteAnsNoMessage(String senderName){super(senderName);}
    }

    static public class ActionNotAllowedMassage implements RemoteMessageInterface{
        public final String msg;

        public ActionNotAllowedMassage(String msg){
            this.msg = msg;
        }
    }
    static public class InviteAnsOkMessage implements RemoteMessageInterface{}


}
