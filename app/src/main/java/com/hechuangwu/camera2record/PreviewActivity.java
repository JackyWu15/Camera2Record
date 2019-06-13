package com.hechuangwu.camera2record;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.hechuangwu.utils.Camera2Config;

public class PreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_preview );
        Intent intent = getIntent();
        String picPath = intent.getStringExtra( Camera2Config.INTENT_PATH_SAVE_PIC );
        String videoPath = intent.getStringExtra( Camera2Config.INTENT_PATH_SAVE_VIDEO );
        TextView tvPath = findViewById( R.id.tv_path );
        if (!TextUtils.isEmpty( picPath )) {
            Bitmap bitmap = BitmapFactory.decodeFile( picPath );
            ImageView iv_photo = findViewById( R.id.iv_photo );
            iv_photo.setImageBitmap( bitmap );
            tvPath.setText( "当前文件路径："+picPath );
        } else {
            VideoView vv_video = findViewById( R.id.vv_video );
            vv_video.setVisibility( View.VISIBLE );
            vv_video.setVideoPath( videoPath );
            vv_video.start();
            tvPath.setText( "当前文件路径："+videoPath );
        }
    }
}
