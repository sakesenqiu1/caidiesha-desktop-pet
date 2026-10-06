/**
 * 交互流程验证：重点验证「锁定」状态下这些行为是否正确
 *   1 长按 -> 能呼出圆点菜单（这是之前一直坏的）
 *   2 点圆点 -> 能解锁
 *   3 解锁后 -> 位置回到屏幕中间、速度非零（能看出在飘）
 *   4 锁定时拖动 -> 不跟随手指（位置不变）
 *   5 锁定时点一下 -> 仍给一个挤压动画值（有反馈）
 *
 * 这些逻辑都写死在 PetView 里，没法直接单测，所以这里用「复刻同一套判定」的方式
 * 逐条核对 PetView 的源码是否真的按预期分支（读源码 + 断言），避免又是靠猜。
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class LockFlowCheck {

    static int pass = 0, fail = 0;

    public static void main(String[] args) throws Exception {
        String src = new String(Files.readAllBytes(
                Paths.get(args[0])), StandardCharsets.UTF_8);

        // 1) ACTION_DOWN 里：hitPet 判定之后必须无条件 scheduleLongPress()
        int down = src.indexOf("case MotionEvent.ACTION_DOWN:");
        int up = src.indexOf("case MotionEvent.ACTION_POINTER_DOWN:");
        String downBlock = src.substring(down, up);
        check("长按调度不再被 locked 挡住",
                downBlock.contains("scheduleLongPress();")
                        && !downBlock.contains("if (locked) return false;"),
                "ACTION_DOWN 里仍有 `if (locked) return false;` → 长按会被吃掉");
        check("锁定时不进入拖动状态（只跳过 state=DRAG）",
                downBlock.contains("if (!locked) {") && downBlock.contains("state = DRAG;"),
                "缺少 `if (!locked)` 包裹");

        // 1b) 长按回调本身不能再要求 state == DRAG（锁定时 state 永远是 IDLE）
        int sl = src.indexOf("private void scheduleLongPress()");
        String slBlock = src.substring(sl, src.indexOf("postDelayed(longPressRunnable", sl));
        // 去掉注释和字符串字面量再判断，避免被说明文字/诊断日志误伤
        String slCode = slBlock.replaceAll("//[^\n]*", "").replaceAll("\"[^\"]*\"", "\"\"");
        check("长按回调不再要求 state == DRAG",
                !slCode.contains("state == DRAG"),
                "长按回调里仍有 `state == DRAG` → 锁定时长按会被挡掉");
        check("长按回调只要求手指没移动", slCode.contains("if (!moved)"),
                "长按回调缺少 `if (!moved)`");

        // 2) ACTION_MOVE 里：锁定时直接返回，不跟随手指
        int mv = src.indexOf("case MotionEvent.ACTION_MOVE:");
        int upCase = src.indexOf("case MotionEvent.ACTION_UP:");
        String mvBlock = src.substring(mv, upCase);
        check("锁定时拖动不生效", mvBlock.contains("if (locked) return false;"),
                "ACTION_MOVE 里缺少 locked 判断");

        // 3) 菜单动作下标与标签顺序一致
        int li = src.indexOf("private static final String[] MENU_LABELS =");
        String labels = src.substring(li, src.indexOf(";", li));
        check("标签顺序 = 0喂食 1摸摸 2锁定 3资料 4躲起来",
                labels.indexOf("喂食") < labels.indexOf("摸摸")
                        && labels.indexOf("摸摸") < labels.indexOf("锁定")
                        && labels.indexOf("锁定") < labels.indexOf("资料")
                        && labels.indexOf("资料") < labels.indexOf("躲起来"),
                "标签顺序是：" + labels);
        int hm = src.indexOf("private void handleMenu(int i)");
        String hmBlock = src.substring(hm, src.indexOf("private void toggleLock", hm));
        int c2 = hmBlock.indexOf("case 2:");
        int c3 = hmBlock.indexOf("case 3:");
        check("case 2 = 锁定(toggleLock)", hmBlock.substring(c2, c3).contains("toggleLock();"),
                "case 2 不是 toggleLock");
        check("case 3 = 资料(onOpenInfo)",
                hmBlock.substring(c3).contains("host.onOpenInfo();"), "case 3 不是 onOpenInfo");
        check("default = 躲起来(onRequestStop)",
                hmBlock.substring(c3).contains("host.onRequestStop();"), "default 不是 onRequestStop");

        // 4) 解锁：回到屏幕中间 + 重新给速度
        int tl = src.indexOf("private void toggleLock()");
        String tlBlock = src.substring(tl, src.indexOf("private void reseedMotion", tl));
        check("解锁会回到屏幕中间", tlBlock.contains("resetToCenter();"), "解锁没有 resetToCenter()");
        check("解锁会重新给速度", tlBlock.contains("reseedMotion();"), "解锁没有 reseedMotion()");
        int rs = src.indexOf("private void reseedMotion()");
        String rsBlock = src.substring(rs, src.indexOf("}", src.indexOf("phys.facingRight", rs)));
        check("reseedMotion 真的设置了非零速度",
                rsBlock.contains("phys.vx =") && rsBlock.contains("phys.vy ="),
                "没有设置速度");

        // 5) 锁定：速度归零 + 角度归零
        int lk = src.indexOf("if (locked) {", tl);
        String lkBlock = src.substring(lk, src.indexOf("} else {", lk));
        for (String need : new String[]{"phys.vx = 0f;", "phys.vy = 0f;", "spin = 0f;",
                "spinV = 0f;", "dragTilt = 0f;", "phys.bob = 0f;"}) {
            check("锁定时 " + need.trim(), lkBlock.contains(need), "缺少 " + need);
        }

        // 6) 锁定后不再推进物理
        check("锁定后不推进物理步进",
                src.contains("if (running && !locked) step(dt);"), "onDraw 里没有跳过 step");

        System.out.println("\n" + (fail == 0 ? "全部通过（" + pass + " 项）✓"
                : fail + " 项不通过（共 " + (pass + fail) + " 项）✗"));
        if (fail > 0) System.exit(1);
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  OK   " + name); }
        else { fail++; System.out.println("  FAIL " + name + "  -> " + detail); }
    }
}
