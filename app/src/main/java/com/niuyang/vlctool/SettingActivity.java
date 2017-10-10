package com.niuyang.vlctool;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class SettingActivity extends AppCompatActivity {

    private Button bnsave_setting;
    private Button bnrestore_setting;
    private EditText etexposure_duration;
    private EditText ettag;
    private EditText etserver_url;
    private Spinner spcapture_mode;
    private Switch swstore_to_gallery;
    private TextView txt;

    //设置值
    private String strexposure_duration;
    private String strtag;
    private String strserver_url;
    private String strcapture_mode;
    private String strstore_to_gallery;
    private long shorttime;
    private long longtime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        bnsave_setting=(Button)findViewById(R.id.save_setting);
        bnrestore_setting=(Button)findViewById(R.id.restore_setting);
        etexposure_duration=(EditText)findViewById(R.id.exposure_duration);
        ettag=(EditText)findViewById(R.id.tag);
        etserver_url=(EditText)findViewById(R.id.server_url);
        spcapture_mode=(Spinner)findViewById(R.id.mode_select);
        swstore_to_gallery=(Switch)findViewById(R.id.store_to_gallery);
        txt=(TextView)findViewById(R.id.txtex);


        //获取数据
        Intent intent = getIntent();
        strexposure_duration= intent.getStringExtra("exposure_duration").toString();
        strtag= intent.getStringExtra("tag").toString();
        strserver_url= intent.getStringExtra("server_url").toString();
        strcapture_mode= intent.getStringExtra("capture_mode").toString();
        strstore_to_gallery= intent.getStringExtra("store_to_gallery").toString();
        shorttime = intent.getLongExtra("shorttime",0);
        longtime = intent.getLongExtra("longtime",0);

        //初始化
        txt.append("("+shorttime/1000+"~"+longtime/1000+")");
        etexposure_duration.setText(strexposure_duration);
        ettag.setText(strtag);
        etserver_url.setText(strserver_url);
        if(strcapture_mode.equals("single")){
            spcapture_mode.setSelection(0);
        }else{
            spcapture_mode.setSelection(1);
        }
        if(strstore_to_gallery.equals("1")){
            swstore_to_gallery.setChecked(true);
        }else{
            swstore_to_gallery.setChecked(false);
        }

        //store_to_gallery开关监听
        swstore_to_gallery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    strstore_to_gallery="1";
                }else{
                    strstore_to_gallery="0";
                }
            }
        });

        //save按钮监听
        bnsave_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                putIntent();
//                Intent mIntent=new Intent();
//
//                mIntent.putExtra("exposure_duration",etexposure_duration.getText());
//                mIntent.putExtra("tag",ettag.getText());
//                mIntent.putExtra("server_url",etserver_url.getText());
//                mIntent.putExtra("capture_mode",spcapture_mode.getSelectedItem().toString());
//                mIntent.putExtra("store_to_gallery",swstore_to_gallery.getText());
//
//                setResult(RESULT_OK,mIntent);
//                finish();
            }
        });

        bnrestore_setting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent();
                setResult(RESULT_CANCELED,intent);
                finish();
            }
        });
    }

    private void putIntent(){
        Intent mIntent=new Intent();

        mIntent.putExtra("exposure_duration",etexposure_duration.getText().toString());
        mIntent.putExtra("tag",ettag.getText().toString());
        mIntent.putExtra("server_url",etserver_url.getText().toString());
        mIntent.putExtra("capture_mode",spcapture_mode.getSelectedItem().toString());
        mIntent.putExtra("store_to_gallery",strstore_to_gallery.toString());


        this.setResult(RESULT_OK,mIntent);
        finish();
    }
}
