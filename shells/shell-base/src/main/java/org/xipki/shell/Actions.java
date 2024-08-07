// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.shell;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.FileCompleter;
import org.xipki.util.Args;
import org.xipki.util.Base64;
import org.xipki.util.Curl.CurlResult;
import org.xipki.util.FileUtils;
import org.xipki.util.IoUtil;
import org.xipki.util.StringUtil;
import org.xipki.util.http.HttpStatusCode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Basic actions.
 *
 * @author Lijun Liao (xipki)
 *
 */
public class Actions {

  @Command(scope = "xi", name = "confirm", description = "confirm an action")
  @Service
  public static class Confirm extends XiAction {

    @Argument(index = 0, name = "message", required = true, description = "prompt message")
    private String prompt;

    @Override
    protected Object execute0() throws Exception {
      boolean toContinue = confirm(prompt + "\nDo you want to continue", 3);
      if (!toContinue) {
        throw new CmdFailure("User cancelled");
      }

      return null;
    }

  } // class Confirm

  @Command(scope = "xi", name = "copy-dir", description = "copy content of the directory to destination")
  @Service
  public static class CopyDir extends XiAction {

    @Argument(index = 0, name = "source", required = true, description = "content of this directory will be copied")
    @Completion(Completers.DirCompleter.class)
    private String source;

    @Argument(index = 1, name = "destination", required = true, description = "destination directory")
    @Completion(Completers.DirCompleter.class)
    private String dest;

    @Override
    protected Object execute0() throws Exception {
      source = expandFilepath(source);
      dest = expandFilepath(dest);

      File sourceDir = new File(source);
      if (!sourceDir.exists()) {
        throw new IllegalCmdParamException(source + " does not exist");
      }

      if (!sourceDir.isDirectory()) {
        throw new IllegalCmdParamException(source + " is not a directory");
      }

      File destDir = new File(dest);
      IoUtil.mkdirs(destDir);

      FileUtils.copyDirectory(sourceDir, destDir);

      return null;
    }

  } // class CopyDir

  @Command(scope = "xi", name = "copy-file", description = "copy file")
  @Service
  public static class CopyFile extends XiAction {

    @Argument(index = 0, name = "files or dir", multiValued = true, required = true,
        description = "The last one is the destination file or dir (ending with '/'),\n" +
            "the remaining are the files to be copied")
    @Completion(FileCompleter.class)
    private List<String> files;

    @Option(name = "--force", aliases = "-f", description = "override existing file, never prompt")
    private Boolean force = Boolean.FALSE;

    @Override
    protected Object execute0() throws Exception {
      int n = files.size();
      if (n < 2) {
        throw new IllegalCmdParamException("too less parameters");
      }

      String dest = files.get(n - 1);
      dest = expandFilepath(dest);

      File destObj = new File(dest);
      boolean destExists = destObj.exists();
      boolean isDestDir;
      if (destExists) {
        isDestDir = destObj.isDirectory();
      } else {
        if (n > 2) {
          isDestDir = true;
        } else {
          char c = dest.charAt(dest.length() - 1);
          isDestDir = c == '\\' || c == '/';
        }
      }

      if (n > 2 && !isDestDir) {
        throw new IllegalCmdParamException(dest + " is not a folder");
      }

      List<File> sourceFiles = new ArrayList<>(n - 1);
      for (int i = 0; i < n - 1; i++) {
        String source = expandFilepath(files.get(i));
        File sourceFile = new File(source);

        if (!sourceFile.exists()) {
          throw new IllegalCmdParamException(source + " does not exist");
        }

        if (!sourceFile.isFile()) {
          throw new IllegalCmdParamException(source + " is not a file");
        }
        sourceFiles.add(sourceFile);
     }

      if (isDestDir) {
        for (File sourceFile : sourceFiles) {
          copyFile(sourceFile, new File(dest, sourceFile.getName()));
        }
      } else {
        copyFile(sourceFiles.get(0), new File(dest));
      }

      return null;
    }

