package com.example.videorecoder;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by LvWeiHao
 * Date: 2019/5/5 0005 16:55
 * <p>
 * Describe:
 */
public class ToastUtils {
    public static void shortShow(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
