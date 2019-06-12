package com.hechuangwu.utils;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Size;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by cwh on 2019/6/1 0001.
 * 功能:
 */
public class Camera2Utils {

    /**
     * @param filePath 存储路径，默认camera2或传null
     * @return
     */
    public static String getCamera2DefaultPath(String filePath) {
        String picturePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Camera2/";
        if(!TextUtils.isEmpty( filePath )) {
            picturePath = filePath;
        }
        File file = new File( picturePath );
        if (!file.exists()) {
            file.mkdirs();
        }
        return picturePath;
    }

    /**
     * 通过从sizeMap中获取和Textureview宽高比例相同的map，然后在获取接近自己想获取到的尺寸
     * @param sizeMap
     * @param surfaceWidth
     * @param surfaceHeight
     * @param maxHeight
     * @return
     */
    public static Size getMinPreSize(Size[] sizeMap, int surfaceWidth, int surfaceHeight, int maxHeight) {
        // 得到与传入的宽高比最接近的size
        float reqRatio = ((float) surfaceWidth) / surfaceHeight;
        float curRatio;
        List<Size> sizeList = new ArrayList<>();
        Size retSize = null;
        for (Size size : sizeMap) {
            curRatio = ((float) size.getHeight()) / size.getWidth();
            if (reqRatio == curRatio) {
                sizeList.add(size);
            }
        }

        if (sizeList != null && sizeList.size() != 0) {
            for (int i = sizeList.size() - 1; i >= 0; i--) {
                //取Size宽度大于1000的第一个数,这里我们获取大于maxHeight的第一个数，理论上我们是想获取size.getWidth宽度为1080或者1280那些，因为这样的预览尺寸已经足够了
                if (sizeList.get(i).getWidth() >= maxHeight) {
                    retSize = sizeList.get(i);
                    break;
                }
            }

            //可能没有宽度大于maxHeight的size,则取相同比例中最小的那个size
            if (retSize == null) {
                retSize = sizeList.get(sizeList.size() - 1);
            }

        } else {
            retSize = getCloselyPreSize(sizeMap, surfaceWidth, surfaceHeight);
        }
        return retSize;
    }


    /**
     *通过对比得到与宽高比最接近的尺寸,如果有相同尺寸，优先选择
      */
    protected static Size getCloselyPreSize(Size[] sizeMap, int surfaceWidth, int surfaceHeight) {
        int ReqTmpWidth;
        int ReqTmpHeight;
        ReqTmpWidth = surfaceHeight;
        ReqTmpHeight = surfaceWidth;
        //先查找preview中是否存在与surfaceview相同宽高的尺寸
        for (Size size : sizeMap) {
            if ((size.getWidth() == ReqTmpWidth) && (size.getHeight() == ReqTmpHeight)) {
                return size;
            }
        }

        // 得到与传入的宽高比最接近的size
        float reqRatio = ((float) ReqTmpWidth) / ReqTmpHeight;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Size retSize = null;
        for (Size size : sizeMap) {
            curRatio = ((float) size.getWidth()) / size.getHeight();
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }
        return retSize;
    }
}