    private void copyFile(File sourceFile, File destFile) throws IllegalCmdParamException, IOException {
      if (destFile.exists()) {
        if (!destFile.isFile()) {
          throw new IllegalCmdParamException("cannot override an existing directory by a file");
        } else {
          if (!force && !confirm("Do you want to override the file " + destFile.getPath(), 3)) {
            return;
          }
        }
      } else {
        IoUtil.mkdirsParent(destFile.toPath());
      }

      FileUtils.copyFile(sourceFile, destFile, true);
    }

  } // class CopyFile

  @Command(scope = "xi", name = "file-exists", description = "test whether file or folder exists")
  @Service
  public static class FileExists extends XiAction {

    @Argument(name = "target", required = true, description = "file or dir to be checked")
    @Completion(FileCompleter.class)
    private String target;

    @Override
    protected Object execute0() throws Exception {
      return new File(target).exists();
    }

  } // class CopyFile

  @Command(scope = "xi", name = "base64", description = "Base64 encode / decode")
  @Service
  public static class Base64EnDecode extends XiAction {

    @Option(name = "--decode", aliases = "-d", description = "Decode")
    private boolean decode = false;

    @Argument(index = 0, name = "source", required = true, description = "source file")
    @Completion(FileCompleter.class)
    private String source;

    @Argument(index = 1, name = "destination", required = true, description = "destination file")
    @Completion(FileCompleter.class)
    private String dest;

    @Override
    protected Object execute0() throws Exception {
      source = expandFilepath(source);
      dest = expandFilepath(dest);

      File sourceFile = new File(source);
      if (!sourceFile.exists()) {
        throw new IllegalCmdParamException(source + " does not exist");
      }

      if (!sourceFile.isFile()) {
        throw new IllegalCmdParamException(source + " is not a file");
      }

      byte[] sourceBytes = IoUtil.read(sourceFile);
      byte[] targetBytes = decode ? Base64.decode(sourceBytes) : Base64.encodeToByte(sourceBytes, true);
      IoUtil.save(dest, targetBytes);
      return null;
    }

  } // class CopyDir

  @Command(scope = "xi", name = "curl", description = "transfer a URL")
  @Service
  public static class Curl extends XiAction {

    @Argument(index = 0, name = "url", required = true, description = "URL")
    private String url;

    @Option(name = "--verbose", aliases = "-v", description = "show request and response verbosely")
    private Boolean verbose = Boolean.FALSE;

    @Option(name = "--post", aliases = "-p", description = "send the request via HTTP POST")
    private Boolean usePost = Boolean.FALSE;

    @Option(name = "--data", aliases = "-d", description = "data to be sent in a POST request")
    private String postData;

    @Option(name = "--data-charset", aliases = "-c", description = "charset of data")
    private String postDataCharSet = "UTF-8";

    @Option(name = "--data-file", description = "file contains the data to be sent in a POST request")
    @Completion(FileCompleter.class)
    private String postDataFile;

    @Option(name = "--out", description = "where to save the response")
    @Completion(FileCompleter.class)
    private String outFile;

    @Option(name = "--header", aliases = "-h", multiValued = true, description = "header in request")
    private List<String> headers;

    @Option(name = "--user", aliases = "-u", description = "User and password of the form user:password")
    private String userPassword;

    @Option(name = "--base64", description = "Base64-encode the content")
    private boolean base64;

    @Reference
    private org.xipki.util.Curl curl;

