package com.hechuangwu.utils;

import android.util.SparseIntArray;
import android.view.Surface;

import com.hechuangwu.camera2record.R;


/**
 * Created by cxk on 2017/12/8.
 * 自定义的配置文件
 */

public class Camera2Config {
    //拍照方向
    public static final SparseIntArray ORIENTATION = new SparseIntArray();
    static {
        ORIENTATION.append( Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    public static int RECORD_MAX_TIME = 10;//录制的总时长秒数，单位秒，默认10秒
    public static int RECORD_MIN_TIME = 2;//最小录制时长，单位秒，默认2秒
    public static int RECORD_PROGRESS_VIEW_COLOR = R.color.colorPrimary;//进度条颜色，默认蓝色

    public static int PREVIEW_MAX_HEIGHT = 1000;//最大高度预览尺寸，默认大于1000的第一个


    public static String PATH_SAVE_VIDEO =Camera2Utils.getCamera2DefaultPath( null );//小视频存放地址，不设置的话默认在根目录的Camera2文件夹
    public static String PATH_SAVE_PIC =Camera2Utils.getCamera2DefaultPath( null );;//图片保存地址，不设置的话默认在根目录的Camera2文件夹
    public static String INTENT_PATH_SAVE_VIDEO = "INTENT_PATH_SAVE_VIDEO"; //Intent跳转可用
    public static String INTENT_PATH_SAVE_PIC = "INTENT_PATH_SAVE_PIC";//Intent跳转可用

    public static boolean ENABLE_RECORD=true;//是否需要录像功能
    public static boolean ENABLE_CAPTURE=true;//是否需要拍照功能


}
