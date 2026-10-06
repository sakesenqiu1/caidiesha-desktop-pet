import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 校验 APK：dex 头合法性 + 我们自己的类/字符串是否真的打进去了 */
public class VerifyApk {
    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        System.out.println("APK: " + apk.getAbsolutePath());
        System.out.println("size: " + apk.length() + " bytes");
        try (ZipFile zf = new ZipFile(apk)) {
            System.out.println("entries: " + zf.size());
            byte[] dex = read(zf, "classes.dex");
            System.out.println("classes.dex: " + dex.length + " bytes");
            ByteBuffer b = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[8];
            b.get(magic);
            System.out.println("dex magic: " + new String(magic, StandardCharsets.US_ASCII).trim());
            b.position(56);
            System.out.println("string_ids: " + b.getInt() + "  type_ids: " + b.getInt()
                    + "  proto_ids: " + b.getInt() + "  field_ids: " + b.getInt()
                    + "  method_ids: " + b.getInt() + "  class_defs: " + b.getInt());

            String all = new String(dex, StandardCharsets.ISO_8859_1);
            String[] want = {
                    "Lcom/dsh/caidiesha/MainActivity;",
                    "Lcom/dsh/caidiesha/FloatingPetService;",
                    "Lcom/dsh/caidiesha/PetView;",
                    "Lcom/dsh/caidiesha/CaidieshaDex;",
                    "Lcom/dsh/caidiesha/Sfx;",
                    "Lcom/dsh/caidiesha/BootReceiver;",
                    "最喜欢你了，啵！",
                    "水翼推进",
                    "Tianhong",
            };
            for (String s : want) {
                byte[] needle = s.getBytes(StandardCharsets.UTF_8);
                System.out.println((indexOf(dex, needle) >= 0 ? "  OK   " : "  MISS ") + s);
            }
            // dex 里的字符串是 MUTF-8；抽一遍 ASCII 串来确认类描述符层级
            StringBuilder sb = new StringBuilder();
            for (byte v : dex) {
                if (v >= 32 && v < 127) sb.append((char) v);
                else sb.append('\n');
            }
            String ascii = sb.toString();
            for (String s : new String[]{"caidiesha/PetView", "caidiesha/Sfx", "LayoutParams", "PROPERTY_SPECIAL_USE"}) {
                System.out.println((ascii.contains(s) ? "  ASCII OK   " : "  ASCII MISS ") + s);
            }
            System.out.println("has resources.arsc: " + (zf.getEntry("resources.arsc") != null));
            System.out.println("has AndroidManifest: " + (zf.getEntry("AndroidManifest.xml") != null));
            ZipEntry pet = zf.getEntry("res/drawable-xxxhdpi-v4/pet_caidiesha.png");
            System.out.println("sprite entry: " + (pet != null ? pet.getSize() + " bytes" : "MISSING"));
        }
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    static byte[] read(ZipFile zf, String name) throws IOException {
        ZipEntry e = zf.getEntry(name);
        if (e == null) throw new IOException("missing " + name);
        try (InputStream in = zf.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
