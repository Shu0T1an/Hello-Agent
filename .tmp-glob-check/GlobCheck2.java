import java.nio.file.*;
public class GlobCheck2 {
  public static void main(String[] args) {
    Path p = Path.of("src\\main\\App.java");
    String[] patterns = {
      "glob:**/*.java",
      "glob:**\\*.java",
      "glob:**\\\\*.java",
      "glob:src\\main\\*.java",
      "glob:src/main/*.java"
    };
    for (String g : patterns) {
      try {
        PathMatcher m = FileSystems.getDefault().getPathMatcher(g);
        System.out.println(g + " => " + m.matches(p));
      } catch (Exception ex) {
        System.out.println(g + " => EX: " + ex.getClass().getSimpleName() + ":" + ex.getMessage());
      }
    }
  }
}
