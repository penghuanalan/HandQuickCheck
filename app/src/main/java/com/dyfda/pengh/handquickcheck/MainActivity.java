package com.dyfda.pengh.handquickcheck;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private EditText etUserName,etPassword;
    private Context context=this;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp=getPreferences(Context.MODE_PRIVATE);
        initView();
    }

    private void initView() {

        etUserName=  findViewById(R.id.et_user_name);
        etPassword=  findViewById(R.id.et_password);
        etUserName.setText(sp.getString("userNmae",""));
        etPassword.setText(sp.getString("etPassword",""));

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){

            case R.id.btn_login:
                if(TextUtils.isEmpty(etUserName.getText())||TextUtils.isEmpty(etPassword.getText())){
                    Toast.makeText(context,"用户名和密码不能为空",Toast.LENGTH_SHORT).show();

                }else{
                    sp.edit().putString("userNmae",etUserName.getText().toString().trim()).apply();
                    sp.edit().putString("password",etPassword.getText().toString().trim()).apply();
                    startActivity(new Intent(context,DetecitonActivity.class));
                        finish();
                }


                break;
        }
    }
}
