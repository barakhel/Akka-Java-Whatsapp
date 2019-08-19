package controllers.IO;

public class InputParser {
    static public abstract class UserInput{
        public final String toUser;

        public UserInput(String toUser){this.toUser = toUser;}
    }

    static public abstract class GroupInput{
        public final String groupName;

        public GroupInput(String groupName){this.groupName = groupName;}
    }

    static public abstract class GroupToUserInput extends GroupInput {
        public final String toUser;

        public GroupToUserInput(String groupName, String toNser){
            super(groupName);
            this.toUser = toNser;
        }
    }

    static public class ConnectInput{
        public final String userName;
        public ConnectInput(String userName){this.userName = userName;}
    }

    static public class DisconnectInput{}

    static public class IllegalInput{
        public final String input;
        public final String where;
        public IllegalInput(String input,String where){
            this.input = input;
            this.where = where;
        }
    }

    ////////////User////////////
    static public class UserTextInput extends UserInput {
        public final String msg;

        public UserTextInput(String userName, String msg){
            super(userName);
            this.msg = msg;
        }
    }

    static public class UserFileInput extends UserInput {
        public final String filePath;

        public UserFileInput(String userName, String filePath){
            super(userName);
            this.filePath = filePath;
        }
    }

    //////////Group/////////////
    static public class GroupCreateInput extends GroupInput {
        public GroupCreateInput(String groupName){super(groupName);}
    }

    static public class GroupLeaveInput extends GroupInput {
        public GroupLeaveInput(String groupName){super(groupName);}
    }

    static public class GroupTextInput extends GroupInput {
        public final String msg;

        public GroupTextInput(String groupName, String msg){
            super(groupName);
            this.msg = msg;
        }
    }

    static public class GroupFileInput extends GroupInput {
        public final String filePath;

        public GroupFileInput(String groupName, String filePath){
            super(groupName);
            this.filePath = filePath;
        }
    }

    static public class GroupInviteInput extends GroupToUserInput {
        public GroupInviteInput(String groupName, String toUser){super(groupName,toUser);}
    }

    static public class GroupRemoveInput extends GroupToUserInput {
        public GroupRemoveInput(String groupName, String toUser){super(groupName,toUser);}
    }

    static public class GroupMuteInput extends GroupToUserInput {
        public final int time;

        public GroupMuteInput(String groupName, String toUser,int time){
            super(groupName,toUser);
            this.time = time;
        }
    }

    static public class GroupUnmuteInput extends GroupToUserInput {
        public GroupUnmuteInput(String groupName, String toUser){super(groupName,toUser);}
    }

    static public class GroupCoadminAddInput extends GroupToUserInput {
        public GroupCoadminAddInput(String groupName, String toUser){super(groupName,toUser);}
    }

    static public class GroupCoadminRemoveInput extends GroupToUserInput {
        public GroupCoadminRemoveInput(String groupName, String toUser){super(groupName,toUser);}
    }

    static public Object parseInput(String input) {
        try {
            if(input.startsWith("user "))
                return parseUseInput(input.substring(5));
            if(input.startsWith("group "))
                return parseGroupInput(input.substring(6));
            if(input.equals("yes") || input.equals("no"))
                return input;
            throw new IllegalArgumentException("InputParser.parseInput");
        } catch (IllegalArgumentException e) {
            return new IllegalInput(input,e.getMessage());
        }
    }
    static private Object parseUseInput(String input) throws IllegalArgumentException {
        String[] splited = input.split(" ",3);

        switch (splited.length) {
            case 3:
                if (splited[0].equals("text"))
                    return new UserTextInput(splited[1], splited[2]);
                if (splited[0].equals("file"))
                    return new UserFileInput(splited[1], splited[2]);
                break;
            case 2:
                if(splited[0].equals("connect") || !(splited[1].equals("")))
                    return new ConnectInput(splited[1]);
                break;
            case 1:
                if(splited[0].equals("disconnect"))
                    return new DisconnectInput();
            default:
                break;
        }

        throw new IllegalArgumentException("InputParser.parseUseInput");
    }
    static private GroupInput parseGroupInput(String input) throws IllegalArgumentException {
        if(input.startsWith("create "))
            return new GroupCreateInput(input.substring(7));
        if(input.startsWith("leave "))
            return new GroupLeaveInput(input.substring(6));
        if(input.startsWith("send "))
            return parseGroupSendInput(input.substring(5));
        if(input.startsWith("user "))
            return parseGroupUserInput(input.substring(5));
        if(input.startsWith("coadmin "))
            return parseGroupCoadminInput(input.substring(8));
        throw new IllegalArgumentException("InputParser.parseGroupInput");
    }

    static private GroupInput parseGroupSendInput(String input) throws IllegalArgumentException {
        String[] splited = input.split(" ",3);
        if(splited.length == 3){
            if(splited[0].equals("text"))
                return new GroupTextInput(splited[1],splited[2]);
            if(splited[0].equals("file"))
                return new GroupFileInput(splited[1],splited[2]);
        }
        throw new IllegalArgumentException("InputParser.parseGroupSendInput");
    }

    static private GroupInput parseGroupUserInput(String input) throws IllegalArgumentException {
        String[] splited = input.split(" ");
        switch (splited.length){
            case 3:
                if(splited[0].equals("invite"))
                    return new GroupInviteInput(splited[1],splited[2]);
                if(splited[0].equals("remove"))
                    return new GroupRemoveInput(splited[1],splited[2]);
                if(splited[0].equals("unmute"))
                    return new GroupUnmuteInput(splited[1],splited[2]);
                break;
            case 4:
                if(splited[0].equals("mute")){
                    try {
                        int time = Integer.parseInt(splited[3]);
                        if(time > 0)
                            return new GroupMuteInput(splited[1],splited[2],time);
                        throw new IllegalArgumentException(
                                String.format("InputParser.parseGroupUserInput\ngiven time = %d : time must be a positive number!",time));
                    }catch (NumberFormatException e){
                        throw new IllegalArgumentException("InputParser.parseGroupUserInput\ntime is not an Integer!");
                    }
                }
                break;
            default:
                break;

        }
        throw new IllegalArgumentException("InputParser.parseGroupUserInput");
    }

    static private GroupInput parseGroupCoadminInput(String input) throws IllegalArgumentException {
        String[] splited = input.split(" ");
        if(splited.length == 3){
            if(splited[0].equals("add"))
                return new GroupCoadminAddInput(splited[1],splited[2]);
            if(splited[0].equals("remove"))
                return new GroupCoadminRemoveInput(splited[1],splited[2]);
        }
        throw new IllegalArgumentException("InputParser.parseGroupCoadminInput");
    }
}

