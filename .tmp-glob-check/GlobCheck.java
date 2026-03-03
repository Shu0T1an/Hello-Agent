import java.nio.file.*;
public class GlobCheck {
  public static void main(String[] args) {
    PathMatcher m1 = FileSystems.getDefault().getPathMatcher("glob:**/*.java");
    Path p1 = Path.of("src/main/App.java");
    Path p2 = Path.of("src\\main\\App.java");
    System.out.println("sep=" + FileSystems.getDefault().getSeparator());
    System.out.println("m1-p1=" + m1.matches(p1));
    System.out.println("m1-p2=" + m1.matches(p2));
    PathMatcher m2 = FileSystems.getDefault().getPathMatcher("glob:**\\*.java");
    System.out.println("m2-p1=" + m2.matches(p1));
    System.out.println("m2-p2=" + m2.matches(p2));
  }
}
