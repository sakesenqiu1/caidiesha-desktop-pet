import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 把 APK 的 classes.dex 里所有含中文的字符串抽出来，逐条人工核对
 * —— 用来确认界面上没有夹带自编台词。
 */
public class DumpStrings {
    public static void main(String[] args) throws Exception {
        try (ZipFile zf = new ZipFile(new File(args[0]))) {
            ZipEntry e = zf.getEntry("classes.dex");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (InputStream in = zf.getInputStream(e)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            }
            byte[] dex = bos.toByteArray();

            // MUTF-8 解码：扫描可打印的 UTF-8 序列
            TreeSet<String> found = new TreeSet<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dex.length; ) {
                int b = dex[i] & 0xff;
                int len = 0;
                if (b < 0x80) len = 1;
                else if ((b & 0xE0) == 0xC0) len = 2;
                else if ((b & 0xF0) == 0xE0) len = 3;
                else if ((b & 0xF8) == 0xF0) len = 4;
                else { i++; continue; }
                if (i + len > dex.length) break;
                String s = new String(dex, i, len, StandardCharsets.UTF_8);
                char c = s.charAt(0);
                boolean good = Character.isLetterOrDigit(c) || c == ' '
                        || (c >= 0x4E00 && c <= 0x9FFF) || c == '·' || c == '／'
                        || "，。：、（）【】—…～！？.".indexOf(c) >= 0;
                if (good) sb.append(c);
                else {
                    if (sb.length() >= 2 && hasCjk(sb.toString())) found.add(sb.toString());
                    sb.setLength(0);
                }
                i += len;
            }
            if (sb.length() >= 2 && hasCjk(sb.toString())) found.add(sb.toString());

            System.out.println("APK 内含中文的字符串（共 " + found.size() + " 条）:");
            for (String s : found) {
                System.out.println("  [" + (s.length() > 90 ? s.length() + "字长文" : s) + "]"
                        + (s.length() > 90 ? "  << " + s.substring(0, 60).replace('\n', ' ') + " …" : ""));
            }
        }
    }

    static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }
}
