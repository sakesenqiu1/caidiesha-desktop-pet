package com.dsh.caidiesha;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 彩蝶鲨资料卡内容。
 *
 * 数据整理自公开图鉴站 rocokingdomworld.org / BiliWiki，属于官方设定与游戏数值，
 * 没有任何我自己编写的台词或设定。
 */
public final class CaidieshaDex {

    public static final String NAME = "彩蝶鲨";
    public static final String NO = "NO.110";

    /** 资料正文（显示在主界面） */
    public static final String INFO =
            "彩蝶鲨　Caidiesha　" + NO + "\n" +
            "水属性 · 轻飘飘精灵 · 最终形态（首领化后为神谕鲨）\n" +
            "\n" +
            "【进化链】\n" +
            "风铃鲨 → 蓝蝶鲨（Lv.32）→ 彩蝶鲨（由蓝蝶鲨 Lv.50 进化）→ 神谕鲨（首领化）\n" +
            "\n" +
            "【基础种族值】总和 621\n" +
            "HP 90　物攻 98　魔攻 98　物防 105　魔防 105　速度 125\n" +
            "\n" +
            "【特性】水翼推进\n" +
            "己方精灵每使用 1 次水系技能，自己入场时获得全技能能耗 -1。\n" +
            "\n" +
            "【体型 / 生态】\n" +
            "身高 1.18–1.68 m　体重 80.5–96.5 kg\n" +
            "蛋组：海洋组、天空组　性别比：50% / 50%\n" +
            "获取：蓝蝶鲨 50 级进化；星光值 60；回顾 4500\n" +
            "\n" +
            "【属性克制】\n" +
            "攻击 2 倍：恶、幽灵　　攻击 0.5 倍：草、冰\n" +
            "受击 2 倍：幽灵、草　　受击 0.5 倍：恶、超能\n" +
            "\n" +
            "【升级技能】\n" +
            "Lv1 魔法增效（变化）：自己获得魔攻 +70%\n" +
            "Lv1 甩水（水·魔法 30）：造成魔伤，自己回复 1 能量\n" +
            "Lv5 水花四溅（水·魔法 20）：造成魔伤，4 连击\n" +
            "Lv7 风隐（翼·变化）：敌方和自己均脱离，先手 -1\n" +
            "Lv13 回旋风暴（翼·魔法 100）\n" +
            "Lv21 打湿（水·变化）：自己获得 1 层湿润印记\n" +
            "Lv24 能量炮（普通·魔法 50）：2 连击\n" +
            "Lv28 借用（普通·变化）：每回合随机变成队友的技能\n" +
            "Lv35 翼击（翼·魔法 50）：迅捷\n" +
            "Lv36 落雨（水·变化）：天气改为雨天，持续 8 回合\n" +
            "Lv41 水炮（水·魔法 110）：每次使用后本技能能耗永久 -1\n" +
            "Lv47 盐水浴（水·变化）：全技能能耗 -2，应对防御改为 -3\n" +
            "Lv49 天洪（水·魔法 150）：应对状态时本技能能耗永久 -6\n" +
            "\n" +
            "【技能石】热身运动 / 突袭 / 寒风吹 / 极寒领域 / 冰捆缚 / 虫蛊 / 贮藏 /\n" +
            "消毒法 / 追打 / 风起 / 快速移动 / 防御 / 涌泉 / 血气\n" +
            "\n" +
            "【图鉴描述】\n" +
            "常被目击从水面贴风飞过，轻盈得几乎不留波纹。\n" +
            "它飞行时会借气流而动，并且发出清亮的声音。危险逼近时，\n" +
            "它会迅速褪淡体色，降低存在感，再沿风向逃跑。\n" +
            "\n" +
            "—— 资料整理自 rocokingdomworld.org / BiliWiki，仅供个人收藏对照。";

    private static SharedPreferences prefsHolder;

    /** 由 Activity / Service 在启动时调用一次 */
    public static void init(Context c) {
        if (prefsHolder == null) {
            prefsHolder = c.getApplicationContext()
                    .getSharedPreferences("caidiesha", Context.MODE_PRIVATE);
        }
    }

    public static int affection() {
        return prefsHolder == null ? 0 : prefsHolder.getInt("affection", 0);
    }

    public static int addAffection(int delta) {
        if (prefsHolder == null) return 0;
        int v = Math.max(0, prefsHolder.getInt("affection", 0) + delta);
        prefsHolder.edit().putInt("affection", v).apply();
        return v;
    }
}
