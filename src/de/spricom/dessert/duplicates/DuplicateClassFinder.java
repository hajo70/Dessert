package de.spricom.dessert.duplicates;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.spricom.dessert.traversal.ClassVisitor;
import de.spricom.dessert.traversal.PathProcessor;

public class DuplicateClassFinder implements ClassVisitor {
    private static final Logger logger = Logger.getLogger(DuplicateClassFinder.class.getName());

    private String hashAlgorithm = "MD5";
    private boolean ignoreEqualDuplicates;
    private boolean continueOnDuplicate;

    private boolean initialized;
    private MessageDigest digest;

    private Map<String, ClassInfo> scannedClasses = new HashMap<>();
    private Map<String, List<ClassInfo>> duplicates = new HashMap<>();

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        DuplicateClassFinder finder = new DuplicateClassFinder();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "-hashAlgorithm":
                finder.setHashAlgorithm(args[++i]);
                break;
            case "-ignoreEqualDuplicates":
                finder.setIgnoreEqualDuplicates(true);
                break;
            case "-continueOnDuplicate":
                finder.setContinueOnDuplicate(true);
                break;
            }
        }
        finder.process(System.getProperty("java.class.path"));
    }

    public void process(String classPath) throws IOException, NoSuchAlgorithmException {
        ensureInitialized();
        PathProcessor processor = new PathProcessor();
        processor.setPath(classPath);
        processor.traverseAllClasses(this);
    }

    @Override
    public void visit(File root, String classname, InputStream content) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("Processing " + classname + " in " + root.getAbsolutePath());
        }
        try {
            ClassInfo info = new ClassInfo(root, classname, hash(content));
            ClassInfo previous = scannedClasses.put(classname, info);
            if (previous != null) {
                handleDuplicate(info, previous);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Cannot calculate hash for " + classname + " in " + root.getAbsolutePath(), ex);
            throw new ClassIOException(classname, root, ex);
        }
    }

    private void handleDuplicate(ClassInfo info, ClassInfo previous) {
        boolean equalHash = Arrays.equals(info.getHash(), previous.getHash());
        if (equalHash) {
            logger.info("Identical duplicates of " + info.getClassname() + " are in " + info.getRoot().getAbsolutePath()
                    + " and " + previous.getRoot().getAbsolutePath());
        } else {
            logger.warning("Different duplicates of " + info.getClassname() + " are in "
                    + info.getRoot().getAbsolutePath() + " and " + previous.getRoot().getAbsolutePath());
        }
        List<ClassInfo> list = duplicates.get(info.getClassname());
        if (list == null) {
            list = new ArrayList<>();
            duplicates.put(info.getClassname(), list);
            list.add(previous);
        }
        list.add(info);
        if (continueOnDuplicate) {
            return;
        }
        if (equalHash && ignoreEqualDuplicates) {
            return;
        }
        throw new DuplicateClassException(info.getClassname(), info.getRoot(), previous.getRoot());
    }

    private byte[] hash(InputStream inputStream) throws IOException {
        byte[] bytesBuffer = new byte[1024];
        int bytesRead = -1;

        while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
            digest.update(bytesBuffer, 0, bytesRead);
        }

        byte[] hashedBytes = digest.digest();
        return hashedBytes;
    }

    private void ensureInitialized() throws NoSuchAlgorithmException {
        if (initialized) {
            return;
        }
        digest = MessageDigest.getInstance(hashAlgorithm);
        initialized = true;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public boolean isIgnoreEqualDuplicates() {
        return ignoreEqualDuplicates;
    }

    public void setIgnoreEqualDuplicates(boolean ignoreEqualDuplicates) {
        this.ignoreEqualDuplicates = ignoreEqualDuplicates;
    }

    public Map<String, List<ClassInfo>> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(Map<String, List<ClassInfo>> duplicates) {
        this.duplicates = duplicates;
    }

    public Map<String, ClassInfo> getScannedClasses() {
        return scannedClasses;
    }

    public boolean isContinueOnDuplicate() {
        return continueOnDuplicate;
    }

    public void setContinueOnDuplicate(boolean continueOnDuplicate) {
        this.continueOnDuplicate = continueOnDuplicate;
    }
}
