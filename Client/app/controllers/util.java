package controllers;

import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.SourceRef;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamRefs;
import akka.util.ByteString;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.CompletionStage;

public class util {
    ///////////////////////////////FILES UTIL////////////////////////////////////////////////
    /**
     * create sourceRef to file in path.
     * the main purpose is to consume this sourceRef with sink  FileIO.toPath(@some path).
     * @param path: file path
     * @param mat: Materializer
     * @return completionStage to SourceRef of ByteString from file in path
     */
    static public CompletionStage<SourceRef<ByteString>> createFSRef(Path path, Materializer mat){
        Source<ByteString, CompletionStage<IOResult>> fileStream = FileIO.fromPath(path);
        return fileStream.runWith(StreamRefs.sourceRef(), mat);
    }

    /**
     * find unused file name in path @path, the name
     * will be @fname if unused or unused @fname(i) with the min i.
     * @param path: path to directory
     * @param fname: some name
     * @return
     */
    static public Path findUnusedName(String path,String fname){
        String availName = fname;
        String fnameAndExt[] = fname.split("\\.");
        for(int i = 1; fileExsits(path,availName);i++)
            if(fnameAndExt.length == 2)
                availName = String.format("%s(%d)%s",fnameAndExt[0],i,fnameAndExt[1]);
            else
                availName = String.format("%s(%d)",fname,i);

        return Paths.get(path,availName);
    }

    static boolean fileExsits(String path,String fname){
        Path p = Paths.get(path,fname);
        return Files.exists(p) ||  !Files.notExists(p);
    }

    static public boolean fileExsits(String path){
        Path p = Paths.get(path);
        return Files.exists(p) && !Files.isDirectory(p);
    }

    static public String createdir(Path path){
        try {
            if(Files.notExists(path))
                Files.createDirectories(path);
            return path.toString();
        }catch (Exception e){
            return "";
        }
    }

    /**
     * delete file/directory
     * if is directory all file and subdirectories in it delete recursively.
     * @param path file/directory path
     */
    static public void deleteFileIfExsits(String path){
        File f = new File(path);
        String[] subs = f.list();
        if(subs != null)
            for(String sub : subs)
                deleteFileIfExsits(Paths.get(path,sub).toString());
        try {
            Files.deleteIfExists(Paths.get(path));
        }catch (Exception e){}
    }

    ///////////////////////////TIME UTIL/////////////////////////////////////////
    static public String GetCurrTime(){
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss, dd.MM");
        return sdf.format(cal.getTime());
    }
}