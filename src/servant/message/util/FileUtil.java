package servant.message.util;

import app.AppConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FileUtil {

    public static boolean checkIfFileExists(String path) {
        boolean result = false;
        Map<Integer, List<String>> fileListMap = AppConfig.systemState.getFileListMap();
        for (Integer key : fileListMap.keySet()) {
            List<String> fileList = fileListMap.get(key);
            if(fileList != null) {
                for(String s: fileList){
                    if(s.equals(path))
                        result = true;
                }
            }
        }
        return result;
    }

    public static void addFile(String absPath, String fileName) {
        Integer myId = AppConfig.myServantInfo.getId();

        Map<Integer, List<String>> fileListMap = AppConfig.systemState.getFileListMap();

        List<String> fileList = fileListMap.get(myId);

        if(fileList == null) fileList = new ArrayList<String>();

        File file = new File(absPath);

        if (!file.exists()) {
            System.out.println("The file does not exist.");
            return;
        }

        Path targetDir = Path.of(AppConfig.myServantInfo.getRootDir());
        Path sourcePath = Path.of(absPath);

        if(!file.isDirectory()) {
            try {
                fileList.add(fileName);


                Path targetPath = Files.copy(sourcePath, targetDir.resolve(sourcePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                Path targetDirPath = Path.of(AppConfig.myServantInfo.getRootDir() + "\\" + fileName);
                Files.createDirectories(targetDirPath);
                List<String> finalFileList = fileList;
//                finalFileList.add(fileName);
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path targetSubDir = targetDirPath.resolve(sourcePath.relativize(dir));
                        if (!finalFileList.contains(fileName + "\\" + sourcePath.relativize(dir))) {
                            if (!(sourcePath.relativize(dir)).equals(""))
                                finalFileList.add(fileName + "\\" + sourcePath.relativize(dir));
                            else
                                finalFileList.add(fileName);
                        }
                        Files.createDirectories(targetSubDir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path targetFile = targetDirPath.resolve(sourcePath.relativize(file));
                        System.out.println(sourcePath.relativize(file));
                        finalFileList.add(fileName + "\\" + sourcePath.relativize(file));
                        Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                        return FileVisitResult.CONTINUE;
                    }
                });
//                fileList.addAll(finalFileList);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            fileListMap.put(myId, fileList);
        }
        System.out.println("LIST FAJLOVA");
        System.out.println(fileListMap);
        AppConfig.systemState.getFileListMap().put(myId, fileList);
    }

    public static void deleteFile(String args, Integer id) {
        Path fileOrFolderPath = Path.of(AppConfig.myServantInfo.getRootDir().substring(0, AppConfig.myServantInfo.getRootDir().length() -1) + id + "\\" + args);
        Map<Integer, List<String>> fileListMap = AppConfig.systemState.getFileListMap();
        List<String> fileList = fileListMap.get(id);

        if (Files.exists(fileOrFolderPath)) {
            if (Files.isDirectory(fileOrFolderPath)) {
                // Delete the directory and its contents
                try {
                    deleteDirectory(fileOrFolderPath);
                    Iterator<String> iterator = fileList.iterator();
                    while (iterator.hasNext()) {
                        String s = iterator.next();
                        if (s.startsWith(args)) {
                            iterator.remove();
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // Delete the file
                try {
                    Files.delete(fileOrFolderPath);
                    fileList.remove(args);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            System.out.println("The file or folder does not exist.");
        }
        fileListMap.put(id, fileList);
        System.out.println("LIST FAJLOVA");
        System.out.println(fileListMap);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
