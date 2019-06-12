package com.hechuangwu.camera2record;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.hechuangwu.utils.Camera2Config;

public class PreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_preview );
        Intent intent = getIntent();
        String picPath = intent.getStringExtra( Camera2Config.INTENT_PATH_SAVE_PIC );
        ImageView iv_photo = findViewById( R.id.iv_photo );
        Glide.with( this ).load( "file://"+picPath ).crossFade().into( iv_photo );
    }
}