    @Override
    protected Object execute0() throws Exception {
      byte[] content = null;
      if (postData != null) {
        content = postData.getBytes(postDataCharSet);
      } else if (postDataFile != null) {
        content = IoUtil.read(postDataFile);
      }

      if (content != null) {
        usePost = Boolean.TRUE;
      }

      Map<String, String> headerNameValues = base64 || headers != null ? new HashMap<>() : null;
      if (headers != null) {
        for (String header : headers) {
          int idx = header.indexOf(':');
          if (idx == -1 || idx == header.length() - 1) {
            throw new IllegalCmdParamException("invalid HTTP header: '" + header + "'");
          }

          String key = header.substring(0, idx);
          String value = header.substring(idx + 1).trim();
          headerNameValues.put(key, value);
        }
      }

      if (base64) {
        headerNameValues.put("Content-Transfer-Encoding", "base64");
        if (content != null) {
          content = Base64.encodeToByte(content, true);
        }
      }

      CurlResult result = usePost ? curl.curlPost(url, verbose, headerNameValues, userPassword, content)
          : curl.curlGet(url, verbose, headerNameValues, userPassword);

      if (result.getContent() == null && result.getErrorContent() == null) {
        println("NO response content");
      } else {
        if (outFile != null) {
          if (result.getContent() != null) {
            saveVerbose("saved response to file", outFile, result.getContent());
          } else {
            saveVerbose("saved (error) response to file", "error-" + outFile, result.getErrorContent());
          }
        } else {
          String ct = result.getContentType();
          String charset = getCharset(ct);
          if (charset == null) {
            charset = "UTF-8";
          }

          if (result.getContent() != null) {
            println(new String(result.getContent(), charset));
          } else {
            println("ERROR: ");
            println(new String(result.getContent(), charset));
          }
        }
      }

      int sc = result.getStatusCode();
      if (sc != HttpStatusCode.SC_OK) {
        throw new RuntimeException("Received status code other than OK: " + sc);
      }
      return null;
    } // method execute0

    private static String getCharset(String contentType) {
      if (StringUtil.isBlank(contentType) || contentType.indexOf(';') == -1) {
        return null;
      }

      StringTokenizer st = new StringTokenizer(contentType, ";");
      st.nextToken();

      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int idx = token.indexOf('=');
        if (idx == -1) {
          continue;
        }

        String paramName = token.substring(0, idx).trim();
        if ("charset".equalsIgnoreCase(paramName)) {
          return token.substring(idx + 1);
        }
      }

