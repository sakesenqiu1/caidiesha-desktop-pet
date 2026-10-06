import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 递归自调用检测器：找出「无参方法里又无参调用了自己」这种无限递归。
 * 这正是导致 StackOverflowError（彩蝶鲨从不显示）的那类 bug。
 *
 * 只报「零参数方法 + 零参数自调用」——这类几乎必然是错的（重载/委托都会带参数）。
 */
public class RecursionCheck {

    static int problems = 0;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        List<File> files = new ArrayList<>();
        collect(dir, files);
        System.out.println("扫描 " + files.size() + " 个 java 文件\n");

        for (File f : files) {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            // 找出所有零参数方法定义及其行号
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = Pattern.compile(
                        "^\\s*(?:public|private|protected|static|final|\\s)*[\\w<>\\[\\]]+\\s+(\\w+)\\s*\\(\\s*\\)\\s*\\{")
                        .matcher(lines.get(i));
                if (!m.find()) continue;
                String name = m.group(1);
                if (name.equals("run") || name.equals("onCreate") || name.equals("onDraw")
                        || name.equals("onReceive") || name.equals("toString")) continue;

                // 顺着大括号找到方法体结束
                int depth = 0, end = i;
                boolean started = false;
                for (int j = i; j < lines.size(); j++) {
                    for (char c : lines.get(j).toCharArray()) {
                        if (c == '{') { depth++; started = true; }
                        else if (c == '}') depth--;
                    }
                    if (started && depth <= 0) { end = j; break; }
                }
                // 在方法体里找「无参调用自己」
                for (int j = i + 1; j <= end && j < lines.size(); j++) {
                    String line = lines.get(j);
                    String stripped = line.replaceAll("//.*$", "");
                    Matcher call = Pattern.compile("(?<![\\w.$])" + Pattern.quote(name) + "\\s*\\(\\s*\\)")
                            .matcher(stripped);
                    while (call.find()) {
                        // 排除 return name(); 以外的常见合法形式：this.name() / PetView.this.name()
                        int start = Math.max(0, call.start() - 12);
                        String before = stripped.substring(start, call.start());
                        if (before.matches(".*(this\\.|\\.)\\s*$")) continue;
                        problems++;
                        System.out.println("!! 疑似无限递归");
                        System.out.println("   文件: " + f.getName() + "  第 " + (j + 1) + " 行");
                        System.out.println("   方法: " + name + "()   定义在第 " + (i + 1) + " 行");
                        System.out.println("   代码: " + line.trim());
                        System.out.println("   修法: 改成 外部类名.this." + name + "()  或其他正确的目标");
                    }
                }
            }
        }

        System.out.println(problems == 0
                ? "\n没有发现「无参方法自己调自己」的无限递归 ✓"
                : "\n发现 " + problems + " 处，必须修掉");
        if (problems > 0) System.exit(1);
    }

    static void collect(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }
}
