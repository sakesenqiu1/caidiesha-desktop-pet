package com.dsh.caidiesha;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * 用合成音频代替音频文件：彩蝶鲨「清亮的声音」。
 * 每句都是正弦波 + 指数衰减包络，不需要任何素材文件。
 */
public final class Sfx {

    public static final int CHIRP = 0;   // 点击：清亮的两声啵
    public static final int PAT = 1;     // 摸摸：柔和单音
    public static final int FEED = 2;    // 喂食：上行三音
    public static final int SPIN = 3;    // 双击：下滑旋转音

    private static final int RATE = 44100;

    private Sfx() {
    }

    public static void play(final int kind) {
        new Thread(() -> {
            try {
                short[] pcm = render(kind);
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(pcm.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
                // 等播完再释放，避免被掐断
                long ms = pcm.length * 1000L / RATE + 60;
                Thread.sleep(ms);
                track.stop();
                track.release();
            } catch (Throwable ignored) {
            }
        }, "sfx").start();
    }

    private static short[] render(int kind) {
        switch (kind) {
            case PAT:
                return tone(new double[][]{{880, 0.0, 0.16, 0.30}});
            case FEED:
                return tone(new double[][]{
                        {784, 0.00, 0.10, 0.26},
                        {988, 0.09, 0.10, 0.26},
                        {1319, 0.18, 0.16, 0.30}});
            case SPIN:
                return tone(new double[][]{{1600, 0.00, 0.30, 0.26}}, true);
            default:
                return tone(new double[][]{
                        {1175, 0.00, 0.09, 0.30},
                        {1568, 0.07, 0.12, 0.26}});
        }
    }

    /** notes: {频率, 起始秒, 时长秒, 音量} */
    private static short[] tone(double[][] notes) {
        return tone(notes, false);
    }

    /** notes: {频率, 起始秒, 时长秒, 音量} */
    private static short[] tone(double[][] notes, boolean sweepDown) {
        double total = 0;
        for (double[] n : notes) total = Math.max(total, n[1] + n[2] + 0.04);
        int len = (int) (total * RATE);
        double[] buf = new double[len];
        for (double[] n : notes) {
            int start = (int) (n[1] * RATE);
            int dur = (int) (n[2] * RATE);
            for (int i = 0; i < dur && start + i < len; i++) {
                double t = i / (double) RATE;
                double env = Math.exp(-6.0 * i / dur) * Math.min(1.0, i / (0.004 * RATE));
                double f = sweepDown ? n[0] * (1.0 - 0.55 * i / dur) : n[0];
                buf[start + i] += n[3] * env * Math.sin(2 * Math.PI * f * t);
            }
        }
        short[] out = new short[len];
        for (int i = 0; i < len; i++) {
            double v = Math.max(-1, Math.min(1, buf[i]));
            out[i] = (short) (v * 26000);
        }
        return out;
    }
}