      return null;
    } // method getCharset

  } // class Curl

  @Command(scope = "xi", name = "mkdir", description = "make directories")
  @Service
  public static class Mkdir extends XiAction {

    @Argument(index = 0, name = "directory", required = true, description = "directory to be created")
    @Completion(Completers.DirCompleter.class)
    private String dirName;

    @Override
    protected Object execute0() throws Exception {
      File target = new File(expandFilepath(dirName));
      try {
        IoUtil.mkdirs(target);
      } catch (IOException ex) {
        System.err.println(ex.getMessage());
      }

      return null;
    }

  } // class Mkdir

  @Command(scope = "xi", name = "move-dir", description = "move content of the directory to destination")
  @Service
  public static class MoveDir extends XiAction {

    @Argument(index = 0, name = "source", required = true, description = "content of this directory will be copied")
    @Completion(Completers.DirCompleter.class)
    private String source;

    @Argument(index = 1, name = "destination", required = true, description = "destination directory")
    @Completion(Completers.DirCompleter.class)
    private String dest;

    @Override
    protected Object execute0() throws Exception {
      source = expandFilepath(source);
      dest = expandFilepath(dest);

      File sourceDir = new File(source);
      if (!sourceDir.exists()) {
        throw new IllegalCmdParamException(source + " does not exist");
      }

      if (!sourceDir.isDirectory()) {
        throw new IllegalCmdParamException(source + " is not a directory");
      }

      File destDir = new File(dest);
      if (destDir.exists()) {
        if (destDir.isFile()) {
          throw new IllegalCmdParamException(dest + " is not a directory");
        }
      } else {
        IoUtil.mkdirs(destDir);
      }

      FileUtils.copyDirectory(sourceDir, destDir);
      FileUtils.deleteDirectory(sourceDir);

      return null;
    }

  } // class MoveDir

  @Command(scope = "xi", name = "move-file", description = "move file")
  @Service
  public static class MoveFile extends XiAction {

    @Argument(index = 0, name = "source-file", required = true, description = "file to be moved")
    @Completion(FileCompleter.class)
    private String source;

    @Argument(index = 1, name = "destination", required = true, description = "destination file")
    @Completion(FileCompleter.class)
    private String dest;

    @Option(name = "--force", aliases = "-f", description = "override existing file, never prompt")
    private Boolean force = Boolean.FALSE;

    @Override
    protected Object execute0() throws Exception {
      source = expandFilepath(source);
      dest = expandFilepath(dest);

      File sourceFile = new File(source);
      if (!sourceFile.exists()) {
        throw new IllegalCmdParamException(source + " does not exist");
      }

      if (!sourceFile.isFile()) {
        throw new IllegalCmdParamException(source + " is not a file");
      }

      File destFile = new File(dest);
      if (destFile.exists()) {
        if (!destFile.isFile()) {
          throw new IllegalCmdParamException("cannot override an existing directory by a file");
        } else {
          if (!force && !confirm("Do you want to override the file " + dest, 3)) {
            return null;
          }
        }
      } else {
        IoUtil.mkdirsParent(destFile.toPath());
      }

      FileUtils.copyFile(sourceFile, destFile, true);
      IoUtil.deleteFile0(sourceFile);

      return null;
    }

  } // class MoveFile

  @Command(scope = "xi", name = "replace", description = "replace text in file")
  @Service
  public static class Replace extends XiAction {

    @Argument(name = "files", multiValued = true, required = true, description = "files to be replaced")
    @Completion(FileCompleter.class)
    private List<String> sources;

    @Option(name = "--old", required = true, multiValued = true, description = "text to be replaced")
    private List<String> oldTexts;

    @Option(name = "--new", required = true, multiValued = true, description = "new text")
    private List<String> newTexts;

    @Override
    protected Object execute0() throws Exception {
      Args.notNull(oldTexts, "oldTexts");
      Args.notNull(newTexts, "newTexts");
      if (oldTexts.size() != newTexts.size()) {
        throw new IllegalCmdParamException("old.size != new.size");
      }

      for (String source : sources) {
        File sourceFile = new File(expandFilepath(source));
        if (!sourceFile.exists()) {
          System.err.println(source + " does not exist");
          continue;
        }

        if (!sourceFile.isFile()) {
          System.err.println(source + " is not a file");
          continue;
        }

        replaceFile(sourceFile, oldTexts, newTexts);
      }

      return null;
    }

    private void replaceFile(File file, List<String> oldTexts, List<String> newTexts) throws Exception {
      boolean changed = false;
      byte[] newBytes = null;
      try (BufferedReader reader = Files.newBufferedReader(file.toPath());
           ByteArrayOutputStream writer = new ByteArrayOutputStream()) {
        String line;
        while ((line = reader.readLine()) != null) {
          String origLine = line;
          for (int i = 0; i < oldTexts.size(); i++) {
            String old = oldTexts.get(i);
            if (line.contains(old)) {
              line = line.replace(old, newTexts.get(i));
            }
          }

          writer.write(StringUtil.toUtf8Bytes(line));
          writer.write('\n');

          if (!line.equals(origLine)) {
            changed = true;
          }
        }

        if (changed) {
          newBytes = writer.toByteArray();
        }
      }

      if (changed) {
        IoUtil.save(new File(file.getPath()), newBytes);
      }
    }

  } // class Replace

  @Command(scope = "xi", name = "rm", description = "remove file or directory")
  @Service
  public static class Rm extends XiAction {

    @Argument(name = "file", required = true, multiValued = true, description = "files and directories to be deleted")
    @Completion(FileCompleter.class)
    private List<String> targetPaths;

    @Option(name = "--recursive", aliases = "-r", description = "remove directories and their contents recursively")
    private Boolean recursive = Boolean.FALSE;

    @Option(name = "--force", aliases = "-f", description = "remove files without prompt")
    private Boolean force = Boolean.FALSE;

    @Override
    protected Object execute0() throws Exception {
      if (targetPaths == null) {
        throw new IllegalCmdParamException("targetPath not set");
      }

      for (String targetPath : targetPaths) {
        targetPath = expandFilepath(targetPath);

        File target = new File(targetPath);
        if (!target.exists()) {
          return null;
        }

        if (target.isDirectory()) {
          if (!recursive) {
            println("Please use option --recursive to delete directory");
            return null;
          }

          if (force || confirm("Do you want to remove directory " + targetPath, 3)) {
            FileUtils.deleteDirectory(target);
            println("removed directory " + targetPath);
          }
        } else {
          if (force || confirm("Do you want to remove file " + targetPath, 3)) {
            IoUtil.deleteFile0(target);
            println("removed file " + targetPath);
          }
        }
      }

      return null;
    }

  } // class Rm

  @Command(scope = "xi", name = "datetime", description = "get current date-time")
  @Service
  public static class DateTime extends XiAction {

    @Argument(name = "format", description = "format")
    @Completion(FileCompleter.class)
    private String format = "yyyyMMdd-HHmmss";

    @Override
    protected Object execute0() throws Exception {
      return new SimpleDateFormat(format).format(Instant.now());
    }
  }

  @Command(scope = "xi", name = "osinfo", description = "get info of operation system")
  @Service
  public static class OsInfo extends XiAction {

    @Option(name = "--name", aliases = "-n", description = "output OS name")
    private Boolean printName;

    @Option(name = "--arch", aliases = "-a", description = "output OS arch")
    private Boolean printArch;

    @Override
    protected Object execute0() throws Exception {
      String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
      name = name.startsWith("windows") ? "windows"
          : name.startsWith("linux") ? "linux"
          : name.startsWith("mac os x") ? "macosx" : name;

      String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
      if (printName == null && printArch == null) {
        return name + "/" + arch;
      }

      boolean bName = printName != null && printName;
      boolean bArch = printArch != null && printArch;
      return (bName && bArch) ? name + "/" + arch
          : bName ? name
          : bArch ? arch : "";
    }
  }

  @Command(scope = "xi", name = "exec", description = "execute terminal")
  @Service
  public static class ExecTerminalCommand extends XiAction {

    @Argument(name = "terminal command", required = true, description = "Terminal command")
    @Completion(FileCompleter.class)
    private String command;

    @Option(name = "--ignore-error", description = "whether ignores error")
    private Boolean ignoreError;

    @Option(name = "--env", multiValued = true, description = "Environment variables")
    @Completion(FileCompleter.class)
    private String[] envs;

    @Option(name = "--working-dir", aliases ="-w", multiValued = true, description = "Working dir")
    @Completion(Completers.DirCompleter.class)
    private String workingDir;

    @Override
    protected Object execute0() throws Exception {
      System.out.println("Executing command '" + command + "'");

      if (envs != null) {
        for (int i = 0; i < envs.length; i++) {
          if (envs[i].contains("~/")) {
            StringTokenizer tokenizer = new StringTokenizer(envs[i], "=");
            String name = tokenizer.nextToken();
            String value = tokenizer.nextToken();
            value = IoUtil.expandFilepath(value);
            envs[i] = name + "=" + value;
          }
        }
      }

      if (workingDir != null && workingDir.startsWith("~/")) {
        workingDir = IoUtil.expandFilepath(workingDir);
      }

      command = IoUtil.expandFilepath(command, false);
      Process process = Runtime.getRuntime().exec(command, envs,
          workingDir == null ? null : new File(workingDir));
      int status = process.waitFor();
      System.out.write(IoUtil.readAllBytes(process.getInputStream()));
      if (status != 0) {
        System.err.write(IoUtil.readAllBytes(process.getErrorStream()));
        if (ignoreError == null || !ignoreError){
          throw new Exception("process exited with status " + status);
        }
      }
      return null;
    }
  }

}
