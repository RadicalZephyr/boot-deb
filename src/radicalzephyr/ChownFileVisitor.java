package radicalzephyr;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;

public class ChownFileVisitor extends SimpleFileVisitor<Path> {
    private UserPrincipal user;
    private GroupPrincipal group;

    public ChownFileVisitor(UserPrincipal user, GroupPrincipal group) {
        this.user = user;
        this.group = group;
    }

    private void setUserAndGroup(Path path) {
        try {
            Files.setOwner(path, this.user);
            Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).setGroup(this.group);
        } catch (IOException e) {

        }
    }

    @Override
    public FileVisitResult	postVisitDirectory(Path dir, IOException exc) {
        this.setUserAndGroup(dir);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult	visitFile(Path file, BasicFileAttributes attrs) {
        this.setUserAndGroup(file);
        return FileVisitResult.CONTINUE;
    }
}
